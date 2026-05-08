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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public final class OpenAiConnectionCondition {

	private static final String OPENAI_API_KEY = "OPENAI_API_KEY";

	private static final String OPENAI_BASE_URL = "OPENAI_BASE_URL";

	private static final String AZURE_OPENAI_BASE_URL = "AZURE_OPENAI_BASE_URL";

	private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

	private static final String GITHUB_MODELS_URL = "https://models.github.ai/inference";

	private OpenAiConnectionCondition() {
	}

	public static final class Chat extends OnAvailableOpenAiConnection {

		public Chat() {
			super(OpenAiChatProperties.CONFIG_PREFIX, OpenAiChatProperties.class);
		}

	}

	public static final class Image extends OnAvailableOpenAiConnection {

		public Image() {
			super(OpenAiImageProperties.CONFIG_PREFIX, OpenAiImageProperties.class);
		}

	}

	public static final class Embedding extends OnAvailableOpenAiConnection {

		public Embedding() {
			super(OpenAiEmbeddingProperties.CONFIG_PREFIX, OpenAiEmbeddingProperties.class);
		}

	}

	public static final class AudioSpeech extends OnAvailableOpenAiConnection {

		public AudioSpeech() {
			super(OpenAiAudioSpeechProperties.CONFIG_PREFIX, OpenAiAudioSpeechProperties.class);
		}

	}

	public static final class AudioTranscription extends OnAvailableOpenAiConnection {

		public AudioTranscription() {
			super(OpenAiAudioTranscriptionProperties.CONFIG_PREFIX, OpenAiAudioTranscriptionProperties.class);
		}

	}

	public static final class Moderation extends OnAvailableOpenAiConnection {

		public Moderation() {
			super(OpenAiModerationProperties.CONFIG_PREFIX, OpenAiModerationProperties.class);
		}

	}

	private abstract static class OnAvailableOpenAiConnection extends SpringBootCondition {

		private final String subPrefix;

		private final Class<? extends AbstractOpenAiProperties> subPropertiesType;

		OnAvailableOpenAiConnection(String subPrefix, Class<? extends AbstractOpenAiProperties> subPropertiesType) {
			this.subPrefix = subPrefix;
			this.subPropertiesType = subPropertiesType;
		}

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			Environment environment = context.getEnvironment();
			ConditionMessage.Builder message = ConditionMessage.forCondition(getClass().getSimpleName());

			if (hasText(resolve(environment, "api-key"))) {
				return ConditionOutcome.match(message.because("OpenAI api-key is configured"));
			}
			if (hasCredential(context)) {
				return ConditionOutcome.match(message.because("OpenAI credential is configured"));
			}

			ModelProvider provider = detectModelProvider(environment);
			if (provider == ModelProvider.MICROSOFT_FOUNDRY) {
				return ConditionOutcome
					.match(message.because("Microsoft Foundry can use api-key or passwordless auth"));
			}
			// Use System.getenv() to mirror OpenAiSetup.detectApiKey(); the condition
			// and the credential resolver must see the same inputs, otherwise a
			// property-only OPENAI_API_KEY would match here but fail at bean creation
			// (GH-1818).
			if (provider == ModelProvider.GITHUB_MODELS && hasText(System.getenv(GITHUB_TOKEN))) {
				return ConditionOutcome.match(message.because("GITHUB_TOKEN environment variable is configured"));
			}
			if (provider == ModelProvider.OPEN_AI && hasText(System.getenv(OPENAI_API_KEY))) {
				return ConditionOutcome.match(message.because("OPENAI_API_KEY environment variable is configured"));
			}

			return ConditionOutcome
				.noMatch(message.because("no OpenAI api-key, credential, or supported env fallback"));
		}

		private ModelProvider detectModelProvider(Environment environment) {
			if (resolveBoolean(environment, "microsoft-foundry")) {
				return ModelProvider.MICROSOFT_FOUNDRY;
			}
			if (resolveBoolean(environment, "git-hub-models")) {
				return ModelProvider.GITHUB_MODELS;
			}

			String baseUrl = detectBaseUrl(environment);
			if (baseUrl != null && hasText(baseUrl)) {
				if (baseUrl.endsWith("openai.azure.com") || baseUrl.endsWith("openai.azure.com/")
						|| baseUrl.endsWith("cognitiveservices.azure.com")
						|| baseUrl.endsWith("cognitiveservices.azure.com/")) {
					return ModelProvider.MICROSOFT_FOUNDRY;
				}
				if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
					return ModelProvider.GITHUB_MODELS;
				}
			}

			if (hasText(resolve(environment, "microsoft-deployment-name"))
					|| hasText(resolve(environment, "deployment-name"))
					|| hasText(resolve(environment, "microsoft-foundry-service-version"))) {
				return ModelProvider.MICROSOFT_FOUNDRY;
			}

			return ModelProvider.OPEN_AI;
		}

		private @Nullable String detectBaseUrl(Environment environment) {
			String baseUrl = resolve(environment, "base-url");
			if (!hasText(baseUrl)) {
				baseUrl = System.getenv(OPENAI_BASE_URL);
				String azureBaseUrl = System.getenv(AZURE_OPENAI_BASE_URL);
				if (hasText(azureBaseUrl)) {
					baseUrl = azureBaseUrl;
				}
			}
			return baseUrl;
		}

		private @Nullable String resolve(Environment environment, String propertyName) {
			String subValue = environment.getProperty(this.subPrefix + "." + propertyName);
			if (hasText(subValue)) {
				return subValue;
			}
			return environment.getProperty(OpenAiCommonProperties.CONFIG_PREFIX + "." + propertyName);
		}

		private boolean resolveBoolean(Environment environment, String propertyName) {
			return environment.getProperty(this.subPrefix + "." + propertyName, Boolean.class, false) || environment
				.getProperty(OpenAiCommonProperties.CONFIG_PREFIX + "." + propertyName, Boolean.class, false);
		}

		private boolean hasCredential(ConditionContext context) {
			ListableBeanFactory beanFactory = context.getBeanFactory();
			if (beanFactory == null) {
				return false;
			}
			return hasCredential(beanFactory, OpenAiCommonProperties.class)
					|| hasCredential(beanFactory, this.subPropertiesType);
		}

		private boolean hasCredential(ListableBeanFactory beanFactory,
				Class<? extends AbstractOpenAiProperties> propertiesType) {
			Map<String, ? extends AbstractOpenAiProperties> beans = beanFactory.getBeansOfType(propertiesType, false,
					false);
			return beans.values().stream().anyMatch(properties -> properties.getCredential() != null);
		}

		private boolean hasText(@Nullable String value) {
			return StringUtils.hasText(value);
		}

	}

	private enum ModelProvider {

		OPEN_AI, MICROSOFT_FOUNDRY, GITHUB_MODELS

	}

}
