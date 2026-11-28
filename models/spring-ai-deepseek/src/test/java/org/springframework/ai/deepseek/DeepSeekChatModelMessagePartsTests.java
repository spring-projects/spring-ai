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

package org.springframework.ai.deepseek;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion.Choice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionFinishReason;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.common.DeepSeekConstants;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Tests for the {@link MessagePart} mapping of {@link DeepSeekChatModel}: the reasoning
 * content as a {@link ReasoningPart}, the streaming contract, and the replay of assistant
 * messages, including the deprecated {@link DeepSeekAssistantMessage}.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("removal")
class DeepSeekChatModelMessagePartsTests {

	private static final JsonMapper JSON_MAPPER = JacksonUtils.getDefaultJsonMapper();

	private static final String THINKING_BLOCKS_JSON = "[{\"type\":\"thinking\",\"thinking\":\"\","
			+ "\"signature\":\"sig-123\",\"extension\":{\"nested\":true}}]";

	private static final AssistantMessage.ToolCall WEATHER_CALL = new AssistantMessage.ToolCall("call_1", "function",
			"getWeather", "{\"city\":\"Seoul\"}");

	@Mock
	private DeepSeekApi deepSeekApi;

	// -- inbound, non-streaming --

	@Test
	void reasoningTextAndToolCallBecomeOrderedParts() {
		givenCompletion(
				new ChatCompletionMessage("Let me check.", Role.ASSISTANT, null, null,
						List.of(new ToolCall("call_1", "function",
								new ChatCompletionFunction("getWeather", "{\"city\":\"Seoul\"}"))),
						null, "Think first."));

		AssistantMessage message = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(ReasoningPart.of("Think first."), TextPart.of("Let me check."),
				ToolCallPart.of(WEATHER_CALL));
		assertThat(message.getText()).isEqualTo("Let me check.");
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("Think first."));
		assertThat(message.getToolCalls()).containsExactly(WEATHER_CALL);
		// Deprecated access path
		assertThat(message).isInstanceOfSatisfying(DeepSeekAssistantMessage.class,
				deepSeek -> assertThat(deepSeek.getReasoningContent()).isEqualTo("Think first."));
	}

	@Test
	void plainAnswerIsASingleTextPart() {
		givenCompletion(new ChatCompletionMessage("hello", Role.ASSISTANT));

		AssistantMessage message = chatModel().call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getReasoning()).isEmpty();
		assertThat(((DeepSeekAssistantMessage) message).getReasoningContent()).isNull();
	}

	@Test
	void toolCallResponseWithEmptyContentHasNoTextPart() {
		givenCompletion(new ChatCompletionMessage("", Role.ASSISTANT, null, null, List
			.of(new ToolCall("call_1", "function", new ChatCompletionFunction("getWeather", "{\"city\":\"Seoul\"}"))),
				null, null));

		AssistantMessage message = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(ToolCallPart.of(WEATHER_CALL));
		assertThat(message.getText()).isEmpty();
	}

	@Test
	void signedThinkingBlocksSurviveResponseParsingAndReplay() {
		ChatCompletionMessage responseMessage = JSON_MAPPER.readValue("""
				{
				"content": "",
				"role": "assistant",
				"reasoning_content": "",
				"tool_calls": [
				{"id": "call_1", "type": "function", "function": {"name": "getWeather", "arguments": "{}"}}
				],
				"thinking_blocks": %s
				}
				""".formatted(THINKING_BLOCKS_JSON), ChatCompletionMessage.class);
		givenCompletion(responseMessage);

		AssistantMessage assistant = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(assistant.getReasoning()).hasSize(1);
		ReasoningPart reasoning = assistant.getReasoning().get(0);
		assertThat(reasoning.text()).isEmpty();
		OpaquePayload payload = Objects.requireNonNull(reasoning.payload());
		assertThat(payload.provider()).isEqualTo(DeepSeekConstants.PROVIDER_NAME);
		assertThat(payload.kind()).isEqualTo("thinking_blocks");
		assertThat(JSON_MAPPER.readTree(payload.data())).isEqualTo(JSON_MAPPER.readTree(THINKING_BLOCKS_JSON));

		ChatCompletionMessage replayed = assistantParam(assistant);

		assertThat(serializedThinkingBlocks(replayed)).isEqualTo(JSON_MAPPER.readTree(THINKING_BLOCKS_JSON));
	}

	@Test
	void responseWithoutAnyContentKeepsAnEmptyText() {
		givenCompletion(new ChatCompletionMessage(null, Role.ASSISTANT));

		AssistantMessage message = chatModel().call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of(""));
		assertThat(message.getText()).isEmpty();
	}

	// -- outbound --

	@Test
	void reasoningPartIsReplayedAsReasoningContent() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("25 * 4 = 100."))
			.part(TextPart.of("100"))
			.build();

		ChatCompletionMessage param = assistantParam(assistant);

		assertThat(param.content()).isEqualTo("100");
		assertThat(param.reasoningContent()).isEqualTo("25 * 4 = 100.");
		assertThat(param.prefix()).isNull();
	}

	@Test
	void reasoningPartsAreJoined() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("Step one. "))
			.part(TextPart.of("Checking."))
			.part(ReasoningPart.of("Step two."))
			.build();

		assertThat(assistantParam(assistant).reasoningContent()).isEqualTo("Step one. Step two.");
	}

	@Test
	void reasoningIsOnlyReplayedFromTheParts() {
		// The reasoningContent metadata key OpenAiChatModel writes is not a source of
		// reasoning
		AssistantMessage assistant = AssistantMessage.builder()
			.content("100")
			.properties(Map.of("reasoningContent", "from the metadata"))
			.build();

		assertThat(assistantParam(assistant).reasoningContent()).isNull();
	}

	@Test
	void reasoningSignedByAnotherProviderIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("unsigned"))
			.part(new ReasoningPart("anthropic thoughts", null, new OpaquePayload("anthropic", "signature", "sig"),
					Map.of()))
			.part(TextPart.of("100"))
			.build();

		ChatCompletionMessage param = assistantParam(assistant);

		assertThat(param.reasoningContent()).isNull();
		assertThat(param.content()).isEqualTo("100");
	}

	@Test
	void unrelatedPayloadsAreNotReplayedAsThinkingBlocks() {
		List<OpaquePayload> payloads = List.of(new OpaquePayload("anthropic", "thinking_blocks", THINKING_BLOCKS_JSON),
				new OpaquePayload(DeepSeekConstants.PROVIDER_NAME, "signature", "sig-123"));
		for (OpaquePayload payload : payloads) {
			AssistantMessage assistant = AssistantMessage.builder()
				.part(new ReasoningPart("reasoning", null, payload, Map.of()))
				.build();

			ChatCompletionMessage replayed = assistantParam(assistant);

			assertThat(serializedThinkingBlocks(replayed)).as("%s/%s", payload.provider(), payload.kind()).isNull();
		}
	}

	@Test
	void emptyReasoningIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of(""))
			.part(TextPart.of("100"))
			.build();

		assertThat(assistantParam(assistant).reasoningContent()).isNull();
	}

	@Test
	void toolCallPartsAreReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("Need the weather."))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		ChatCompletionMessage param = assistantParam(assistant);

		// A tool-call turn without text has an empty text, sent as an empty content
		assertThat(param.content()).isEmpty();
		assertThat(param.reasoningContent()).isEqualTo("Need the weather.");
		assertThat(param.toolCalls()).hasSize(1);
		assertThat(param.toolCalls().get(0).id()).isEqualTo("call_1");
		assertThat(param.toolCalls().get(0).function().name()).isEqualTo("getWeather");
		assertThat(param.toolCalls().get(0).function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
	}

	@Test
	void prefixIsSentFromTheMetadataKey() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("```python\n")
			.properties(Map.of(DeepSeekChatModel.PREFIX_METADATA_KEY, true))
			.build();

		ChatCompletionMessage param = lastAssistantParam(assistant);

		assertThat(param.prefix()).isTrue();
		assertThat(param.content()).isEqualTo("```python\n");
	}

	@Test
	void prefixIsOnlySentOnTheLastMessage() {
		// A flagged message restored from chat memory is a plain turn when it is not last
		AssistantMessage assistant = AssistantMessage.builder()
			.content("```python\n")
			.properties(Map.of(DeepSeekChatModel.PREFIX_METADATA_KEY, true))
			.build();

		assertThat(assistantParam(assistant).prefix()).isNull();
	}

	@Test
	void deprecatedMessageIsReplayedFromItsPartsAndMetadata() {
		DeepSeekAssistantMessage assistant = DeepSeekAssistantMessage.builder()
			.content("```python\n")
			.reasoningContent("Write code.")
			.prefix(true)
			.build();

		ChatCompletionMessage param = lastAssistantParam(assistant);

		assertThat(param.prefix()).isTrue();
		assertThat(param.reasoningContent()).isEqualTo("Write code.");
		assertThat(param.content()).isEqualTo("```python\n");
	}

	@Test
	void deprecatedSettersAreReplayed() {
		DeepSeekAssistantMessage assistant = DeepSeekAssistantMessage.builder()
			.content("```python\n")
			.reasoningContent("Write code.")
			.build();
		assistant.setReasoningContent("Write Python code.");
		assistant.setPrefix(true);

		ChatCompletionMessage param = lastAssistantParam(assistant);

		assertThat(param.prefix()).isTrue();
		assertThat(param.reasoningContent()).isEqualTo("Write Python code.");
	}

	@Test
	void reasoningContentRemovedThroughTheDeprecatedSetterIsNotReplayed() {
		DeepSeekAssistantMessage assistant = DeepSeekAssistantMessage.builder()
			.content("100")
			.reasoningContent("25 * 4 = 100.")
			.build();
		assistant.setReasoningContent(null);

		assertThat(assistantParam(assistant).reasoningContent()).isNull();
	}

	@Test
	void prefixFalseIsNotSent() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("hi")
			.properties(Map.of(DeepSeekChatModel.PREFIX_METADATA_KEY, false))
			.build();

		assertThat(lastAssistantParam(assistant).prefix()).isNull();
	}

	// -- streaming --

	@Test
	void streamingReasoningTextAndToolCallProduceIndexedParts() {
		// As DeepSeek streams a tool call: the first delta carries the id and name, the
		// later ones an empty id and an arguments fragment
		givenStream(chunk(new ChatCompletionMessage("", Role.ASSISTANT), null), chunk(delta(null, "Think "), null),
				chunk(delta(null, "first."), null), chunk(delta("Let me ", null), null),
				chunk(delta("check.", null), null),
				chunk(toolCallDelta(
						new ToolCall(0, "call_1", "function", new ChatCompletionFunction("getWeather", "{\"city\":"))),
						null),
				chunk(toolCallDelta(new ToolCall(0, "", null, new ChatCompletionFunction(null, "\"Seoul\"}"))), null),
				chunk(delta(null, null), ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		// the role chunk, reasoning deltas, text deltas, tool call argument deltas, then
		// one merged tool call chunk
		assertThat(responses).hasSize(8);
		assertThat(responses).allSatisfy(r -> assertThat(r.getMetadata().getId()).isEqualTo("chatcmpl-123"));
		// The role-only first chunk keeps an empty text
		assertThat(output(responses.get(0)).getParts()).containsExactly(TextPart.of(""));
		assertThat(output(responses.get(0)).getMetadata()).containsEntry("role", "ASSISTANT");
		assertThat(output(responses.get(1)).getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("Think "), 0));
		assertThat(output(responses.get(1)).getText()).isEmpty();
		assertThat(output(responses.get(2)).getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("first."), 0));
		assertThat(output(responses.get(3)).getParts())
			.containsExactly(StreamingParts.partial(TextPart.of("Let me "), 1));
		assertThat(output(responses.get(3)).getText()).isEqualTo("Let me ");
		// The argument deltas carry the id and name of the call, and are not tool calls
		assertThat(output(responses.get(5)).getParts()).containsExactly(StreamingParts.partial(
				ToolCallPart.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":")), 2));
		assertThat(output(responses.get(6)).getParts()).containsExactly(StreamingParts.partial(
				ToolCallPart.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "\"Seoul\"}")), 2));
		assertThat(responses.subList(5, 7)).allSatisfy(response -> {
			assertThat(response.hasToolCalls()).isFalse();
			assertThat(output(response).getText()).isEmpty();
		});
		assertThat(output(responses.get(7)).getParts())
			.containsExactly(StreamingParts.complete(ToolCallPart.of(WEATHER_CALL), 2));
		assertThat(responses.get(7).hasToolCalls()).isTrue();
		// The role of the first chunk
		assertThat(output(responses.get(7)).getMetadata()).containsEntry("role", "ASSISTANT")
			.doesNotContainKey("reasoningContent");
		// Streamed chunks are still DeepSeekAssistantMessages; the aggregated message is
		// a plain AssistantMessage, as before
		assertThat(responses).allSatisfy(r -> assertThat(output(r)).isInstanceOf(DeepSeekAssistantMessage.class));

		AssistantMessage aggregated = output(aggregatedRef.get());
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Think first."),
				TextPart.of("Let me check."), ToolCallPart.of(WEATHER_CALL));
		assertThat(aggregated.getText()).isEqualTo("Let me check.");
		assertThat(aggregated.getToolCalls()).containsExactly(WEATHER_CALL);
	}

	@Test
	void streamingKeepsWhitespaceOnlyReasoningDeltasAndIgnoresEmptyContent() {
		// The "" content sent next to every reasoning delta must not split the reasoning
		givenStream(chunk(delta("", "Step one."), null), chunk(delta("", "\n\n"), null),
				chunk(delta("", "Step two."), null), chunk(delta("Done", null), ChatCompletionFinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Think")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(output(responses.get(1)).getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("\n\n"), 0));
		assertThat(output(aggregatedRef.get()).getParts()).containsExactly(ReasoningPart.of("Step one.\n\nStep two."),
				TextPart.of("Done"));
	}

	@Test
	void streamedToolCallTurnIsReplayedWithItsReasoning() {
		// DeepSeek documents that in thinking mode every assistant message of a request
		// with tools must carry its reasoning_content, so the aggregated turn keeps it.
		// The whole tool call arrives in one chunk here.
		givenStream(chunk(delta(null, "Need "), null), chunk(delta(null, "the weather."), null),
				chunk(toolCallDelta(new ToolCall("call_1", "function",
						new ChatCompletionFunction("getWeather", "{\"city\":\"Seoul\"}"))),
						ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set).blockLast();

		ChatCompletionMessage param = assistantParam(output(aggregatedRef.get()));

		assertThat(param.reasoningContent()).isEqualTo("Need the weather.");
		assertThat(param.content()).isEmpty();
		assertThat(param.toolCalls()).hasSize(1);
		assertThat(param.toolCalls().get(0).function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
	}

	@Test
	void streamedThinkingBlocksAreAggregatedWithReasoningAndReplayed() {
		ChatCompletionMessage toolCallDelta = JSON_MAPPER.readValue("""
				{
				"tool_calls": [
				{"id": "call_1", "type": "function", "function": {"name": "getWeather", "arguments": "{}"}}
				],
				"thinking_blocks": %s
				}
				""".formatted(THINKING_BLOCKS_JSON), ChatCompletionMessage.class);
		givenStream(chunk(delta(null, "Need "), null), chunk(delta(null, "the weather."), null),
				chunk(toolCallDelta, ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set).blockLast();

		ChatCompletionMessage replayed = assistantParam(output(aggregatedRef.get()));

		assertThat(replayed.reasoningContent()).isEqualTo("Need the weather.");
		assertThat(serializedThinkingBlocks(replayed)).isEqualTo(JSON_MAPPER.readTree(THINKING_BLOCKS_JSON));
	}

	@Test
	void streamedThinkingBlocksWithoutReasoningTextAreReplayed() {
		ChatCompletionMessage toolCallDelta = JSON_MAPPER.readValue("""
				{
				"tool_calls": [
				{"id": "call_1", "type": "function", "function": {"name": "getWeather", "arguments": "{}"}}
				],
				"thinking_blocks": %s
				}
				""".formatted(THINKING_BLOCKS_JSON), ChatCompletionMessage.class);
		givenStream(chunk(toolCallDelta, ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set).blockLast();

		AssistantMessage aggregated = output(aggregatedRef.get());
		assertThat(aggregated.getParts()).satisfiesExactly(part -> assertThat(part).isInstanceOf(ReasoningPart.class),
				part -> assertThat(part).isInstanceOf(ToolCallPart.class));
		ChatCompletionMessage replayed = assistantParam(aggregated);

		assertThat(replayed.reasoningContent()).isNull();
		assertThat(serializedThinkingBlocks(replayed)).isEqualTo(JSON_MAPPER.readTree(THINKING_BLOCKS_JSON));
	}

	// -- helpers --

	private DeepSeekChatModel chatModel() {
		return DeepSeekChatModel.builder()
			.deepSeekApi(this.deepSeekApi)
			.options(DeepSeekChatOptions.builder().model("deepseek-v4-flash").build())
			.build();
	}

	private void givenCompletion(ChatCompletionMessage message) {
		ChatCompletion completion = new ChatCompletion("chatcmpl-123",
				List.of(new Choice(ChatCompletionFinishReason.STOP, 0, message, null)), 1777799928L,
				"deepseek-v4-flash", "fp", "chat.completion", new DeepSeekApi.Usage(1, 1, 2));
		given(this.deepSeekApi.chatCompletionEntity(any(ChatCompletionRequest.class)))
			.willReturn(ResponseEntity.ok(completion));
	}

	private void givenStream(ChatCompletionChunk... chunks) {
		given(this.deepSeekApi.chatCompletionStreamRaw(any(ChatCompletionRequest.class))).willReturn(Flux.just(chunks));
	}

	private ChatCompletionMessage assistantParam(AssistantMessage assistant) {
		Prompt prompt = new Prompt(List.<Message>of(new UserMessage("Weather?"), assistant,
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{\"temp\":20}")))
					.build()),
				DeepSeekChatOptions.builder().model("deepseek-v4-flash").build());
		return chatModel().createRequest(prompt, false)
			.messages()
			.stream()
			.filter(m -> m.role() == Role.ASSISTANT)
			.findFirst()
			.orElseThrow();
	}

	private ChatCompletionMessage lastAssistantParam(AssistantMessage assistant) {
		Prompt prompt = new Prompt(List.<Message>of(new UserMessage("Write code"), assistant),
				DeepSeekChatOptions.builder().model("deepseek-v4-flash").build());
		List<ChatCompletionMessage> messages = chatModel().createRequest(prompt, false).messages();
		return messages.get(messages.size() - 1);
	}

	private static JsonNode serializedThinkingBlocks(ChatCompletionMessage message) {
		return JSON_MAPPER.readTree(JSON_MAPPER.writeValueAsString(message)).get("thinking_blocks");
	}

	private static AssistantMessage output(ChatResponse response) {
		return response.getResult().getOutput();
	}

	private static ChatCompletionMessage delta(String content, String reasoning) {
		return new ChatCompletionMessage(content, null, null, null, null, null, reasoning);
	}

	private static ChatCompletionMessage toolCallDelta(ToolCall toolCall) {
		return new ChatCompletionMessage(null, null, null, null, List.of(toolCall), null, null);
	}

	private static ChatCompletionChunk chunk(ChatCompletionMessage delta, ChatCompletionFinishReason finishReason) {
		return new ChatCompletionChunk("chatcmpl-123", List.of(new ChunkChoice(finishReason, 0, delta, null)),
				1777799928L, "deepseek-v4-flash", null, "fp", "chat.completion.chunk", null);
	}

}
