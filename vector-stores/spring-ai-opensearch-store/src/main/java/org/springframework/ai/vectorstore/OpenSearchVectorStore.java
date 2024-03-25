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
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
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
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jemin Huh
 * @since 1.0.0
 */
public class OpenSearchVectorStore implements VectorStore, InitializingBean {

    public static final String COSINE_SIMILARITY_FUNCTION = "cosinesimil";

    private static final Logger logger = LoggerFactory.getLogger(OpenSearchVectorStore.class);

    private static final String INDEX_NAME = "spring-ai-document-index";

    private final EmbeddingClient embeddingClient;

    private final OpenSearchClient openSearchClient;

    private final String index;

    private final FilterExpressionConverter filterExpressionConverter;

    private String similarityFunction;

    public OpenSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingClient embeddingClient) {
        this(INDEX_NAME, openSearchClient, embeddingClient);
    }

    public OpenSearchVectorStore(String index, OpenSearchClient openSearchClient,
            EmbeddingClient embeddingClient) {
        Objects.requireNonNull(embeddingClient, "RestClient must not be null");
        Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
        this.openSearchClient = openSearchClient;
        this.embeddingClient = embeddingClient;
        this.index = index;
        this.filterExpressionConverter = new OpenSearchAiSearchFilterExpressionConverter();
        // the potential functions for vector fields at
        // https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces
        this.similarityFunction = COSINE_SIMILARITY_FUNCTION;
    }

    public OpenSearchVectorStore withSimilarityFunction(String similarityFunction) {
        this.similarityFunction = similarityFunction;
        return this;
    }

    @Override
    public void add(List<Document> documents) {
        BulkRequest.Builder builkRequestBuilder = new BulkRequest.Builder();
        for (Document document : documents) {
            if (Objects.isNull(document.getEmbedding()) || document.getEmbedding().isEmpty()) {
                logger.debug("Calling EmbeddingClient for document id = " + document.getId());
                document.setEmbedding(this.embeddingClient.embed(document));
            }
            builkRequestBuilder
                    .operations(op -> op.index(idx -> idx.index(this.index).id(document.getId()).document(document)));
        }
        bulkRequest(builkRequestBuilder.build());
    }

    @Override
    public Optional<Boolean> delete(List<String> idList) {
        BulkRequest.Builder builkRequestBuilder = new BulkRequest.Builder();
        for (String id : idList)
            builkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.index).id(id)));
        return Optional.of(bulkRequest(builkRequestBuilder.build()).errors());
    }

    private BulkResponse bulkRequest(BulkRequest bulkRequest) {
        try {
            return this.openSearchClient.bulk(bulkRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "The search request must not be null.");
        return similaritySearch(this.embeddingClient.embed(searchRequest.getQuery()), searchRequest.getTopK(),
                searchRequest.getSimilarityThreshold(), searchRequest.getFilterExpression());
    }

    public List<Document> similaritySearch(List<Double> embedding, int topK, double similarityThreshold,
            Filter.Expression filterExpression) {
        return similaritySearch(new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                .query(getOpenSearchSimilarityQuery(embedding, filterExpression))
                .size(topK)
                .minScore(similarityThreshold)
                .build());
    }

    private Query getOpenSearchSimilarityQuery(List<Double> embedding, Filter.Expression filterExpression) {
        return Query.of(queryBuilder -> queryBuilder.scriptScore(scriptScoreQueryBuilder -> {
            scriptScoreQueryBuilder.query(
                            queryBuilder2 -> queryBuilder2.queryString(queryStringQuerybuilder -> queryStringQuerybuilder
                                    .query(getOpenSearchQueryString(filterExpression))))
                    .script(scriptBuilder -> scriptBuilder
                            .inline(inlineScriptBuilder -> inlineScriptBuilder.source("knn_score")
                                    .lang("knn")
                                    .params("field", JsonData.of("embedding"))
                                    .params("query_value", JsonData.of(embedding))
                                    .params("space_type", JsonData.of(this.similarityFunction))));
            // https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script
            // k-NN ensures non-negative scores by adding 1 to cosine similarity, extending OpenSearch scores to 0-2.
            // A 0.5 boost normalizes to 0-1.
            return this.similarityFunction.equals(COSINE_SIMILARITY_FUNCTION) ? scriptScoreQueryBuilder.boost(
                    0.5f) : scriptScoreQueryBuilder;
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CreateIndexResponse createIndexMapping(String index, Map<String, Property> properties) {
        try {
            return this.openSearchClient.indices()
                    .create(new CreateIndexRequest.Builder().index(index).settings(setting -> setting.knn(true))
                            .mappings(propertiesBuilder -> propertiesBuilder.properties(properties)).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (!exists(this.index)) {
            createIndexMapping(this.index, Map.of("embedding", Property.of(p -> p.knnVector(k -> k.dimension(1536)))));
        }
    }
}