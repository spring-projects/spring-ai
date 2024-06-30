/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Jemin Huh
 * @author Soby Chacko
 * @since 1.0.0
 */
public class OpenSearchVectorStore implements VectorStore, InitializingBean {

	public static final String COSINE_SIMILARITY_FUNCTION = "cosinesimil";

	private static final Logger logger = LoggerFactory.getLogger(OpenSearchVectorStore.class);

	public static final String DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION_1536 = """
			{
			   "properties":{
			      "embedding":{
			         "type":"knn_vector",
			         "dimension":1536
			      }
			   }
			}
			""";

	private final OpenSearchVectorStoreOptions openSearchVectorStoreOptions;

	private final EmbeddingModel embeddingModel;

	private final OpenSearchClient openSearchClient;

	private final String index;

	private final FilterExpressionConverter filterExpressionConverter;

	private final String similarityFunction;

	private final boolean isUseApproximateKnn;

	private final boolean initializeSchema;

	public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
		this(openSearchClient, embeddingModel, new OpenSearchVectorStoreOptions());
	}

	public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel,
			OpenSearchVectorStoreOptions openSearchVectorStoreOptions) {
		this(openSearchClient, embeddingModel, openSearchVectorStoreOptions, true);
	}

	public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel,
			OpenSearchVectorStoreOptions openSearchVectorStoreOptions, boolean initializeSchema) {
		Objects.requireNonNull(openSearchClient, "OpenSearchClient must not be null");
		Objects.requireNonNull(embeddingModel, "EmbeddingModel must not be null");
		this.openSearchClient = openSearchClient;
		this.embeddingModel = embeddingModel;
		this.openSearchVectorStoreOptions = openSearchVectorStoreOptions;
		this.index = openSearchVectorStoreOptions.getIndexName();
		this.filterExpressionConverter = new OpenSearchAiSearchFilterExpressionConverter();
		this.similarityFunction = openSearchVectorStoreOptions.getSimilarity();
		this.isUseApproximateKnn = openSearchVectorStoreOptions.isUseApproximateKnn();
		this.initializeSchema = initializeSchema;
	}

	@Override
	public void add(List<Document> documents) {
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (Document document : documents) {
			if (Objects.isNull(document.getEmbedding()) || document.getEmbedding().isEmpty()) {
				logger.debug("Calling EmbeddingModel for document id = " + document.getId());
				document.setEmbedding(this.embeddingModel.embed(document));
			}
			bulkRequestBuilder
					.operations(op -> op.index(idx -> idx.index(this.index).id(document.getId()).document(document)));
		}
		bulkRequest(bulkRequestBuilder.build());
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (String id : idList)
			bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.index).id(id)));
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
	public List<Document> similaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");
		return similaritySearch(this.embeddingModel.embed(searchRequest.getQuery()), searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold(), searchRequest.getFilterExpression());
	}

	public List<Document> similaritySearch(List<Double> embedding, int topK, double similarityThreshold,
			Filter.Expression filterExpression) {
		float[] floatEmbedding = new float[embedding.size()];
		for (int i = 0; i < embedding.size(); i++)
			floatEmbedding[i] = embedding.get(i).floatValue();
		return similaritySearch(isUseApproximateKnn ? buildApproximateQuery(topK, similarityThreshold, filterExpression,
				floatEmbedding) : buildExactQuery(embedding, topK, similarityThreshold, filterExpression));
	}

	private org.opensearch.client.opensearch.core.SearchRequest buildApproximateQuery(int topK,
			double similarityThreshold,
			Filter.Expression filterExpression, float[] floatEmbedding) {
		return new org.opensearch.client.opensearch.core.SearchRequest.Builder()
				.query(Query.of(builder -> builder.knn(KnnQueryBuilder -> KnnQueryBuilder.filter(Query.of(
								queryBuilder -> queryBuilder.queryString(
										queryStringQuerybuilder -> queryStringQuerybuilder.query(
												getOpenSearchQueryString(filterExpression)))))
						.field("embedding").k(topK).vector(floatEmbedding))))
				.minScore(similarityThreshold).build();
	}

	private org.opensearch.client.opensearch.core.SearchRequest buildExactQuery(List<Double> embedding, int topK,
			double similarityThreshold, Filter.Expression filterExpression) {
		return new org.opensearch.client.opensearch.core.SearchRequest.Builder().query(
						buildExactQuery(embedding, filterExpression)).sort(sortOptionsBuilder -> sortOptionsBuilder.score(
						scoreSortBuilder -> scoreSortBuilder.order(SortOrder.Desc))).size(topK).minScore(similarityThreshold)
				.build();
	}

	private Query buildExactQuery(List<Double> embedding, Filter.Expression filterExpression) {
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
		} catch (IOException e) {
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

	private CreateIndexResponse createIndexMapping(String mappingJson) {
		JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
		try {
			return this.openSearchClient.indices()
				.create(new CreateIndexRequest.Builder().index(this.index)
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
		/**
		 * Generates a JSON string for the k-NN vector mapping configuration.
		 * The knn_vector field allows k-NN vectors ingestion into OpenSearch and supports various k-NN searches.
		 * https://opensearch.org/docs/latest/search-plugins/knn/knn-index#method-definitions
		 */
		if (this.initializeSchema && !exists(this.index)) {
			createIndexMapping(Objects.requireNonNullElseGet(openSearchVectorStoreOptions.getMappingJson(),
					() -> this.isUseApproximateKnn ? """
							   {
							       "properties": {
							           "embedding": {
							               "type": "knn_vector",
							               "dimension": "%d",
							               "method": {
							                   "name": "hnsw",
							                   "engine": "lucene",
							                   "space_type": "%s"
							               }
							           }
							       }
							   }
							""".formatted(this.openSearchVectorStoreOptions.getDimensions(),
							this.similarityFunction) : DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION_1536));
		}
	}

}