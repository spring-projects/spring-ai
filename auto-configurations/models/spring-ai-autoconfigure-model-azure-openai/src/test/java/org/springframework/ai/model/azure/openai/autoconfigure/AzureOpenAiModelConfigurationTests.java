/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.azure.openai.autoconfigure;

import com.azure.ai.openai.OpenAIClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Azure OpenAI auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class AzureOpenAiModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.ai.azure.openai.openai-api-key=" + System.getenv("OPENAI_API_KEY"),
			"spring.ai.openai.base-url=TEST_BASE_URL");

	@Test
	void chatModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=azure-openai", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=azure-openai",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
			});
	}

	@Test
	void imageModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiImageOptionsProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiImageOptionsProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=azure-openai", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
			});
	}

	@Test
	void audioTranscriptionModelActivation() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=azure-openai", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAIClientBuilder.class)).isNotEmpty();
			});
	}

}
