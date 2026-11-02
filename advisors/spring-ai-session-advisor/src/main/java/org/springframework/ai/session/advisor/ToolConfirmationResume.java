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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.question.Question;
import org.springframework.ai.session.question.ToolConfirmationQuestion;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.util.Assert;

/**
 * Resumes a session parked on a tool confirmation, for the {@link SessionTurnAdvisor}:
 * records the caller's decisions as the answer, starts the turn again, runs the allowed
 * calls through the tool calling manager with the tools the request carries, and stores
 * the results after the stored reply, where the model reads them in the resumed turn's
 * prompt. Without decisions every call is denied as
 * {@link SessionTurnAdvisor#ABANDONED_DENY_MESSAGE abandoned}: the caller moved on with
 * new text.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class ToolConfirmationResume {

	// The advisor's category, so its logging configuration keeps applying
	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	private final SessionService sessionService;

	private final TurnService turns;

	private final ToolCallingManager toolCallingManager;

	@Nullable private final Duration turnTimeout;

	ToolConfirmationResume(SessionService sessionService, ToolCallingManager toolCallingManager,
			@Nullable Duration turnTimeout) {
		this.sessionService = sessionService;
		this.turns = sessionService.turns();
		this.toolCallingManager = toolCallingManager;
		this.turnTimeout = turnTimeout;
	}

	/**
	 * Resumes the parked session with the decisions, or with every call denied as
	 * abandoned when there are none.
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
	Turn resume(String sessionId, ChatClientRequest request, @Nullable ToolCallDecisions decisions) {
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
		ConversationTail tail = ConversationTail.of(this.sessionService, sessionId);
		if (!tail.isOpenReply() || !tail.event().getId().equals(asked.replyEventId())) {
			throw new IllegalStateException("The reply session '" + sessionId
					+ "' parked on is not its newest message; the log changed since the turn parked");
		}
		AssistantMessage paused = tail.reply();
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
			tail.storeResults(results(result));
		}
		catch (RuntimeException ex) {
			// The answer is recorded and the question is closed; the calls get a result
			// that says so, so the log never keeps a call without one
			tail.answerUnresolved();
			endOnFailure(sessionId, runId);
			throw ex;
		}
		return Turn.of(runId);
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

	/** Every call of the reply denied: the caller moved on without deciding. */
	private static ToolCallDecisions abandoned(AssistantMessage paused) {
		return new ToolCallDecisions(ToolCallDecisions.toolCallKeys(paused)
			.stream()
			.map(key -> ToolCallDecision.deny(key, SessionTurnAdvisor.ABANDONED_DENY_MESSAGE))
			.toList());
	}

	/** Ends the resumed turn with an error after the run failed; best-effort. */
	private void endOnFailure(String sessionId, String runId) {
		try {
			this.turns.end(sessionId, runId, StopReason.ERROR);
		}
		catch (RuntimeException ex) {
			logger.warn("Could not end the resumed turn of session [{}] under run [{}] with {}: {}", sessionId, runId,
					StopReason.ERROR, ex.getMessage());
		}
	}

}
