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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import java.io.IOException;

import com.google.cloud.vertexai.VertexAI;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@AutoConfiguration(after = VertexAiEmbeddingConnectionAutoConfiguration.class)
@ConditionalOnClass({ VertexAI.class, VertexAiMultimodalEmbeddingModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.MULTI_MODAL_EMBEDDING_MODEL,
		havingValue = SpringAIModels.VERTEX_AI, matchIfMissing = true)
@EnableConfigurationProperties(VertexAiMultimodalEmbeddingProperties.class)
public class VertexAiMultiModalEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiMultimodalEmbeddingModel multimodalEmbedding(VertexAiEmbeddingConnectionDetails connectionDetails,
			VertexAiMultimodalEmbeddingProperties multimodalEmbeddingProperties) throws IOException {

		return new VertexAiMultimodalEmbeddingModel(connectionDetails, multimodalEmbeddingProperties.getOptions());
	}

}
