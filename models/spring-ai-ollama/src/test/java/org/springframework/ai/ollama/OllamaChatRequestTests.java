/*
 * Copyright 2024-2024 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.ChatOptions;
import org.springframework.ai.chat.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class OllamaChatRequestTests {

	OllamaChatClient client = new OllamaChatClient(new OllamaApi()).withDefaultOptions(
			new OllamaOptions().withModel("MODEL_NAME").withTopK(99).withTemperature(66.6f).withNumGPU(1));

	@Test
	public void createRequestWithDefaultOptions() {

		var request = client.ollamaChatRequest(new Prompt("Test message content"), false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(66.6);
		assertThat(request.options().get("top_k")).isEqualTo(99);
		assertThat(request.options().get("num_gpu")).isEqualTo(1);
		assertThat(request.options().get("top_p")).isNull();
	}

	@Test
	public void createRequestWithPromptOllamaOptions() {

		// Runtime options should override the default options.
		OllamaOptions promptOptions = new OllamaOptions().withTemperature(0.8f).withTopP(0.5f).withNumGPU(2);

		var request = client.ollamaChatRequest(new Prompt("Test message content", promptOptions), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(0.8);
		assertThat(request.options().get("top_k")).isEqualTo(99); // still the default
																	// value.
		assertThat(request.options().get("num_gpu")).isEqualTo(2);
		assertThat(request.options().get("top_p")).isEqualTo(0.5); // new field introduced
																	// by the
																	// promptOptions.
	}

	@Test
	public void createRequestWithPromptPortableChatOptions() {

		// Ollama runtime options.
		ChatOptions portablePromptOptions = ChatOptionsBuilder.builder()
			.withTemperature(0.9f)
			.withTopK(100)
			.withTopP(0.6f)
			.build();

		var request = client.ollamaChatRequest(new Prompt("Test message content", portablePromptOptions), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(0.9);
		assertThat(request.options().get("top_k")).isEqualTo(100);
		assertThat(request.options().get("num_gpu")).isEqualTo(1); // default value.
		assertThat(request.options().get("top_p")).isEqualTo(0.6);
	}

	@Test
	public void createRequestWithPromptOptionsModelOverride() {

		// Ollama runtime options.
		OllamaOptions promptOptions = new OllamaOptions().withModel("PROMPT_MODEL");

		var request = client.ollamaChatRequest(new Prompt("Test message content", promptOptions), true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

	@Test
	public void createRequestWithDefaultOptionsModelOverride() {

		OllamaChatClient client2 = new OllamaChatClient(new OllamaApi())
			.withDefaultOptions(new OllamaOptions().withModel("DEFAULT_OPTIONS_MODEL"));

		var request = client2.ollamaChatRequest(new Prompt("Test message content"), true);

		assertThat(request.model()).isEqualTo("DEFAULT_OPTIONS_MODEL");

		// Prompt options should override the default options.
		OllamaOptions promptOptions = new OllamaOptions().withModel("PROMPT_MODEL");

		request = client2.ollamaChatRequest(new Prompt("Test message content", promptOptions), true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

}
