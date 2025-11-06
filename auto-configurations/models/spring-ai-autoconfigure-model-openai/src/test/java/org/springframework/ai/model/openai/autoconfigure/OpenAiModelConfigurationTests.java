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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for OpenAI auto configurations' conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
public class OpenAiModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL");

	@Test
	void chatModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiApi.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=openai",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void imageModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=openai", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.withConfiguration(AutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void audioSpeechModelActivation() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.speech=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.speech=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=openai",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void audioTranscriptionModelActivation() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=openai", "spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void moderationModelActivation() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiModerationAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.moderation=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiModerationProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.moderation=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiModerationProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
					OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
					OpenAiModerationAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
					"spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=openai")
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiModerationModel.class)).isNotEmpty();
			});
	}

	@Test
	void openAiApiBean() {
		// Test that OpenAiApi bean is registered and can be injected
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiApi.class)).hasSize(1);
				OpenAiApi openAiApi = context.getBean(OpenAiApi.class);
				assertThat(openAiApi).isNotNull();
			});
	}

}
