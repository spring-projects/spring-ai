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

package org.springframework.ai.model.voyageai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.VoyageAiEmbeddingModel;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Text embedding {@link AutoConfiguration Auto-configuration} for Voyage AI.
 *
 * <p>
 * There is no default model: the application fails to start when
 * {@code spring.ai.voyageai.embedding.model} is missing or is not a Voyage AI text
 * embedding model.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
@AutoConfiguration
@EnableConfigurationProperties({ VoyageAiConnectionProperties.class, VoyageAiEmbeddingProperties.class })
@ConditionalOnClass(VoyageAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.VOYAGE_AI,
		matchIfMissing = true)
public class VoyageAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VoyageAiEmbeddingModel voyageAiEmbeddingModel(VoyageAiConnectionProperties connectionProperties,
			VoyageAiEmbeddingProperties embeddingProperties, ObjectProvider<RestClient.Builder> restClientBuilder,
			ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		// there is no default model: fail at startup rather than at the first request
		Assert.hasText(embeddingProperties.getModel(),
				"The Voyage AI embedding model must be set, there is no "
						+ "default model: set spring.ai.voyageai.embedding.model, or spring.ai.model.embedding=none to "
						+ "disable the Voyage AI text embedding model");
		VoyageAiApi.EmbeddingModel.fromValue(embeddingProperties.getModel());

		var embeddingModel = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(VoyageAiApiFactory.create(connectionProperties, restClientBuilder, responseErrorHandler))
			.metadataMode(embeddingProperties.getMetadataMode())
			.options(embeddingProperties.toOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

}
