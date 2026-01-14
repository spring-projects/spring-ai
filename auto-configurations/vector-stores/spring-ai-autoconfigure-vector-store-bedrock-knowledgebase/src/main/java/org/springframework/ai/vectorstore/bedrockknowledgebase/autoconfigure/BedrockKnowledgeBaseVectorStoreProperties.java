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

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

import org.springframework.ai.vectorstore.bedrockknowledgebase.BedrockKnowledgeBaseVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Amazon Bedrock Knowledge Base VectorStore.
 *
 * <p>
 * These properties configure the {@link BedrockKnowledgeBaseVectorStore} when using
 * Spring Boot auto-configuration.
 * </p>
 *
 * <p>
 * Example configuration in {@code application.properties}:
 * </p>
 * <pre>
 * spring.ai.vectorstore.bedrock-knowledge-base.knowledge-base-id=ABCD1234XY
 * spring.ai.vectorstore.bedrock-knowledge-base.region=us-east-1
 * spring.ai.vectorstore.bedrock-knowledge-base.top-k=10
 * spring.ai.vectorstore.bedrock-knowledge-base.similarity-threshold=0.5
 * spring.ai.vectorstore.bedrock-knowledge-base.search-type=SEMANTIC
 * </pre>
 *
 * <p>
 * Or using environment variables:
 * </p>
 * <pre>
 * SPRING_AI_VECTORSTORE_BEDROCK_KNOWLEDGE_BASE_KNOWLEDGE_BASE_ID=ABCD1234XY
 * </pre>
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 * @see BedrockKnowledgeBaseVectorStore
 * @see BedrockKnowledgeBaseVectorStoreAutoConfiguration
 */
@Validated
@ConfigurationProperties(BedrockKnowledgeBaseVectorStoreProperties.CONFIG_PREFIX)
public class BedrockKnowledgeBaseVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.bedrock-knowledge-base";

	/**
	 * The ID of the Bedrock Knowledge Base to query.
	 */
	private @Nullable String knowledgeBaseId;

	/**
	 * The AWS region for the Bedrock service. If not specified, uses the default region
	 * from the AWS SDK (environment variable, system property, or config file).
	 */
	private @Nullable String region;

	/**
	 * The number of results to return from similarity search.
	 */
	@Min(1)
	private int topK = BedrockKnowledgeBaseVectorStore.DEFAULT_TOP_K;

	/**
	 * The minimum similarity threshold for results. Results with scores below this
	 * threshold are filtered out.
	 */
	@DecimalMin("0.0")
	@DecimalMax("1.0")
	private double similarityThreshold = BedrockKnowledgeBaseVectorStore.DEFAULT_SIMILARITY_THRESHOLD;

	/**
	 * The search type to use for queries. HYBRID combines semantic and keyword search
	 * (not supported by all vector store types). SEMANTIC uses only semantic (vector)
	 * search. Default: null (uses KB default behavior)
	 */
	private @Nullable SearchType searchType;

	/**
	 * The ARN of the Bedrock reranking model to use for improving relevance. Example:
	 * arn:aws:bedrock:us-east-1::foundation-model/cohere.rerank-v3-5:0 Default: null (no
	 * reranking)
	 */
	private @Nullable String rerankingModelArn;

	public @Nullable String getKnowledgeBaseId() {
		return this.knowledgeBaseId;
	}

	public void setKnowledgeBaseId(@Nullable String knowledgeBaseId) {
		this.knowledgeBaseId = knowledgeBaseId;
	}

	public @Nullable String getRegion() {
		return this.region;
	}

	public void setRegion(@Nullable String region) {
		this.region = region;
	}

	public int getTopK() {
		return this.topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

	public double getSimilarityThreshold() {
		return this.similarityThreshold;
	}

	public void setSimilarityThreshold(double similarityThreshold) {
		this.similarityThreshold = similarityThreshold;
	}

	public @Nullable SearchType getSearchType() {
		return this.searchType;
	}

	public void setSearchType(@Nullable SearchType searchType) {
		this.searchType = searchType;
	}

	public @Nullable String getRerankingModelArn() {
		return this.rerankingModelArn;
	}

	public void setRerankingModelArn(@Nullable String rerankingModelArn) {
		this.rerankingModelArn = rerankingModelArn;
	}

}
