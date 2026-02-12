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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.moderation.MistralAiModerationModel;
import org.springframework.ai.mistralai.ocr.MistralOcrApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Mistral AI auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ricken Bazolo
 * @author Issam El-atif
 * @author Nicolas Krier
 */
class MistralModelConfigurationTests {

	private static final String NONE = "none";

	private static final ApplicationContextRunner CHAT_CONTEXT_RUNNER = createApplicationContextRunner(
			MistralAiChatAutoConfiguration.class);

	private static final ApplicationContextRunner EMBEDDING_CONTEXT_RUNNER = createApplicationContextRunner(
			MistralAiEmbeddingAutoConfiguration.class);

	private static final ApplicationContextRunner MODERATION_CONTEXT_RUNNER = createApplicationContextRunner(
			MistralAiModerationAutoConfiguration.class);

	private static final ApplicationContextRunner OCR_CONTEXT_RUNNER = createApplicationContextRunner(
			MistralAiOcrAutoConfiguration.class);

	private static ApplicationContextRunner createApplicationContextRunner(Class<?> autoConfigurationClass) {
		return new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.api-key=FAKE_MISTRAL_AI_API_KEY")
			.withConfiguration(SpringAiTestAutoConfigurations.of(autoConfigurationClass));
	}

	private static ApplicationContextRunner chatContextRunnerWithChatModel(String chatModel) {
		return CHAT_CONTEXT_RUNNER.withPropertyValues(SpringAIModelProperties.CHAT_MODEL + "=" + chatModel);
	}

	private static ApplicationContextRunner embeddingContextRunnerWithEmbeddingModel(String embeddingModel) {
		return EMBEDDING_CONTEXT_RUNNER
			.withPropertyValues(SpringAIModelProperties.EMBEDDING_MODEL + "=" + embeddingModel);
	}

	private static ApplicationContextRunner moderationContextRunnerWithModerationModel(String moderationModel) {
		return MODERATION_CONTEXT_RUNNER
			.withPropertyValues(SpringAIModelProperties.MODERATION_MODEL + "=" + moderationModel);
	}

	private static ApplicationContextRunner ocrContextRunnerWithOcrModel(String ocrModel) {
		return OCR_CONTEXT_RUNNER.withPropertyValues(SpringAIModelProperties.OCR_MODEL + "=" + ocrModel);
	}

	@Test
	void chatModelDeactivation() {
		verifyDeactivation(chatContextRunnerWithChatModel(NONE));
	}

	@ParameterizedTest
	@MethodSource("activatedChatModelContextRunners")
	void chatModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(MistralAiChatProperties.class);
			assertThat(context).hasSingleBean(MistralAiChatModel.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationModel.class);
			assertThat(context).doesNotHaveBean(MistralAiOcrProperties.class);
			assertThat(context).doesNotHaveBean(MistralOcrApi.class);
		});
	}

	static List<Arguments> activatedChatModelContextRunners() {
		// @formatter:off
		return List.of(
				// default chat model
				Arguments.of(Named.of("default chat model", CHAT_CONTEXT_RUNNER)),
				// Mistral AI chat model
				Arguments.of(Named.of("Mistral AI chat model",
						chatContextRunnerWithChatModel(SpringAIModels.MISTRAL)))
		);
		// @formatter:on
	}

	@Test
	void embeddingModelDeactivation() {
		verifyDeactivation(embeddingContextRunnerWithEmbeddingModel(NONE));
	}

	@ParameterizedTest
	@MethodSource("activatedEmbeddingModelContextRunners")
	void embeddingModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(MistralAiChatProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiChatModel.class);
			assertThat(context).hasSingleBean(MistralAiEmbeddingProperties.class);
			assertThat(context).hasSingleBean(MistralAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationModel.class);
			assertThat(context).doesNotHaveBean(MistralAiOcrProperties.class);
			assertThat(context).doesNotHaveBean(MistralOcrApi.class);
		});
	}

	static List<Arguments> activatedEmbeddingModelContextRunners() {
		// @formatter:off
		return List.of(
				// default embedding model
				Arguments.of(Named.of("default embedding model", EMBEDDING_CONTEXT_RUNNER)),
				// Mistral AI embedding model
				Arguments.of(Named.of("Mistral AI embedding model",
						embeddingContextRunnerWithEmbeddingModel(SpringAIModels.MISTRAL)))
		);
		// @formatter:on
	}

	@Test
	void moderationModelDeactivation() {
		verifyDeactivation(moderationContextRunnerWithModerationModel(NONE));
	}

	@ParameterizedTest
	@MethodSource("activatedModerationModelContextRunners")
	void moderationModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(MistralAiChatProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiChatModel.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingModel.class);
			assertThat(context).hasSingleBean(MistralAiModerationProperties.class);
			assertThat(context).hasSingleBean(MistralAiModerationModel.class);
			assertThat(context).doesNotHaveBean(MistralAiOcrProperties.class);
			assertThat(context).doesNotHaveBean(MistralOcrApi.class);
		});
	}

	static List<Arguments> activatedModerationModelContextRunners() {
		// @formatter:off
		return List.of(
				// default moderation model
				Arguments.of(Named.of("default moderation model", MODERATION_CONTEXT_RUNNER)),
				// Mistral AI moderation model
				Arguments.of(Named.of("Mistral AI moderation model",
						moderationContextRunnerWithModerationModel(SpringAIModels.MISTRAL)))
		);
		// @formatter:on
	}

	@Test
	void ocrModelDeactivation() {
		verifyDeactivation(ocrContextRunnerWithOcrModel(NONE));
	}

	@ParameterizedTest
	@MethodSource("activatedOcrModelContextRunners")
	void ocrModelActivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(MistralAiChatProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiChatModel.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationModel.class);
			assertThat(context).hasSingleBean(MistralAiOcrProperties.class);
			assertThat(context).hasSingleBean(MistralOcrApi.class);
		});
	}

	static List<Arguments> activatedOcrModelContextRunners() {
		// @formatter:off
		return List.of(
				// default OCR model
				Arguments.of(Named.of("default OCR model", OCR_CONTEXT_RUNNER)),
				// Mistral AI OCR model
				Arguments.of(Named.of("Mistral AI OCR model",
						ocrContextRunnerWithOcrModel(SpringAIModels.MISTRAL)))
		);
		// @formatter:on
	}

	private void verifyDeactivation(ApplicationContextRunner contextRunner) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(MistralAiChatProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiChatModel.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationProperties.class);
			assertThat(context).doesNotHaveBean(MistralAiModerationModel.class);
			assertThat(context).doesNotHaveBean(MistralAiOcrProperties.class);
			assertThat(context).doesNotHaveBean(MistralOcrApi.class);
		});
	}

}
