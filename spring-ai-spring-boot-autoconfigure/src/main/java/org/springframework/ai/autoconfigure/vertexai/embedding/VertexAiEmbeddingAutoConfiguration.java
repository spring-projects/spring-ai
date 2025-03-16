/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.embedding;

import java.io.IOException;

import com.google.cloud.vertexai.VertexAI;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
@AutoConfiguration(after = { SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass({ VertexAI.class, VertexAiTextEmbeddingModel.class })
@EnableConfigurationProperties({ VertexAiEmbeddingConnectionProperties.class, VertexAiTextEmbeddingProperties.class,
		VertexAiMultimodalEmbeddingProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class })
public class VertexAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiEmbeddingConnectionDetails connectionDetails(
			VertexAiEmbeddingConnectionProperties connectionProperties) {

		Assert.hasText(connectionProperties.getProjectId(), "Vertex AI project-id must be set!");
		Assert.hasText(connectionProperties.getLocation(), "Vertex AI location must be set!");

		var connectionBuilder = VertexAiEmbeddingConnectionDetails.builder()
			.projectId(connectionProperties.getProjectId())
			.location(connectionProperties.getLocation());

		if (StringUtils.hasText(connectionProperties.getApiEndpoint())) {
			connectionBuilder.apiEndpoint(connectionProperties.getApiEndpoint());
		}

		return connectionBuilder.build();

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiTextEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiTextEmbeddingModel textEmbedding(VertexAiEmbeddingConnectionDetails connectionDetails,
			VertexAiTextEmbeddingProperties textEmbeddingProperties, RetryTemplate retryTemplate,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var embeddingModel = new VertexAiTextEmbeddingModel(connectionDetails, textEmbeddingProperties.getOptions(),
				retryTemplate, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiMultimodalEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiMultimodalEmbeddingModel multimodalEmbedding(VertexAiEmbeddingConnectionDetails connectionDetails,
			VertexAiMultimodalEmbeddingProperties multimodalEmbeddingProperties) throws IOException {

		return new VertexAiMultimodalEmbeddingModel(connectionDetails, multimodalEmbeddingProperties.getOptions());
	}

}
