/*
 * Copyright 2023-2026 the original author or authors.
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

import org.jspecify.annotations.NonNull;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama API.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Nicolas Krier
 * @since 0.8.0
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OllamaApi.class)
@Conditional(OllamaApiAutoConfiguration.OllamaChatOrEmbeddingCondition.class)
@EnableConfigurationProperties(OllamaConnectionProperties.class)
public class OllamaApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(OllamaConnectionDetails.class)
	PropertiesOllamaConnectionDetails ollamaConnectionDetails(OllamaConnectionProperties properties) {
		return new PropertiesOllamaConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
		return OllamaApi.builder()
			.baseUrl(connectionDetails.getBaseUrl())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

	static class PropertiesOllamaConnectionDetails implements OllamaConnectionDetails {

		private final OllamaConnectionProperties properties;

		PropertiesOllamaConnectionDetails(OllamaConnectionProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getBaseUrl() {
			return this.properties.getBaseUrl();
		}

	}

	static class OllamaChatOrEmbeddingCondition extends SpringBootCondition {

		@Override
		public @NonNull ConditionOutcome getMatchOutcome(@NonNull ConditionContext context,
				@NonNull AnnotatedTypeMetadata metadata) {
			var messageBuilder = ConditionMessage.forCondition("OllamaChatOrEmbeddingCondition");
			var environment = context.getEnvironment();
			var chatModel = environment.getProperty(SpringAIModelProperties.CHAT_MODEL, SpringAIModels.OLLAMA);

			if (SpringAIModels.OLLAMA.equals(chatModel)) {
				return ConditionOutcome.match(messageBuilder.because("Chat model corresponds to Ollama."));
			}

			var embeddingModel = environment.getProperty(SpringAIModelProperties.EMBEDDING_MODEL,
					SpringAIModels.OLLAMA);

			if (SpringAIModels.OLLAMA.equals(embeddingModel)) {
				return ConditionOutcome.match(messageBuilder.because("Embedding model corresponds to Ollama."));
			}

			return ConditionOutcome
				.noMatch(messageBuilder.because("Neither chat model nor embedding model correspond to Ollama."));
		}

	}

}
