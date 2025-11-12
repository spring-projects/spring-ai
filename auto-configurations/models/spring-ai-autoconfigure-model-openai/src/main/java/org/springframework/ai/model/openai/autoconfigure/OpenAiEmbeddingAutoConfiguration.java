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

package org.springframework.ai.model.openai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

/**
 * Embedding {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Christian Tzolov
 * @author Stefan Vassilev
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 * @author Yanming Zhou
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiEmbeddingProperties.class })
public class OpenAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var openAiApi = openAiApi(commonProperties, embeddingProperties,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler, "embedding");

		var embeddingModel = new OpenAiEmbeddingModel(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private OpenAiApi openAiApi(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			String modelType) {

		OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
				commonProperties, embeddingProperties, modelType);

		return OpenAiApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.embeddingsPath(embeddingProperties.getEmbeddingsPath())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

}
