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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

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
	}

	@Test
	void toolChoiceNone() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("none").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("SDK version does not support typed 'none' toolChoice");
	}

	@Test
	void toolChoiceRequired() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("required").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("SDK version does not support typed 'required' toolChoice");
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
	void preserveRateLimitAndPromptMetadataInAggregation() throws Exception {
		RateLimit rateLimit = mock(RateLimit.class);
		PromptMetadata promptMetadata = mock(PromptMetadata.class);
		Usage usage = mock(Usage.class);

		ChatResponseMetadata originalMetadata = ChatResponseMetadata.builder()
			.id("test-id")
			.model("test-model")
			.rateLimit(rateLimit)
			.promptMetadata(promptMetadata)
			.keyValue("custom-key", "custom-value")
			.build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.build();

		Method fromMethod = OpenAiChatModel.class.getDeclaredMethod("from", ChatResponseMetadata.class, Usage.class);
		fromMethod.setAccessible(true);

		ChatResponseMetadata aggregatedMetadata = (ChatResponseMetadata) fromMethod.invoke(chatModel, originalMetadata,
				usage);

		assertThat(aggregatedMetadata.getId()).isEqualTo("test-id");
		assertThat(aggregatedMetadata.getModel()).isEqualTo("test-model");
		assertThat(aggregatedMetadata.getUsage()).isSameAs(usage);
		assertThat(aggregatedMetadata.getRateLimit()).isSameAs(rateLimit);
		assertThat(aggregatedMetadata.getPromptMetadata()).isSameAs(promptMetadata);
		assertThat((String) aggregatedMetadata.get("custom-key")).isEqualTo("custom-value");
	}

}
