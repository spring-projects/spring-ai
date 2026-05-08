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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Condition-level tests for {@link OpenAiConnectionCondition} using all six OpenAI
 * auto-configurations. Verifies that model beans are only created when a valid credential
 * source is configured for the corresponding sub-model, and that no model beans are
 * created (and the context does not fail) when no credential is provided. Env-var
 * fallback ({@code OPENAI_API_KEY}) is covered via System Stubs.
 */
@ExtendWith(SystemStubsExtension.class)
class OpenAiConnectionConditionAutoConfigurationTests {

	@SystemStub
	private EnvironmentVariables environmentVariables;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
				OpenAiEmbeddingAutoConfiguration.class, OpenAiAudioSpeechAutoConfiguration.class,
				OpenAiAudioTranscriptionAutoConfiguration.class, OpenAiModerationAutoConfiguration.class,
				ToolCallingAutoConfiguration.class));

	@Test
	void commonApiKeyEnablesAllSixModels() {
		this.contextRunner.withPropertyValues("spring.ai.openai.api-key=COMMON").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context).hasSingleBean(OpenAiImageModel.class);
			assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
			assertThat(context).hasSingleBean(OpenAiAudioSpeechModel.class);
			assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).hasSingleBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void chatSubKeyOnlyEnablesOnlyChat() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api-key=CHAT_KEY").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
			assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
		});
	}

	/**
	 * Core GH-1818 scenario: providing only an image sub-key must not trigger startup
	 * failure in the other five auto-configurations.
	 */
	@Test
	void imageSubKeyOnlyEnablesOnlyImage() {
		this.contextRunner.withPropertyValues("spring.ai.openai.image.api-key=IMAGE_KEY").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).hasSingleBean(OpenAiImageModel.class);
			assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void embeddingSubKeyOnlyEnablesOnlyEmbedding() {
		this.contextRunner.withPropertyValues("spring.ai.openai.embedding.api-key=EMBEDDING_KEY").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
			assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void audioSpeechSubKeyOnlyEnablesOnlyAudioSpeech() {
		this.contextRunner.withPropertyValues("spring.ai.openai.audio.speech.api-key=SPEECH_KEY").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
			assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
			assertThat(context).hasSingleBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void audioTranscriptionSubKeyOnlyEnablesOnlyAudioTranscription() {
		this.contextRunner.withPropertyValues("spring.ai.openai.audio.transcription.api-key=TRANSCRIPTION_KEY")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
				assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
				assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
				assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
				assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class);
				assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
			});
	}

	@Test
	void moderationSubKeyOnlyEnablesOnlyModeration() {
		this.contextRunner.withPropertyValues("spring.ai.openai.moderation.api-key=MODERATION_KEY").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
			assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).hasSingleBean(OpenAiModerationModel.class);
		});
	}

	/**
	 * Core GH-1818 verification: no API key configured must not crash the context, and no
	 * model beans should be created.
	 */
	@Test
	void noApiKeyDoesNotFailContextAndProducesNoModelBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiImageModel.class);
			assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
			assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void openAiApiKeyEnvVarEnablesAllSixModels() {
		this.environmentVariables.set("OPENAI_API_KEY", "ENV_OPENAI_KEY");
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context).hasSingleBean(OpenAiImageModel.class);
			assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
			assertThat(context).hasSingleBean(OpenAiAudioSpeechModel.class);
			assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class);
			assertThat(context).hasSingleBean(OpenAiModerationModel.class);
		});
	}

	@Test
	void mixedKeysEnableOnlyConfiguredModels() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.api-key=CHAT_KEY", "spring.ai.openai.image.api-key=IMAGE_KEY")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(OpenAiChatModel.class);
				assertThat(context).hasSingleBean(OpenAiImageModel.class);
				assertThat(context).doesNotHaveBean(OpenAiEmbeddingModel.class);
				assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
				assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
				assertThat(context).doesNotHaveBean(OpenAiModerationModel.class);
			});
	}

	@Test
	void modelSelectorOffSuppressesChatModel() {
		this.contextRunner.withPropertyValues("spring.ai.openai.api-key=COMMON", "spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			});
	}

	@Test
	void userDefinedChatModelBeanIsKeptWithNoApiKey() {
		this.contextRunner.withUserConfiguration(UserChatModelConfiguration.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
		});
	}

	/**
	 * Unit-style test for the Microsoft Foundry path. Constructs a minimal
	 * {@link org.springframework.context.annotation.ConditionContext} backed by a
	 * {@link org.springframework.mock.env.MockEnvironment} so the test is independent of
	 * Azure Identity being on the classpath.
	 */
	@Test
	void microsoftFoundryEnablesChatWithoutApiKey() {
		org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
		env.setProperty("spring.ai.openai.microsoft-foundry", "true");

		org.springframework.context.support.GenericApplicationContext appCtx = new org.springframework.context.support.GenericApplicationContext();
		appCtx.setEnvironment(env);
		appCtx.refresh();

		org.springframework.context.annotation.ConditionContext conditionContext = new org.springframework.context.annotation.ConditionContext() {
			@Override
			public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getBeanFactory() {
				return appCtx.getBeanFactory();
			}

			@Override
			public org.springframework.core.env.Environment getEnvironment() {
				return env;
			}

			@Override
			public org.springframework.core.io.ResourceLoader getResourceLoader() {
				return appCtx;
			}

			@Override
			public ClassLoader getClassLoader() {
				return getClass().getClassLoader();
			}

			@Override
			public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() {
				return appCtx;
			}
		};

		OpenAiConnectionCondition.Chat condition = new OpenAiConnectionCondition.Chat();
		org.springframework.boot.autoconfigure.condition.ConditionOutcome outcome = condition
			.getMatchOutcome(conditionContext, null);

		appCtx.close();
		assertThat(outcome.isMatch()).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	static class UserChatModelConfiguration {

		@Bean
		OpenAiChatModel userChatModel() {
			return Mockito.mock(OpenAiChatModel.class);
		}

	}

}
