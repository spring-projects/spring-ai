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

package org.springframework.ai.session.support;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.question.Answer;
import org.springframework.ai.session.question.Question;
import org.springframework.ai.session.question.QuestionEvents;
import org.springframework.ai.session.status.SessionStatusEvents;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.ai.session.support.SessionEventCodec.EncodedEvent;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Round-trip tests for {@link SessionEventCodec}: every message part type, message
 * metadata, non-message payloads and the pre-0.11.0 shape.
 *
 * @author Christian Tzolov
 */
class SessionEventCodecTests {

	private final SessionEventCodec codec = new SessionEventCodec();

	private Message roundTrip(Message message) {
		SessionEvent event = SessionEvent.builder().sessionId("s").message(message).build();
		EncodedEvent encoded = this.codec.encode(event);
		assertThat(encoded.type()).isEqualTo(EventType.MESSAGE);
		assertThat(encoded.messageType()).isEqualTo(message.getMessageType());
		assertThat(encoded.text()).isEqualTo(message.getText());
		return this.codec.decode(encoded).sessionId("s").build().getMessage();
	}

	@Test
	void textOnlyMessagesRoundTrip() {
		assertThat(roundTrip(new UserMessage("hello"))).isInstanceOf(UserMessage.class)
			.extracting(Message::getText)
			.isEqualTo("hello");
		assertThat(roundTrip(new AssistantMessage("hi"))).isInstanceOf(AssistantMessage.class)
			.extracting(Message::getText)
			.isEqualTo("hi");
		assertThat(roundTrip(new SystemMessage("be brief"))).isInstanceOf(SystemMessage.class)
			.extracting(Message::getText)
			.isEqualTo("be brief");
	}

	@Test
	void messageMetadataRoundTrips() {
		Message decoded = roundTrip(UserMessage.builder().text("hi").metadata(Map.of("source", "web")).build());

		assertThat(decoded.getMetadata()).containsEntry("source", "web");
		assertThat(decoded.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	void mediaRoundTrips() {
		Media image = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(URI.create("https://example.com/cat.png"))
			.build();
		UserMessage decoded = (UserMessage) roundTrip(UserMessage.builder().text("look").media(image).build());

		assertThat(decoded.getText()).isEqualTo("look");
		assertThat(decoded.getMedia()).hasSize(1);
		assertThat(decoded.getMedia().get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(String.valueOf(decoded.getMedia().get(0).getData())).contains("cat.png");
	}

	@Test
	void toolCallsRoundTrip() {
		AssistantMessage decoded = (AssistantMessage) roundTrip(AssistantMessage.builder()
			.content("calling")
			.toolCalls(List.of(new ToolCall("c1", "function", "get_weather", "{\"city\":\"Sofia\"}")))
			.build());

		assertThat(decoded.getText()).isEqualTo("calling");
		assertThat(decoded.getToolCalls())
			.containsExactly(new ToolCall("c1", "function", "get_weather", "{\"city\":\"Sofia\"}"));
	}

	@Test
	void signedReasoningRoundTripsWithItsProviderPayload() {
		OpaquePayload signature = new OpaquePayload("anthropic", "signature", "sig-123");
		AssistantMessage decoded = (AssistantMessage) roundTrip(AssistantMessage.builder()
			.parts(List.of(new ReasoningPart("let me think", null, signature, Map.of()), TextPart.of("42")))
			.build());

		assertThat(decoded.getText()).isEqualTo("42");
		assertThat(decoded.getReasoning()).hasSize(1);
		assertThat(decoded.getReasoning().get(0).text()).isEqualTo("let me think");
		assertThat(decoded.getReasoning().get(0).payload()).isEqualTo(signature);
		assertThat(decoded.getReasoning().get(0).replayableTo("anthropic")).isTrue();
		assertThat(decoded.getReasoning().get(0).replayableTo("openai")).isFalse();
	}

	@Test
	void toolResultsRoundTripWithErrorFlagAndNestedContent() {
		ToolResultPart failed = new ToolResultPart("c1", "get_weather", List.of(TextPart.of("boom")), true, null,
				Map.of());
		ToolResultPart ok = ToolResultPart.of(new ToolResponseMessage.ToolResponse("c2", "get_time", "noon"));
		ToolResponseMessage decoded = (ToolResponseMessage) roundTrip(
				ToolResponseMessage.builder().results(List.of(failed, ok)).build());

		assertThat(decoded.getResults()).hasSize(2);
		assertThat(decoded.getResults().get(0).isError()).isTrue();
		assertThat(decoded.getResults().get(0).name()).isEqualTo("get_weather");
		assertThat(decoded.getResponses().get(0).responseData()).isEqualTo("boom");
		assertThat(decoded.getResponses().get(1))
			.isEqualTo(new ToolResponseMessage.ToolResponse("c2", "get_time", "noon"));
	}

	@Test
	void unserializableMessageMetadataIsDroppedNotFatal() {
		Object poison = new Object() {
			@SuppressWarnings("unused")
			public String getName() {
				throw new IllegalStateException("not serializable");
			}
		};
		Message decoded = roundTrip(UserMessage.builder().text("hi").metadata(Map.of("poison", poison)).build());

		assertThat(decoded.getText()).isEqualTo("hi");
		assertThat(decoded.getMetadata()).doesNotContainKey("poison");
	}

	@Test
	void damagedPayloadFallsBackToText() {
		Message decoded = this.codec
			.decode(new EncodedEvent(EventType.MESSAGE, MessageType.ASSISTANT, "kept", "{not json"))
			.sessionId("s")
			.build()
			.getMessage();

		assertThat(decoded).isInstanceOf(AssistantMessage.class);
		assertThat(decoded.getText()).isEqualTo("kept");
	}

	@Test
	void nonMessageEventsRoundTripTheirPayload() {
		SessionEvent event = SessionEvent.builder()
			.sessionId("s")
			.type(EventType.of("USAGE"))
			.payload(Map.of("promptTokens", 12, "model", "claude-opus-5-5"))
			.build();
		EncodedEvent encoded = this.codec.encode(event);

		assertThat(encoded.type()).isEqualTo(EventType.of("USAGE"));
		assertThat(encoded.messageType()).isNull();
		assertThat(encoded.text()).isNull();

		SessionEvent decoded = this.codec.decode(encoded).sessionId("s").build();
		assertThat(decoded.getType()).isEqualTo(EventType.of("USAGE"));
		assertThat(decoded.hasMessage()).isFalse();
		assertThat(decoded.getPayload()).containsEntry("promptTokens", 12).containsEntry("model", "claude-opus-5-5");
	}

	@Test
	void jsonHelpersRejectNullAndForgiveBlankOrDamagedText() {
		assertThat(this.codec.toJson(Map.of())).isEqualTo("{}");
		assertThat(this.codec.toJson(Map.of("k", 1))).isEqualTo("{\"k\":1}");
		assertThatIllegalArgumentException().isThrownBy(() -> this.codec.toJson(null));

		assertThat(this.codec.fromJsonMap("{\"k\":1}")).containsEntry("k", 1);
		assertThat(this.codec.fromJsonMap("")).isEmpty();
		assertThat(this.codec.fromJsonMap("{not json")).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.codec.fromJsonMap(null));
	}

	@Test
	void statusEventsRoundTripWithTheirPayload() {
		SessionEvent running = SessionStatusEvents.running("s", "run-1", Instant.parse("2026-06-01T12:30:00Z"));
		SessionEvent idle = SessionStatusEvents.idle("s", "run-1", StopReason.INTERRUPTED);
		SessionEvent interrupt = SessionStatusEvents.interrupt("s", "user pressed stop");

		for (SessionEvent event : List.of(running, idle, interrupt)) {
			EncodedEvent encoded = this.codec.encode(event);
			assertThat(encoded.type()).isEqualTo(event.getType());
			assertThat(encoded.messageType()).isNull();
			assertThat(encoded.text()).isNull();
			SessionEvent decoded = this.codec.decode(encoded)
				.id(event.getId())
				.sessionId("s")
				.timestamp(event.getTimestamp())
				.build();
			assertThat(decoded.getType()).isEqualTo(event.getType());
			assertThat(decoded.hasMessage()).isFalse();
			assertThat(decoded.getPayload()).isEqualTo(event.getPayload());
			if (EventType.STATUS.equals(event.getType())) {
				assertThat(TurnStatus.from(decoded)).isEqualTo(TurnStatus.from(event));
			}
			else {
				assertThat(SessionStatusEvents.reason(decoded)).isEqualTo("user pressed stop");
			}
		}
	}

	@Test
	void questionAndAnswerEventsRoundTripWithNestedPayloads() {
		// A tool confirmation's question and answer: lists of maps, as the ChatClient
		// records them
		Question question = new Question(Question.KIND_TOOL_CONFIRMATION,
				Map.of("replyEventId", "reply-1", "toolCalls", List.of(Map.of("key", "c1", "name", "bookFlight",
						"arguments", "{\"flight\":\"VX205\"}", "awaitingDecision", true))));
		SessionEvent asked = QuestionEvents.ask("s", question);
		SessionEvent answered = QuestionEvents.answer("s", asked.getId(),
				List.of(Map.of("key", "c1", "allowed", false, "message", "Not today.")));

		for (SessionEvent event : List.of(asked, answered)) {
			EncodedEvent encoded = this.codec.encode(event);
			assertThat(encoded.type()).isEqualTo(event.getType());
			assertThat(encoded.messageType()).isNull();
			assertThat(encoded.text()).isNull();
			SessionEvent decoded = this.codec.decode(encoded)
				.id(event.getId())
				.sessionId("s")
				.timestamp(event.getTimestamp())
				.build();
			assertThat(decoded.getType()).isEqualTo(event.getType());
			assertThat(decoded.hasMessage()).isFalse();
			assertThat(decoded.getPayload()).isEqualTo(event.getPayload());
		}
		assertThat(Question.from(this.codec.decode(this.codec.encode(asked)).sessionId("s").build()))
			.isEqualTo(question);
		Answer answer = Answer.from(this.codec.decode(this.codec.encode(answered)).sessionId("s").build());
		assertThat(answer.questionEventId()).isEqualTo(asked.getId());
		assertThat(answer.value()).isEqualTo(List.of(Map.of("key", "c1", "allowed", false, "message", "Not today.")));
	}

}
