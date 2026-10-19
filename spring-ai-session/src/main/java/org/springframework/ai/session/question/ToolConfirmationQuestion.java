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

package org.springframework.ai.session.question;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * The question of kind {@link Question#KIND_TOOL_CONFIRMATION}: a reply paused on tool
 * calls that need a person's confirmation. It names the stored reply by event id and
 * lists the reply's calls, each with the key a decision names it by, its tool and
 * arguments, and whether it waits for a decision. The answer is the person's
 * {@link ToolCallDecisions}, one per call.
 * <p>
 * Every harness that parks a turn on a tool confirmation records this shape, and every UI
 * reads it, so a decision made on the session of one harness resumes the session of
 * another. The {@code ChatClient}'s turn advisor is the first such harness. <pre>{@code
 * // The harness, when the reply pauses
 * Question question = ToolConfirmationQuestion
 *     .of(replyEventId, reply, ToolCallDecisions.awaitingDecision(response))
 *     .toQuestion();
 * sessionService.turns().park(sessionId, runId, question);
 *
 * // The UI, with the person's decisions
 * ToolConfirmationQuestion asked = ToolConfirmationQuestion.from(sessionService.turns().pendingQuestion(sessionId));
 * sessionService.turns().resume(sessionId, null, ToolConfirmationQuestion.answer(decisions));
 *
 * // A reader of the log
 * ToolCallDecisions decided = ToolConfirmationQuestion.decisions(Answer.from(answerEvent));
 * }</pre>
 * <p>
 * Stored as JSON, the question's payload is an object with
 * {@value #PAYLOAD_REPLY_EVENT_ID} and {@value #PAYLOAD_TOOL_CALLS}, a list of objects
 * with {@code key}, {@code name}, {@code arguments} and {@code awaitingDecision}. The
 * answer is a list of objects with {@code key}, {@code allowed} and, for a denied call,
 * {@code message}.
 *
 * @param replyEventId the event id of the stored reply whose calls wait
 * @param toolCalls the reply's calls, in the order the model asked for them
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record ToolConfirmationQuestion(String replyEventId, List<ToolCall> toolCalls) {

	/** Payload key of the event id of the stored reply. */
	public static final String PAYLOAD_REPLY_EVENT_ID = "replyEventId";

	/** Payload key of the list of tool calls. */
	public static final String PAYLOAD_TOOL_CALLS = "toolCalls";

	private static final String KEY = "key";

	private static final String NAME = "name";

	private static final String ARGUMENTS = "arguments";

	private static final String AWAITING_DECISION = "awaitingDecision";

	private static final String ALLOWED = "allowed";

	private static final String MESSAGE = "message";

	public ToolConfirmationQuestion {
		Assert.hasText(replyEventId, "replyEventId must not be null or empty");
		Assert.notNull(toolCalls, "toolCalls must not be null");
		toolCalls = List.copyOf(toolCalls);
	}

	/**
	 * The question for a paused reply: one entry per call of the reply, keyed as
	 * {@link ToolCallDecisions#toolCallKeys} names them, and marked as waiting when its
	 * key is among the awaiting ones.
	 * @param replyEventId the event id of the stored reply
	 * @param reply the paused reply, with its tool calls
	 * @param awaitingDecision the keys of the calls that wait for a decision, as
	 * {@link ToolCallDecisions#awaitingDecision} reads them off the response
	 */
	public static ToolConfirmationQuestion of(String replyEventId, AssistantMessage reply,
			Collection<String> awaitingDecision) {
		Assert.notNull(reply, "reply must not be null");
		Assert.notNull(awaitingDecision, "awaitingDecision must not be null");
		List<String> keys = ToolCallDecisions.toolCallKeys(reply);
		List<AssistantMessage.ToolCall> calls = reply.getToolCalls();
		List<ToolCall> toolCalls = new ArrayList<>(keys.size());
		for (int i = 0; i < keys.size(); i++) {
			AssistantMessage.ToolCall call = calls.get(i);
			toolCalls.add(new ToolCall(keys.get(i), call.name(), Objects.requireNonNullElse(call.arguments(), ""),
					awaitingDecision.contains(keys.get(i))));
		}
		return new ToolConfirmationQuestion(replyEventId, toolCalls);
	}

	/**
	 * Reads the question a {@link org.springframework.ai.session.EventType#QUESTION}
	 * event records.
	 * @throws IllegalArgumentException if the event is not a question event, the question
	 * is of another kind, or its payload lacks the reply event id
	 */
	public static ToolConfirmationQuestion from(SessionEvent event) {
		return from(Question.from(event));
	}

	/**
	 * Reads the question of kind {@link Question#KIND_TOOL_CONFIRMATION}.
	 * @throws IllegalArgumentException if the question is of another kind, or its payload
	 * lacks the reply event id
	 */
	public static ToolConfirmationQuestion from(Question question) {
		Assert.notNull(question, "question must not be null");
		Assert.isTrue(Question.KIND_TOOL_CONFIRMATION.equals(question.kind()),
				() -> "Not a tool confirmation: the question is of kind '" + question.kind() + "'");
		Assert.isTrue(question.payload() instanceof Map,
				"A tool confirmation question carries the reply event id and the tool calls in its payload");
		Map<?, ?> payload = (Map<?, ?>) question.payload();
		String replyEventId = QuestionEvents.text(payload.get(PAYLOAD_REPLY_EVENT_ID));
		Assert.hasText(replyEventId, "A tool confirmation question names the reply its calls belong to");
		List<ToolCall> toolCalls = new ArrayList<>();
		if (payload.get(PAYLOAD_TOOL_CALLS) instanceof List<?> calls) {
			for (Object entry : calls) {
				Assert.isTrue(entry instanceof Map, "Each tool call of a tool confirmation question is an object");
				Map<?, ?> call = (Map<?, ?>) entry;
				toolCalls.add(new ToolCall(required(call, KEY), required(call, NAME),
						Objects.requireNonNullElse(QuestionEvents.text(call.get(ARGUMENTS)), ""),
						Boolean.TRUE.equals(call.get(AWAITING_DECISION))));
			}
		}
		return new ToolConfirmationQuestion(replyEventId, toolCalls);
	}

	/** The question as the {@link Question} a turn parks on. */
	public Question toQuestion() {
		List<Map<String, Object>> calls = new ArrayList<>(this.toolCalls.size());
		for (ToolCall call : this.toolCalls) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put(KEY, call.key());
			entry.put(NAME, call.name());
			entry.put(ARGUMENTS, call.arguments());
			entry.put(AWAITING_DECISION, call.awaitingDecision());
			calls.add(entry);
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(PAYLOAD_REPLY_EVENT_ID, this.replyEventId);
		payload.put(PAYLOAD_TOOL_CALLS, calls);
		return new Question(Question.KIND_TOOL_CONFIRMATION, payload);
	}

	/**
	 * The decisions as the value an {@link Answer} stores: one entry per decision with
	 * its key, whether the call is allowed and, for a denied call, the message the model
	 * gets.
	 */
	public static List<Map<String, Object>> answer(ToolCallDecisions decisions) {
		Assert.notNull(decisions, "decisions must not be null");
		List<Map<String, Object>> answer = new ArrayList<>(decisions.decisions().size());
		for (ToolCallDecision decision : decisions.decisions()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put(KEY, decision.toolCallId());
			entry.put(ALLOWED, decision.allowed());
			String message = decision.message();
			if (message != null) {
				entry.put(MESSAGE, message);
			}
			answer.add(entry);
		}
		return answer;
	}

	/**
	 * Reads the decisions an answer to a tool confirmation holds, as {@link #answer}
	 * stored them.
	 * @throws IllegalArgumentException if the answer's value is not a list of decisions
	 */
	public static ToolCallDecisions decisions(Answer answer) {
		Assert.notNull(answer, "answer must not be null");
		Assert.isTrue(answer.value() instanceof List,
				"The answer to a tool confirmation is a list of decisions, one per tool call");
		List<ToolCallDecision> decisions = new ArrayList<>();
		for (Object entry : (List<?>) answer.value()) {
			Assert.isTrue(entry instanceof Map, "Each decision of a tool confirmation answer is an object");
			Map<?, ?> decision = (Map<?, ?>) entry;
			String key = required(decision, KEY);
			if (Boolean.TRUE.equals(decision.get(ALLOWED))) {
				decisions.add(ToolCallDecision.allow(key));
			}
			else {
				String message = QuestionEvents.text(decision.get(MESSAGE));
				decisions.add((message != null) ? ToolCallDecision.deny(key, message) : ToolCallDecision.deny(key));
			}
		}
		return new ToolCallDecisions(decisions);
	}

	private static String required(Map<?, ?> object, String key) {
		String value = QuestionEvents.text(object.get(key));
		Assert.hasText(value, () -> "A tool confirmation entry names no '" + key + "'");
		return value;
	}

	/**
	 * A tool call of the paused reply.
	 *
	 * @param key what a decision names the call by: its id, or its position when it has
	 * none, as {@link ToolCallDecisions#toolCallKeys} assigns them
	 * @param name the tool
	 * @param arguments the arguments, as the model gave them; empty when it gave none
	 * @param awaitingDecision whether the call waits for a decision; a call that does not
	 * ran, or runs, without one
	 */
	public record ToolCall(String key, String name, String arguments, boolean awaitingDecision) {

		public ToolCall {
			Assert.hasText(key, "key must not be null or empty");
			Assert.hasText(name, "name must not be null or empty");
			Assert.notNull(arguments, "arguments must not be null");
		}

	}

}
