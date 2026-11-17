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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import com.google.cloud.aiplatform.v1.PredictionServiceSettings;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Vertex AI Embedding Connection.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Nguyen Tran
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(PredictionServiceSettings.class)
@Conditional(VertexAiEmbeddingConnectionAutoConfiguration.OnVertexAiEmbeddingModelCondition.class)
@EnableConfigurationProperties(VertexAiEmbeddingConnectionProperties.class)
public class VertexAiEmbeddingConnectionAutoConfiguration {

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

	/**
	 * Active when either consuming embedding model auto-configuration is enabled: the
	 * text embedding model or the multimodal embedding model. Mirrors the
	 * {@code spring.ai.model.*} conditions of both so the connection backs off together
	 * with its consumers (e.g. when every embedding model is set to {@code none}).
	 */
	static class OnVertexAiEmbeddingModelCondition extends AnyNestedCondition {

		OnVertexAiEmbeddingModelCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = SpringAIModelProperties.TEXT_EMBEDDING_MODEL,
				havingValue = SpringAIModels.VERTEX_AI, matchIfMissing = true)
		static class OnTextEmbeddingModel {

		}

		@ConditionalOnProperty(name = SpringAIModelProperties.MULTI_MODAL_EMBEDDING_MODEL,
				havingValue = SpringAIModels.VERTEX_AI, matchIfMissing = true)
		static class OnMultiModalEmbeddingModel {

		}

	}

}
