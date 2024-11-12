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

package org.springframework.ai.vectorstore;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Builder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * An ObservationVectorStore implementation that stores vectors in OpenSearch.
 *
 * @author Jemin Huh
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author inpink
 * @since 1.0.0
 */
public class OpenSearchVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final String COSINE_SIMILARITY_FUNCTION = "cosinesimil";

	public static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	public static final String DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION = """
			{
				"properties":{
					"embedding":{
						"type":"knn_vector",
						"dimension":%s
					}
				}
			}
			""";

	private static final Logger logger = LoggerFactory.getLogger(OpenSearchVectorStore.class);

	private final EmbeddingModel embeddingModel;

	private final OpenSearchClient openSearchClient;

	private final String index;

	private final FilterExpressionConverter filterExpressionConverter;

	private final String mappingJson;

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	private String similarityFunction;

	public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		this(openSearchClient, embeddingModel, DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION, initializeSchema);
	}

	public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel, String mappingJson,
			boolean initializeSchema) {
		this(DEFAULT_INDEX_NAME, openSearchClient, embeddingModel, mappingJson, initializeSchema);
	}

	public OpenSearchVectorStore(String index, OpenSearchClient openSearchClient, EmbeddingModel embeddingModel,
			String mappingJson, boolean initializeSchema) {
		this(index, openSearchClient, embeddingModel, mappingJson, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	public OpenSearchVectorStore(String index, OpenSearchClient openSearchClient, EmbeddingModel embeddingModel,
			String mappingJson, boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {

		super(observationRegistry, customObservationConvention);

		Objects.requireNonNull(embeddingModel, "RestClient must not be null");
		Objects.requireNonNull(embeddingModel, "EmbeddingModel must not be null");
		this.openSearchClient = openSearchClient;
		this.embeddingModel = embeddingModel;
		this.index = index;
		this.mappingJson = mappingJson;
		this.filterExpressionConverter = new OpenSearchAiSearchFilterExpressionConverter();
		// the potential functions for vector fields at
		// https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces
		this.similarityFunction = COSINE_SIMILARITY_FUNCTION;
		this.initializeSchema = initializeSchema;
		this.batchingStrategy = batchingStrategy;
	}

	public OpenSearchVectorStore withSimilarityFunction(String similarityFunction) {
		this.similarityFunction = similarityFunction;
		return this;
	}

	@Override
	public void doAdd(List<Document> documents) {
		this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (Document document : documents) {
			bulkRequestBuilder
				.operations(op -> op.index(idx -> idx.index(this.index).id(document.getId()).document(document)));
		}
		bulkRequest(bulkRequestBuilder.build());
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (String id : idList) {
			bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.index).id(id)));
		}
		return Optional.of(bulkRequest(bulkRequestBuilder.build()).errors());
	}

	private BulkResponse bulkRequest(BulkRequest bulkRequest) {
		try {
			return this.openSearchClient.bulk(bulkRequest);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");
		return similaritySearch(this.embeddingModel.embed(searchRequest.getQuery()), searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold(), searchRequest.getFilterExpression());
	}

	public List<Document> similaritySearch(float[] embedding, int topK, double similarityThreshold,
			Filter.Expression filterExpression) {
		return similaritySearch(new org.opensearch.client.opensearch.core.SearchRequest.Builder()
			.query(getOpenSearchSimilarityQuery(embedding, filterExpression))
			.index(this.index)
			.sort(sortOptionsBuilder -> sortOptionsBuilder
				.score(scoreSortBuilder -> scoreSortBuilder.order(SortOrder.Desc)))
			.size(topK)
			.minScore(similarityThreshold)
			.build());
	}

	private Query getOpenSearchSimilarityQuery(float[] embedding, Filter.Expression filterExpression) {
		return Query.of(queryBuilder -> queryBuilder.scriptScore(scriptScoreQueryBuilder -> {
			scriptScoreQueryBuilder
				.query(queryBuilder2 -> queryBuilder2.queryString(queryStringQuerybuilder -> queryStringQuerybuilder
					.query(getOpenSearchQueryString(filterExpression))))
				.script(scriptBuilder -> scriptBuilder
					.inline(inlineScriptBuilder -> inlineScriptBuilder.source("knn_score")
						.lang("knn")
						.params("field", JsonData.of("embedding"))
						.params("query_value", JsonData.of(embedding))
						.params("space_type", JsonData.of(this.similarityFunction))));
			// https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script
			// k-NN ensures non-negative scores by adding 1 to cosine similarity,
			// extending OpenSearch scores to 0-2.
			// A 0.5 boost normalizes to 0-1.
			return this.similarityFunction.equals(COSINE_SIMILARITY_FUNCTION) ? scriptScoreQueryBuilder.boost(0.5f)
					: scriptScoreQueryBuilder;
		}));
	}

	private String getOpenSearchQueryString(Filter.Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);

	}

	private List<Document> similaritySearch(org.opensearch.client.opensearch.core.SearchRequest searchRequest) {
		try {
			return this.openSearchClient.search(searchRequest, Document.class)
				.hits()
				.hits()
				.stream()
				.map(this::toDocument)
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document toDocument(Hit<Document> hit) {
		Document document = hit.source();
		document.getMetadata().put("distance", 1 - hit.score().floatValue());
		return document;
	}

	public boolean exists(String targetIndex) {
		try {
			BooleanResponse response = this.openSearchClient.indices()
				.exists(existRequestBuilder -> existRequestBuilder.index(targetIndex));
			return response.value();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private CreateIndexResponse createIndexMapping(String index, String mappingJson) {
		JsonpMapper jsonpMapper = this.openSearchClient._transport().jsonpMapper();
		try {
			return this.openSearchClient.indices()
				.create(new CreateIndexRequest.Builder().index(index)
					.settings(settingsBuilder -> settingsBuilder.knn(true))
					.mappings(TypeMapping._DESERIALIZER.deserialize(
							jsonpMapper.jsonProvider().createParser(new StringReader(mappingJson)), jsonpMapper))
					.build());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() {
		if (this.initializeSchema && !exists(this.index)) {
			createIndexMapping(this.index, String.format(this.mappingJson, this.embeddingModel.dimensions()));
		}
	}

	@Override
	public Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.OPENSEARCH.value(), operationName)
			.withCollectionName(this.index)
			.withDimensions(this.embeddingModel.dimensions())
			.withSimilarityMetric(getSimilarityFunction());
	}

	private String getSimilarityFunction() {
		if ("cosinesimil".equalsIgnoreCase(this.similarityFunction)) {
			return VectorStoreSimilarityMetric.COSINE.value();
		}
		else if ("l2".equalsIgnoreCase(this.similarityFunction)) {
			return VectorStoreSimilarityMetric.EUCLIDEAN.value();
		}

		return this.similarityFunction;
	}

}
