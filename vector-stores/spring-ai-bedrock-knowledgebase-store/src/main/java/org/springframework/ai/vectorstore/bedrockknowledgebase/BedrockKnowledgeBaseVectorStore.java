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

package org.springframework.ai.vectorstore.bedrockknowledgebase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;
import software.amazon.awssdk.services.bedrockagentruntime.model.VectorSearchBedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.VectorSearchBedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.VectorSearchRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.VectorSearchRerankingConfigurationType;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.Assert;

/**
 * Amazon Bedrock Knowledge Base implementation of {@link VectorStore}.
 *
 * <p>
 * This store uses the Bedrock Agent Runtime Retrieve API to perform similarity searches
 * against a pre-configured Knowledge Base.
 * </p>
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 * @see <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base.html"> Amazon
 * Bedrock Knowledge Bases</a>
 */
public final class BedrockKnowledgeBaseVectorStore implements VectorStore {

	public static final int DEFAULT_TOP_K = 5;

	public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

	private final BedrockAgentRuntimeClient client;

	private final String knowledgeBaseId;

	private final int defaultTopK;

	private final double defaultSimilarityThreshold;

	private final @Nullable SearchType searchType;

	private final @Nullable String rerankingModelArn;

	private final BedrockKnowledgeBaseFilterExpressionConverter filterConverter;

	private BedrockKnowledgeBaseVectorStore(final Builder builder) {
		this.client = builder.client;
		this.knowledgeBaseId = builder.knowledgeBaseId;
		this.defaultTopK = builder.topK;
		this.defaultSimilarityThreshold = builder.similarityThreshold;
		this.searchType = builder.searchType;
		this.rerankingModelArn = builder.rerankingModelArn;
		this.filterConverter = builder.filterConverter != null ? builder.filterConverter
				: new BedrockKnowledgeBaseFilterExpressionConverter();
	}

	/**
	 * Creates a new builder for BedrockKnowledgeBaseVectorStore.
	 * @param client the Bedrock Agent Runtime client
	 * @param knowledgeBaseId the ID of the Knowledge Base to query
	 * @return a new builder instance
	 */
	public static Builder builder(final BedrockAgentRuntimeClient client, final String knowledgeBaseId) {
		return new Builder(client, knowledgeBaseId);
	}

	@Override
	public void add(final List<Document> documents) {
		throw new UnsupportedOperationException("Documents are ingested via data source sync, not direct add.");
	}

	@Override
	public void delete(final List<String> idList) {
		throw new UnsupportedOperationException("Documents are managed via data source, not direct delete.");
	}

	@Override
	public void delete(final Filter.Expression filterExpression) {
		throw new UnsupportedOperationException("Documents are managed via data source, not direct delete.");
	}

	@Override
	public List<Document> similaritySearch(final SearchRequest request) {
		Assert.notNull(request, "SearchRequest must not be null");
		Assert.hasText(request.getQuery(), "Query must not be empty");

		int topK = request.getTopK() > 0 ? request.getTopK() : this.defaultTopK;
		double threshold = request.getSimilarityThreshold() >= 0 ? request.getSimilarityThreshold()
				: this.defaultSimilarityThreshold;

		RetrievalFilter bedrockFilter = null;
		if (request.hasFilterExpression()) {
			Assert.state(request.getFilterExpression() != null, "filterExpression should not be null");
			bedrockFilter = this.filterConverter.convertExpression(request.getFilterExpression());
		}

		List<Document> allDocuments = new ArrayList<>();
		String nextToken = null;

		do {
			RetrieveResponse response = executeRetrieve(request.getQuery(), topK, bedrockFilter, nextToken);

			List<Document> pageDocuments = response.retrievalResults()
				.stream()
				.filter(r -> r.score() != null && r.score() >= threshold)
				.map(this::toDocument)
				.toList();

			allDocuments.addAll(pageDocuments);
			nextToken = response.nextToken();
		}
		while (nextToken != null && allDocuments.size() < topK);

		if (allDocuments.size() > topK) {
			allDocuments = allDocuments.subList(0, topK);
		}

		return allDocuments;
	}

	private RetrieveResponse executeRetrieve(final String query, final int topK, @Nullable final RetrievalFilter filter,
			@Nullable final String nextToken) {

		RetrieveRequest.Builder requestBuilder = RetrieveRequest.builder()
			.knowledgeBaseId(this.knowledgeBaseId)
			.retrievalQuery(q -> q.text(query));

		requestBuilder.retrievalConfiguration(config -> config.vectorSearchConfiguration(vs -> {
			vs.numberOfResults(topK);
			if (filter != null) {
				vs.filter(filter);
			}
			if (this.searchType != null) {
				vs.overrideSearchType(this.searchType);
			}
			if (this.rerankingModelArn != null) {
				vs.rerankingConfiguration(buildRerankingConfig());
			}
		}));

		if (nextToken != null) {
			requestBuilder.nextToken(nextToken);
		}

		return this.client.retrieve(requestBuilder.build());
	}

	private VectorSearchRerankingConfiguration buildRerankingConfig() {
		VectorSearchRerankingConfigurationType type = VectorSearchRerankingConfigurationType.BEDROCK_RERANKING_MODEL;
		VectorSearchBedrockRerankingModelConfiguration modelConfig = VectorSearchBedrockRerankingModelConfiguration
			.builder()
			.modelArn(this.rerankingModelArn)
			.build();
		VectorSearchBedrockRerankingConfiguration bedrockConfig = VectorSearchBedrockRerankingConfiguration.builder()
			.modelConfiguration(modelConfig)
			.build();
		return VectorSearchRerankingConfiguration.builder()
			.type(type)
			.bedrockRerankingConfiguration(bedrockConfig)
			.build();
	}

	Document toDocument(final KnowledgeBaseRetrievalResult result) {
		Map<String, Object> metadata = new HashMap<>();
		Double score = result.score();

		if (score != null) {
			metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - score);
		}
		else {
			score = 0.0;
		}

		extractLocationMetadata(result.location(), metadata);

		if (result.metadata() != null) {
			result.metadata().forEach((key, docValue) -> {
				if (docValue != null) {
					metadata.put(key, documentValueToObject(docValue));
				}
			});
		}

		String text = extractTextContent(result);

		return Document.builder().id(UUID.randomUUID().toString()).text(text).metadata(metadata).score(score).build();
	}

	private void extractLocationMetadata(@Nullable final RetrievalResultLocation loc,
			final Map<String, Object> metadata) {
		if (loc == null) {
			return;
		}

		metadata.put("locationType", loc.typeAsString());

		if (loc.s3Location() != null) {
			metadata.put("source", loc.s3Location().uri());
		}
		else if (loc.confluenceLocation() != null) {
			metadata.put("source", loc.confluenceLocation().url());
		}
		else if (loc.sharePointLocation() != null) {
			metadata.put("source", loc.sharePointLocation().url());
		}
		else if (loc.salesforceLocation() != null) {
			metadata.put("source", loc.salesforceLocation().url());
		}
		else if (loc.webLocation() != null) {
			metadata.put("source", loc.webLocation().url());
		}
		else if (loc.kendraDocumentLocation() != null) {
			metadata.put("source", loc.kendraDocumentLocation().uri());
		}
		else if (loc.sqlLocation() != null) {
			metadata.put("source", loc.sqlLocation().query());
			metadata.put("sourceType", "SQL");
		}
		else if (loc.customDocumentLocation() != null) {
			metadata.put("source", loc.customDocumentLocation().id());
			metadata.put("sourceType", "CUSTOM");
		}
	}

	private String extractTextContent(final KnowledgeBaseRetrievalResult result) {
		if (result.content() == null) {
			return "";
		}

		var content = result.content();

		if (content.text() != null) {
			return content.text();
		}

		if (content.row() != null && !content.row().isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (var cell : content.row()) {
				if (cell != null && cell.columnName() != null && cell.columnValue() != null) {
					sb.append(cell.columnName()).append(": ").append(cell.columnValue()).append("\n");
				}
			}
			return sb.toString().trim();
		}

		return "";
	}

	private Object documentValueToObject(final software.amazon.awssdk.core.document.Document doc) {
		if (doc.isString()) {
			return doc.asString();
		}
		if (doc.isNumber()) {
			return doc.asNumber();
		}
		if (doc.isBoolean()) {
			return doc.asBoolean();
		}
		if (doc.isList()) {
			return doc.asList().stream().map(this::documentValueToObject).toList();
		}
		if (doc.isMap()) {
			Map<String, Object> map = new HashMap<>();
			doc.asMap().forEach((k, v) -> map.put(k, documentValueToObject(v)));
			return map;
		}
		return doc.toString();
	}

	@Override
	public String getName() {
		return "BedrockKnowledgeBaseVectorStore";
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T nativeClient = (T) this.client;
		return Optional.of(nativeClient);
	}

	/**
	 * Returns the Knowledge Base ID this store is configured to query.
	 * @return the Knowledge Base ID
	 */
	public String getKnowledgeBaseId() {
		return this.knowledgeBaseId;
	}

	/**
	 * Builder for {@link BedrockKnowledgeBaseVectorStore}.
	 */
	public static final class Builder {

		private final BedrockAgentRuntimeClient client;

		private final String knowledgeBaseId;

		private int topK = DEFAULT_TOP_K;

		private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		private @Nullable SearchType searchType;

		private @Nullable String rerankingModelArn;

		private @Nullable BedrockKnowledgeBaseFilterExpressionConverter filterConverter;

		private Builder(final BedrockAgentRuntimeClient client, final String knowledgeBaseId) {
			Assert.notNull(client, "BedrockAgentRuntimeClient must not be null");
			Assert.hasText(knowledgeBaseId, "Knowledge Base ID must not be empty");
			this.client = client;
			this.knowledgeBaseId = knowledgeBaseId;
		}

		/**
		 * Sets the default number of results to return.
		 * @param topK the number of results (default: 5)
		 * @return this builder
		 */
		public Builder topK(final int topK) {
			Assert.isTrue(topK > 0, "topK must be positive");
			this.topK = topK;
			return this;
		}

		/**
		 * Sets the default similarity threshold for filtering results.
		 * @param similarityThreshold minimum score (0.0 to 1.0)
		 * @return this builder
		 */
		public Builder similarityThreshold(final double similarityThreshold) {
			Assert.isTrue(similarityThreshold >= 0.0 && similarityThreshold <= 1.0,
					"similarityThreshold must be between 0.0 and 1.0");
			this.similarityThreshold = similarityThreshold;
			return this;
		}

		/**
		 * Sets the search type to use for queries.
		 * @param searchType HYBRID or SEMANTIC
		 * @return this builder
		 */
		public Builder searchType(@Nullable final SearchType searchType) {
			this.searchType = searchType;
			return this;
		}

		/**
		 * Enables reranking with a Bedrock reranking model.
		 * @param modelArn the ARN of the Bedrock reranking model
		 * @return this builder
		 */
		public Builder rerankingModelArn(@Nullable final String modelArn) {
			this.rerankingModelArn = modelArn;
			return this;
		}

		/**
		 * Sets a custom filter expression converter.
		 * @param filterConverter the filter converter to use
		 * @return this builder
		 */
		public Builder filterConverter(@Nullable final BedrockKnowledgeBaseFilterExpressionConverter filterConverter) {
			this.filterConverter = filterConverter;
			return this;
		}

		/**
		 * Builds the BedrockKnowledgeBaseVectorStore.
		 * @return a new BedrockKnowledgeBaseVectorStore instance
		 */
		public BedrockKnowledgeBaseVectorStore build() {
			return new BedrockKnowledgeBaseVectorStore(this);
		}

	}

}
