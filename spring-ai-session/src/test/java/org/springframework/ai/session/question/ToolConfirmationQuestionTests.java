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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.question.ToolConfirmationQuestion.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link ToolConfirmationQuestion}: the shape every harness records for a tool
 * confirmation, and the answer it reads back.
 *
 * @author Christian Tzolov
 */
class ToolConfirmationQuestionTests {

	private static final AssistantMessage REPLY = AssistantMessage.builder()
		.content("")
		.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "bookFlight", "{\"city\":\"Lisbon\"}"),
				new AssistantMessage.ToolCall("", "function", "get_weather", null)))
		.build();

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void ofListsTheCallsWithTheirKeysAndWhichOnesWait() {
		ToolConfirmationQuestion question = ToolConfirmationQuestion.of("reply-1", REPLY, List.of("c1"));

		assertThat(question.replyEventId()).isEqualTo("reply-1");
		// A call without an id is keyed by its position, and null arguments read as empty
		assertThat(question.toolCalls()).containsExactly(
				new ToolCall("c1", "bookFlight", "{\"city\":\"Lisbon\"}", true),
				new ToolCall("#1", "get_weather", "", false));
	}

	@Test
	void questionRoundTripsThroughItsPayloadAndJson() {
		ToolConfirmationQuestion question = ToolConfirmationQuestion.of("reply-1", REPLY, List.of("c1"));

		Question asked = question.toQuestion();

		assertThat(asked.kind()).isEqualTo(Question.KIND_TOOL_CONFIRMATION);
		assertThat(asked.payload()).isEqualTo(Map.of(ToolConfirmationQuestion.PAYLOAD_REPLY_EVENT_ID, "reply-1",
				ToolConfirmationQuestion.PAYLOAD_TOOL_CALLS,
				List.of(Map.of("key", "c1", "name", "bookFlight", "arguments", "{\"city\":\"Lisbon\"}",
						"awaitingDecision", true),
						Map.of("key", "#1", "name", "get_weather", "arguments", "", "awaitingDecision", false))));
		assertThat(ToolConfirmationQuestion.from(asked)).isEqualTo(question);
		// As stored: through the event and through JSON
		SessionEvent event = QuestionEvents.ask("s", asked);
		assertThat(ToolConfirmationQuestion.from(event)).isEqualTo(question);
		Object payload = this.mapper.readValue(this.mapper.writeValueAsString(asked.payload()), Object.class);
		assertThat(ToolConfirmationQuestion.from(new Question(asked.kind(), payload))).isEqualTo(question);
	}

	@Test
	void fromRejectsAnotherKindOrAQuestionWithoutTheReply() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ToolConfirmationQuestion.from(new Question("myApp.approval", Map.of())))
			.withMessageContaining("myApp.approval");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ToolConfirmationQuestion.from(new Question(Question.KIND_TOOL_CONFIRMATION, null)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ToolConfirmationQuestion
				.from(new Question(Question.KIND_TOOL_CONFIRMATION, Map.of("toolCalls", List.of()))))
			.withMessageContaining("names the reply");
	}

	@Test
	void answerRoundTripsTheDecisionsThroughJson() {
		ToolCallDecisions decisions = ToolCallDecisions.of(ToolCallDecision.allow("c1"),
				ToolCallDecision.deny("#1", "Not now"), ToolCallDecision.deny("c3"));

		List<Map<String, Object>> value = ToolConfirmationQuestion.answer(decisions);

		assertThat(value).containsExactly(Map.of("key", "c1", "allowed", true),
				Map.of("key", "#1", "allowed", false, "message", "Not now"), Map.of("key", "c3", "allowed", false));
		assertThat(ToolConfirmationQuestion.decisions(new Answer("q-1", value))).isEqualTo(decisions);
		Object stored = this.mapper.readValue(this.mapper.writeValueAsString(value), Object.class);
		assertThat(ToolConfirmationQuestion.decisions(new Answer("q-1", stored))).isEqualTo(decisions);
	}

	@Test
	void decisionsRejectAnAnswerThatIsNotAListOfDecisions() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ToolConfirmationQuestion.decisions(new Answer("q-1", "yes")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ToolConfirmationQuestion.decisions(new Answer("q-1", List.of(Map.of("allowed", true)))))
			.withMessageContaining("'key'");
	}

}
