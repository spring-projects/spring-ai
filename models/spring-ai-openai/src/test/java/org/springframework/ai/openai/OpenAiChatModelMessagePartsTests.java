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

package org.springframework.ai.openai;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpResponseFor;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionAudio;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageCustomToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
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
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link MessagePart} mapping of {@link OpenAiChatModel}: the parts of a
 * response, the streaming contract, and the replay of an assistant message.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatModelMessagePartsTests {

	private static final String EXTRA_CONTENT = "{\"extra_content\":{\"google\":{\"thought_signature\":\"sig-1\"}}}";

	private static final OpaquePayload EXTRA_CONTENT_PAYLOAD = new OpaquePayload(
			OpenAiChatModel.CHAT_COMPLETIONS_PROVIDER, OpenAiChatModel.PAYLOAD_TOOL_CALL_ADDITIONAL_PROPERTIES,
			EXTRA_CONTENT);

	@Mock
	OpenAIClient openAiClient;

	@Mock
	OpenAIClientAsync openAiClientAsync;

	// -- inbound, non-streaming --

	@Test
	void reasoningTextAndToolCallBecomeOrderedParts() {
		givenCompletion(assistantMessage().content("Let me check.")
			.toolCalls(List.of(ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
				.id("call_1")
				.function(ChatCompletionMessageFunctionToolCall.Function.builder()
					.name("getWeather")
					.arguments("{\"city\":\"Seoul\"}")
					.build())
				.putAdditionalProperty("extra_content",
						JsonValue.from(Map.of("google", Map.of("thought_signature", "sig-1"))))
				.build())))
			.putAdditionalProperty("reasoning_content", JsonValue.from("Think first."))
			.build());

		AssistantMessage message = chatModel("deepseek-reasoner").call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts()).hasSize(3);
		assertThat(message.getParts().get(0)).isEqualTo(ReasoningPart.of("Think first."));
		assertThat(message.getParts().get(1)).isEqualTo(TextPart.of("Let me check."));
		assertThat(message.getParts().get(2)).isEqualTo(new ToolCallPart(
				new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}"),
				EXTRA_CONTENT_PAYLOAD, Map.of()));
		assertThat(message.getText()).isEqualTo("Let me check.");
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("Think first."));
		assertThat(message.getToolCalls()).hasSize(1);
		// Legacy metadata keys, kept for one release
		assertThat(message.getMetadata()).containsEntry("reasoningContent", "Think first.")
			.containsEntry(OpenAiChatModel.TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY,
					Map.of("call_1", EXTRA_CONTENT));
	}

	@Test
	void plainAnswerIsASingleTextPart() {
		givenCompletion(assistantMessage().content("hello").build());

		AssistantMessage message = chatModel("gpt-4o-mini").call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getReasoning()).isEmpty();
		assertThat(message.getMetadata()).containsEntry("reasoningContent", "");
	}

	@Test
	void toolCallAnswerHasNoTextPartButAnEmptyText() {
		givenCompletion(assistantMessage().content(Optional.empty())
			.toolCalls(List.of(functionToolCall("call_1", "getWeather", "{}")))
			.build());

		AssistantMessage message = chatModel("gpt-4o-mini").call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts())
			.containsExactly(ToolCallPart.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}")));
		assertThat(message.getText()).isEmpty();
		assertThat(message.hasToolCalls()).isTrue();
		assertThat(message.getMetadata())
			.doesNotContainKey(OpenAiChatModel.TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY);
	}

	@Test
	void refusalOnlyAnswerKeepsAnEmptyText() {
		givenCompletion(assistantMessage().content(Optional.empty()).refusal("I can't help with that.").build());

		AssistantMessage message = chatModel("gpt-4o-mini").call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of(""));
		assertThat(message.getText()).isEmpty();
		assertThat(message.getMetadata()).containsEntry("refusal", "I can't help with that.");
	}

	@Test
	void customToolCallIsDropped() {
		givenCompletion(assistantMessage().content(Optional.empty())
			.toolCalls(List.of(customToolCall("call_c"), functionToolCall("call_1", "getWeather", "{}")))
			.build());

		AssistantMessage message = chatModel("gpt-5").call(new Prompt("Fix the grammar")).getResult().getOutput();

		// Spring AI declares function tools only, so a tool call of any other type is
		// not something a tool callback can execute
		assertThat(message.getParts())
			.containsExactly(ToolCallPart.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}")));
	}

	@Test
	void audioOutputBecomesMediaPartAfterText() {
		givenCompletion(assistantMessage().content(Optional.empty())
			.audio(ChatCompletionAudio.builder()
				.id("audio_1")
				.data(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }))
				.expiresAt(1777799999L)
				.transcript("Hello there")
				.build())
			.build());
		Prompt prompt = new Prompt("Say hi",
				OpenAiChatOptions.builder()
					.model("gpt-audio")
					.outputModalities(List.of("text", "audio"))
					.outputAudio(new OpenAiChatOptions.AudioParameters(OpenAiChatOptions.AudioParameters.Voice.ALLOY,
							OpenAiChatOptions.AudioParameters.AudioResponseFormat.WAV))
					.build());

		AssistantMessage message = chatModel("gpt-audio").call(prompt).getResult().getOutput();

		assertThat(message.getParts()).hasSize(2);
		assertThat(message.getParts().get(0)).isEqualTo(TextPart.of("Hello there"));
		assertThat(message.getParts().get(1)).isInstanceOf(MediaPart.class);
		assertThat(message.getMedia()).hasSize(1);
		assertThat(message.getMedia().get(0).getId()).isEqualTo("audio_1");
		assertThat(message.getMedia().get(0).getDataAsByteArray()).containsExactly(1, 2, 3);
		assertThat(message.getMedia().get(0).getMimeType().toString()).isEqualTo("audio/wav");
	}

	// -- outbound --

	@Test
	void toolCallPayloadIsReplayedAsAdditionalProperties() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ToolCallPart(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}"),
					EXTRA_CONTENT_PAYLOAD, Map.of()))
			.part(ToolCallPart.of(new AssistantMessage.ToolCall("call_2", "function", "getTime", "{}")))
			.build();

		List<ChatCompletionMessageToolCall> toolCalls = assistantParam(chatModel("gemini-3.5-flash"), assistant)
			.toolCalls()
			.orElseThrow();

		assertThat(toolCalls).hasSize(2);
		assertThat(extraContent(toolCalls.get(0))).containsEntry("google", Map.of("thought_signature", "sig-1"));
		assertThat(toolCalls.get(1).asFunction()._additionalProperties()).doesNotContainKey("extra_content");
	}

	@Test
	void legacyToolCallMetadataIsReplayedWhenThePartHasNoPayload() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}")))
			.properties(Map.of(OpenAiChatModel.TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY,
					Map.of("call_1", EXTRA_CONTENT)))
			.build();

		List<ChatCompletionMessageToolCall> toolCalls = assistantParam(chatModel("gemini-3.5-flash"), assistant)
			.toolCalls()
			.orElseThrow();

		assertThat(extraContent(toolCalls.get(0))).containsEntry("google", Map.of("thought_signature", "sig-1"));
	}

	@Test
	void toolCallPayloadOfAnotherProviderIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ToolCallPart(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}"),
					new OpaquePayload("google", "thought_signature", "c2ln"), Map.of()))
			.build();

		List<ChatCompletionMessageToolCall> toolCalls = assistantParam(chatModel("gpt-4o-mini"), assistant).toolCalls()
			.orElseThrow();

		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).asFunction().id()).isEqualTo("call_1");
		assertThat(toolCalls.get(0).asFunction()._additionalProperties()).isEmpty();
	}

	@Test
	void reasoningPartIsReplayedAsReasoningContent() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("25 * 4 "))
			.part(ReasoningPart.of("= 100."))
			.part(TextPart.of("100"))
			.properties(Map.of("reasoningContent", "stale metadata"))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("deepseek-reasoner"), assistant);

		// The parts win over the legacy metadata key
		assertThat(param._additionalProperties()).containsEntry("reasoning_content", JsonValue.from("25 * 4 = 100."));
		assertThat(param.content().orElseThrow().asText()).isEqualTo("100");
	}

	@Test
	void reasoningPartIsNotReplayedWhenReplayIsTurnedOff() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("25 * 4 = 100."))
			.part(TextPart.of("100"))
			.build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(OpenAiChatOptions.builder().model("llama-3.3-70b-versatile").replayReasoningContent(false).build())
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel, assistant);

		assertThat(param._additionalProperties()).doesNotContainKey("reasoning_content");
		assertThat(param.content().orElseThrow().asText()).isEqualTo("100");
	}

	@Test
	void legacyReasoningMetadataIsReplayedWhenThereIsNoReasoningPart() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("100")
			.properties(Map.of("reasoningContent", "25 * 4 = 100."))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("deepseek-reasoner"), assistant);

		assertThat(param._additionalProperties()).containsEntry("reasoning_content", JsonValue.from("25 * 4 = 100."));
	}

	@Test
	void reasoningSignedByAnotherProviderIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("anthropic thoughts", null, new OpaquePayload("anthropic", "signature", "sig"),
					Map.of()))
			.part(TextPart.of("100"))
			.properties(Map.of("reasoningContent", "anthropic thoughts"))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("deepseek-reasoner"), assistant);

		// Nor is the legacy key read: the message has reasoning parts
		assertThat(param._additionalProperties()).doesNotContainKey("reasoning_content");
	}

	@Test
	void reasoningPartlySignedByAnotherProviderIsNotReplayed() {
		// A provider that signs only some of its reasoning parts
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("First thought. ", null, new OpaquePayload("google", "thought_signature", "c2ln"),
					Map.of()))
			.part(ReasoningPart.of("Second thought."))
			.part(TextPart.of("100"))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("deepseek-reasoner"), assistant);

		assertThat(param._additionalProperties()).doesNotContainKey("reasoning_content");
	}

	@Test
	void unknownPartIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new UnknownPart("anthropic", "server_tool_use", "{\"id\":\"call_1\"}", null, Map.of()))
			.part(TextPart.of("Done."))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("gpt-4o-mini"), assistant);

		assertThat(param.toolCalls()).isEmpty();
		assertThat(param.content().orElseThrow().asText()).isEqualTo("Done.");
	}

	@Test
	void assistantMediaIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(TextPart.of("Hello there"))
			.part(MediaPart
				.of(Media.builder().mimeType(MimeTypeUtils.parseMimeType("audio/wav")).data(new byte[] { 1 }).build()))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("gpt-audio"), assistant);

		assertThat(param.content().orElseThrow().asText()).isEqualTo("Hello there");
		assertThat(param.toolCalls()).isEmpty();
	}

	@Test
	void toolResultPartInAnAssistantMessageIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(TextPart.of("Done."))
			.part(ToolResultPart.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{}")))
			.build();

		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("gpt-4o-mini"), assistant);

		assertThat(param.content().orElseThrow().asText()).isEqualTo("Done.");
		assertThat(param.toolCalls()).isEmpty();
	}

	// -- streaming --

	@Test
	void streamingReasoningTextAndToolCallProduceIndexedParts() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty("reasoning_content", JsonValue.from("Think ")), null),
				streamingChunk(delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("first.")),
						null),
				streamingChunk(delta -> delta.content("Let me "), null),
				streamingChunk(delta -> delta.content("check."), null),
				streamingChunk(delta -> delta.toolCalls(List.of(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
					.index(0L)
					.id("call_1")
					.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
						.name("getWeather")
						.arguments("{\"city\":")
						.build())
					.putAdditionalProperty("extra_content",
							JsonValue.from(Map.of("google", Map.of("thought_signature", "sig-1"))))
					.build())), null),
				streamingChunk(delta -> delta.toolCalls(List.of(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
					.index(0L)
					.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
						.arguments("\"Seoul\"}")
						.build())
					.build())), null),
				streamingChunk(delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set)
			.collectList()
			.block();

		// reasoning deltas, text deltas, then one merged tool-call chunk
		assertThat(responses).hasSize(5);
		assertThat(responses).allSatisfy(r -> assertThat(r.getMetadata().getId()).isEqualTo("chatcmpl-123"));
		assertThat(responses.get(0).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("Think "), 0));
		assertThat(responses.get(0).getResult().getOutput().getText()).isEmpty();
		assertThat(responses.get(1).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("first."), 0));
		assertThat(responses.get(2).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(TextPart.of("Let me "), 1));
		assertThat(responses.get(2).getResult().getOutput().getText()).isEqualTo("Let me ");
		List<MessagePart> lastParts = responses.get(4).getResult().getOutput().getParts();
		assertThat(lastParts).containsExactly(StreamingParts.complete(new ToolCallPart(
				new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}"),
				EXTRA_CONTENT_PAYLOAD, Map.of()), 2));
		assertThat(responses.get(4).hasToolCalls()).isTrue();
		// Legacy per-chunk cumulative metadata is still there
		assertThat(responses.get(1).getResult().getOutput().getMetadata()).containsEntry("reasoningContent",
				"Think first.");

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Think first."),
				TextPart.of("Let me check."),
				new ToolCallPart(
						new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}"),
						EXTRA_CONTENT_PAYLOAD, Map.of()));
		assertThat(aggregated.getText()).isEqualTo("Let me check.");

		// The aggregated turn replays reasoning, text and the signed tool call
		ChatCompletionAssistantMessageParam param = assistantParam(chatModel("deepseek-reasoner"), aggregated);
		assertThat(param._additionalProperties()).containsEntry("reasoning_content", JsonValue.from("Think first."));
		assertThat(param.content().orElseThrow().asText()).isEqualTo("Let me check.");
		assertThat(extraContent(param.toolCalls().orElseThrow().get(0))).containsEntry("google",
				Map.of("thought_signature", "sig-1"));
	}

	@Test
	void streamingReasoningAfterTheToolCallStartsContinuesTheReasoningPart() {
		List<ChatCompletionChunk> chunks = List
			.of(streamingChunk(
					delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
						.putAdditionalProperty("reasoning_content", JsonValue.from("Think. ")),
					null),
					streamingChunk(delta -> delta.toolCalls(List.of(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
						.index(0L)
						.id("call_1")
						.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
							.name("getWeather")
							.arguments("{}")
							.build())
						.build())).putAdditionalProperty("reasoning_content", JsonValue.from("More.")), null),
					streamingChunk(delta -> {
					}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set).blockLast();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Think. More."),
				ToolCallPart.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{}")));
	}

	@Test
	void streamingWithoutToolCallsAggregatesReasoningAndText() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty("reasoning", JsonValue.from("Quick thought.")), null),
				streamingChunk(delta -> delta.content("Hello"), null),
				streamingChunk(delta -> delta.content("!"), ChatCompletionChunk.Choice.FinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set).blockLast();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Quick thought."), TextPart.of("Hello!"));
		assertThat(aggregated.getMetadata()).containsEntry("reasoningContent", "Quick thought.");
	}

	@Test
	void streamingKeepsWhitespaceOnlyReasoningDeltas() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty("reasoning_content", JsonValue.from("Step one.")), null),
				streamingChunk(delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("\n\n")), null),
				streamingChunk(delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("Step two.")),
						null),
				streamingChunk(delta -> delta.content("Done"), ChatCompletionChunk.Choice.FinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(responses.get(1).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("\n\n"), 0));
		assertThat(aggregatedRef.get().getResult().getOutput().getParts())
			.containsExactly(ReasoningPart.of("Step one.\n\nStep two."), TextPart.of("Done"));
	}

	@Test
	void streamingEmptyContentNextToReasoningDoesNotSplitTheReasoning() {
		// OpenRouter-style deltas: "content": "" on every reasoning chunk
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.content("")
					.putAdditionalProperty("reasoning", JsonValue.from("Think ")), null),
				streamingChunk(delta -> delta.content("").putAdditionalProperty("reasoning", JsonValue.from("first.")),
						null),
				streamingChunk(delta -> delta.content("Hello"), null),
				streamingChunk(delta -> delta.content("!"), ChatCompletionChunk.Choice.FinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(responses.get(0).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("Think "), 0));
		assertThat(responses.get(2).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(TextPart.of("Hello"), 1));
		assertThat(aggregatedRef.get().getResult().getOutput().getParts())
			.containsExactly(ReasoningPart.of("Think first."), TextPart.of("Hello!"));
	}

	@Test
	void streamingChunkWithoutContentKeepsAnEmptyText() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT).content(""), null),
				streamingChunk(delta -> delta.content("Hello"), null), streamingChunk(delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set)
			.collectList()
			.block();

		AssistantMessage first = responses.get(0).getResult().getOutput();
		assertThat(first.getParts()).containsExactly(TextPart.of(""));
		assertThat(first.getText()).isEmpty();
		assertThat(first.getMetadata()).containsEntry("role", "assistant");
		assertThat(responses.get(2).getResult().getOutput().getText()).isEmpty();
		assertThat(aggregatedRef.get().getResult().getOutput().getParts()).containsExactly(TextPart.of("Hello"));
	}

	@Test
	void streamingChoicesOfOneResponseAreConcatenatedNotOverwritten() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(0, delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("Think.")),
						null),
				streamingChunk(1, delta -> delta.content("Second."), null),
				streamingChunk(0, delta -> delta.content("First."), ChatCompletionChunk.Choice.FinishReason.STOP),
				streamingChunk(1, delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.STOP));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set).blockLast();

		// MessageAggregator does not separate choices, but, as before the parts model,
		// nothing is lost: the text deltas are concatenated in arrival order
		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Think."), TextPart.of("Second.First."));
	}

	@Test
	void streamingToolCallsOfTwoChoicesGetTheirOwnIndex() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(0,
						delta -> delta.toolCalls(List.of(toolCallDelta("call_a", "getWeather", "{\"city\":"))), null),
				streamingChunk(1, delta -> delta.toolCalls(List.of(toolCallDelta("call_b", "getTime", "{\"tz\":"))),
						null),
				streamingChunk(0, delta -> delta.toolCalls(List.of(toolCallDelta(null, null, "\"Paris\"}"))), null),
				streamingChunk(1, delta -> delta.toolCalls(List.of(toolCallDelta(null, null, "\"CET\"}"))), null),
				streamingChunk(0, delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS), streamingChunk(1, delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS));

		List<ChatResponse> responses = streamResponses(chunks).collectList().block();

		// Both choices are merged into one chunk, each with its complete tool call at an
		// index of its own
		ChatResponse merged = responses.get(0);
		assertThat(merged.getResults()).hasSize(2);
		assertThat(merged.getResults().get(0).getOutput().getParts())
			.containsExactly(StreamingParts.complete(
					ToolCallPart
						.of(new AssistantMessage.ToolCall("call_a", "function", "getWeather", "{\"city\":\"Paris\"}")),
					0));
		assertThat(merged.getResults().get(1).getOutput().getParts()).containsExactly(StreamingParts.complete(
				ToolCallPart.of(new AssistantMessage.ToolCall("call_b", "function", "getTime", "{\"tz\":\"CET\"}")),
				1));
	}

	@Test
	void streamingTextOfBufferedToolCallChunksIsConcatenated() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.content("Let me ")
					.toolCalls(List.of(toolCallDelta("call_1", "getWeather", "{\"city\":"))), null),
				streamingChunk(
						delta -> delta.content("check.").toolCalls(List.of(toolCallDelta(null, null, "\"Paris\"}"))),
						null),
				streamingChunk(delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(streamResponses(chunks), aggregatedRef::set).blockLast();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getText()).isEqualTo("Let me check.");
		assertThat(aggregated.getToolCalls()).extracting(AssistantMessage.ToolCall::arguments)
			.containsExactly("{\"city\":\"Paris\"}");
	}

	// -- helpers --

	private static ChatCompletionChunk.Choice.Delta.ToolCall toolCallDelta(String id, String name, String arguments) {
		ChatCompletionChunk.Choice.Delta.ToolCall.Function.Builder function = ChatCompletionChunk.Choice.Delta.ToolCall.Function
			.builder()
			.arguments(arguments);
		if (name != null) {
			function.name(name);
		}
		ChatCompletionChunk.Choice.Delta.ToolCall.Builder toolCall = ChatCompletionChunk.Choice.Delta.ToolCall.builder()
			.index(0L)
			.function(function.build());
		if (id != null) {
			toolCall.id(id);
		}
		return toolCall.build();
	}

	private OpenAiChatModel chatModel(String model) {
		return OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(OpenAiChatOptions.builder().model(model).build())
			.build();
	}

	private static ChatCompletionMessage.Builder assistantMessage() {
		return ChatCompletionMessage.builder()
			.content(Optional.empty())
			.refusal(Optional.empty())
			.role(JsonValue.from("assistant"))
			.annotations(List.of())
			.toolCalls(List.of());
	}

	private static ChatCompletionMessageToolCall functionToolCall(String id, String name, String arguments) {
		return ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
			.id(id)
			.function(ChatCompletionMessageFunctionToolCall.Function.builder().name(name).arguments(arguments).build())
			.build());
	}

	private static ChatCompletionMessageToolCall customToolCall(String id) {
		return ChatCompletionMessageToolCall.ofCustom(ChatCompletionMessageCustomToolCall.builder()
			.id(id)
			.custom(ChatCompletionMessageCustomToolCall.Custom.builder().name("grammar").input("fix me").build())
			.build());
	}

	private static ToolResponseMessage toolResponse(String id) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(id, "getWeather", "{\"temp\":20}")))
			.build();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> extraContent(ChatCompletionMessageToolCall toolCall) {
		return toolCall.asFunction()._additionalProperties().get("extra_content").convert(Map.class);
	}

	@SuppressWarnings("unchecked")
	private void givenCompletion(ChatCompletionMessage message) {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);
		ChatCompletionService.WithRawResponse chatCompletions = mock(ChatCompletionService.WithRawResponse.class);
		HttpResponseFor<ChatCompletion> rawResponse = mock(HttpResponseFor.class);
		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.withRawResponse()).thenReturn(chatCompletions);
		when(chatCompletions.create(any(ChatCompletionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(rawResponse);
		when(rawResponse.headers()).thenReturn(Headers.builder().build());
		when(rawResponse.parse()).thenReturn(ChatCompletion.builder()
			.id("chatcmpl-123")
			.created(1777799928)
			.model("test-model")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(message)
				.build())
			.build());
	}

	private static ChatCompletionAssistantMessageParam assistantParam(OpenAiChatModel chatModel,
			AssistantMessage assistant) {
		return requestMessages(chatModel, assistant, toolResponse("call_1")).stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
	}

	private static List<ChatCompletionMessageParam> requestMessages(OpenAiChatModel chatModel,
			AssistantMessage assistant, ToolResponseMessage toolResponse) {
		Prompt prompt = new Prompt(List.<Message>of(new UserMessage("Weather?"), assistant, toolResponse),
				chatModel.getOptions());
		return chatModel.createRequest(prompt, false).messages();
	}

	private Flux<ChatResponse> streamResponses(List<ChatCompletionChunk> chunks) {
		ChatServiceAsync chatServiceAsync = mock(ChatServiceAsync.class);
		ChatCompletionServiceAsync chatCompletionServiceAsync = mock(ChatCompletionServiceAsync.class);
		when(this.openAiClientAsync.chat()).thenReturn(chatServiceAsync);
		when(chatServiceAsync.completions()).thenReturn(chatCompletionServiceAsync);
		when(chatCompletionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class),
				any(RequestOptions.class)))
			.thenReturn(asyncStreamResponseOf(chunks));
		OpenAiChatModel chatModel = chatModel("deepseek-reasoner");
		return chatModel.stream(new Prompt("Stream parts", chatModel.getOptions()));
	}

	private static ChatCompletionChunk streamingChunk(
			Consumer<ChatCompletionChunk.Choice.Delta.Builder> deltaCustomizer,
			ChatCompletionChunk.Choice.FinishReason finishReason) {
		return streamingChunk(0, deltaCustomizer, finishReason);
	}

	private static ChatCompletionChunk streamingChunk(long choiceIndex,
			Consumer<ChatCompletionChunk.Choice.Delta.Builder> deltaCustomizer,
			ChatCompletionChunk.Choice.FinishReason finishReason) {
		ChatCompletionChunk.Choice.Delta.Builder deltaBuilder = ChatCompletionChunk.Choice.Delta.builder();
		deltaCustomizer.accept(deltaBuilder);
		return ChatCompletionChunk.builder()
			.id("chatcmpl-123")
			.created(1777799928L)
			.model("deepseek-reasoner")
			.addChoice(ChatCompletionChunk.Choice.builder()
				.index(choiceIndex)
				.delta(deltaBuilder.build())
				.finishReason(finishReason)
				.build())
			.build();
	}

	private static AsyncStreamResponse<ChatCompletionChunk> asyncStreamResponseOf(List<ChatCompletionChunk> chunks) {
		CompletableFuture<Void> onComplete = new CompletableFuture<>();
		return new AsyncStreamResponse<>() {

			@Override
			public AsyncStreamResponse<ChatCompletionChunk> subscribe(Handler<? super ChatCompletionChunk> handler) {
				chunks.forEach(handler::onNext);
				handler.onComplete(Optional.empty());
				onComplete.complete(null);
				return this;
			}

			@Override
			public AsyncStreamResponse<ChatCompletionChunk> subscribe(Handler<? super ChatCompletionChunk> handler,
					Executor executor) {
				return subscribe(handler);
			}

			@Override
			public CompletableFuture<Void> onCompleteFuture() {
				return onComplete;
			}

			@Override
			public void close() {
			}

		};
	}

}
