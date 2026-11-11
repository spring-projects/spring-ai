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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
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
 * <li>With {@link Builder#sessionIdInToolContext(boolean)}, puts the session id into the
 * tool context of the call, so that a tool that reads the session, the session recall
 * tools for example, finds it without the caller passing it a second time. Off by
 * default, because the tool context goes to every tool of the call.</li>
 * <li>Before a new turn, answers the tool calls the conversation ends with when they have
 * no results, with {@link #UNANSWERED_TOOL_RESULT}: a tool call that waited for a
 * person's confirmation and never got one, or a loop that failed after the reply was
 * stored. The log then keeps the call with a result, so the model sees what it asked for
 * and the memory advisor has nothing to drop from the prompt. A request that brings
 * results for those calls, as the resume of a confirmation does, is left alone. The
 * advisor cannot tell an abandoned call from one whose tool still runs in another call on
 * the same session, so a session takes one call at a time.</li>
 * <li>After a loop that ended on a {@code returnDirect} tool or a breached tool call
 * limit, stores the reply's tool results, which no model round stored.</li>
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
 * alone keeps the history, without compaction.
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

	/** The result a tool call gets in the log when the loop ended before it ran. */
	public static final String NOT_RUN_TOOL_RESULT = "The tool call did not run: the tool call limit was reached.";

	/**
	 * The result a tool call gets in the log when a new turn starts and the call still
	 * has none: the turn that made it paused for a confirmation that never came, or
	 * failed before its results were stored. It says what the log knows and no more: the
	 * tool may have run.
	 */
	public static final String UNANSWERED_TOOL_RESULT = "No result was recorded for this tool call.";

	/**
	 * How often the version-checked write of {@link #UNANSWERED_TOOL_RESULT} is tried
	 * when other appends, a compaction for example, keep moving the log.
	 */
	private static final int UNANSWERED_WRITE_ATTEMPTS = 3;

	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	/**
	 * Carries the caller's context, such as the security context, to the session calls of
	 * a stream and to an async compaction: the thread locals of a blocking caller, the
	 * Reactor context of a reactive one, the latter winning where both hold a key.
	 */
	private static final ContextSnapshotFactory CONTEXT_SNAPSHOTS = ContextSnapshotFactory.builder().build();

	private final SessionService sessionService;

	private final int order;

	private final Scheduler scheduler;

	@Nullable private final CompactionTrigger compactionTrigger;

	@Nullable private final CompactionStrategy compactionStrategy;

	private final boolean asyncCompaction;

	private final boolean sessionIdInToolContext;

	/**
	 * The sessions an asynchronous compaction of this advisor runs on, so it runs once
	 * each.
	 */
	private final Set<String> compacting = ConcurrentHashMap.newKeySet();

	private SessionTurnAdvisor(Builder builder) {
		this.sessionService = builder.sessionService;
		this.order = builder.order;
		this.scheduler = builder.scheduler;
		this.compactionTrigger = builder.compactionTrigger;
		this.compactionStrategy = builder.compactionStrategy;
		this.asyncCompaction = builder.asyncCompaction;
		this.sessionIdInToolContext = builder.sessionIdInToolContext;
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
		answerUnansweredCalls(sessionId, request);
		ChatClientResponse response = chain.nextCall(withSessionIdInToolContext(request, sessionId));
		complete(sessionId, response.chatResponse());
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		Assert.notNull(request, "request must not be null");
		Assert.notNull(chain, "chain must not be null");
		String sessionId = SessionContexts.sessionId(request.context());
		// The session calls run on the scheduler, never on the model's thread, with the
		// caller's context (the security context, the current observation) restored
		// around them and around the subscription of the rest of the chain: the thread
		// locals of a blocking caller, and what a reactive caller carries in the Reactor
		// context
		return Flux.deferContextual(contextView -> {
			ContextSnapshot context = CONTEXT_SNAPSHOTS.captureAll(contextView);
			return Mono.just(request).publishOn(this.scheduler).flatMapMany(r -> {
				// A returnDirect result or a breached limit ends the stream on a chunk
				// of its own
				AtomicReference<@Nullable ChatResponse> unanswered = new AtomicReference<>();
				Flux<ChatClientResponse> rest = chain.nextStream(withSessionIdInToolContext(r, sessionId))
					.doOnNext(response -> {
						if (endsWithoutModel(response.chatResponse())) {
							unanswered.set(response.chatResponse());
						}
					})
					.publishOn(this.scheduler)
					.doOnComplete(context.wrap(() -> complete(sessionId, unanswered.get())));
				return Flux.from(subscriber -> {
					try (ContextSnapshot.Scope ignored = context.setThreadLocals()) {
						this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, r.context()));
						answerUnansweredCalls(sessionId, r);
						rest.subscribe(subscriber);
					}
				});
			});
		});
	}

	/**
	 * The request with the session id in the tool context of its options, under
	 * {@link SessionMemoryAdvisor#SESSION_ID_CONTEXT_KEY}, when the advisor is set to put
	 * it there. The tool-calling loop runs inside this advisor and takes the tool context
	 * from the request it gets, so the tools of the call see the id. A value the caller
	 * set under the key stands, the other entries are kept, and options that take no tool
	 * context are left alone.
	 */
	private ChatClientRequest withSessionIdInToolContext(ChatClientRequest request, String sessionId) {
		if (!this.sessionIdInToolContext
				|| !(request.prompt().getOptions() instanceof ToolCallingChatOptions options)) {
			return request;
		}
		Map<String, Object> toolContext = options.getToolContext();
		if (toolContext != null && toolContext.containsKey(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY)) {
			return request;
		}
		Map<String, Object> withSessionId = (toolContext != null) ? new HashMap<>(toolContext) : new HashMap<>();
		withSessionId.put(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId);
		ChatOptions withToolContext = options.mutate().toolContext(withSessionId).build();
		return request.mutate().prompt(request.prompt().mutate().chatOptions(withToolContext).build()).build();
	}

	/** Stores the results of a loop that ended without a model round, then compacts. */
	private void complete(String sessionId, @Nullable ChatResponse response) {
		if (response != null && endsWithoutModel(response)) {
			storeResults(sessionId, response);
		}
		compactIfTriggered(sessionId);
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
	 * Answers the tool calls the conversation ends with when they have no results, before
	 * a new turn adds to the log: the newest message is a reply with tool calls, and the
	 * request brings no result for any of them. Every call gets
	 * {@link #UNANSWERED_TOOL_RESULT}. The append is version-checked, so it lands only
	 * while the reply is still the newest message. When the log moved, the reply is read
	 * again: if another writer stored results, a resume on another instance for example,
	 * nothing is written; if it was an unrelated append, a compaction for example, the
	 * write is tried again, a few times. It has to land now: once the new turn's message
	 * follows the reply, no later turn finds it. Best-effort all the same: a failure is
	 * logged, and the memory advisor keeps the unanswered call out of the prompt.
	 */
	private void answerUnansweredCalls(String sessionId, ChatClientRequest request) {
		try {
			// One read for a turn that finds nothing open, which is nearly every turn
			SessionEvent open = newestConversationEvent(sessionId);
			if (open == null || !open.hasToolCalls()) {
				return;
			}
			for (int attempt = 0; attempt < UNANSWERED_WRITE_ATTEMPTS; attempt++) {
				// The version first, then the reply again: the append must not land
				// after results another writer stored in between
				long version = this.sessionService.getEventVersion(sessionId);
				open = newestConversationEvent(sessionId);
				if (open == null || !open.hasToolCalls()) {
					return;
				}
				List<AssistantMessage.ToolCall> calls = ((AssistantMessage) open.getMessage()).getToolCalls();
				if (answersAny(request, calls)) {
					return;
				}
				SessionEvent answers = answersOf(open, "-unanswered",
						Collections.nCopies(calls.size(), UNANSWERED_TOOL_RESULT));
				if (this.sessionService.appendEvents(sessionId, List.of(answers), version)) {
					logger.debug("Answered {} tool call(s) of session [{}] that had no results", calls.size(),
							sessionId);
					return;
				}
			}
			logger.warn("Could not answer the unanswered tool calls of session [{}]: the log kept changing", sessionId);
		}
		catch (RuntimeException ex) {
			logger.warn("Could not answer the unanswered tool calls of session [{}]: {}", sessionId, ex.getMessage());
		}
	}

	/**
	 * The event that answers the reply's tool calls, one answer per call in the order of
	 * the calls, with an id that derives from the reply's.
	 */
	private static SessionEvent answersOf(SessionEvent reply, String idSuffix, List<String> answers) {
		List<AssistantMessage.ToolCall> calls = ((AssistantMessage) reply.getMessage()).getToolCalls();
		List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
		for (int i = 0; i < calls.size(); i++) {
			responses.add(new ToolResponseMessage.ToolResponse(calls.get(i).id(), calls.get(i).name(), answers.get(i)));
		}
		return SessionEvent.builder()
			.id(reply.getId() + idSuffix)
			.sessionId(reply.getSessionId())
			.message(ToolResponseMessage.builder().responses(responses).build())
			.build();
	}

	/** Whether the request carries a tool result for one of the calls. */
	private static boolean answersAny(ChatClientRequest request, List<AssistantMessage.ToolCall> calls) {
		Set<String> ids = new HashSet<>();
		calls.forEach(call -> ids.add(call.id()));
		return request.prompt()
			.getInstructions()
			.stream()
			.filter(ToolResponseMessage.class::isInstance)
			.map(ToolResponseMessage.class::cast)
			.flatMap(results -> results.getResponses().stream())
			.anyMatch(result -> ids.contains(result.id()));
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
			int calls = ((AssistantMessage) stored.getMessage()).getToolCalls().size();
			this.sessionService.appendEvent(answersOf(stored, "-results", answers(response, calls)));
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
			Runnable task = CONTEXT_SNAPSHOTS.captureAll().wrap(() -> {
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

		private boolean sessionIdInToolContext;

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
		 * Whether the advisor puts the session id into the tool context of the call,
		 * under {@link SessionMemoryAdvisor#SESSION_ID_CONTEXT_KEY}; {@code false} by
		 * default. A tool that reads the session, such as
		 * {@link org.springframework.ai.session.tool.SessionRecallTools}, then finds the
		 * id without the caller passing it with {@code toolContext(..)} too. A value the
		 * caller sets under the key stands.
		 * <p>
		 * The tool context goes to every tool of the call, so turning this on has three
		 * effects beyond the session tools. Every tool that takes a {@code ToolContext}
		 * sees the session id. The tools of an MCP server get it as request metadata,
		 * unless a {@code ToolContextToMcpMetaConverter} leaves it out. And a tool that
		 * takes a {@code ToolContext} is no longer refused when the call passes no tool
		 * context: it runs with one that holds the session id only.
		 */
		public Builder sessionIdInToolContext(boolean sessionIdInToolContext) {
			this.sessionIdInToolContext = sessionIdInToolContext;
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
