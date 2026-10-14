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

package org.springframework.ai.mistralai;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion.Choice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ReferenceChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.TextChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ThinkChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.api.MistralAiApi.FinishReason;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Tests for the {@link MessagePart} mapping of {@link MistralAiChatModel}: content chunks
 * as ordered parts, the streaming contract, and the replay of assistant and user
 * messages.
 *
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class MistralAiChatModelMessagePartsTests {

	private static final String PROVIDER = MistralAiApi.PROVIDER_NAME;

	private static final Map<String, String> MISTRAL_ATTRIBUTES = Map.of(MessagePart.PROVIDER_ATTRIBUTE, PROVIDER);

	private static final AssistantMessage.ToolCall WEATHER_CALL = new AssistantMessage.ToolCall("call_1", "function",
			"getWeather", "{\"city\":\"Paris\"}");

	private static final ToolCall MISTRAL_WEATHER_CALL = new ToolCall("call_1", "function",
			new ChatCompletionFunction("getWeather", "{\"city\":\"Paris\"}"), null);

	@Mock
	private MistralAiApi mistralAiApi;

	// -- inbound, non-streaming --

	@Test
	void contentChunksAndToolCallsBecomeOrderedParts() {
		List<ContentChunk> content = List.of(thinking("Think first.", "sig-1"), new TextChunk("Seoul"),
				new ReferenceChunk(List.of(0)), new TextChunk("."));
		givenCompletion(new ChatCompletionMessage(content, Role.ASSISTANT, null, List.of(MISTRAL_WEATHER_CALL), null));

		AssistantMessage message = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		List<MessagePart> parts = message.getParts();
		assertThat(parts).hasSize(5);
		assertThat(parts.get(0)).isEqualTo(new ReasoningPart("Think first.", null,
				new OpaquePayload(PROVIDER, MistralAiChatModel.SIGNATURE_PAYLOAD_KIND, "sig-1"), MISTRAL_ATTRIBUTES));
		assertThat(parts.get(1)).isEqualTo(TextPart.of("Seoul"));
		assertThat(parts.get(2)).isInstanceOfSatisfying(UnknownPart.class, part -> {
			assertThat(part.provider()).isEqualTo(PROVIDER);
			assertThat(part.kind()).isEqualTo("reference");
		});
		assertThat(parts.get(3)).isEqualTo(TextPart.of("."));
		assertThat(parts.get(4)).isEqualTo(ToolCallPart.of(WEATHER_CALL));
		assertThat(message.getText()).isEqualTo("Seoul.");
		assertThat(message.getToolCalls()).containsExactly(WEATHER_CALL);
		assertThat(message.getMetadata()).containsEntry(MistralAiChatModel.REFERENCE_CONTENT_METADATA, List.of(0));
	}

	@Test
	@SuppressWarnings("removal")
	void thinkingIsStillWrittenToTheMetadata() {
		givenCompletion(new ChatCompletionMessage(List.of(thinking("Think first.", null), new TextChunk("Jupiter")),
				Role.ASSISTANT));

		AssistantMessage message = chatModel().call(new Prompt("Planet?")).getResult().getOutput();

		assertThat(message.getReasoning())
			.containsExactly(new ReasoningPart("Think first.", null, null, MISTRAL_ATTRIBUTES));
		assertThat(message.getMetadata()).containsEntry(MistralAiChatModel.THINKING_CONTENT_METADATA, "Think first.");
	}

	@Test
	void plainStringAnswerIsASingleTextPart() {
		givenCompletion(new ChatCompletionMessage("hello", Role.ASSISTANT));

		AssistantMessage message = chatModel().call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getReasoning()).isEmpty();
	}

	@Test
	void toolCallResponseWithEmptyContentHasNoTextPart() {
		givenCompletion(new ChatCompletionMessage("", Role.ASSISTANT, null, List.of(MISTRAL_WEATHER_CALL), null));

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

	// -- inbound, streaming --

	@Test
	void streamedThinkingAndTextAggregateToOrderedParts() {
		// As documented by Mistral: thinking deltas as a list holding a thinking chunk,
		// a transition delta with the closing thinking chunk and the first text chunk,
		// then plain string deltas
		given(this.mistralAiApi.chatCompletionStream(any(ChatCompletionRequest.class))).willReturn(Flux.just(
				chunk(List.of(thinking("Let ", null)), null), chunk(List.of(thinking("me think.", null)), null),
				chunk(List.of(new ThinkChunk(List.of(), true), new TextChunk("The")), null),
				chunk(" answer", FinishReason.STOP)));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Question?")), aggregated::set)
			.collectList()
			.block();

		assertThat(chunks).allSatisfy(response -> assertThat(response.getMetadata().getId()).isEqualTo("id-1"));
		assertThat(aggregated.get().getResult().getOutput().getParts()).containsExactly(
				new ReasoningPart("Let me think.", null, null, MISTRAL_ATTRIBUTES), TextPart.of("The answer"));
	}

	@Test
	void streamedChunksKeepANonNullText() {
		given(this.mistralAiApi.chatCompletionStream(any(ChatCompletionRequest.class))).willReturn(
				Flux.just(chunk(List.of(thinking("Thinking.", null)), null), chunk("Done.", FinishReason.STOP)));

		List<ChatResponse> chunks = chatModel().stream(new Prompt("Question?")).collectList().block();

		assertThat(chunks).extracting(response -> response.getResult().getOutput().getText())
			.containsExactly("", "Done.");
	}

	// -- outbound --

	@Test
	void mistralReasoningIsReplayedAsThinkingChunkInPartOrder() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("Think first.", null,
					new OpaquePayload(PROVIDER, MistralAiChatModel.SIGNATURE_PAYLOAD_KIND, "sig-1"),
					MISTRAL_ATTRIBUTES))
			.part(TextPart.of("Checking."))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		ChatCompletionMessage replayed = assistantParam(assistant);

		assertThat(replayed.content()).isEqualTo(List
			.of(new ThinkChunk(List.of(new TextChunk("Think first.")), null, "sig-1"), new TextChunk("Checking.")));
		assertThat(replayed.toolCalls()).containsExactly(new ToolCall("call_1", "function",
				new ChatCompletionFunction("getWeather", "{\"city\":\"Paris\"}"), null));
	}

	@Test
	void responseRoundTripsThroughReplay() {
		List<ContentChunk> content = List.of(thinking("Think first.", null), new TextChunk("Seoul"),
				new ReferenceChunk(List.of(0)), new TextChunk("."));
		givenCompletion(new ChatCompletionMessage(content, Role.ASSISTANT));

		AssistantMessage message = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(assistantParam(message).content()).isEqualTo(content);
	}

	@Test
	void reasoningFromAnotherProviderIsNotReplayed() {
		AssistantMessage unattributed = AssistantMessage.builder()
			.part(ReasoningPart.of("Foreign thoughts."))
			.part(TextPart.of("100"))
			.build();
		AssistantMessage signedElsewhere = AssistantMessage.builder()
			.part(new ReasoningPart("Signed thoughts.", null, new OpaquePayload("anthropic", "signature", "abc"),
					MISTRAL_ATTRIBUTES))
			.part(TextPart.of("100"))
			.build();

		assertThat(assistantParam(unattributed).content()).isEqualTo("100");
		assertThat(assistantParam(signedElsewhere).content()).isEqualTo("100");
	}

	@Test
	void unknownPartFromAnotherProviderIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new UnknownPart("openai", "web_search_call", "{\"type\":\"web_search_call\"}", null, Map.of()))
			.part(TextPart.of("100"))
			.build();

		assertThat(assistantParam(assistant).content()).isEqualTo("100");
	}

	@Test
	@SuppressWarnings("removal")
	void thinkingMetadataIsReplayedWhenThereAreNoReasoningParts() {
		AssistantMessage legacy = AssistantMessage.builder()
			.content("100")
			.properties(Map.of(MistralAiChatModel.THINKING_CONTENT_METADATA, "Think first."))
			.build();

		assertThat(assistantParam(legacy).content())
			.isEqualTo(List.of(new ThinkChunk(List.of(new TextChunk("Think first.")), null), new TextChunk("100")));
	}

	@Test
	@SuppressWarnings("removal")
	void thinkingMetadataIsIgnoredWhenThereAreReasoningParts() {
		AssistantMessage foreign = AssistantMessage.builder()
			.part(ReasoningPart.of("Foreign thoughts."))
			.part(TextPart.of("100"))
			.properties(Map.of(MistralAiChatModel.THINKING_CONTENT_METADATA, "Stale thoughts."))
			.build();

		assertThat(assistantParam(foreign).content()).isEqualTo("100");
	}

	@Test
	void plainAssistantMessageIsStillSentAsText() {
		AssistantMessage assistant = AssistantMessage.builder().content("100").build();

		assertThat(assistantParam(assistant).content()).isEqualTo("100");
	}

	@Test
	void toolResultPartOnAnAssistantMessageIsRejected() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ToolResultPart.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "sunny")))
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> assistantParam(assistant));
	}

	@Test
	void userMessagePartsAreSentInOrder() {
		Media image = Media.builder()
			.mimeType(Media.Format.IMAGE_PNG)
			.data(URI.create("https://example.com/image.png"))
			.build();
		UserMessage user = UserMessage.builder()
			.part(TextPart.of("Compare this"))
			.part(MediaPart.of(image))
			.part(TextPart.of("with the previous one."))
			.build();

		ChatCompletionMessage sent = singleParam(user);

		assertThat(sent.content()).isInstanceOfSatisfying(List.class, chunks -> {
			assertThat(chunks).hasSize(3);
			assertThat(chunks.get(0)).isEqualTo(new TextChunk("Compare this"));
			assertThat(chunks.get(1)).isInstanceOfSatisfying(ImageUrlChunk.class,
					chunk -> assertThat(chunk.imageUrl().url()).isEqualTo("https://example.com/image.png"));
			assertThat(chunks.get(2)).isEqualTo(new TextChunk("with the previous one."));
		});
	}

	private static ThinkChunk thinking(String text, @Nullable String signature) {
		return new ThinkChunk(List.of(new TextChunk(text)), null, signature);
	}

	private static ChatCompletionChunk chunk(Object content, @Nullable FinishReason finishReason) {
		return new ChatCompletionChunk("id-1", "chat.completion.chunk", 1L, "mistral-small",
				List.of(new ChunkChoice(0, new ChatCompletionMessage(content, Role.ASSISTANT), finishReason, null)),
				null);
	}

	private void givenCompletion(ChatCompletionMessage message) {
		ChatCompletion completion = new ChatCompletion("id-1", "chat.completion", 1L, "mistral-small",
				List.of(new Choice(0, message, FinishReason.STOP, null)), new MistralAiApi.Usage(1, 2, 1));
		given(this.mistralAiApi.chatCompletionEntity(any(ChatCompletionRequest.class)))
			.willReturn(ResponseEntity.ok(completion));
	}

	private ChatCompletionMessage assistantParam(AssistantMessage assistant) {
		return singleParam(assistant);
	}

	private ChatCompletionMessage singleParam(Message message) {
		List<ChatCompletionMessage> messages = chatModel()
			.createRequest(new Prompt(List.of(message), MistralAiChatOptions.builder().build()), false)
			.messages();
		assertThat(messages).hasSize(1);
		return messages.get(0);
	}

	private MistralAiChatModel chatModel() {
		return MistralAiChatModel.builder().mistralAiApi(this.mistralAiApi).build();
	}

}
