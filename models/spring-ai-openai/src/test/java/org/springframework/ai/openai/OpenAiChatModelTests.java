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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
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
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiChatModel}.
 *
 * @author Jewoo Shin
 * @author Dimitar Proynov
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
	void mergeStreamToolCallWhenIdNameAndArgumentsArriveInSeparateChunks() {
		ChatServiceAsync chatServiceAsync = mock(ChatServiceAsync.class);
		ChatCompletionServiceAsync chatCompletionServiceAsync = mock(ChatCompletionServiceAsync.class);
		when(this.openAiClientAsync.chat()).thenReturn(chatServiceAsync);
		when(chatServiceAsync.completions()).thenReturn(chatCompletionServiceAsync);
		when(chatCompletionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class)))
			.thenReturn(asyncStreamResponse(ChatCompletionChunk.builder()
				.id("chatcmpl-stream-test")
				.created(1777799928)
				.model("test-model")
				.addChoice(ChatCompletionChunk.Choice.builder()
					.index(0)
					.finishReason(Optional.empty())
					.delta(ChatCompletionChunk.Choice.Delta.builder()
						.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder().index(0).id("call_1").build())
						.build())
					.build())
				.build(),
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("test-model")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(Optional.empty())
							.delta(ChatCompletionChunk.Choice.Delta.builder()
								.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
									.index(0)
									.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
										.name("get_current_weather")
										.build())
									.build())
								.build())
							.build())
						.build(),
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("test-model")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(Optional.empty())
							.delta(ChatCompletionChunk.Choice.Delta.builder()
								.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
									.index(0)
									.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
										.arguments("{\"location\":\"Seoul\"}")
										.build())
									.build())
								.build())
							.build())
						.build(),
					ChatCompletionChunk.builder()
						.id("chatcmpl-stream-test")
						.created(1777799928)
						.model("test-model")
						.addChoice(ChatCompletionChunk.Choice.builder()
							.index(0)
							.finishReason(ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS)
							.delta(ChatCompletionChunk.Choice.Delta.builder().build())
							.build())
						.build()));

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
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

	@ParameterizedTest
	@ValueSource(strings = { "reasoning_content", "reasoning" })
	void streamingReasoningContentSurvivesAggregationWithToolCalls(String reasoningKey) {
		// DeepSeek-style ("reasoning_content") or OpenRouter-style ("reasoning")
		// stream: reasoning deltas first, then a tool call split across chunks, then
		// finish_reason=tool_calls.
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty(reasoningKey, JsonValue.from("Think step one. ")), null),
				streamingChunk(delta -> delta.putAdditionalProperty(reasoningKey, JsonValue.from("Now call the tool.")),
						null),
				streamingChunk(delta -> delta.toolCalls(List.of(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
					.index(0L)
					.id("call_1")
					.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
						.name("getWeather")
						.arguments("{\"city\":")
						.build())
					.build())), null),
				streamingChunk(delta -> delta.toolCalls(List.of(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
					.index(0L)
					.function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
						.arguments("\"Seoul\"}")
						.build())
					.build())).putAdditionalProperty(reasoningKey, JsonValue.from(" One more thought.")), null),
				streamingChunk(delta -> {
				}, ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS));

		AssistantMessage aggregated = aggregateStreaming(chunks);

		assertThat(aggregated.getToolCalls()).hasSize(1);
		assertThat(aggregated.getToolCalls().get(0).name()).isEqualTo("getWeather");
		assertThat(aggregated.getToolCalls().get(0).arguments()).isEqualTo("{\"city\":\"Seoul\"}");
		assertThat(aggregated.getMetadata().get("reasoningContent"))
			.isEqualTo("Think step one. Now call the tool. One more thought.");

		// Replaying the aggregated message on the next turn must re-attach the wire
		// field that providers like DeepSeek require on assistant tool-call messages.
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("deepseek-reasoner").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();
		Prompt replayPrompt = new Prompt(List.of(new UserMessage("What's the weather in Seoul?"), aggregated,
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{\"temp\":20}")))
					.build()),
				options);

		ChatCompletionAssistantMessageParam assistantParam = chatModel.createRequest(replayPrompt, false)
			.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).containsEntry("reasoning_content",
				JsonValue.from("Think step one. Now call the tool. One more thought."));
	}

	@Test
	void streamingReasoningContentSurvivesAggregationWithoutToolCalls() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty("reasoning_content", JsonValue.from("Quick thought. ")), null),
				streamingChunk(
						delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("Another thought.")),
						null),
				streamingChunk(delta -> delta.content("Hello"), null),
				streamingChunk(delta -> delta.content("!"), ChatCompletionChunk.Choice.FinishReason.STOP));

		AssistantMessage aggregated = aggregateStreaming(chunks);

		assertThat(aggregated.getText()).isEqualTo("Hello!");
		assertThat(aggregated.getMetadata().get("reasoningContent")).isEqualTo("Quick thought. Another thought.");
	}

	@Test
	void streamingReasoningContentAccumulatesAcrossIntermediateResponses() {
		List<ChatCompletionChunk> chunks = List.of(
				streamingChunk(delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT)
					.putAdditionalProperty("reasoning_content", JsonValue.from("Think step one. ")), null),
				streamingChunk(
						delta -> delta.putAdditionalProperty("reasoning_content", JsonValue.from("Think step two.")),
						null),
				streamingChunk(delta -> delta.content("Hello"), ChatCompletionChunk.Choice.FinishReason.STOP));

		List<ChatResponse> responses = streamResponses(chunks).collectList().block();

		assertThat(responses).hasSize(3);
		assertThat(responses.get(0).getResult().getOutput().getMetadata().get("reasoningContent"))
			.isEqualTo("Think step one. ");
		assertThat(responses.get(1).getResult().getOutput().getMetadata().get("reasoningContent"))
			.isEqualTo("Think step one. Think step two.");
		assertThat(responses.get(2).getResult().getOutput().getMetadata().get("reasoningContent"))
			.isEqualTo("Think step one. Think step two.");
	}

	private AssistantMessage aggregateStreaming(List<ChatCompletionChunk> chunks) {
		Flux<ChatResponse> responses = streamResponses(chunks);

		// Aggregate the way ChatClient advisors (e.g. ToolCallingAdvisor) do before
		// re-injecting the assistant message into the next request.
		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregatedResponse::set).blockLast();

		assertThat(aggregatedResponse.get()).isNotNull();
		return aggregatedResponse.get().getResult().getOutput();
	}

	private Flux<ChatResponse> streamResponses(List<ChatCompletionChunk> chunks) {
		ChatServiceAsync chatServiceAsync = mock(ChatServiceAsync.class);
		ChatCompletionServiceAsync chatCompletionServiceAsync = mock(ChatCompletionServiceAsync.class);
		when(this.openAiClientAsync.chat()).thenReturn(chatServiceAsync);
		when(chatServiceAsync.completions()).thenReturn(chatCompletionServiceAsync);
		when(chatCompletionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class)))
			.thenReturn(asyncStreamResponseOf(chunks));

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("deepseek-reasoner").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		return chatModel.stream(new Prompt("Preserve reasoning metadata while streaming", options));
	}

	private static ChatCompletionChunk streamingChunk(
			Consumer<ChatCompletionChunk.Choice.Delta.Builder> deltaCustomizer,
			ChatCompletionChunk.Choice.FinishReason finishReason) {
		ChatCompletionChunk.Choice.Delta.Builder deltaBuilder = ChatCompletionChunk.Choice.Delta.builder();
		deltaCustomizer.accept(deltaBuilder);
		return ChatCompletionChunk.builder()
			.id("chatcmpl-123")
			.created(1777799928L)
			.model("deepseek-reasoner")
			.addChoice(ChatCompletionChunk.Choice.builder()
				.index(0L)
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

	@Test
	void metadataDoesNotContainOptionalValues() {
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService chatCompletionService = mock(ChatCompletionService.class);
		when(this.openAiClient.chat()).thenReturn(chatService);
		when(chatService.completions()).thenReturn(chatCompletionService);
		when(chatCompletionService.create(any(ChatCompletionCreateParams.class))).thenReturn(ChatCompletion.builder()
			.id("test-id")
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
		Generation generation = response.getResult();
		assertThat(generation).isNotNull();

		// Verify no Optional values leak into generation metadata
		generation.getMetadata()
			.entrySet()
			.forEach(entry -> assertThat(entry.getValue()).isNotInstanceOf(Optional.class));

		// Verify no Optional values leak into assistant message metadata
		generation.getOutput().getMetadata().forEach((key, value) -> assertThat(value).isNotInstanceOf(Optional.class));
	}

	@Test
	void outputModalitiesUseRootLocaleRegardlessOfDefaultLocale() {
		Locale original = Locale.getDefault();
		Locale.setDefault(new Locale("tr", "TR"));
		try {
			// "AUDIO" contains an uppercase I; under tr_TR, toLowerCase() without
			// Locale.ROOT turns it into "audıo" (dotless ı), which Modality.of(...)
			// won't recognize as the AUDIO modality.
			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("test-model")
				.outputModalities(List.of("TEXT", "AUDIO"))
				.build();
			OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiClient(this.openAiClient)
				.openAiClientAsync(this.openAiClientAsync)
				.options(options)
				.build();

			ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

			assertThat(request.modalities())
				.contains(List.of(ChatCompletionCreateParams.Modality.TEXT, ChatCompletionCreateParams.Modality.AUDIO));
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void reasoningEffortUsesRootLocaleRegardlessOfDefaultLocale() {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(new Locale("tr", "TR"));
			// "MINIMAL" contains an uppercase I; under tr_TR it lowers to "mınımal",
			// which ReasoningEffort.of(...) won't match to the MINIMAL constant.
			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("test-model")
				.reasoningEffort("MINIMAL")
				.build();
			OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiClient(this.openAiClient)
				.openAiClientAsync(this.openAiClientAsync)
				.options(options)
				.build();

			ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

			assertThat(request.reasoningEffort()).contains(ReasoningEffort.MINIMAL);
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void audioVoiceUsesRootLocaleRegardlessOfDefaultLocale() {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(new Locale("tr", "TR"));
			// SHIMMER contains an uppercase I; under tr_TR it lowers to "shımmer"
			// instead of the wire-correct "shimmer". The fix lives in
			// OpenAiChatOptions.AudioParameters#toChatCompletionAudioParam(),
			// but it's only reachable through OpenAiChatModel#createRequest.
			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("test-model")
				.outputAudio(new OpenAiChatOptions.AudioParameters(OpenAiChatOptions.AudioParameters.Voice.SHIMMER,
						OpenAiChatOptions.AudioParameters.AudioResponseFormat.WAV))
				.build();
			OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiClient(this.openAiClient)
				.openAiClientAsync(this.openAiClientAsync)
				.options(options)
				.build();

			ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

			assertThat(request.audio()).isPresent();
			assertThat(request.audio().get().voice().string().get().equals("shimmer"));
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void streamingFinishReasonSurvivesRoundTripUnderTurkishLocale() {
		Locale original = Locale.getDefault();
		Locale.setDefault(new Locale("tr", "TR"));
		try {
			// CONTENT_FILTER contains an uppercase I; under tr_TR the chunk-merging
			// round trip lowers it to "content_fılter", which
			// ChatCompletion.Choice.FinishReason.of(...) treats as unknown.
			List<ChatCompletionChunk> chunks = List.of(streamingChunk(
					delta -> delta.role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT).content("Blocked"),
					ChatCompletionChunk.Choice.FinishReason.CONTENT_FILTER));

			ChatResponse response = streamResponses(chunks).blockLast();

			assertThat(response).isNotNull();
			assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("CONTENT_FILTER");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void toolStrictIsEmittedAtFunctionLevel() {
		ToolCallingManager mockToolCallingManager = mock(ToolCallingManager.class);

		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name("get_weather")
			.description("Get weather")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
			.build();

		when(mockToolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of(toolDefinition));

		// Model options with model set, relying on the fallback default logic of
		// strict(true)
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4.1").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.toolCallingManager(mockToolCallingManager)
			.build();

		// Current behavior: createRequest processes Prompt's options down to
		// getChatCompletionTools
		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

		var tools = request.tools().orElseThrow();
		assertThat(tools).hasSize(1);

		ChatCompletionFunctionTool functionTool = tools.get(0).function().orElseThrow();
		FunctionDefinition functionDef = functionTool.function();

		assertThat(functionDef.strict()).contains(true);

		FunctionParameters parameters = functionDef.parameters().orElseThrow();
		assertThat(parameters._additionalProperties()).doesNotContainKey("strict");
	}

	@Test
	void toolStrictFalseOverrideAtRequestLevel() {
		ToolCallingManager mockToolCallingManager = mock(ToolCallingManager.class);

		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name("get_weather")
			.description("Get weather")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
			.build();

		when(mockToolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of(toolDefinition));

		// Model configured with strict(true) by default
		OpenAiChatOptions modelOptions = OpenAiChatOptions.builder().model("gpt-4.1").strict(true).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(modelOptions)
			.toolCallingManager(mockToolCallingManager)
			.build();

		// Prompt requests strict(false) override
		OpenAiChatOptions requestOptions = OpenAiChatOptions.builder().strict(false).build();
		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", requestOptions), false);

		var tools = request.tools().orElseThrow();
		assertThat(tools).hasSize(1);

		ChatCompletionFunctionTool functionTool = tools.get(0).function().orElseThrow();
		FunctionDefinition functionDef = functionTool.function();

		// Verify that prompt-level option overrides model default to false
		assertThat(functionDef.strict()).contains(false);
	}

	@Test
	void toolStrictMaintainsIsolationPerRequest() {
		ToolCallingManager mockToolCallingManager = mock(ToolCallingManager.class);

		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name("get_weather")
			.description("Get weather")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
			.build();

		when(mockToolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of(toolDefinition));

		OpenAiChatOptions modelOptions = OpenAiChatOptions.builder().model("gpt-4.1").strict(true).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(modelOptions)
			.toolCallingManager(mockToolCallingManager)
			.build();

		// Request A explicitly disables strict mode
		OpenAiChatOptions requestOptionsA = OpenAiChatOptions.builder().strict(false).build();
		ChatCompletionCreateParams requestA = chatModel.createRequest(new Prompt("test A", requestOptionsA), false);

		// Request B explicitly enables strict mode
		OpenAiChatOptions requestOptionsB = OpenAiChatOptions.builder().strict(true).build();
		ChatCompletionCreateParams requestB = chatModel.createRequest(new Prompt("test B", requestOptionsB), false);

		// Extract tool evaluations for Request A
		com.openai.models.FunctionDefinition functionDefA = requestA.tools()
			.orElseThrow()
			.get(0)
			.function()
			.orElseThrow()
			.function();

		// Extract tool evaluations for Request B
		com.openai.models.FunctionDefinition functionDefB = requestB.tools()
			.orElseThrow()
			.get(0)
			.function()
			.orElseThrow()
			.function();

		// Verify isolation across execution payloads
		assertThat(functionDefA.strict()).contains(false);
		assertThat(functionDefB.strict()).contains(true);
	}

	@Test
	void jsonSchemaResponseFormatStrictDefaultsToTrue() {
		String jsonSchema = """
				{
					"type": "object",
					"properties": {
						"name": { "type": "string" }
					}
				}
				""";
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("test-model")
			.responseFormat(ResponseFormat.builder().jsonSchema(jsonSchema).build())
			.build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

		assertThat(request.responseFormat()).isPresent();
		assertThat(request.responseFormat().get().isJsonSchema()).isTrue();
		assertThat(request.responseFormat().get().jsonSchema().get().jsonSchema().strict()).contains(true);
	}

	@Test
	void jsonSchemaResponseFormatStrictCanBeDisabled() {
		String jsonSchema = """
				{
					"type": "object",
					"properties": {
						"name": { "type": "string" }
					}
				}
				""";
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("test-model")
			.responseFormat(ResponseFormat.builder().jsonSchema(jsonSchema).strict(false).build())
			.build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);

		assertThat(request.responseFormat()).isPresent();
		assertThat(request.responseFormat().get().isJsonSchema()).isTrue();
		assertThat(request.responseFormat().get().jsonSchema().get().jsonSchema().strict()).contains(false);
	}

}
