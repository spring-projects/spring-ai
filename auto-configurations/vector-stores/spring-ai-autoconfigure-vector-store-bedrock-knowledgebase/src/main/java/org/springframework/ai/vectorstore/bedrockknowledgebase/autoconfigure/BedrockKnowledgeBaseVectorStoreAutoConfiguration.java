/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.bedrockknowledgebase.autoconfigure;

import java.util.Objects;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClientBuilder;

import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.bedrockknowledgebase.BedrockKnowledgeBaseVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Amazon Bedrock Knowledge Base Vector
 * Store.
 *
 * <p>
 * Provides auto-configuration for {@link BedrockKnowledgeBaseVectorStore} when the
 * required classes are on the classpath and the knowledge base ID is configured.
 * </p>
 *
 * <p>
 * This configuration is activated when:
 * </p>
 * <ul>
 * <li>{@link BedrockAgentRuntimeClient} class is on the classpath</li>
 * <li>{@code spring.ai.vectorstore.bedrock-knowledge-base.knowledge-base-id} property is
 * set</li>
 * </ul>
 *
 * <p>
 * The auto-configuration creates:
 * </p>
 * <ul>
 * <li>{@link BedrockAgentRuntimeClient} - using default AWS credentials chain</li>
 * <li>{@link BedrockKnowledgeBaseVectorStore} - configured from properties</li>
 * </ul>
 *
 * <p>
 * Configuration properties:
 * </p>
 * <pre>
 * spring.ai.vectorstore.bedrock-knowledge-base.knowledge-base-id=your-kb-id
 * spring.ai.vectorstore.bedrock-knowledge-base.region=us-east-1
 * spring.ai.vectorstore.bedrock-knowledge-base.top-k=5
 * spring.ai.vectorstore.bedrock-knowledge-base.similarity-threshold=0.0
 * spring.ai.vectorstore.bedrock-knowledge-base.search-type=SEMANTIC
 * spring.ai.vectorstore.bedrock-knowledge-base.reranking-model-arn=arn:aws:bedrock:...
 * </pre>
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 * @see BedrockKnowledgeBaseVectorStore
 * @see BedrockKnowledgeBaseVectorStoreProperties
 */
@AutoConfiguration
@ConditionalOnClass({ BedrockAgentRuntimeClient.class, BedrockKnowledgeBaseVectorStore.class })
@EnableConfigurationProperties(BedrockKnowledgeBaseVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE,
		havingValue = SpringAIVectorStoreTypes.BEDROCK_KNOWLEDGE_BASE, matchIfMissing = true)
public class BedrockKnowledgeBaseVectorStoreAutoConfiguration {

	/**
	 * Creates a BedrockAgentRuntimeClient using default AWS credentials. This bean is
	 * only created if no other BedrockAgentRuntimeClient is defined.
	 * @param properties the configuration properties
	 * @return the BedrockAgentRuntimeClient
	 */
	@Bean
	@ConditionalOnMissingBean
	BedrockAgentRuntimeClient bedrockAgentRuntimeClient(BedrockKnowledgeBaseVectorStoreProperties properties) {
		BedrockAgentRuntimeClientBuilder builder = BedrockAgentRuntimeClient.builder();

		if (StringUtils.hasText(properties.getRegion())) {
			builder.region(Region.of(properties.getRegion()));
		}

		return builder.build();
	}

	/**
	 * Creates a BedrockKnowledgeBaseVectorStore configured from properties. This bean is
	 * only created if no other BedrockKnowledgeBaseVectorStore is defined and the
	 * knowledge-base-id property is set.
	 * @param client the BedrockAgentRuntimeClient
	 * @param properties the configuration properties
	 * @return the BedrockKnowledgeBaseVectorStore
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = BedrockKnowledgeBaseVectorStoreProperties.CONFIG_PREFIX, name = "knowledge-base-id")
	BedrockKnowledgeBaseVectorStore bedrockKnowledgeBaseVectorStore(BedrockAgentRuntimeClient client,
			BedrockKnowledgeBaseVectorStoreProperties properties) {

		var builder = BedrockKnowledgeBaseVectorStore
			.builder(client,
					Objects.requireNonNull(properties.getKnowledgeBaseId(), "knowledgeBaseId must not be null"))
			.topK(properties.getTopK())
			.similarityThreshold(properties.getSimilarityThreshold());

		if (properties.getSearchType() != null) {
			builder.searchType(properties.getSearchType());
		}

		if (StringUtils.hasText(properties.getRerankingModelArn())) {
			builder.rerankingModelArn(properties.getRerankingModelArn());
		}

		return builder.build();
	}

}
