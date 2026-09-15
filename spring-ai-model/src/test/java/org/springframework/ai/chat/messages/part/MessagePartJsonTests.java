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

package org.springframework.ai.chat.messages.part;

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON round-trip tests for the {@link MessagePart} hierarchy using the default Spring AI
 * {@link JsonMapper}.
 *
 * @author Christian Tzolov
 */
class MessagePartJsonTests {

	private final JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();

	@Test
	void textPartRoundTripsWithTypeDiscriminator() {
		TextPart part = new TextPart("hello", null, Map.of("partIndex", "0"));

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"hello\"");
		assertThat(back).isEqualTo(part);
	}

	@Test
	void reasoningPartRoundTripsWithPayload() {
		ReasoningPart part = new ReasoningPart("The user wants the weather", "summary", false,
				new OpaquePayload("anthropic", "signature", "EqQBCkYIBBgC"), Map.of());

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"reasoning\"")
			.contains("\"provider\":\"anthropic\"")
			.contains("\"kind\":\"signature\"")
			.contains("\"redacted\":false");
		assertThat(back).isEqualTo(part);
	}

	@Test
	void redactedReasoningPartRoundTripsWithNullText() {
		ReasoningPart part = new ReasoningPart(null, null, true,
				new OpaquePayload("anthropic", "redacted_thinking", "blob"), Map.of());

		MessagePart back = this.mapper.readValue(this.mapper.writeValueAsString(part), MessagePart.class);

		assertThat(back).isEqualTo(part);
	}

	@Test
	void toolCallPartRoundTripsWithGeminiSignatureOnTheCall() {
		ToolCallPart part = new ToolCallPart(
				new ToolCall("call_7", "function", "getWeather", "{\"city\":\"Amsterdam\"}"),
				new OpaquePayload("google", "thought_signature", "CkQBAgME"), Map.of());

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"tool_call\"").contains("\"thought_signature\"");
		assertThat(back).isEqualTo(part);
	}

	@Test
	void toolResultPartWithoutIdNameOrContentRoundTrips() {
		// What a null tool response (Ollama call without id, tool without text) becomes
		ToolResultPart part = new ToolResultPart(null, null, List.of(), false, null, Map.of());

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(back).isEqualTo(part);
		assertThat(((ToolResultPart) back).id()).isNull();
		assertThat(((ToolResultPart) back).toToolResponse().responseData()).isNull();
	}

	@Test
	void toolResultPartRoundTripsWithNestedContent() {
		ToolResultPart part = new ToolResultPart("call_7", "getWeather",
				List.of(TextPart.of("{\"tempC\":18}"), ReasoningPart.of("nested")), true, null, Map.of());

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"tool_result\"").contains("\"isError\":true");
		assertThat(back).isEqualTo(part);
	}

	@Test
	void mediaPartWithBytesRoundTrips() {
		Media media = Media.builder()
			.id("m1")
			.name("photo")
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(new byte[] { 1, 2, 3 })
			.build();

		String json = this.mapper.writeValueAsString(MediaPart.of(media));
		MediaPart back = (MediaPart) this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"media\"").contains("\"dataKind\":\"bytes\"");
		assertThat(back.media().getId()).isEqualTo("m1");
		assertThat(back.media().getName()).isEqualTo("photo");
		assertThat(back.media().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(back.media().getDataAsByteArray()).containsExactly(1, 2, 3);
	}

	@Test
	void mediaPartWithBase64StringIsNotMistakenForUri() {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("aGVsbG8=").build();

		MediaPart back = (MediaPart) this.mapper.readValue(this.mapper.writeValueAsString(MediaPart.of(media)),
				MessagePart.class);

		assertThat(back.media().getData()).isEqualTo("aGVsbG8=");
	}

	@Test
	void mediaPartWithUriStringRoundTrips() {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/a.png").build();

		MediaPart back = (MediaPart) this.mapper.readValue(this.mapper.writeValueAsString(MediaPart.of(media)),
				MessagePart.class);

		assertThat(back.media().getData()).isEqualTo("https://example.com/a.png");
	}

	@Test
	void mediaPartWithUrlRoundTripsAsUriString() throws Exception {
		Media media = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(new URL("https://example.com/a.png"))
			.build();

		String json = this.mapper.writeValueAsString(MediaPart.of(media));
		MediaPart back = (MediaPart) this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"dataKind\":\"url\"");
		assertThat(back.media().getData()).isEqualTo("https://example.com/a.png");
	}

	@Test
	void unknownPartWrittenByUsReadsBackUnchanged() {
		UnknownPart part = new UnknownPart("anthropic", "server_tool_use",
				"{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_01\"}", null, Map.of("k", "v"));

		String json = this.mapper.writeValueAsString(part);
		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(json).contains("\"type\":\"unknown\"");
		assertThat(back).isEqualTo(part);
	}

	@Test
	void foreignTypeBecomesUnknownPartWrappingTheWholeNode() {
		String json = "{\"type\":\"citation\",\"url\":\"https://example.com\",\"attributes\":{}}";

		MessagePart back = this.mapper.readValue(json, MessagePart.class);

		assertThat(back).isInstanceOf(UnknownPart.class);
		UnknownPart unknown = (UnknownPart) back;
		assertThat(unknown.provider()).isEqualTo(UnknownPart.SPRING_AI_PROVIDER);
		assertThat(unknown.kind()).isEqualTo("citation");
		assertThat(unknown.rawJson()).contains("\"type\":\"citation\"").contains("https://example.com");
		assertThat(unknown.payload()).isNull();
	}

	@Test
	void mixedPartListRoundTripsInOrder() {
		List<MessagePart> parts = List.of(
				new ReasoningPart("think", null, false, new OpaquePayload("anthropic", "signature", "s"), Map.of()),
				ToolCallPart.of(new ToolCall("toolu_01", "function", "getWeather", "{}")), TextPart.of("Let me check."),
				new UnknownPart("anthropic", "server_tool_use", "{\"type\":\"server_tool_use\"}", null, Map.of()));

		String json = this.mapper.writeValueAsString(new Parts(parts));
		Parts back = this.mapper.readValue(json, Parts.class);

		assertThat(back.parts()).containsExactlyElementsOf(parts);
		assertThat(json).containsSubsequence("\"type\":\"reasoning\"", "\"type\":\"tool_call\"", "\"type\":\"text\"",
				"\"type\":\"unknown\"");
	}

	record Parts(List<MessagePart> parts) {
	}

}
