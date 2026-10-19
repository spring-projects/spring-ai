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
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.ai.session.question.Question;
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
 * from a stop the session was asked for. When an {@link TurnService#interrupt interrupt}
 * ended the turn first, that end stands and the advisor leaves the status alone.</li>
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

	@Nullable private final CompactionTrigger compactionTrigger;

	@Nullable private final CompactionStrategy compactionStrategy;

	private final boolean asyncCompaction;

	@Nullable private final Duration turnTimeout;

	private final ToolCallingManager toolCallingManager;

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
		this.toolCallingManager = builder.toolCallingManager;
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
		checkRequest(sessionId, request);
		this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, request.context()));
		Turn turn = beginTurn(sessionId, request);
		String runId = turn.runId();
		ChatClientResponse response = turn.direct(request);
		if (response == null) {
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
					checkRequest(sessionId, r);
					this.sessionService.findOrCreate(SessionContexts.createRequest(sessionId, r.context()));
					Turn turn = beginTurn(sessionId, r);
					String runId = turn.runId();
					// A returnDirect result, a breached limit or a pause ends the stream
					// on a chunk of its own: the one the turn completes with
					AtomicReference<@Nullable ChatResponse> ending = new AtomicReference<>();
					// The turn ends once, on the first of complete, error and cancel
					AtomicBoolean ended = new AtomicBoolean();
					Flux<ChatClientResponse> rounds;
					try {
						ChatClientResponse direct = turn.direct(r);
						rounds = (direct != null) ? Flux.just(direct) : chain.nextStream(withRunId(r, runId));
					}
					catch (RuntimeException ex) {
						// The rest of the chain failed before its stream existed, so no
						// operator below sees the error: the turn ends here
						end(sessionId, runId, StopReason.ERROR);
						throw ex;
					}
					rounds.doOnNext(response -> {
						ChatResponse chunk = response.chatResponse();
						if (endsWithoutModel(chunk) || ToolCallDecisions.isAwaitingDecision(chunk)) {
							ending.set(chunk);
						}
					}).publishOn(this.scheduler).doOnComplete(context.wrap(() -> {
						if (ended.compareAndSet(false, true)) {
							complete(sessionId, runId, ending.get());
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
	private Turn start(String sessionId, Map<String, @Nullable Object> context) {
		TurnStatus started = this.turns.start(sessionId, SessionContexts.runId(context), this.turnTimeout);
		return new Turn(Objects.requireNonNull(started.runId(), "a running turn has a run id"), null);
	}

	/**
	 * Begins the call's turn and returns it, with the effective run id: starts a turn, or
	 * resumes a session parked on a tool confirmation. A request that carries the
	 * caller's decisions ({@link #TOOL_CALL_DECISIONS_CONTEXT_KEY}) answers the question
	 * with them. A request that carries new text instead moves on: the question is
	 * answered with every call denied as {@link #ABANDONED_DENY_MESSAGE abandoned}, so
	 * the log stays whole, and the text runs in the resumed turn.
	 * @throws TurnInProgressException if a turn runs on the session
	 * @throws IllegalStateException if decisions arrive for a session that is not parked,
	 * or if the session is parked on a question of another kind, which this advisor
	 * cannot answer
	 * @throws IllegalArgumentException if the decisions fail a rule of
	 * {@link #checkDecisions}
	 */
	private Turn beginTurn(String sessionId, ChatClientRequest request) {
		ToolCallDecisions decisions = SessionContexts.decisions(request.context());
		if (decisions != null) {
			return resume(sessionId, request, decisions);
		}
		try {
			return start(sessionId, request.context());
		}
		catch (TurnParkedException ex) {
			return resume(sessionId, request, null);
		}
	}

	/**
	 * Checks the request before the session is found or created, so that a rejected
	 * request leaves no session behind.
	 * @throws IllegalArgumentException if decisions arrive with a new user message; the
	 * text goes in the next call
	 */
	private static void checkRequest(String sessionId, ChatClientRequest request) {
		if (SessionContexts.decisions(request.context()) != null
				&& request.prompt().getInstructions().stream().anyMatch(UserMessage.class::isInstance)) {
			throw new IllegalArgumentException("A request that answers the tool calls of session '" + sessionId
					+ "' carries no new user message; send the text in the next call");
		}
	}

	/**
	 * Resumes the session parked on a tool confirmation: records the decisions as the
	 * answer, starts the turn again under the run id of the context or a new one, runs
	 * the allowed calls through the tool calling manager with the tools the request
	 * carries, and stores the results after the reply, where the model reads them in the
	 * resumed turn's prompt. Without decisions every call is denied as abandoned.
	 * <p>
	 * A question is pending only while the session is parked, since a start refuses a
	 * parked session and a resume answers the question. So the open question is the one
	 * check made here; whether the session may resume, the service decides under the
	 * version, and a session another node resumed meanwhile fails there as busy.
	 * @return the resumed turn: its run id, and the response to return in place of the
	 * chain's when an allowed call's tool returns directly, as a loop that was never
	 * paused would; the model is not called then
	 * @throws IllegalStateException if the session is not parked on a tool confirmation
	 * @throws IllegalArgumentException if the decisions fail a rule of
	 * {@link #checkDecisions}; nothing is written and the question stays open
	 * @throws TurnInProgressException if a turn runs on the session
	 */
	private Turn resume(String sessionId, ChatClientRequest request, @Nullable ToolCallDecisions decisions) {
		SessionEvent pending = this.turns.pendingQuestion(sessionId);
		if (pending == null) {
			throw new IllegalStateException(
					"Session '" + sessionId + "' is not parked on a question; there is nothing to decide on");
		}
		Question question = Question.from(pending);
		if (!Question.KIND_TOOL_CONFIRMATION.equals(question.kind())) {
			throw new IllegalStateException("Session '" + sessionId + "' is parked on a question of kind '"
					+ question.kind() + "', which tool call decisions do not answer");
		}
		ToolConfirmationQuestion asked = ToolConfirmationQuestion.from(question);
		SessionEvent reply = newestConversationEvent(sessionId);
		if (reply == null || !reply.hasToolCalls() || !reply.getId().equals(asked.replyEventId())) {
			throw new IllegalStateException("The reply session '" + sessionId
					+ "' parked on is not its newest message; the log changed since the turn parked");
		}
		AssistantMessage paused = (AssistantMessage) reply.getMessage();
		ToolCallDecisions effective = (decisions != null) ? decisions : abandoned(paused);
		// Before anything is written: a rejected answer must leave the question open
		checkDecisions(effective, paused, request.prompt().getOptions());
		TurnStatus resumed = this.turns.resume(sessionId, SessionContexts.runId(request.context()),
				ToolConfirmationQuestion.answer(effective), this.turnTimeout);
		String runId = Objects.requireNonNull(resumed.runId(), "a running turn has a run id");
		try {
			ToolExecutionResult result = run(sessionId, paused, effective, request.prompt().getOptions());
			if (result.returnDirect()) {
				// The results go back to the caller as the response, and the turn
				// completes on them like a loop that ended on a returnDirect tool: the
				// results are stored then
				return new Turn(runId,
						ChatResponse.builder().generations(ToolExecutionResult.buildGenerations(result)).build());
			}
			storeResults(sessionId, reply, results(result));
		}
		catch (RuntimeException ex) {
			// The answer is recorded and the question is closed; the calls get a result
			// that says so, so the log never keeps a call without one
			answerUnresolved(sessionId, reply);
			end(sessionId, runId, StopReason.ERROR);
			throw ex;
		}
		return new Turn(runId, null);
	}

	/**
	 * Checks the decisions against the paused reply with the rules the tool calling
	 * manager applies, before the resume is recorded: every call of the reply needs a
	 * decision, every decision names a call, and an allowed call needs its tool among the
	 * request's tools, since the manager runs an allowed call only with a tool the
	 * request carries, never with one resolved by name. A rejected answer leaves the
	 * question open.
	 * @throws IllegalArgumentException if the decisions fail a rule
	 */
	private static void checkDecisions(ToolCallDecisions decisions, AssistantMessage paused,
			@Nullable ChatOptions options) {
		List<String> keys = ToolCallDecisions.toolCallKeys(paused);
		Set<String> undecided = new LinkedHashSet<>(keys);
		for (ToolCallDecision decision : decisions.decisions()) {
			Assert.isTrue(keys.contains(decision.toolCallId()), () -> "No tool call of the paused reply has the key '"
					+ decision.toolCallId() + "'; the reply's tool call keys: " + keys);
			undecided.remove(decision.toolCallId());
		}
		Assert.isTrue(undecided.isEmpty(),
				() -> "Every tool call of the paused reply needs a decision; undecided: " + undecided);
		Set<String> tools = new HashSet<>();
		if (options instanceof ToolCallingChatOptions toolCallingOptions
				&& toolCallingOptions.getToolCallbacks() != null) {
			toolCallingOptions.getToolCallbacks().forEach(callback -> tools.add(callback.getToolDefinition().name()));
		}
		List<AssistantMessage.ToolCall> calls = paused.getToolCalls();
		for (int i = 0; i < calls.size(); i++) {
			ToolCallDecision decision = decisions.decisionOn(keys.get(i));
			if (decision != null && decision.allowed() && !tools.contains(calls.get(i).name())) {
				throw new IllegalArgumentException(
						"The request does not carry the tool '" + calls.get(i).name() + "' of the allowed call '"
								+ keys.get(i) + "'; pass the tools of the paused call with the decisions");
			}
		}
	}

	/**
	 * Runs the paused reply's calls as decided: the allowed calls run with the tools the
	 * options carry, a denied call answers with the decision's message. The manager sees
	 * the turn so far, so its tool call limits count the rounds before the pause.
	 */
	private ToolExecutionResult run(String sessionId, AssistantMessage paused, ToolCallDecisions decisions,
			@Nullable ChatOptions options) {
		ChatResponse reply = new ChatResponse(List.of(new Generation(paused)));
		Prompt turnSoFar = new Prompt(this.sessionService.getActiveMessages(sessionId), options);
		return this.toolCallingManager.executeToolCalls(turnSoFar, reply, decisions);
	}

	/** The results of the run, one per call, in the order the model asked for them. */
	private static ToolResponseMessage results(ToolExecutionResult result) {
		List<Message> history = result.conversationHistory();
		if (history.isEmpty() || !(history.get(history.size() - 1) instanceof ToolResponseMessage results)) {
			throw new IllegalStateException("The tool calling manager returned no results for the decided tool calls");
		}
		return results;
	}

	/**
	 * Stores the results of the stored reply's tool calls after it, once: the id derives
	 * from the reply's.
	 */
	private void storeResults(String sessionId, SessionEvent reply, ToolResponseMessage results) {
		this.sessionService.appendEvent(
				SessionEvent.builder().id(reply.getId() + "-results").sessionId(sessionId).message(results).build());
	}

	/**
	 * Answers every tool call of the stored reply with {@link #UNANSWERED_TOOL_RESULT},
	 * when the turn ends without a result for them. Best-effort: a failure is logged.
	 */
	private void answerUnresolved(String sessionId, SessionEvent reply) {
		try {
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (AssistantMessage.ToolCall call : ((AssistantMessage) reply.getMessage()).getToolCalls()) {
				responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), UNANSWERED_TOOL_RESULT));
			}
			storeResults(sessionId, reply, ToolResponseMessage.builder().responses(responses).build());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not answer the tool calls of session [{}] left without a result: {}", sessionId,
					ex.getMessage());
		}
	}

	/** Every call of the reply denied: the caller moved on without deciding. */
	private static ToolCallDecisions abandoned(AssistantMessage paused) {
		return new ToolCallDecisions(ToolCallDecisions.toolCallKeys(paused)
			.stream()
			.map(key -> ToolCallDecision.deny(key, ABANDONED_DENY_MESSAGE))
			.toList());
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
		SessionEvent reply = newestConversationEvent(sessionId);
		if (reply == null || !reply.hasToolCalls()) {
			// Without the stored reply the question could never be answered and the
			// session would stay parked for good: fail fast instead
			end(sessionId, runId, StopReason.ERROR);
			throw new IllegalStateException("The reply with the tool calls that paused session '" + sessionId
					+ "' is not the newest message in its log, so the turn cannot park on it; "
					+ "is the memory advisor registered inside the tool-calling loop?");
		}
		TurnStatus status;
		try {
			AssistantMessage paused = (AssistantMessage) reply.getMessage();
			ToolConfirmationQuestion question = ToolConfirmationQuestion.of(reply.getId(), paused,
					ToolCallDecisions.awaitingDecision(response));
			status = this.turns.park(sessionId, runId, question.toQuestion());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not park the turn of session [{}] under run [{}]: {}; ending it instead", sessionId,
					runId, ex.getMessage());
			if (ownsNewestTurn(sessionId, runId)) {
				answerUnresolved(sessionId, reply);
			}
			end(sessionId, runId, StopReason.ERROR);
			return;
		}
		if (!status.isParked()) {
			// Another writer, an interrupt for example, ended the turn before the
			// question
			// was asked. The caller holds the paused reply, but no decision can reach the
			// session any more: the calls are answered as unresolved
			logger.debug("The turn of session [{}] under run [{}] was ended by another writer before it could "
					+ "park; the status is {}", sessionId, runId, status);
			if (isReplyOf(reply, response)) {
				answerUnresolved(sessionId, reply);
			}
		}
	}

	/** Whether the stored reply is the response's: the same tool calls. */
	private static boolean isReplyOf(SessionEvent reply, ChatResponse response) {
		List<AssistantMessage.ToolCall> calls = ((AssistantMessage) reply.getMessage()).getToolCalls();
		return response.getResults()
			.stream()
			.map(Generation::getOutput)
			.anyMatch(paused -> paused.hasToolCalls() && paused.getToolCalls().equals(calls));
	}

	/**
	 * Returns the request with the effective run id on its context, for the advisors
	 * inside the loop.
	 */
	private static ChatClientRequest withRunId(ChatClientRequest request, String runId) {
		return request.mutate().context(RUN_ID_CONTEXT_KEY, runId).build();
	}

	/**
	 * Completes the turn: parks it when the loop paused on a tool call that needs
	 * confirmation; otherwise stores the tool results of a loop that ended without a
	 * model round, ends the turn with {@link StopReason#END_TURN}, then compacts if the
	 * trigger fires.
	 */
	private void complete(String sessionId, String runId, @Nullable ChatResponse response) {
		if (response != null && ToolCallDecisions.isAwaitingDecision(response)) {
			// The turn is not over, it waits; nothing is compacted meanwhile
			park(sessionId, runId, response);
			return;
		}
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
			storeResults(sessionId, stored, ToolResponseMessage.builder().responses(responses).build());
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

	/**
	 * The call's turn as begun: the run id it runs under and, when a resumed call's tool
	 * returned directly, the response to return in place of the chain's.
	 */
	private record Turn(String runId, @Nullable ChatResponse directResponse) {

		/** The direct response as the call's response, with the run id in its context. */
		@Nullable ChatClientResponse direct(ChatClientRequest request) {
			if (this.directResponse == null) {
				return null;
			}
			return ChatClientResponse.builder()
				.chatResponse(this.directResponse)
				.context(withRunId(request, this.runId).context())
				.build();
		}

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
