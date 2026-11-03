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
import java.util.Objects;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.ai.session.question.ToolConfirmationQuestion;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnParkedException;
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
 * nothing is queued. A session parked on a tool confirmation is resumed instead of
 * started, see below.</li>
 * <li>After a loop that ended on a {@code returnDirect} tool or a breached tool call
 * limit, stores the reply's tool results, which no model round stored.</li>
 * <li>Ends the turn with {@link TurnService#end}: with {@link StopReason#END_TURN} when
 * the call returned, with {@link StopReason#ERROR} when it threw. A stream the client
 * cancelled ends with {@link StopReason#CANCELLED}, so the log tells a dropped connection
 * from a stop the session was asked for. A turn that fails or is cancelled while the
 * reply's tool calls are still open, because a tool threw after the reply was stored,
 * answers them with {@link #UNANSWERED_TOOL_RESULT} first, so the log never keeps a call
 * without its results. When an {@link TurnService#interrupt interrupt} ended the turn
 * first, that end stands and the advisor leaves the status alone.</li>
 * <li>Parks the turn instead when the loop paused on a tool call that needs a person's
 * confirmation: with {@link TurnService#park}, on a {@link ToolConfirmationQuestion}, the
 * shape every harness records for a tool confirmation, which lists the reply's calls,
 * each with the key a decision names it by and whether it waits for one, and names the
 * stored reply. The status is {@link StopReason#REQUIRES_ACTION}; nothing is compacted
 * while the session waits. The next request on the session resumes it with
 * {@link TurnService#resume}. One that carries the caller's {@link ToolCallDecisions}
 * under {@link #TOOL_CALL_DECISIONS_CONTEXT_KEY} ({@link SessionAdvisors#decisions})
 * records them as the answer, runs the allowed calls through the
 * {@link Builder#toolCallingManager tool calling manager} with the tools the request
 * carries, stores the results after the reply, and lets the model continue; it carries no
 * new user message. When an allowed call's tool returns directly, its results are the
 * call's response and the model is not called, as in a loop that never paused. The
 * decisions are checked before anything is written, with the rules the manager applies:
 * every call decided, every key known, every allowed call's tool in the request.
 * Decisions that fail a rule are rejected with {@link IllegalArgumentException} and the
 * question stays open. One that carries new text instead moves on: every call is denied
 * as {@link #ABANDONED_DENY_MESSAGE abandoned}, the results are stored, and the text runs
 * in the resumed turn. Whenever a turn ends without a result for the reply's calls,
 * because the pause was lost to an interrupt or a resumed turn failed, the calls are
 * answered with {@link #UNANSWERED_TOOL_RESULT}, so the log never keeps a tool call
 * without its results.</li>
 * <li>Continues the session's last turn instead of starting one when the request asks for
 * it with {@link #RESUME_CONTEXT_KEY} ({@link SessionAdvisors#resume}), and carries no
 * new user message: a turn that an interrupt, a dropped stream, an error or an expired
 * lease ended before the model answered. A new run starts the turn again with
 * {@link TurnService#start}, and no user message is added, so the log keeps one turn: a
 * turn begins with a user message only. What the continued turn does follows from the
 * newest message of the active view. After a user message or tool results the model runs
 * a round over the stored history, which the memory advisor prepends to the empty prompt,
 * as the loop would have after the results. After a reply whose calls got no results,
 * which a crash or a tool failure leaves, the calls are answered with
 * {@link #UNANSWERED_TOOL_RESULT} first. After a reply without tool calls there is
 * nothing to run: the model had answered before the stop landed, so the turn is settled,
 * it starts and ends on that reply, which the call returns. A turn that ended with
 * {@link StopReason#END_TURN} is not continued, {@link IllegalStateException}; a parked
 * session is answered with decisions instead, {@link TurnParkedException}.</li>
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
	 * Context key of the caller's {@link ToolCallDecisions} on the tool calls a parked
	 * session waits on, set with {@link SessionAdvisors#decisions}. A request that
	 * carries them resumes the session instead of starting a turn, and carries no new
	 * user message.
	 */
	public static final String TOOL_CALL_DECISIONS_CONTEXT_KEY = "chat_memory_tool_call_decisions";

	/**
	 * Context key that asks the advisor to continue the session's last turn where it
	 * stopped instead of starting one, set with {@link SessionAdvisors#resume}; the value
	 * is {@code Boolean.TRUE}. The request carries no new user message: the turn carries
	 * on over the stored history, see the class description.
	 */
	public static final String RESUME_CONTEXT_KEY = "chat_memory_resume_turn";

	/**
	 * The message a denied call answers the model with when the caller moved on from a
	 * parked session with new text instead of deciding.
	 */
	public static final String ABANDONED_DENY_MESSAGE = "The user moved on without deciding on this tool call.";

	/**
	 * The lease a call takes on the session's turn unless
	 * {@link Builder#turnTimeout(Duration)} says otherwise: thirty minutes, well above
	 * any {@code ChatClient} call with its tool loop, so a lease that ran out means the
	 * call's JVM is gone, not slow.
	 */
	public static final Duration DEFAULT_TURN_TIMEOUT = Duration.ofMinutes(30);

	/** The result a tool call gets in the log when the loop ended before it ran. */
	public static final String NOT_RUN_TOOL_RESULT = "The tool call did not run: the tool call limit was reached.";

	/**
	 * The result stored for a tool call whose turn ended without one: the pause could not
	 * be recorded, because an interrupt ended the turn first, or the resumed turn failed
	 * before its results were stored. The log keeps the call with this result, so the
	 * model learns that the call did not complete and the next prompt stays whole.
	 */
	public static final String UNANSWERED_TOOL_RESULT = "The tool call did not complete: the turn ended before its result could be recorded.";

	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	private final SessionService sessionService;

	private final TurnService turns;

	private final int order;

	private final Scheduler scheduler;

	@Nullable private final Duration turnTimeout;

	private final ToolConfirmationResume confirmations;

	private final CompactionScheduler compaction;

	private SessionTurnAdvisor(Builder builder) {
		this.sessionService = builder.sessionService;
		this.turns = this.sessionService.turns();
		this.order = builder.order;
		this.scheduler = builder.scheduler;
		this.turnTimeout = builder.turnTimeout;
		this.confirmations = new ToolConfirmationResume(this.sessionService, builder.toolCallingManager,
				builder.turnTimeout);
		this.compaction = new CompactionScheduler(this.sessionService, builder.compactionTrigger,
				builder.compactionStrategy, builder.asyncCompaction, builder.scheduler);
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
		TurnIntent intent = TurnIntent.of(sessionId, request);
		this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, request.context()));
		Turn turn = beginTurn(sessionId, request, intent);
		String runId = turn.runId();
		ChatClientResponse response = turn.direct(request);
		if (response == null) {
			boolean returned = false;
			try {
				response = chain.nextCall(SessionContexts.withRunId(request, runId));
				returned = true;
			}
			finally {
				if (!returned) {
					finish(sessionId, runId, Outcome.FAILED, null);
				}
			}
		}
		finish(sessionId, runId, Outcome.COMPLETED, response.chatResponse());
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
					TurnIntent intent = TurnIntent.of(sessionId, r);
					this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, r.context()));
					Turn turn = beginTurn(sessionId, r, intent);
					String runId = turn.runId();
					Flux<ChatClientResponse> rounds;
					try {
						ChatClientResponse direct = turn.direct(r);
						rounds = (direct != null) ? Flux.just(direct)
								: chain.nextStream(SessionContexts.withRunId(r, runId));
					}
					catch (RuntimeException ex) {
						// The rest of the chain failed before its stream existed, so no
						// operator below sees the error: the turn ends here
						finish(sessionId, runId, Outcome.FAILED, null);
						throw ex;
					}
					// A returnDirect result, a breached limit or a pause ends the stream
					// on a chunk of its own: the one the turn completes with
					AtomicReference<@Nullable ChatResponse> ending = new AtomicReference<>();
					// The turn ends once, on the first of complete, error and cancel
					AtomicBoolean ended = new AtomicBoolean();
					rounds.doOnNext(response -> {
						ChatResponse chunk = response.chatResponse();
						if (endsWithoutModel(chunk) || ToolCallDecisions.isAwaitingDecision(chunk)) {
							ending.set(chunk);
						}
					}).publishOn(this.scheduler).doOnComplete(context.wrap(() -> {
						if (ended.compareAndSet(false, true)) {
							finish(sessionId, runId, Outcome.COMPLETED, ending.get());
						}
					})).doOnError(ex -> {
						if (ended.compareAndSet(false, true)) {
							context.wrap(() -> finish(sessionId, runId, Outcome.FAILED, null)).run();
						}
					}).doOnCancel(() -> {
						if (ended.compareAndSet(false, true)) {
							endOnCancel(context.wrap(() -> finish(sessionId, runId, Outcome.CANCELLED, null)));
						}
					}).subscribe(subscriber);
				}).run();
			}));
		});
	}

	/**
	 * Finishes a cancelled turn on the scheduler, since a cancel may arrive on the
	 * model's thread, through an operator downstream that has what it needs, and a
	 * blocking write does not belong there. When the scheduler takes no more work,
	 * because it was disposed at shutdown for example, the write runs on the cancelling
	 * thread instead: one blocking write there is better than a turn left running.
	 */
	private void endOnCancel(Runnable finish) {
		try {
			this.scheduler.schedule(finish);
		}
		catch (RejectedExecutionException ex) {
			logger.debug("The scheduler rejected the end of a cancelled turn; ending it on the cancelling thread");
			finish.run();
		}
	}

	/**
	 * Begins the call's turn as the request asks and returns it with the effective run
	 * id: starts a turn, answers the tool calls a parked session waits on, or continues
	 * the session's last turn. The intent was read before the session was found or
	 * created, so a request the intent rejects leaves no session behind. New text on a
	 * parked session moves on: the question is answered with every call denied as
	 * {@link #ABANDONED_DENY_MESSAGE abandoned}, so the log stays whole, and the text
	 * runs in the resumed turn.
	 * @throws TurnInProgressException if a turn runs on the session
	 * @throws TurnParkedException if a resume finds the session parked; decisions answer
	 * it
	 * @throws IllegalStateException if decisions arrive for a session that is not parked,
	 * if the session is parked on a question of another kind, which this advisor cannot
	 * answer, or if a resume finds nothing to continue
	 * @throws IllegalArgumentException if the decisions fail a rule
	 */
	private Turn beginTurn(String sessionId, ChatClientRequest request, TurnIntent intent) {
		return switch (intent) {
			case DECISIONS ->
				this.confirmations.resume(sessionId, request, SessionContexts.decisions(request.context()));
			case RESUME -> continueStoppedTurn(sessionId, request);
			case START -> {
				try {
					yield start(sessionId, request);
				}
				catch (TurnParkedException ex) {
					yield this.confirmations.resume(sessionId, request, null);
				}
			}
		};
	}

	/** Starts the turn under the run id of the context, or under a new one. */
	private Turn start(String sessionId, ChatClientRequest request) {
		TurnStatus started = this.turns.start(sessionId, SessionContexts.runId(request.context()), this.turnTimeout);
		return Turn.of(Objects.requireNonNull(started.runId(), "a running turn has a run id"));
	}

	/**
	 * Continues the session's last turn, one that stopped short of its answer, as the
	 * class description says: starts the turn again with {@link TurnService#start}, with
	 * no user message, and decides from the {@link ConversationTail tail} of the
	 * conversation what the turn does. After a user message or tool results the chain
	 * runs a model round over the stored history. After an open reply its calls are
	 * answered with {@link #UNANSWERED_TOOL_RESULT} first. After an answered reply the
	 * turn is settled: the stored reply is the call's response, and the turn completes on
	 * it with no model round.
	 * @throws TurnInProgressException if a turn runs on the session
	 * @throws TurnParkedException if the session is parked on a question
	 * @throws IllegalStateException if the session's last turn ended with
	 * {@link StopReason#END_TURN}, or the session holds no conversation to continue
	 */
	private Turn continueStoppedTurn(String sessionId, ChatClientRequest request) {
		ConversationTail tail = ConversationTail.of(this.sessionService, sessionId);
		if (tail.kind() == ConversationTail.Kind.EMPTY) {
			throw new IllegalStateException("Session '" + sessionId
					+ "' has no turn to continue: its log holds no conversation; start a turn with a user message");
		}
		TurnStatus last = this.turns.status(sessionId);
		if (!last.isRunning() && last.stopReason() == StopReason.END_TURN) {
			throw new IllegalStateException("Session '" + sessionId + "' has no turn to continue: its last turn '"
					+ last.runId() + "' ended with " + StopReason.END_TURN + "; start a turn with a user message");
		}
		TurnStatus continued = this.turns.start(sessionId, SessionContexts.runId(request.context()), this.turnTimeout);
		String runId = Objects.requireNonNull(continued.runId(), "a running turn has a run id");
		// Read again now that the turn is this run's: the tail read before the start may
		// predate a turn another harness ran meanwhile, and what the turn does follows
		// from the log as it is
		tail = ConversationTail.of(this.sessionService, sessionId);
		return switch (tail.kind()) {
			case OPEN_REPLY -> {
				// A crash or a failed tool left the reply's calls without results:
				// answered before the round, so the model learns they did not complete
				// and the prompt stays whole
				tail.answerUnresolved();
				yield Turn.of(runId);
			}
			// The model answered before the stop landed: nothing to run, the turn is
			// settled on the stored reply
			case REPLY -> new Turn(runId, tail.replyAsResponse());
			default -> Turn.of(runId);
		};
	}

	/**
	 * Parks the turn on the reply's tool calls that wait for a decision: a
	 * {@link ToolConfirmationQuestion} that lists the reply's calls and names the stored
	 * reply. The stored reply must be the newest message in the log, or the question
	 * could never be answered; without it the turn ends with an error and the call fails.
	 * When another writer ended the turn first, an interrupt for example, or the park
	 * cannot be recorded, the pause is lost: the reply's calls are answered with
	 * {@link #UNANSWERED_TOOL_RESULT}, so the log stays whole, and in the second case the
	 * turn ends with an error.
	 */
	private void park(String sessionId, String runId, ChatResponse response) {
		ConversationTail tail = ConversationTail.of(this.sessionService, sessionId);
		if (!tail.isOpenReply()) {
			// Without the stored reply the question could never be answered and the
			// session would stay parked for good: fail fast instead
			end(sessionId, runId, StopReason.ERROR);
			throw new IllegalStateException("The reply with the tool calls that paused session '" + sessionId
					+ "' is not the newest message in its log, so the turn cannot park on it; "
					+ "is the memory advisor registered inside the tool-calling loop?");
		}
		TurnStatus status;
		try {
			ToolConfirmationQuestion question = ToolConfirmationQuestion.of(tail.event().getId(), tail.reply(),
					ToolCallDecisions.awaitingDecision(response));
			status = this.turns.park(sessionId, runId, question.toQuestion());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not park the turn of session [{}] under run [{}]: {}; ending it instead", sessionId,
					runId, ex.getMessage());
			if (ownsNewestTurn(sessionId, runId)) {
				tail.answerUnresolved();
			}
			end(sessionId, runId, StopReason.ERROR);
			return;
		}
		if (!status.isParked()) {
			// Another writer, an interrupt for example, ended the turn before the
			// question was asked. The caller holds the paused reply, but no decision can
			// reach the session any more: the calls are answered as unresolved, when the
			// newest reply is still this call's and not a later turn's
			logger.debug("The turn of session [{}] under run [{}] was ended by another writer before it could "
					+ "park; the status is {}", sessionId, runId, status);
			if (isReplyOf(tail, response)) {
				tail.answerUnresolved();
			}
		}
	}

	/** Whether the tail's reply is the response's: the same tool calls. */
	private static boolean isReplyOf(ConversationTail tail, ChatResponse response) {
		return response.getResults()
			.stream()
			.map(Generation::getOutput)
			.anyMatch(reply -> reply.hasToolCalls() && reply.getToolCalls().equals(tail.reply().getToolCalls()));
	}

	/** How the chain of a turn ended, and the stop reason that records it. */
	private enum Outcome {

		/** The chain returned its response. */
		COMPLETED(StopReason.END_TURN),

		/** The chain threw. */
		FAILED(StopReason.ERROR),

		/** The client cancelled the stream. */
		CANCELLED(StopReason.CANCELLED);

		private final StopReason stopReason;

		Outcome(StopReason stopReason) {
			this.stopReason = stopReason;
		}

	}

	/**
	 * Finishes the turn, however its chain ended. A completed loop that paused on a tool
	 * call that needs confirmation parks the turn instead, see {@link #park}. Otherwise
	 * the log is made whole first, see {@link #makeWhole}, the turn ends with the
	 * outcome's stop reason, and a completed turn compacts the session if the trigger
	 * fires. When another writer ended the turn first, an interrupt for example, that end
	 * stands.
	 * @param response the chain's response, or the chunk a stream completed with; {@code
	 * null} when the chain failed or was cancelled
	 */
	private void finish(String sessionId, String runId, Outcome outcome, @Nullable ChatResponse response) {
		if (outcome == Outcome.COMPLETED && response != null && ToolCallDecisions.isAwaitingDecision(response)) {
			// The turn is not over, it waits; nothing is compacted meanwhile
			park(sessionId, runId, response);
			return;
		}
		makeWhole(sessionId, runId, outcome, response);
		end(sessionId, runId, outcome.stopReason);
		if (outcome == Outcome.COMPLETED) {
			this.compaction.afterTurn(sessionId);
		}
	}

	/**
	 * Leaves no tool call of the newest reply without a result when the turn ends. A loop
	 * that ended without a model round, on a {@code returnDirect} tool or a breached tool
	 * call limit, stored no results for its last reply: they are stored now, from the
	 * response. A failed or cancelled turn may have left the reply's calls open, because
	 * a tool threw after the memory advisor stored the reply: they are answered with
	 * {@link #UNANSWERED_TOOL_RESULT}. A completed loop whose last round was a model
	 * round left nothing open, and the log is not read. Only the call's own reply is
	 * answered: a call that lost the turn, to an interrupt and a later turn or to a lease
	 * takeover, may find the later turn's reply as the newest one, and leaves it to that
	 * turn. Best-effort: a failure is logged.
	 */
	private void makeWhole(String sessionId, String runId, Outcome outcome, @Nullable ChatResponse response) {
		boolean withoutModel = endsWithoutModel(response);
		if (outcome == Outcome.COMPLETED && !withoutModel) {
			return;
		}
		try {
			if (!ownsNewestTurn(sessionId, runId)) {
				return;
			}
			ConversationTail tail = ConversationTail.of(this.sessionService, sessionId);
			if (!tail.isOpenReply()) {
				return;
			}
			if (withoutModel) {
				tail.storeResults(Objects.requireNonNull(response));
			}
			else {
				tail.answerUnresolved();
			}
		}
		catch (RuntimeException ex) {
			logger.warn("Could not check session [{}] for tool calls left without a result: {}", sessionId,
					ex.getMessage());
		}
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

	/** Whether an asynchronous compaction of this advisor runs on the session. */
	boolean isCompacting(String sessionId) {
		return this.compaction.isCompacting(sessionId);
	}

	public static final class Builder {

		private final SessionService sessionService;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private boolean asyncCompaction;

		@Nullable private Duration turnTimeout = DEFAULT_TURN_TIMEOUT;

		private ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

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

		/**
		 * The manager that runs the tool calls a person allowed when a parked session
		 * resumes; a default one by default. Pass the one the tool-calling advisor uses,
		 * so the same limits and observation apply. The manager does not consult its
		 * confirmation policy again on resumption, and it refuses decisions that do not
		 * cover every call of the reply or name a call the reply does not have.
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
			this.toolCallingManager = toolCallingManager;
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
