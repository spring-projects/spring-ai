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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiChatModel}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatModelTests {

	private static final String TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY = "openai.tool_calls.additional_properties";

	@Mock
	OpenAIClient openAiClient;

	@Mock
	OpenAIClientAsync openAiClientAsync;

	@Test
	void preserveUnmappedRootResponseMetadata() {
		Map<String, JsonValue> additionalProperties = new HashMap<>();
		additionalProperties.put("string_field", JsonValue.from("abc"));
		additionalProperties.put("number_field", JsonValue.from(123));
		additionalProperties.put("boolean_field", JsonValue.from(true));
		additionalProperties.put("list_field", JsonValue.from(List.of("abc", 123)));
		additionalProperties.put("object_field", JsonValue.from(Map.of("key1", 1, "key2", 2)));
		additionalProperties.put("null_field", null);

		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);
		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("gen-1888888888-XYZabc123NewId")
			.created(1777799928)
			.model("moonshotai/kimi-k2.5-0127")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("hello")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List.of())
					.build())
				.build())
			.additionalProperties(additionalProperties)
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		ChatResponseMetadata metadata = response.getMetadata();
		assertThat((Object) metadata.get("created")).isEqualTo(1777799928L);
		assertThat((Object) metadata.get("string_field")).isEqualTo("abc");
		assertThat((Object) metadata.get("number_field")).isEqualTo(123);
		assertThat((Object) metadata.get("boolean_field")).isEqualTo(true);
		assertThat((Object) metadata.get("list_field")).isEqualTo(List.of("abc", 123));
		assertThat(metadata.<Map<String, Object>>get("object_field")).containsEntry("key1", 1).containsEntry("key2", 2);
		assertThat(metadata.containsKey("null_field")).isFalse();
	}

	@Test
	void preserveUnmappedToolCallAdditionalProperties() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);
		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("chatcmpl-test")
			.created(1777799928)
			.model("gemini-3.5-flash")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.TOOL_CALLS)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List
						.of(ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
							.id("call_1")
							.function(ChatCompletionMessageFunctionToolCall.Function.builder()
								.name("get_current_weather")
								.arguments("{\"location\":\"Seoul\"}")
								.build())
							.putAdditionalProperty("extra_content",
									JsonValue.from(Map.of("google", Map.of("thought_signature", "signature-123"))))
							.build())))
					.build())
				.build())
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gemini-3.5-flash").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getToolCalls()).singleElement().satisfies(toolCall -> {
			assertThat(toolCall.id()).isEqualTo("call_1");
			assertThat(toolCall.name()).isEqualTo("get_current_weather");
			assertThat(toolCall.arguments()).isEqualTo("{\"location\":\"Seoul\"}");
		});
		assertThat(toolCallProperties(assistantMessage, "call_1")).containsEntry("extra_content",
				Map.of("google", Map.of("thought_signature", "signature-123")));
	}

	@Test
	void preserveUnmappedToolCallAdditionalPropertiesFromStream() {
		ChatServiceAsync chatServiceAsync = mock(ChatServiceAsync.class);
		ChatCompletionServiceAsync chatCompletionServiceAsync = mock(ChatCompletionServiceAsync.class);
		when(this.openAiClientAsync.chat()).thenReturn(chatServiceAsync);
		when(chatServiceAsync.completions()).thenReturn(chatCompletionServiceAsync);
		when(chatCompletionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class)))
			.thenReturn(asyncStreamResponse(
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("gemini-3.5-flash")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(Optional.empty())
							.delta(ChatCompletionChunk.Choice.Delta.builder()
								.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
									.index(0)
									.id("call_1")
									.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
										.name("get_current_weather")
										.arguments("{\"location\"")
										.build())
									.build())
								.build())
							.build())
						.build(),
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("gemini-3.5-flash")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(Optional.empty())
							.delta(ChatCompletionChunk.Choice.Delta.builder()
								.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
									.index(0)
									.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
										.arguments(":\"Seoul\"}")
										.build())
									.putAdditionalProperty("extra_content",
											JsonValue
												.from(Map.of("google", Map.of("thought_signature", "signature-123"))))
									.build())
								.build())
							.build())
						.build(),
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("gemini-3.5-flash")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS)
							.delta(ChatCompletionChunk.Choice.Delta.builder().build())
							.build())
						.build()));

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gemini-3.5-flash").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.stream(new Prompt("hi", options)).blockLast();

		assertThat(response).isNotNull();
		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getToolCalls()).singleElement().satisfies(toolCall -> {
			assertThat(toolCall.id()).isEqualTo("call_1");
			assertThat(toolCall.name()).isEqualTo("get_current_weather");
			assertThat(toolCall.arguments()).isEqualTo("{\"location\":\"Seoul\"}");
		});
		assertThat(toolCallProperties(assistantMessage, "call_1")).containsEntry("extra_content",
				Map.of("google", Map.of("thought_signature", "signature-123")));
	}

	@Test
	void restoreUnmappedToolCallAdditionalProperties() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gemini-3.5-flash").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY,
					Map.of("call_1", "{\"extra_content\":{\"google\":{\"thought_signature\":\"signature-123\"}}}")))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "get_current_weather",
					"{\"location\":\"Seoul\"}")))
			.build();

		ChatCompletionCreateParams request = chatModel
			.createRequest(new Prompt(List.<Message>of(assistantMessage), options), false);

		ChatCompletionMessageFunctionToolCall toolCall = request.messages()
			.get(0)
			.asAssistant()
			.toolCalls()
			.orElseThrow()
			.get(0)
			.asFunction();
		@SuppressWarnings("unchecked")
		Map<String, Object> extraContent = toolCall._additionalProperties().get("extra_content").convert(Map.class);
		assertThat(extraContent).containsEntry("google", Map.of("thought_signature", "signature-123"));
	}

	@Test
	void restoreUnmappedToolCallAdditionalPropertiesByToolCallId() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gemini-3.5-flash").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();
		Map<String, String> additionalProperties = new LinkedHashMap<>();
		additionalProperties.put("call_2", "{\"extra_content\":{\"google\":{\"thought_signature\":\"signature-2\"}}}");
		additionalProperties.put("call_1", "{\"extra_content\":{\"google\":{\"thought_signature\":\"signature-1\"}}}");
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY, additionalProperties))
			.toolCalls(List.of(
					new AssistantMessage.ToolCall("call_1", "function", "get_current_weather",
							"{\"location\":\"Seoul\"}"),
					new AssistantMessage.ToolCall("call_2", "function", "get_current_time",
							"{\"timezone\":\"Asia/Seoul\"}")))
			.build();

		ChatCompletionCreateParams request = chatModel
			.createRequest(new Prompt(List.<Message>of(assistantMessage), options), false);

		List<ChatCompletionMessageToolCall> toolCalls = request.messages()
			.get(0)
			.asAssistant()
			.toolCalls()
			.orElseThrow();
		ChatCompletionMessageFunctionToolCall firstToolCall = toolCalls.get(0).asFunction();
		ChatCompletionMessageFunctionToolCall secondToolCall = toolCalls.get(1).asFunction();
		assertThat(firstToolCall.id()).isEqualTo("call_1");
		assertThat(secondToolCall.id()).isEqualTo("call_2");
		@SuppressWarnings("unchecked")
		Map<String, Object> firstExtraContent = firstToolCall._additionalProperties()
			.get("extra_content")
			.convert(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> secondExtraContent = secondToolCall._additionalProperties()
			.get("extra_content")
			.convert(Map.class);
		assertThat(firstExtraContent).containsEntry("google", Map.of("thought_signature", "signature-1"));
		assertThat(secondExtraContent).containsEntry("google", Map.of("thought_signature", "signature-2"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toolCallProperties(AssistantMessage assistantMessage, String toolCallId) {
		Map<String, String> toolCallProperties = (Map<String, String>) assistantMessage.getMetadata()
			.get(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY);
		String json = toolCallProperties.get(toolCallId);
		return json != null ? new JsonHelper().fromJsonToMap(json) : Map.of();
	}

	private AsyncStreamResponse<ChatCompletionChunk> asyncStreamResponse(ChatCompletionChunk... chunks) {
		return new AsyncStreamResponse<>() {
			private final CompletableFuture<Void> completion = new CompletableFuture<>();

			@Override
			public AsyncStreamResponse<ChatCompletionChunk> subscribe(
					AsyncStreamResponse.Handler<? super ChatCompletionChunk> handler) {
				try {
					for (ChatCompletionChunk chunk : chunks) {
						handler.onNext(chunk);
					}
					handler.onComplete(Optional.empty());
					this.completion.complete(null);
				}
				catch (Throwable throwable) {
					handler.onComplete(Optional.of(throwable));
					this.completion.completeExceptionally(throwable);
				}
				return this;
			}

			@Override
			public AsyncStreamResponse<ChatCompletionChunk> subscribe(
					AsyncStreamResponse.Handler<? super ChatCompletionChunk> handler, Executor executor) {
				executor.execute(() -> subscribe(handler));
				return this;
			}

			@Override
			public CompletableFuture<Void> onCompleteFuture() {
				return this.completion;
			}

			@Override
			public void close() {
			}
		};
	}

	@Test
	void toolChoiceAuto() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("auto").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isAuto()).isTrue();
		assertThat(request.toolChoice().get().auto().get().asString()).isEqualTo("auto");
	}

	@Test
	void toolChoiceNone() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("none").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isAuto()).isTrue();
		assertThat(request.toolChoice().get().auto().get().asString()).isEqualTo("none");
	}

	@Test
	void toolChoiceRequired() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("required").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isAuto()).isTrue();
		assertThat(request.toolChoice().get().auto().get().asString()).isEqualTo("required");
	}

	@Test
	void toolChoiceFunction() {
		String json = """
				{
					"type": "function",
					"function": {
						"name": "my_function"
					}
				}
				""";
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice(json).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isNamedToolChoice()).isTrue();
		assertThat(request.toolChoice().get().asNamedToolChoice().function().name()).isEqualTo("my_function");
	}

	@ParameterizedTest
	@ValueSource(strings = { "auto", "required", "none" })
	void toolChoiceJson(String mode) {
		String json = new JsonHelper().toJson(Map.of("type", mode));
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice(json).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isAuto()).isTrue();
		assertThat(request.toolChoice().get().auto().get().asString()).isEqualTo(mode);
	}

	@Test
	void toolChoiceInvalidJson() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("invalid-json").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failed to parse toolChoice JSON");
	}

	@Test
	void reasoningContentFromReasoningContentProperty() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);

		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("gen-1888888888-XYZabc123NewId")
			.created(1777799928)
			.model("deepseek-reasoner")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("hello")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List.of())
					.putAdditionalProperty("reasoning_content", JsonValue.from("Test reasoning content"))
					.build())
				.build())
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("deepseek-reasoner").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		assertThat(response.getResult().getOutput().getMetadata().get("reasoningContent"))
			.isEqualTo("Test reasoning content");
	}

	@Test
	void reasoningContentFromReasoningProperty() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);

		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("gen-1888888888-XYZabc123NewId")
			.created(1777799928)
			.model("test-reasoner")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("hello")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List.of())
					.putAdditionalProperty("reasoning", JsonValue.from("Test reasoning content"))
					.build())
				.build())
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-reasoner").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		assertThat(response.getResult().getOutput().getMetadata().get("reasoningContent"))
			.isEqualTo("Test reasoning content");
	}

	@Test
	void reasoningContentEmptyWhenNeitherPropertyPresent() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);

		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("gen-1888888888-XYZabc123NewId")
			.created(1777799928)
			.model("test-model")
			.usage(CompletionUsage.builder().promptTokens(1).completionTokens(1).totalTokens(2).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("hello")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List.of())
					.build())
				.build())
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		assertThat(response.getResult().getOutput().getMetadata().get("reasoningContent")).isEqualTo("");
	}

	@Test
	void createdFieldPassedThroughInMetadata() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);
		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("test-id")
			.created(1234567890L)
			.model("test-model")
			.usage(CompletionUsage.builder().promptTokens(10).completionTokens(20).totalTokens(30).build())
			.addChoice(ChatCompletion.Choice.builder()
				.finishReason(ChatCompletion.Choice.FinishReason.STOP)
				.index(0)
				.logprobs(Optional.empty())
				.message(ChatCompletionMessage.builder()
					.content("hello")
					.refusal(Optional.empty())
					.role(JsonValue.from("assistant"))
					.annotations(List.of())
					.toolCalls(List.of())
					.build())
				.build())
			.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatResponse response = chatModel.call(new Prompt("hi", options));

		ChatResponseMetadata metadata = response.getMetadata();
		assertThat((Object) metadata.get("created")).isEqualTo(1234567890L);
	}

	@Test
	void reasoningContentReplayedWhenPresentInAssistantHistory() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("100")
			.properties(Map.of("reasoningContent", "25 * 4 = 100."))
			.build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				options);

		ChatCompletionCreateParams request = chatModel.createRequest(prompt, false);

		ChatCompletionAssistantMessageParam assistantParam = request.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).containsEntry("reasoning_content",
				JsonValue.from("25 * 4 = 100."));
	}

	@Test
	void reasoningContentOmittedWhenAbsentFromAssistantHistory() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		AssistantMessage assistantMessage = AssistantMessage.builder().content("100").build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				options);

		ChatCompletionCreateParams request = chatModel.createRequest(prompt, false);

		ChatCompletionAssistantMessageParam assistantParam = request.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).doesNotContainKey("reasoning_content");
	}

	@Test
	void promptCacheKeyIsIncludedInRequest() {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("gpt-4o-mini")
			.promptCacheKey("my-cache-key")
			.build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("hi", options), false);
		assertThat(request.promptCacheKey()).contains("my-cache-key");
	}

	@Test
	void userMessagePdfMediaMapsToFileContentPart() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		UserMessage userMessage = UserMessage.builder()
			.text("Summarize the attached document.")
			.media(Media.builder()
				.mimeType(Media.Format.DOC_PDF)
				.data("%PDF-1.7".getBytes(StandardCharsets.UTF_8))
				.name("sample.pdf")
				.build())
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt(List.<Message>of(userMessage), options),
				false);

		List<ChatCompletionContentPart> parts = request.messages()
			.get(0)
			.user()
			.orElseThrow()
			.content()
			.arrayOfContentParts()
			.orElseThrow();
		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).text().orElseThrow().text()).isEqualTo("Summarize the attached document.");
		ChatCompletionContentPart.File.FileObject file = parts.get(1).file().orElseThrow().file();
		assertThat(file.filename()).contains("sample.pdf");
		assertThat(file.fileData()).contains("data:application/pdf;base64,JVBERi0xLjc=");
	}

	@Test
	void userMessagePdfMediaWithStringDataPassesItThroughAsFileData() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		UserMessage userMessage = UserMessage.builder()
			.text("Summarize the attached document.")
			.media(Media.builder()
				.mimeType(Media.Format.DOC_PDF)
				.data("data:application/pdf;base64,JVBERi0xLjc=")
				.name("sample.pdf")
				.build())
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt(List.<Message>of(userMessage), options),
				false);

		ChatCompletionContentPart.File.FileObject file = request.messages()
			.get(0)
			.user()
			.orElseThrow()
			.content()
			.arrayOfContentParts()
			.orElseThrow()
			.get(1)
			.file()
			.orElseThrow()
			.file();
		assertThat(file.fileData()).contains("data:application/pdf;base64,JVBERi0xLjc=");
	}

}
