/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vertexai.embedding;

import java.io.IOException;

import org.springframework.ai.vertexai.embedding.VertexAiEmbeddigConnectionDetails;
import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.cloud.vertexai.VertexAI;

/**
 * Auto-configuration for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConditionalOnClass({ VertexAI.class, VertexAiTextEmbeddingModel.class })
@EnableConfigurationProperties({ VertexAiEmbeddingConnectionProperties.class, VertexAiTextEmbeddingProperties.class,
		VertexAiMultimodalEmbeddingProperties.class, })
public class VertexAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiEmbeddigConnectionDetails connectionDetails(
			VertexAiEmbeddingConnectionProperties connectionProperties) {

		Assert.hasText(connectionProperties.getProjectId(), "Vertex AI project-id must be set!");
		Assert.hasText(connectionProperties.getLocation(), "Vertex AI location must be set!");

		var connectionBuilder = VertexAiEmbeddigConnectionDetails.builder()
			.withProjectId(connectionProperties.getProjectId())
			.withLocation(connectionProperties.getLocation());

		if (StringUtils.hasText(connectionProperties.getApiEndpoint())) {
			connectionBuilder.withApiEndpoint(connectionProperties.getApiEndpoint());
		}

		return connectionBuilder.build();

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiTextEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiTextEmbeddingModel textEmbedding(VertexAiEmbeddigConnectionDetails connectionDetails,
			VertexAiTextEmbeddingProperties textEmbeddingProperties) throws IOException {

		return new VertexAiTextEmbeddingModel(connectionDetails, textEmbeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiMultimodalEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiMultimodalEmbeddingModel multimodalEmbedding(VertexAiEmbeddigConnectionDetails connectionDetails,
			VertexAiMultimodalEmbeddingProperties multimodalEmbeddingProperties) throws IOException {

		return new VertexAiMultimodalEmbeddingModel(connectionDetails, multimodalEmbeddingProperties.getOptions());
	}

}
