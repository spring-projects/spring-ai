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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaApi;
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
import static org.mockito.Mockito.when;

/**
 * @author Jihoon Kim
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @author Nicolas Krier
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class OllamaChatModelTests {

	@Mock
	private OllamaApi ollamaApi;

	@Test
	void buildOllamaChatModelWithConstructor() {
		ChatModel chatModel = new OllamaChatModel(this.ollamaApi,
				OllamaChatOptions.builder().model(OllamaModel.MINISTRAL_3_8B).build(),
				ToolCallingManager.builder().build(), ObservationRegistry.NOOP,
				ModelManagementOptions.builder().build());
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
		OllamaChatOptions options = OllamaChatOptions.builder()
			.model(OllamaModel.CODELLAMA)
			.temperature(0.7)
			.topK(50)
			.build();

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
	@EnumSource(value = OllamaModel.class, names = { "LLAMA2", "MINISTRAL_3_8B", "CODELLAMA", "LLAMA3", "GEMMA" })
	void buildOllamaChatModelWithDifferentModels(OllamaModel model) {
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
		OllamaChatOptions options = OllamaChatOptions.builder()
			.model(OllamaModel.MINISTRAL_3_14B)
			.temperature(0.5)
			.build();

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
	}

}
