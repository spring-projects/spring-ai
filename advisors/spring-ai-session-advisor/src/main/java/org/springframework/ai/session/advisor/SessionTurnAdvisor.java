/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.session.advisor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.context.ContextSnapshot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Runs each {@code ChatClient} call as one turn of the session. A turn is one user
 * message and everything up to the next: the tool rounds and the reply, the unit the chat
 * memory's turn-boundary eviction works with too. The advisor sits outside the
 * tool-calling loop (order {@link #DEFAULT_ORDER}), so it sees the request before the
 * first round and the turn's final response after the last one, however the loop ended;
 * what happens once per turn is its, what happens once per model round is the
 * {@link SessionMemoryAdvisor}'s, inside the loop.
 * <ol>
 * <li>Finds the session or creates it on first use, with the metadata from
 * {@link SessionMemoryAdvisor#SESSION_METADATA_CONTEXT_KEY}, such as the agent
 * attributes.</li>
 * <li>Starts the turn with {@link TurnService#start}, under the run id the caller passed
 * in {@link #RUN_ID_CONTEXT_KEY} or under a new one. The forwarded request carries the
 * effective run id, so the advisors inside the loop can read it. A session on which a
 * turn already runs is busy: the call fails with {@link TurnInProgressException} and
 * nothing is queued.</li>
 * <li>After a loop that ended on a {@code returnDirect} tool or a breached tool call
 * limit, stores the reply's tool results, which no model round stored.</li>
 * <li>Ends the turn with {@link TurnService#end}: with {@link StopReason#END_TURN} when
 * the call returned, with {@link StopReason#ERROR} when it threw. A stream the client
 * cancelled ends with {@link StopReason#CANCELLED}, so the log tells a dropped connection
 * from a stop the session was asked for. When an {@link TurnService#interrupt interrupt}
 * ended the turn first, that end stands and the advisor leaves the status alone.</li>
 * <li>Compacts the session once the call has returned, if a
 * {@link Builder#compactionTrigger trigger} fires: before the reply is returned, or after
 * it on the scheduler with {@link Builder#asyncCompaction(boolean)}. Compaction is
 * best-effort: a failure is logged and the reply returned, and it is retried after the
 * next turn. It appends a boundary with an optimistic compare-and-swap, so of two
 * concurrent compactions only the first lands; a compaction that a new turn's writes
 * overtake keeps them in the view and lands with the same summary.</li>
 * </ol>
 * On its own the advisor stores no conversation: it needs the memory advisor, and
 * {@link SessionAdvisors} builds the two together at their orders. The memory advisor
 * alone keeps the history, without compaction or turn status. Inside the loop the status
 * is read only on request: the {@link SessionInterruptAdvisor} stops the loop at the
 * first round after an interrupt.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionTurnAdvisor implements CallAdvisor, StreamAdvisor {

	/**
	 * Outside the tool-calling advisor ({@code HIGHEST_PRECEDENCE + 300}), so the loop
	 * runs inside the turn.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

	/**
	 * Context key of the run id a call runs under: the token that owns the turn. A
	 * harness passes its own run id,
	 * {@code .advisors(a -> a.param(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY, runId))}, or
	 * the advisor generates a UUID per call. Either way the request the advisor forwards
	 * carries the effective id, so the advisors inside the loop and the response context
	 * have it. The session does not interpret it; see {@link TurnService#start}.
	 */
	public static final String RUN_ID_CONTEXT_KEY = "chat_memory_session_run_id";

	/**
	 * The lease a call takes on the session's turn unless
	 * {@link Builder#turnTimeout(Duration)} says otherwise: thirty minutes, well above
	 * any {@code ChatClient} call with its tool loop, so a lease that ran out means the
	 * call's JVM is gone, not slow.
	 */
	public static final Duration DEFAULT_TURN_TIMEOUT = Duration.ofMinutes(30);

	/** The result a tool call gets in the log when the loop ended before it ran. */
	public static final String NOT_RUN_TOOL_RESULT = "The tool call did not run: the tool call limit was reached.";

	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	private final SessionService sessionService;

	private final TurnService turns;

	private final int order;

	private final Scheduler scheduler;

	@Nullable private final CompactionTrigger compactionTrigger;

	@Nullable private final CompactionStrategy compactionStrategy;

	private final boolean asyncCompaction;

	@Nullable private final Duration turnTimeout;

	/**
	 * The sessions an asynchronous compaction of this advisor runs on, so it runs once
	 * each.
	 */
	private final Set<String> compacting = ConcurrentHashMap.newKeySet();

	private SessionTurnAdvisor(Builder builder) {
		this.sessionService = builder.sessionService;
		this.turns = this.sessionService.turns();
		this.order = builder.order;
		this.scheduler = builder.scheduler;
		this.compactionTrigger = builder.compactionTrigger;
		this.compactionStrategy = builder.compactionStrategy;
		this.asyncCompaction = builder.asyncCompaction;
		this.turnTimeout = builder.turnTimeout;
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		Assert.notNull(request, "request must not be null");
		Assert.notNull(chain, "chain must not be null");
		String sessionId = SessionContexts.sessionId(request.context());
		this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, request.context()));
		String runId = start(sessionId, request.context());
		ChatClientResponse response;
		boolean returned = false;
		try {
			response = chain.nextCall(withRunId(request, runId));
			returned = true;
		}
		finally {
			if (!returned) {
				end(sessionId, runId, StopReason.ERROR);
			}
		}
		complete(sessionId, runId, response.chatResponse());
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		Assert.notNull(request, "request must not be null");
		Assert.notNull(chain, "chain must not be null");
		String sessionId = SessionContexts.sessionId(request.context());
		// The session calls run on the scheduler, never on the model's thread, with the
		// caller's context restored around them and around the subscription of the rest
		// of the chain
		return Flux.deferContextual(contextView -> {
			ContextSnapshot context = SessionContexts.snapshot(contextView);
			return Mono.just(request).publishOn(this.scheduler).flatMapMany(r -> Flux.from(subscriber -> {
				context.wrap(() -> {
					this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, r.context()));
					String runId = start(sessionId, r.context());
					// A returnDirect result or a breached limit ends the stream on a
					// chunk of its own
					AtomicReference<@Nullable ChatResponse> unanswered = new AtomicReference<>();
					// The turn ends once, on the first of complete, error and cancel
					AtomicBoolean ended = new AtomicBoolean();
					Flux<ChatClientResponse> rounds;
					try {
						rounds = chain.nextStream(withRunId(r, runId));
					}
					catch (RuntimeException ex) {
						// The rest of the chain failed before its stream existed, so no
						// operator below sees the error: the turn ends here
						end(sessionId, runId, StopReason.ERROR);
						throw ex;
					}
					rounds.doOnNext(response -> {
						if (endsWithoutModel(response.chatResponse())) {
							unanswered.set(response.chatResponse());
						}
					}).publishOn(this.scheduler).doOnComplete(context.wrap(() -> {
						if (ended.compareAndSet(false, true)) {
							complete(sessionId, runId, unanswered.get());
						}
					})).doOnError(ex -> {
						if (ended.compareAndSet(false, true)) {
							context.wrap(() -> end(sessionId, runId, StopReason.ERROR)).run();
						}
					}).doOnCancel(() -> {
						if (ended.compareAndSet(false, true)) {
							endOnCancel(context.wrap(() -> end(sessionId, runId, StopReason.CANCELLED)));
						}
					}).subscribe(subscriber);
				}).run();
			}));
		});
	}

	/**
	 * Ends a cancelled turn on the scheduler, since a cancel may arrive on the model's
	 * thread, through an operator downstream that has what it needs, and a blocking write
	 * does not belong there. When the scheduler takes no more work, because it was
	 * disposed at shutdown for example, the write runs on the cancelling thread instead:
	 * one blocking write there is better than a turn left running.
	 */
	private void endOnCancel(Runnable end) {
		try {
			this.scheduler.schedule(end);
		}
		catch (RejectedExecutionException ex) {
			logger.debug("The scheduler rejected the end of a cancelled turn; ending it on the cancelling thread");
			end.run();
		}
	}

	/**
	 * Starts the turn under the run id of the context, or under a new one, and returns
	 * the effective run id.
	 * @throws TurnInProgressException if a turn runs on the session
	 */
	private String start(String sessionId, Map<String, @Nullable Object> context) {
		TurnStatus started = this.turns.start(sessionId, SessionContexts.runId(context), this.turnTimeout);
		return Objects.requireNonNull(started.runId(), "a running turn has a run id");
	}

	/**
	 * Returns the request with the effective run id on its context, for the advisors
	 * inside the loop.
	 */
	private static ChatClientRequest withRunId(ChatClientRequest request, String runId) {
		return request.mutate().context(RUN_ID_CONTEXT_KEY, runId).build();
	}

	/**
	 * Completes the turn: stores the tool results of a loop that ended without a model
	 * round, ends the turn with {@link StopReason#END_TURN}, then compacts if the trigger
	 * fires.
	 */
	private void complete(String sessionId, String runId, @Nullable ChatResponse response) {
		if (response != null && endsWithoutModel(response) && ownsNewestTurn(sessionId, runId)) {
			storeResults(sessionId, response);
		}
		end(sessionId, runId, StopReason.END_TURN);
		compactIfTriggered(sessionId);
	}

	/**
	 * Ends the turn with the reason. When another writer ended it first, an interrupt for
	 * example, the service's own-turn guard writes nothing and that end stands; the
	 * advisor logs it at debug. Best-effort: a failure is logged and the reply is still
	 * returned, the bookkeeping does not lose it.
	 */
	private void end(String sessionId, String runId, StopReason stopReason) {
		try {
			TurnStatus status = this.turns.end(sessionId, runId, stopReason);
			if (!runId.equals(status.runId()) || status.stopReason() != stopReason) {
				logger.debug("The turn of session [{}] under run [{}] was ended by another writer; the status is {}",
						sessionId, runId, status);
			}
		}
		catch (RuntimeException ex) {
			logger.warn("Could not end the turn of session [{}] under run [{}] with {}: {}", sessionId, runId,
					stopReason, ex.getMessage());
		}
	}

	/**
	 * Whether the newest status event of the session names this run, running or ended: no
	 * later turn started, so the newest reply in the log is this call's. A call that lost
	 * the turn to a lease takeover, or whose session a later turn runs on, writes no
	 * results, since the reply it would answer is the later turn's.
	 */
	private boolean ownsNewestTurn(String sessionId, String runId) {
		TurnStatus newest = this.turns.status(sessionId);
		if (runId.equals(newest.runId())) {
			return true;
		}
		logger.debug("A later turn runs on session [{}] under run [{}]; run [{}] leaves its log alone", sessionId,
				newest.runId(), runId);
		return false;
	}

	/**
	 * Whether the loop ended on a reply's tool calls without a model round after them: a
	 * {@code returnDirect} result or a breached tool call limit. No round stored the
	 * results then.
	 */
	private static boolean endsWithoutModel(@Nullable ChatResponse response) {
		return response != null && response.getResults()
			.stream()
			.map(Generation::getMetadata)
			.anyMatch(metadata -> ToolExecutionResult.FINISH_REASON.equals(metadata.getFinishReason())
					|| ToolCallLimitExceededException.FINISH_REASON.equals(metadata.getFinishReason()));
	}

	/**
	 * Answers the stored reply's tool calls in the log after a loop that ended without a
	 * model round: with the results that came back directly, or, for a breached limit,
	 * with the results of the calls that ran, the breach for the call that broke it and a
	 * note for the calls after it. The results come in the order of the calls. Once: the
	 * id derives from the reply's.
	 */
	private void storeResults(String sessionId, ChatResponse response) {
		try {
			SessionEvent stored = newestConversationEvent(sessionId);
			if (stored == null || !stored.hasToolCalls()) {
				return;
			}
			List<AssistantMessage.ToolCall> calls = ((AssistantMessage) stored.getMessage()).getToolCalls();
			List<String> answers = answers(response, calls.size());
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (int i = 0; i < calls.size(); i++) {
				responses
					.add(new ToolResponseMessage.ToolResponse(calls.get(i).id(), calls.get(i).name(), answers.get(i)));
			}
			this.sessionService.appendEvent(SessionEvent.builder()
				.id(stored.getId() + "-results")
				.sessionId(sessionId)
				.message(ToolResponseMessage.builder().responses(responses).build())
				.build());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not store the tool results of session [{}]: {}", sessionId, ex.getMessage());
		}
	}

	/** One answer per call, in the order of the calls. */
	private static List<String> answers(ChatResponse response, int calls) {
		List<String> answers = new ArrayList<>(calls);
		for (Generation generation : response.getResults()) {
			ChatGenerationMetadata metadata = generation.getMetadata();
			if (ToolExecutionResult.FINISH_REASON.equals(metadata.getFinishReason())) {
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
			else if (ToolCallLimitExceededException.FINISH_REASON.equals(metadata.getFinishReason())) {
				if (metadata
					.get(ToolCallLimitExceededException.METADATA_PARTIAL_TOOL_RESPONSES) instanceof List<?> ran) {
					ran.stream()
						.filter(ToolResponseMessage.ToolResponse.class::isInstance)
						.map(result -> ((ToolResponseMessage.ToolResponse) result).responseData())
						.forEach(answers::add);
				}
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
		}
		while (answers.size() < calls) {
			answers.add(NOT_RUN_TOOL_RESULT);
		}
		return answers.subList(0, calls);
	}

	/**
	 * Compacts the session if the trigger fires: now, or on the scheduler when compaction
	 * is asynchronous and none runs on the session yet.
	 */
	private void compactIfTriggered(String sessionId) {
		if (this.compactionTrigger == null || this.compactionStrategy == null) {
			return;
		}
		if (!this.asyncCompaction) {
			compact(sessionId, this.compactionTrigger, this.compactionStrategy);
		}
		else if (this.compacting.add(sessionId)) {
			CompactionTrigger trigger = this.compactionTrigger;
			CompactionStrategy strategy = this.compactionStrategy;
			// The caller's thread-local context goes with it: an authorizing
			// SessionService
			// takes the caller from the security context, and tracing keeps its parent
			Runnable task = SessionContexts.snapshot(null).wrap(() -> {
				try {
					compact(sessionId, trigger, strategy);
				}
				finally {
					this.compacting.remove(sessionId);
				}
			});
			try {
				this.scheduler.schedule(task);
			}
			catch (RuntimeException ex) {
				this.compacting.remove(sessionId);
				logger.warn("Could not schedule the compaction of session [{}]; it will be retried after the next "
						+ "turn", sessionId, ex);
			}
		}
	}

	/** Whether an asynchronous compaction of this advisor runs on the session. */
	boolean isCompacting(String sessionId) {
		return this.compacting.contains(sessionId);
	}

	/** Compacts the session if the trigger fires; a failure is logged, not thrown. */
	private void compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
		try {
			this.sessionService.compact(sessionId, trigger, strategy);
		}
		catch (RuntimeException ex) {
			logger.warn("Compaction failed for session [{}]; the event log is unchanged and compaction will be "
					+ "retried after the next turn", sessionId, ex);
		}
	}

	/**
	 * The newest message of the active view that is not a system message, or
	 * {@code null}.
	 */
	private @Nullable SessionEvent newestConversationEvent(String sessionId) {
		List<SessionEvent> events = this.sessionService.getEvents(sessionId,
				EventFilter.active().toBuilder().lastN(1).build());
		for (int i = events.size() - 1; i >= 0; i--) {
			if (events.get(i).hasMessage() && events.get(i).getMessageType() != MessageType.SYSTEM) {
				return events.get(i);
			}
		}
		return null;
	}

	public static final class Builder {

		private final SessionService sessionService;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private boolean asyncCompaction;

		@Nullable private Duration turnTimeout = DEFAULT_TURN_TIMEOUT;

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		/**
		 * The order; {@link #DEFAULT_ORDER} by default. Keep it before the tool-calling
		 * advisor, so the loop runs inside the turn.
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/** The scheduler the session writes of a streamed call run on. */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * When to compact once a turn has ended; set together with
		 * {@link #compactionStrategy}.
		 */
		public Builder compactionTrigger(CompactionTrigger trigger) {
			Assert.notNull(trigger, "trigger must not be null");
			this.compactionTrigger = trigger;
			return this;
		}

		/** How to compact; set together with {@link #compactionTrigger}. */
		public Builder compactionStrategy(CompactionStrategy strategy) {
			Assert.notNull(strategy, "strategy must not be null");
			this.compactionStrategy = strategy;
			return this;
		}

		/**
		 * Whether compaction runs after the call returns, on the {@link #scheduler}, so a
		 * summarizing strategy adds no model call to the reply's latency; {@code false}
		 * by default. A {@code call()} runs it with the caller's thread-local context,
		 * the security context for example, captured with Micrometer's context
		 * propagation; a {@code stream()} ends on the scheduler already. One compaction
		 * per session runs at a time from this advisor; a new turn that writes to the
		 * session meanwhile stays in the view, the compaction lands rebased onto its
		 * events with the same summary. Only another compaction's boundary, or turns that
		 * keep overtaking it, make it stand down until the next turn.
		 */
		public Builder asyncCompaction(boolean asyncCompaction) {
			this.asyncCompaction = asyncCompaction;
			return this;
		}

		/**
		 * How long a call may hold the session's turn before another call may take it
		 * over; {@link #DEFAULT_TURN_TIMEOUT} by default, {@code null} for no lease.
		 * <p>
		 * The lease is what frees a session after a crash. A JVM that dies between the
		 * start and the end of a turn leaves the session {@code RUNNING} under a run that
		 * no longer exists; without a lease every later call fails with
		 * {@link TurnInProgressException} until someone calls
		 * {@link TurnService#interrupt}. With one, the first call after the lease ran out
		 * ends the dead turn with {@link StopReason#EXPIRED} and runs. A call that
		 * outlives its lease loses the turn the same way, so set it well above the
		 * longest call the client makes, tool loop included; the HTTP timeouts of the
		 * model client bound that in practice.
		 * @param turnTimeout the lease, or {@code null} for a turn that never expires
		 * @throws IllegalArgumentException if the timeout is zero or negative
		 */
		public Builder turnTimeout(@Nullable Duration turnTimeout) {
			Assert.isTrue(turnTimeout == null || turnTimeout.compareTo(Duration.ZERO) > 0,
					"turnTimeout must be positive");
			this.turnTimeout = turnTimeout;
			return this;
		}

		public SessionTurnAdvisor build() {
			if ((this.compactionTrigger == null) != (this.compactionStrategy == null)) {
				throw new IllegalArgumentException(
						"compactionTrigger and compactionStrategy must be set together — set both or neither");
			}
			return new SessionTurnAdvisor(this);
		}

	}

}
