/*
 * Copyright 2023-2025 the original author or authors.
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

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OllamaChatModel} asserting AI metadata.
 *
 * @author Sun Yuhan
 */
@SpringBootTest(classes = OllamaChatModelMetadataTests.Config.class)
class OllamaChatModelMetadataTests extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.QWEN_3_06B.getName();

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	OllamaChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void ollamaThinkingMetadataCaptured() {
		var options = OllamaOptions.builder().model(MODEL).think(true).build();

		Prompt prompt = new Prompt("Why is the sky blue?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		chatResponse.getResults().forEach(generation -> {
			ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
			assertThat(chatGenerationMetadata).isNotNull();
			assertThat(chatGenerationMetadata.containsKey("thinking"));
		});
	}

	@Test
	void ollamaThinkingMetadataNotCapturedWhenNotSetThinkFlag() {
		var options = OllamaOptions.builder().model(MODEL).build();

		Prompt prompt = new Prompt("Why is the sky blue?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		chatResponse.getResults().forEach(generation -> {
			ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
			assertThat(chatGenerationMetadata).isNotNull();
			var thinking = chatGenerationMetadata.get("thinking");
			assertThat(thinking).isNull();
		});
	}

	@Test
	void ollamaThinkingMetadataNotCapturedWhenSetThinkFlagToFalse() {
		var options = OllamaOptions.builder().model(MODEL).think(false).build();

		Prompt prompt = new Prompt("Why is the sky blue?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		chatResponse.getResults().forEach(generation -> {
			ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
			assertThat(chatGenerationMetadata).isNotNull();
			var thinking = chatGenerationMetadata.get("thinking");
			assertThat(thinking).isNull();
		});
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public OllamaChatModel openAiChatModel(OllamaApi ollamaApi, TestObservationRegistry observationRegistry) {
			return OllamaChatModel.builder().ollamaApi(ollamaApi).observationRegistry(observationRegistry).build();
		}

	}

}
