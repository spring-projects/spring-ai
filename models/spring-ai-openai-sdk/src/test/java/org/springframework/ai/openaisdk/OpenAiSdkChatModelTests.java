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

package org.springframework.ai.openaisdk;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenAiSdkChatModel}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSdkChatModelTests {

	@Mock
	OpenAIClient openAiClient;

	@Mock
	OpenAIClientAsync openAiClientAsync;

	@Test
	void toolChoiceAuto() {
		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder().model("test-model").toolChoice("auto").build();
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
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
		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder().model("test-model").toolChoice("none").build();
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
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
		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
			.model("test-model")
			.toolChoice("required")
			.build();
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
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
		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder().model("test-model").toolChoice(json).build();
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
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
		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
			.model("test-model")
			.toolChoice("invalid-json")
			.build();
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failed to parse toolChoice JSON");
	}

}
