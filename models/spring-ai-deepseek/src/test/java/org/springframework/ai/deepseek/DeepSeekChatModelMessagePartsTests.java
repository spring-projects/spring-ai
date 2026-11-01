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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

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
		// DeepSeekApi merges the chunks of a tool call into one chunk
		ChatCompletionMessage toolCallDelta = new ChatCompletionMessage(null, null, null, null, List
			.of(new ToolCall("call_1", "function", new ChatCompletionFunction("getWeather", "{\"city\":\"Seoul\"}"))),
				null, null);
		givenStream(chunk(new ChatCompletionMessage("", Role.ASSISTANT), null), chunk(delta(null, "Think "), null),
				chunk(delta(null, "first."), null), chunk(delta("Let me ", null), null),
				chunk(delta("check.", null), null), chunk(toolCallDelta, ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(responses).hasSize(6);
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
		assertThat(output(responses.get(5)).getParts())
			.containsExactly(StreamingParts.complete(ToolCallPart.of(WEATHER_CALL), 2));
		assertThat(responses.get(5).hasToolCalls()).isTrue();
		// The role of the first chunk
		assertThat(output(responses.get(5)).getMetadata()).containsEntry("role", "ASSISTANT")
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
		// with tools must carry its reasoning_content, so the aggregated turn keeps it
		ChatCompletionMessage toolCallDelta = new ChatCompletionMessage(null, null, null, null, List
			.of(new ToolCall("call_1", "function", new ChatCompletionFunction("getWeather", "{\"city\":\"Seoul\"}"))),
				null, null);
		givenStream(chunk(delta(null, "Need "), null), chunk(delta(null, "the weather."), null),
				chunk(toolCallDelta, ChatCompletionFinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set).blockLast();

		ChatCompletionMessage param = assistantParam(output(aggregatedRef.get()));

		assertThat(param.reasoningContent()).isEqualTo("Need the weather.");
		assertThat(param.content()).isEmpty();
		assertThat(param.toolCalls()).hasSize(1);
		assertThat(param.toolCalls().get(0).function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
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
		given(this.deepSeekApi.chatCompletionStream(any(ChatCompletionRequest.class))).willReturn(Flux.just(chunks));
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

	private static AssistantMessage output(ChatResponse response) {
		return response.getResult().getOutput();
	}

	private static ChatCompletionMessage delta(String content, String reasoning) {
		return new ChatCompletionMessage(content, null, null, null, null, null, reasoning);
	}

	private static ChatCompletionChunk chunk(ChatCompletionMessage delta, ChatCompletionFinishReason finishReason) {
		return new ChatCompletionChunk("chatcmpl-123", List.of(new ChunkChoice(finishReason, 0, delta, null)),
				1777799928L, "deepseek-v4-flash", null, "fp", "chat.completion.chunk", null);
	}

}
