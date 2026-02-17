/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.model.ollama.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Ollama auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Nicolas Krier
 */
class OllamaModelConfigurationTests {

	private static final String NONE = "none";

	private static final ApplicationContextRunner API_CONTEXT_RUNNER = createApplicationContextRunner(
			OllamaApiAutoConfiguration.class);

	private static final ApplicationContextRunner CHAT_CONTEXT_RUNNER = createApplicationContextRunner(
			OllamaChatAutoConfiguration.class);

	private static final ApplicationContextRunner EMBEDDING_CONTEXT_RUNNER = createApplicationContextRunner(
			OllamaEmbeddingAutoConfiguration.class);

	private static ApplicationContextRunner createApplicationContextRunner(Class<?> autoConfigurationClass) {
		return new ApplicationContextRunner()
			.withConfiguration(SpringAiTestAutoConfigurations.of(autoConfigurationClass));
	}

	private static ApplicationContextRunner withChatModel(ApplicationContextRunner contextRunner, String chatModel) {
		return contextRunner.withPropertyValues(SpringAIModelProperties.CHAT_MODEL + "=" + chatModel);
	}

	private static ApplicationContextRunner withEmbeddingModel(ApplicationContextRunner contextRunner,
			String embeddingModel) {
		return contextRunner.withPropertyValues(SpringAIModelProperties.EMBEDDING_MODEL + "=" + embeddingModel);
	}

	private static ApplicationContextRunner apiContextRunnerWithChatModelAndEmbeddingModel(String chatModel,
			String embeddingModel) {
		return API_CONTEXT_RUNNER.withPropertyValues(SpringAIModelProperties.CHAT_MODEL + "=" + chatModel,
				SpringAIModelProperties.EMBEDDING_MODEL + "=" + embeddingModel);
	}

	@Test
	void apiDeactivation() {
		apiContextRunnerWithChatModelAndEmbeddingModel(NONE, NONE).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OllamaApiAutoConfiguration.PropertiesOllamaConnectionDetails.class);
			assertThat(context).doesNotHaveBean(OllamaApi.class);
		});
	}

	@ParameterizedTest
	@MethodSource("activatedApiContextRunners")
	void apiActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OllamaApiAutoConfiguration.PropertiesOllamaConnectionDetails.class);
			assertThat(context).hasSingleBean(OllamaApi.class);
		});
	}

	static List<Arguments> activatedApiContextRunners() {
		// @formatter:off
		return List.of(
				// default models
				Arguments.of(Named.of("default chat model and default embedding model", API_CONTEXT_RUNNER)),
				// Ollama chat model and default embedding model
				Arguments.of(Named.of("Ollama chat model and default embedding model",
						withChatModel(API_CONTEXT_RUNNER, SpringAIModels.OLLAMA))),
				// default chat model and Ollama embedding model
				Arguments.of(Named.of("default chat model and Ollama embedding model",
						withEmbeddingModel(API_CONTEXT_RUNNER, SpringAIModels.OLLAMA))),
				// Ollama models
				Arguments.of(Named.of("Ollama chat model and Ollama embedding model",
						apiContextRunnerWithChatModelAndEmbeddingModel(SpringAIModels.OLLAMA, SpringAIModels.OLLAMA))),
				// Ollama chat model only
				Arguments.of(Named.of("Ollama chat model only",
						apiContextRunnerWithChatModelAndEmbeddingModel(SpringAIModels.OLLAMA, NONE))),
				// Ollama embedding model only
				Arguments.of(Named.of("Ollama embedding model only",
						apiContextRunnerWithChatModelAndEmbeddingModel(NONE, SpringAIModels.OLLAMA)))
		);
		// @formatter:on
	}

	@Test
	void chatModelDeactivation() {
		withChatModel(CHAT_CONTEXT_RUNNER, NONE).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OllamaChatProperties.class);
			assertThat(context).doesNotHaveBean(OllamaChatModel.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingModel.class);
		});
	}

	@ParameterizedTest
	@MethodSource("activatedChatModelContextRunners")
	void chatModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OllamaChatProperties.class);
			assertThat(context).hasSingleBean(OllamaChatModel.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingModel.class);
		});
	}

	static List<Arguments> activatedChatModelContextRunners() {
		// @formatter:off
		return List.of(
				// default chat model
				Arguments.of(Named.of("default chat model", CHAT_CONTEXT_RUNNER)),
				// Ollama chat model
				Arguments.of(Named.of("Ollama chat model",
						withChatModel(CHAT_CONTEXT_RUNNER, SpringAIModels.OLLAMA)))
		);
		// @formatter:on
	}

	@Test
	void embeddingModelDeactivation() {
		withEmbeddingModel(EMBEDDING_CONTEXT_RUNNER, NONE).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OllamaChatProperties.class);
			assertThat(context).doesNotHaveBean(OllamaChatModel.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(OllamaEmbeddingModel.class);
		});
	}

	@ParameterizedTest
	@MethodSource("activatedEmbeddingModelContextRunners")
	void embeddingModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OllamaChatProperties.class);
			assertThat(context).doesNotHaveBean(OllamaChatModel.class);
			assertThat(context).hasSingleBean(OllamaEmbeddingProperties.class);
			assertThat(context).hasSingleBean(OllamaEmbeddingModel.class);
		});
	}

	static List<Arguments> activatedEmbeddingModelContextRunners() {
		// @formatter:off
		return List.of(
				// default embedding model
				Arguments.of(Named.of("default embedding model", EMBEDDING_CONTEXT_RUNNER)),
				// Ollama embedding model
				Arguments.of(Named.of("Ollama embedding model",
						withEmbeddingModel(EMBEDDING_CONTEXT_RUNNER, SpringAIModels.OLLAMA)))
		);
		// @formatter:on
	}

}
