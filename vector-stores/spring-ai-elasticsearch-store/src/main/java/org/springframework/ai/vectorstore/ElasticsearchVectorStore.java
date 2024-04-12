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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Jemin Huh
 * @author Wei Jiang
 * @since 1.0.0
 */
public class ElasticsearchVectorStore implements VectorStore, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

	private final EmbeddingModel embeddingModel;

	private final ElasticsearchClient elasticsearchClient;

	private final ElasticsearchVectorStoreOptions options;

	private final FilterExpressionConverter filterExpressionConverter;

	private String similarityFunction;

	private final boolean initializeSchema;

	public ElasticsearchVectorStore(RestClient restClient, EmbeddingModel embeddingModel, boolean initializeSchema) {
		this(new ElasticsearchVectorStoreOptions(), restClient, embeddingModel, initializeSchema);
	}

	public ElasticsearchVectorStore(ElasticsearchVectorStoreOptions options, RestClient restClient,
			EmbeddingModel embeddingModel, boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
		Objects.requireNonNull(embeddingModel, "RestClient must not be null");
		Objects.requireNonNull(embeddingModel, "EmbeddingModel must not be null");
		this.elasticsearchClient = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(
				new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))));
		this.embeddingModel = embeddingModel;
		this.options = options;
		this.filterExpressionConverter = new ElasticsearchAiSearchFilterExpressionConverter();
	}

	@Override
	public void add(List<Document> documents) {
		BulkRequest.Builder builkRequestBuilder = new BulkRequest.Builder();

		for (Document document : documents) {
			if (Objects.isNull(document.getEmbedding()) || document.getEmbedding().isEmpty()) {
				logger.debug("Calling EmbeddingModel for document id = " + document.getId());
				document.setEmbedding(this.embeddingModel.embed(document));
			}
			builkRequestBuilder.operations(op -> op
				.index(idx -> idx.index(this.options.getIndexName()).id(document.getId()).document(document)));
		}

		BulkResponse bulkRequest = bulkRequest(builkRequestBuilder.build());

		if (bulkRequest.errors()) {
			List<BulkResponseItem> bulkResponseItems = bulkRequest.items();
			for (BulkResponseItem bulkResponseItem : bulkResponseItems) {
				if (bulkResponseItem.error() != null) {
					throw new IllegalStateException(bulkResponseItem.error().reason());
				}
			}
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		BulkRequest.Builder builkRequestBuilder = new BulkRequest.Builder();
		for (String id : idList)
			builkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.options.getIndexName()).id(id)));
		return Optional.of(bulkRequest(builkRequestBuilder.build()).errors());
	}

	private BulkResponse bulkRequest(BulkRequest bulkRequest) {
		try {
			return this.elasticsearchClient.bulk(bulkRequest);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Document> similaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");
		return similaritySearch(this.embeddingModel.embed(searchRequest.getQuery()), searchRequest.getTopK(),
				Double.valueOf(searchRequest.getSimilarityThreshold()).floatValue(),
				searchRequest.getFilterExpression());
	}

			SearchResponse<Document> res = elasticsearchClient.search(
					sr -> sr.index(this.index)
						.minScore(searchRequest.getSimilarityThreshold())
						.knn(knn -> knn.queryVector(vectors)
							.k(searchRequest.getTopK())
							.field(EMBEDDING_FIELD)
							.numCandidates((long) (1.5 * searchRequest.getTopK()))
							.filter(fl -> fl.queryString(
									qs -> qs.query(getElasticsearchQueryString(searchRequest.getFilterExpression()))))),
					Document.class);

			return res.hits().hits().stream().map(this::toDocument).collect(Collectors.toList());

		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String getElasticsearchQueryString(Filter.Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);

	}

	private Document toDocument(Hit<Document> hit) {
		Document document = hit.source();
		document.getMetadata().put("distance", 1 - hit.score().floatValue());
		return document;
	}

	private boolean indexExists() {
		try {
			return this.elasticsearchClient.indices().exists(ex -> ex.index(this.index)).value();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private CreateIndexResponse createIndexMapping() {
		try {
			return this.elasticsearchClient.indices()
				.create(createIndexBuilder -> createIndexBuilder.index(options.getIndexName())
					.mappings(typeMappingBuilder -> {
						typeMappingBuilder.properties("embedding",
								new Property.Builder()
									.denseVector(new DenseVectorProperty.Builder().dims(options.getDimensions())
										.similarity(options.getSimilarity())
										.index(options.isDenseVectorIndexing())
										.build())
									.build());

						return typeMappingBuilder;
					}));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() {

		if (!this.initializeSchema) {
			return;
		}

		if (!indexExists()) {
			createIndexMapping();
		}
	}

}