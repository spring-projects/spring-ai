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
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.VoyageAiContextualizedEmbeddingModel;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Contextualized embedding {@link AutoConfiguration Auto-configuration} for Voyage AI. It
 * is active when {@code spring.ai.voyageai.contextualized-embedding.model} is set.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
@AutoConfiguration
@EnableConfigurationProperties({ VoyageAiConnectionProperties.class, VoyageAiContextualizedEmbeddingProperties.class })
@ConditionalOnClass(VoyageAiApi.class)
@ConditionalOnProperty(prefix = VoyageAiContextualizedEmbeddingProperties.CONFIG_PREFIX, name = "model")
public class VoyageAiContextualizedEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VoyageAiContextualizedEmbeddingModel voyageAiContextualizedEmbeddingModel(
			VoyageAiConnectionProperties connectionProperties,
			VoyageAiContextualizedEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilder, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		// there is no default model: fail at startup rather than at the first request
		VoyageAiApi.ContextualizedEmbeddingModel.fromValue(embeddingProperties.getModel());

		var embeddingModel = VoyageAiContextualizedEmbeddingModel.builder()
			.voyageAiApi(VoyageAiApiFactory.create(connectionProperties, restClientBuilder, responseErrorHandler))
			.options(embeddingProperties.toOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

}
