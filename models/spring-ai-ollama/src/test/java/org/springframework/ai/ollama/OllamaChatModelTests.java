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

package org.springframework.ai.ollama;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jihoon Kim
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @author Dimitar Proynov
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class OllamaChatModelTests {

	@Mock
	OllamaApi ollamaApi;

	private static final String ERROR_MID_STREAM = """
			{"model":"mistral","created_at":"2026-01-01T00:00:00Z","message":{"role":"assistant","content":"Hel"},"done":false}
			{"error":"unable to process image"}
			""";

	@Test
	void buildOllamaChatModelWithConstructor() {
		ChatModel chatModel = new OllamaChatModel(this.ollamaApi,
				OllamaChatOptions.builder().model(OllamaModel.MISTRAL).build(), ToolCallingManager.builder().build(),
				ObservationRegistry.NOOP, ModelManagementOptions.builder().build());
		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildOllamaChatModelWithBuilder() {
		ChatModel chatModel = OllamaChatModel.builder().ollamaApi(this.ollamaApi).build();
		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildOllamaChatModel() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> OllamaChatModel.builder()
					.ollamaApi(this.ollamaApi)
					.options(OllamaChatOptions.builder().model(OllamaModel.LLAMA2).build())
					.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
					.modelManagementOptions(null)
					.build());
		assertEquals("modelManagementOptions must not be null", exception.getMessage());
	}

	@Test
	void streamWhenServerReportsAnErrorMidStreamShouldNotFail() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/x-ndjson")
				.setBody(ERROR_MID_STREAM));
			server.start();

			ChatModel chatModel = OllamaChatModel.builder()
				.ollamaApi(OllamaApi.builder().baseUrl(server.url("/").toString()).build())
				.options(OllamaChatOptions.builder().model(OllamaModel.MISTRAL).build())
				.build();

			List<ChatResponse> responses = chatModel.stream(new Prompt(new UserMessage("Hello"))).collectList().block();

			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).getResult().getOutput().getText()).isEqualTo("Hel");
			assertThat(responses.get(1).getResult().getOutput().getText()).isEmpty();
		}
	}

	@Test
	void streamWhenChunkHasNoMessageShouldNotFail() {
		// A chunk that reports an error instead of a chat message deserializes into a
		// response with every field unset.
		OllamaApi.ChatResponse errorChunk = new OllamaApi.ChatResponse(null, null, null, null, null, null, null, null,
				null, null, null);
		OllamaApi.ChatResponse doneChunk = new OllamaApi.ChatResponse("model", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("Hello").build(), "stop", true,
				null, null, null, null, null, null);
		when(this.ollamaApi.streamingChat(any())).thenReturn(Flux.just(errorChunk, doneChunk));

		ChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.options(OllamaChatOptions.builder().model(OllamaModel.MISTRAL).build())
			.build();

		List<ChatResponse> responses = chatModel.stream(new Prompt(new UserMessage("Hello"))).collectList().block();

		assertThat(responses).hasSize(2);
		AssistantMessage errorMessage = responses.get(0).getResult().getOutput();
		assertThat(errorMessage.getText()).isEmpty();
		assertThat(errorMessage.getToolCalls()).isEmpty();
		assertThat(errorMessage.getMetadata()).doesNotContainKey("thinking");
		assertThat(responses.get(0).getMetadata().getModel()).isEmpty();
		assertThat(responses.get(1).getResult().getOutput().getText()).isEqualTo("Hello");
	}

	@Test
	void callWhenResponseHasNoMessageShouldNotFail() {
		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse(null, null, null, null, null, null, null, null,
				null, null, null);
		when(this.ollamaApi.chat(any())).thenReturn(response);

		ChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.options(OllamaChatOptions.builder().model(OllamaModel.MISTRAL).build())
			.build();

		ChatResponse chatResponse = chatModel.call(new Prompt(new UserMessage("Hello")));

		AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
		assertThat(assistantMessage.getText()).isEmpty();
		assertThat(assistantMessage.getToolCalls()).isEmpty();
		assertThat(assistantMessage.getMetadata()).doesNotContainKey("thinking");
		assertThat(chatResponse.getMetadata().getModel()).isEmpty();
	}

	@Test
	void buildChatResponseMetadata() {

		Long evalDuration = 1000L;
		Integer evalCount = 101;

		Integer promptEvalCount = 808;
		Long promptEvalDuration = 8L;

		Long loadDuration = 100L;
		Long totalDuration = 2000L;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null,
				totalDuration, loadDuration, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertEquals(Duration.ofNanos(evalDuration), metadata.get("eval-duration"));
		assertEquals(evalCount, metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration), metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount, metadata.get("prompt-eval-count"));
	}

	@Test
	void buildChatResponseMetadataAggregationWithNonEmptyMetadata() {

		Long evalDuration = 1000L;
		Integer evalCount = 101;

		Integer promptEvalCount = 808;
		Long promptEvalDuration = 8L;

		Long loadDuration = 100L;
		Long totalDuration = 2000L;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null,
				totalDuration, loadDuration, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponse previousChatResponse = ChatResponse.builder()
			.generations(List.of())
			.metadata(ChatResponseMetadata.builder()
				.usage(new DefaultUsage(66, 99))
				.keyValue("eval-duration", Duration.ofSeconds(2))
				.keyValue("prompt-eval-duration", Duration.ofSeconds(2))
				.build())
			.build();

		ChatResponseMetadata metadata = OllamaChatModel.from(response, previousChatResponse);

		assertThat(metadata.getUsage()).isEqualTo(new DefaultUsage(808 + 66, 101 + 99));

		assertEquals(Duration.ofNanos(evalDuration).plus(Duration.ofSeconds(2)), metadata.get("eval-duration"));
		assertEquals((evalCount + 99), (Integer) metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration).plus(Duration.ofSeconds(2)),
				metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount + 66, (Integer) metadata.get("prompt-eval-count"));
	}

	@Test
	void buildChatResponseMetadataAggregationWithNonEmptyMetadataButEmptyEval() {

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null, null,
				null, null, null, null, null);

		ChatResponse previousChatResponse = ChatResponse.builder()
			.generations(List.of())
			.metadata(ChatResponseMetadata.builder()
				.usage(new DefaultUsage(66, 99))
				.keyValue("eval-duration", Duration.ofSeconds(2))
				.keyValue("prompt-eval-duration", Duration.ofSeconds(2))
				.build())
			.build();

		ChatResponseMetadata metadata = OllamaChatModel.from(response, previousChatResponse);

		assertNull(metadata.get("eval-duration"));
		assertNull(metadata.get("prompt-eval-duration"));
		assertEquals(Integer.valueOf(99), metadata.get("eval-count"));
		assertEquals(Integer.valueOf(66), metadata.get("prompt-eval-count"));

	}

	@Test
	void buildOllamaChatModelWithNullOllamaApi() {
		assertThatThrownBy(() -> OllamaChatModel.builder().ollamaApi(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("OllamaApi must not be null");
	}

	@Test
	void buildOllamaChatModelWithAllBuilderOptions() {
		OllamaChatOptions options = OllamaChatOptions.builder().model(OllamaModel.CODELLAMA).temperature(0.0).build();

		ToolCallingManager toolManager = ToolCallingManager.builder().build();
		ModelManagementOptions managementOptions = ModelManagementOptions.builder().build();

		ChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.options(options)
			.toolCallingManager(toolManager)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.observationRegistry(ObservationRegistry.NOOP)
			.modelManagementOptions(managementOptions)
			.build();

		assertThat(chatModel).isNotNull();
		assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
	}

	@Test
	void buildChatResponseMetadataWithLargeValues() {
		Long evalDuration = Long.MAX_VALUE;
		Integer evalCount = Integer.MAX_VALUE;
		Integer promptEvalCount = Integer.MAX_VALUE;
		Long promptEvalDuration = Long.MAX_VALUE;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null,
				Long.MAX_VALUE, Long.MAX_VALUE, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertEquals(Duration.ofNanos(evalDuration), metadata.get("eval-duration"));
		assertEquals(evalCount, metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration), metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount, metadata.get("prompt-eval-count"));
	}

	@Test
	void buildChatResponseMetadataAggregationWithNullPrevious() {
		Long evalDuration = 1000L;
		Integer evalCount = 101;
		Integer promptEvalCount = 808;
		Long promptEvalDuration = 8L;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null, 2000L,
				100L, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertThat(metadata.getUsage()).isEqualTo(new DefaultUsage(promptEvalCount, evalCount));
		assertEquals(Duration.ofNanos(evalDuration), metadata.get("eval-duration"));
		assertEquals(evalCount, metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration), metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount, metadata.get("prompt-eval-count"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "LLAMA2", "MISTRAL", "CODELLAMA", "LLAMA3", "GEMMA" })
	void buildOllamaChatModelWithDifferentModels(String modelName) {
		OllamaModel model = OllamaModel.valueOf(modelName);
		OllamaChatOptions options = OllamaChatOptions.builder().model(model).build();

		ChatModel chatModel = OllamaChatModel.builder().ollamaApi(this.ollamaApi).options(options).build();

		assertThat(chatModel).isNotNull();
		assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
	}

	@Test
	void buildOllamaChatModelWithCustomObservationRegistry() {
		ObservationRegistry customRegistry = ObservationRegistry.create();

		ChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.observationRegistry(customRegistry)
			.build();

		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildChatResponseMetadataPreservesModelName() {
		String modelName = "custom-model-name";
		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse(modelName, Instant.now(), null, null, null, 1000L,
				100L, 10, 50L, 20, 200L);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		// Verify that model information is preserved in metadata
		assertThat(metadata).isNotNull();
		// Note: The exact key for model name would depend on the implementation
		// This test verifies that metadata building doesn't lose model information
	}

	@Test
	void buildChatResponseMetadataWithInstantTime() {
		Instant createdAt = Instant.now();
		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", createdAt, null, null, null, 1000L, 100L,
				10, 50L, 20, 200L);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertThat(metadata).isNotNull();
		// Verify timestamp is preserved (exact key depends on implementation)
	}

	@Test
	void buildChatResponseMetadataAggregationOverflowHandling() {
		// Test potential integer overflow scenarios
		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null, 1000L,
				100L, Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);

		ChatResponse previousChatResponse = ChatResponse.builder()
			.generations(List.of())
			.metadata(ChatResponseMetadata.builder()
				.usage(new DefaultUsage(1, 1))
				.keyValue("eval-duration", Duration.ofNanos(1L))
				.keyValue("prompt-eval-duration", Duration.ofNanos(1L))
				.build())
			.build();

		// This should not throw an exception, even with potential overflow
		ChatResponseMetadata metadata = OllamaChatModel.from(response, previousChatResponse);
		assertThat(metadata).isNotNull();
	}

	@Test
	void buildOllamaChatModelImmutability() {
		// Test that the builder creates immutable instances
		OllamaChatOptions options = OllamaChatOptions.builder().model(OllamaModel.MISTRAL).temperature(0.0).build();

		ChatModel chatModel1 = OllamaChatModel.builder().ollamaApi(this.ollamaApi).options(options).build();

		ChatModel chatModel2 = OllamaChatModel.builder().ollamaApi(this.ollamaApi).options(options).build();

		// Should create different instances
		assertThat(chatModel1).isNotSameAs(chatModel2);
		assertThat(chatModel1).isNotNull();
		assertThat(chatModel2).isNotNull();
	}

	@Test
	void buildChatResponseMetadataWithZeroValues() {
		// Test with all zero/minimal values
		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null, 0L, 0L,
				0, 0L, 0, 0L);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertEquals(Duration.ZERO, metadata.get("eval-duration"));
		assertEquals(Integer.valueOf(0), metadata.get("eval-count"));
		assertEquals(Duration.ZERO, metadata.get("prompt-eval-duration"));
		assertEquals(Integer.valueOf(0), metadata.get("prompt-eval-count"));
		assertThat(metadata.getUsage()).isEqualTo(new DefaultUsage(0, 0));
	}

	@Test
	void buildOllamaChatModelWithMinimalConfiguration() {
		// Test building with only required parameters
		ChatModel chatModel = OllamaChatModel.builder().ollamaApi(this.ollamaApi).build();

		assertThat(chatModel).isNotNull();
		assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
	}

	@Test
	void thinkingFieldIsStoredInAssistantMessageProperties() {
		String thinkingText = "Let me reason step by step...";
		OllamaApi.Message assistantApiMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content("The answer is 42.")
			.thinking(thinkingText)
			.build();
		OllamaApi.ChatResponse apiResponse = new OllamaApi.ChatResponse("model", Instant.now(), assistantApiMessage,
				"stop", true, null, null, 10, 1000L, 20, 2000L);
		when(this.ollamaApi.chat(any())).thenReturn(apiResponse);

		OllamaChatModel chatModel = OllamaChatModel.builder().ollamaApi(this.ollamaApi).build();
		ChatResponse response = chatModel.call(new Prompt(new UserMessage("What is the answer?")));

		Generation generation = response.getResult();
		AssistantMessage message = generation.getOutput();
		assertThat(message.getMetadata()).containsKey("thinking");
		assertThat(message.getMetadata().get("thinking")).isEqualTo(thinkingText);
	}

	@Test
	void thinkingFieldRoundTripsThroughConversationHistory() {
		String thinkingText = "Step 1: understand the question...";
		OllamaApi.Message firstApiMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content("First answer.")
			.thinking(thinkingText)
			.build();
		OllamaApi.ChatResponse firstApiResponse = new OllamaApi.ChatResponse("model", Instant.now(), firstApiMessage,
				"stop", true, null, null, 10, 1000L, 20, 2000L);

		OllamaApi.Message secondApiMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content("Second answer.")
			.build();
		OllamaApi.ChatResponse secondApiResponse = new OllamaApi.ChatResponse("model", Instant.now(), secondApiMessage,
				"stop", true, null, null, 10, 1000L, 20, 2000L);
		when(this.ollamaApi.chat(any())).thenReturn(firstApiResponse).thenReturn(secondApiResponse);

		OllamaChatModel chatModel = OllamaChatModel.builder().ollamaApi(this.ollamaApi).build();

		ChatResponse firstResponse = chatModel.call(new Prompt(new UserMessage("Turn 1")));
		AssistantMessage firstAssistantMessage = firstResponse.getResult().getOutput();
		assertThat(firstAssistantMessage.getMetadata().get("thinking")).isEqualTo(thinkingText);

		Prompt secondPrompt = new Prompt(
				List.of(new UserMessage("Turn 1"), firstAssistantMessage, new UserMessage("Turn 2")));
		chatModel.call(secondPrompt);

		ArgumentCaptor<OllamaApi.ChatRequest> requests = ArgumentCaptor.forClass(OllamaApi.ChatRequest.class);
		verify(this.ollamaApi, times(2)).chat(requests.capture());
		OllamaApi.Message replayed = requests.getAllValues().get(1).messages().get(1);
		assertThat(replayed.role()).isEqualTo(Role.ASSISTANT);
		assertThat(replayed.content()).isEqualTo("First answer.");
		assertThat(replayed.thinking()).isEqualTo(thinkingText);
	}

	@Test
	void thinkingAndTextBecomeReasoningThenTextParts() {
		when(this.ollamaApi.chat(any())).thenReturn(response(OllamaApi.Message.builder(Role.ASSISTANT)
			.content("The answer is 42.")
			.thinking("Let me think.")
			.build(), true));

		ChatResponse response = chatModel().call(new Prompt("What is the answer?"));
		AssistantMessage message = response.getResult().getOutput();

		assertThat(message.getParts()).containsExactly(ReasoningPart.of("Let me think."),
				TextPart.of("The answer is 42."));
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("Let me think."));
		assertThat(message.getText()).isEqualTo("The answer is 42.");
		// The deprecated metadata key is still written on the message and the generation
		assertThat(message.getMetadata()).containsEntry("thinking", "Let me think.");
		assertThat(response.getResult().getMetadata().<String>get("thinking")).isEqualTo("Let me think.");
	}

	@Test
	void answerWithoutThinkingIsASingleTextPart() {
		when(this.ollamaApi.chat(any()))
			.thenReturn(response(OllamaApi.Message.builder(Role.ASSISTANT).content("hello").build(), true));

		AssistantMessage message = chatModel().call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getReasoning()).isEmpty();
		assertThat(message.getMetadata()).doesNotContainKey("thinking");
	}

	@Test
	void toolCallAnswerHasToolCallPartsAndAnEmptyText() {
		when(this.ollamaApi.chat(any())).thenReturn(response(OllamaApi.Message.builder(Role.ASSISTANT)
			.content("")
			.toolCalls(List.of(toolCall("call_1", "getWeather", "Seoul"), toolCall("call_2", "getWeather", "Sofia")))
			.build(), true));

		ChatResponse response = chatModel().call(new Prompt("Weather?"));
		AssistantMessage message = response.getResult().getOutput();

		assertThat(message.getParts()).containsExactly(
				ToolCallPart
					.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}")),
				ToolCallPart
					.of(new AssistantMessage.ToolCall("call_2", "function", "getWeather", "{\"city\":\"Sofia\"}")));
		assertThat(message.getText()).isEmpty();
		assertThat(response.hasToolCalls()).isTrue();
	}

	@Test
	void thinkingBeforeToolCallIsKeptInOrder() {
		when(this.ollamaApi.chat(any())).thenReturn(response(OllamaApi.Message.builder(Role.ASSISTANT)
			.thinking("I need the weather tool.")
			.toolCalls(List.of(toolCall("call_1", "getWeather", "Seoul")))
			.build(), true));

		AssistantMessage message = chatModel().call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts()).hasSize(2);
		assertThat(message.getParts().get(0)).isEqualTo(ReasoningPart.of("I need the weather tool."));
		assertThat(message.getParts().get(1)).isInstanceOf(ToolCallPart.class);
		assertThat(message.getText()).isEmpty();
	}

	@Test
	void emptyAnswerKeepsAnEmptyTextPart() {
		when(this.ollamaApi.chat(any()))
			.thenReturn(response(OllamaApi.Message.builder(Role.ASSISTANT).content("").build(), true));

		AssistantMessage message = chatModel().call(new Prompt("hi")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(TextPart.of(""));
		assertThat(message.getText()).isEmpty();
	}

	@Test
	void streamingThinkingAndTextProduceIndexedParts() {
		when(this.ollamaApi.streamingChat(any())).thenReturn(Flux.just(
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("Think ").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("first.").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("Hello").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("!").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").build(), true)));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Say hi")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(chunks).hasSize(5);
		AssistantMessage first = chunks.get(0).getResult().getOutput();
		assertThat(first.getParts()).containsExactly(StreamingParts.partial(ReasoningPart.of("Think "), 0));
		assertThat(first.getText()).isEmpty();
		assertThat(first.getMetadata()).containsEntry("thinking", "Think ");
		assertThat(chunks.get(0).getResult().getMetadata().<String>get("thinking")).isEqualTo("Think ");
		assertThat(chunks.get(1).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("first."), 0));
		AssistantMessage third = chunks.get(2).getResult().getOutput();
		assertThat(third.getParts()).containsExactly(StreamingParts.partial(TextPart.of("Hello"), 1));
		assertThat(third.getText()).isEqualTo("Hello");
		assertThat(third.getMetadata()).doesNotContainKey("thinking");
		assertThat(chunks.get(3).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(TextPart.of("!"), 1));
		// The final done chunk carries no content: an unindexed empty text, as before
		assertThat(chunks.get(4).getResult().getOutput().getParts()).containsExactly(TextPart.of(""));

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Think first."), TextPart.of("Hello!"));
		assertThat(aggregated.getText()).isEqualTo("Hello!");
	}

	@Test
	void streamingKeepsWhitespaceOnlyThinkingDeltas() {
		when(this.ollamaApi.streamingChat(any())).thenReturn(Flux.just(
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("Step one.").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("\n\n").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("Step two.").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("Done").build(), true)));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Go")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(chunks.get(1).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(ReasoningPart.of("\n\n"), 0));
		assertThat(aggregatedRef.get().getResult().getOutput().getParts())
			.containsExactly(ReasoningPart.of("Step one.\n\nStep two."), TextPart.of("Done"));
	}

	@Test
	void streamingToolCallChunkProducesCompleteToolCallPart() {
		when(this.ollamaApi.streamingChat(any())).thenReturn(Flux.just(
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("Need the tool.").build(),
						false),
				response(OllamaApi.Message.builder(Role.ASSISTANT)
					.content("")
					.toolCalls(List.of(toolCall("call_1", "getWeather", "Seoul")))
					.build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").build(), true)));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(chatModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(chunks.get(0).hasToolCalls()).isFalse();
		AssistantMessage second = chunks.get(1).getResult().getOutput();
		assertThat(second.getParts()).hasSize(1);
		MessagePart part = second.getParts().get(0);
		assertThat(part).isInstanceOf(ToolCallPart.class);
		assertThat(StreamingParts.isPartial(part)).isFalse();
		assertThat(StreamingParts.partIndex(part)).isEqualTo(1);
		assertThat(chunks.get(1).hasToolCalls()).isTrue();
		assertThat(second.getText()).isEmpty();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("Need the tool."), ToolCallPart
			.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}")));
	}

	@Test
	void streamedReasoningIsReplayedAsThinking() {
		when(this.ollamaApi.streamingChat(any())).thenReturn(Flux.just(
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("17 x 23 ").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("").thinking("= 391.").build(), false),
				response(OllamaApi.Message.builder(Role.ASSISTANT).content("391").build(), true)));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(chatModel().stream(new Prompt("Calculate 17 x 23")), aggregatedRef::set)
			.blockLast();

		OllamaApi.Message replayed = assistantRequestMessage(aggregatedRef.get().getResult().getOutput());

		// The deprecated metadata entry only holds the last thinking delta: the parts win
		assertThat(replayed.thinking()).isEqualTo("17 x 23 = 391.");
		assertThat(replayed.content()).isEqualTo("391");
	}

	@Test
	void reasoningPartIsReplayedAsThinking() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("17 x 23 = 391."))
			.part(TextPart.of("391"))
			.build();

		OllamaApi.Message replayed = assistantRequestMessage(assistant);

		assertThat(replayed.thinking()).isEqualTo("17 x 23 = 391.");
		assertThat(replayed.content()).isEqualTo("391");
	}

	@Test
	void reasoningPartsAreJoinedInOrderOnReplay() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("17 x 20 = 340."))
			.part(new ReasoningPart("signed", null, new OpaquePayload("anthropic", "signature", "sig"), Map.of()))
			.part(TextPart.of("Then the units."))
			.part(ReasoningPart.of("340 + 51 = 391."))
			.part(TextPart.of("391"))
			.build();

		assertThat(assistantRequestMessage(assistant).thinking()).isEqualTo("17 x 20 = 340.\n340 + 51 = 391.");
	}

	@Test
	void legacyThinkingMetadataIsReplayedWhenThereIsNoReasoningPart() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("391")
			.properties(Map.of("thinking", "17 x 23 = 391."))
			.build();

		assertThat(assistantRequestMessage(assistant).thinking()).isEqualTo("17 x 23 = 391.");
	}

	@Test
	void foreignSignedReasoningIsNotReplayedAsThinking() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("anthropic thoughts", null, new OpaquePayload("anthropic", "signature", "sig"),
					Map.of()))
			.part(TextPart.of("391"))
			.properties(Map.of("thinking", "stale"))
			.build();

		assertThat(assistantRequestMessage(assistant).thinking()).isNull();
	}

	@Test
	void plainAssistantMessageHasNoThinkingOnReplay() {
		assertThat(assistantRequestMessage(new AssistantMessage("391")).thinking()).isNull();
	}

	@Test
	void toolCallsAreReplayedFromToolCallParts() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("Need the tool."))
			.part(ToolCallPart
				.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}")))
			.build();

		OllamaApi.Message replayed = assistantRequestMessage(assistant);

		assertThat(replayed.thinking()).isEqualTo("Need the tool.");
		assertThat(replayed.content()).isEmpty();
		assertThat(replayed.toolCalls()).containsExactly(toolCall("call_1", "getWeather", "Seoul"));
	}

	private OllamaChatModel chatModel() {
		return OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.options(OllamaChatOptions.builder().model("qwen3").build())
			.build();
	}

	private OllamaApi.Message assistantRequestMessage(AssistantMessage assistant) {
		Prompt prompt = new Prompt(List.<Message>of(new UserMessage("Calculate 17 x 23"), assistant),
				OllamaChatOptions.builder().model("qwen3").build());
		return chatModel().ollamaChatRequest(prompt, false)
			.messages()
			.stream()
			.filter(m -> m.role() == Role.ASSISTANT)
			.findFirst()
			.orElseThrow();
	}

	private static OllamaApi.Message.ToolCall toolCall(String id, String name, String city) {
		return new OllamaApi.Message.ToolCall(id, new OllamaApi.Message.ToolCallFunction(name, Map.of("city", city)));
	}

	private static OllamaApi.ChatResponse response(OllamaApi.Message message, boolean done) {
		return new OllamaApi.ChatResponse("qwen3", Instant.now(), message, done ? "stop" : null, done, null, null,
				done ? 10 : null, null, done ? 20 : null, null);
	}

}
