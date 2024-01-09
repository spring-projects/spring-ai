package org.springframework.ai.vectorstore;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


public class ElasticsearchVectorStore implements VectorStore, InitializingBean {

    // divided by 2 to get score in the range [0, 1]
    public static final String COSINE_SIMILARITY_FUNCTION =
            "(cosineSimilarity(params.query_vector, 'embedding') + 1.0) / 2";

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

    private static final String INDEX_NAME = "spring-ai-document-index";

    private final EmbeddingClient embeddingClient;

    private final ElasticsearchClient elasticsearchClient;

    private final String index;

    private final FilterExpressionConverter filterExpressionConverter;

    private String similarityFunction;

    public ElasticsearchVectorStore(RestClient restClient, EmbeddingClient embeddingClient) {
        this(INDEX_NAME, restClient, embeddingClient);
    }

    public ElasticsearchVectorStore(String index, RestClient restClient, EmbeddingClient embeddingClient) {
        Objects.requireNonNull(embeddingClient, "RestClient must not be null");
        Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
        this.elasticsearchClient =
                new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(
                        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))));
        this.embeddingClient = embeddingClient;
        this.index = index;
        this.filterExpressionConverter = new ElasticsearchAiSearchFilterExpressionConverter();
        // the potential functions for vector fields at https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions
        this.similarityFunction = COSINE_SIMILARITY_FUNCTION;
    }

    public ElasticsearchVectorStore withSimilarityFunction(String similarityFunction) {
        this.similarityFunction = similarityFunction;
        return this;
    }

    @Override
    public void add(List<Document> documents) {
        BulkRequest.Builder builkRequestBuilder = new BulkRequest.Builder();
        for (Document document : documents) {
            if (Objects.isNull(document.getEmbedding()) || document.getEmbedding().isEmpty()) {
                logger.info("Calling EmbeddingClient for document id = " + document.getId());
                document.setEmbedding(this.embeddingClient.embed(document));
            }
            builkRequestBuilder.operations(
                    op -> op.index(idx -> idx.index(this.index).id(document.getId()).document(document)));
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
            return this.elasticsearchClient.bulk(bulkRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "The search request must not be null.");
        return similaritySearch(this.embeddingClient.embed(searchRequest.getQuery()), searchRequest.getTopK(),
                Double.valueOf(searchRequest.getSimilarityThreshold()).floatValue(),
                searchRequest.getFilterExpression());
    }

    public List<Document> similaritySearch(List<Double> embedding, int topK, float similarityThreshold,
            Filter.Expression filterExpression) {
        return similaritySearch(new co.elastic.clients.elasticsearch.core.SearchRequest.Builder().query(
                        getElasticsearchSimilarityQuery(embedding, filterExpression)).size(topK)
                .minScore(Float.valueOf(similarityThreshold).doubleValue()).build());
    }

    private Query getElasticsearchSimilarityQuery(List<Double> embedding, Filter.Expression filterExpression) {
        return Query.of(queryBuilder -> queryBuilder.scriptScore(
                scriptScoreQueryBuilder -> scriptScoreQueryBuilder.query(
                                queryBuilder2 -> queryBuilder2.queryString(
                                        queryStringQuerybuilder -> queryStringQuerybuilder.query(
                                                getElasticsearchQueryString(filterExpression))))
                        .script(scriptBuilder -> scriptBuilder.inline(
                                inlineScriptBuilder -> inlineScriptBuilder.source(this.similarityFunction)
                                        .params("query_vector", JsonData.of(embedding))))));
    }

    private String getElasticsearchQueryString(Filter.Expression filterExpression) {
        return Objects.isNull(filterExpression) ? "*" : this.filterExpressionConverter.convertExpression(
                filterExpression);

    }

    private List<Document> similaritySearch(co.elastic.clients.elasticsearch.core.SearchRequest searchRequest) {
        try {
            return  this.elasticsearchClient.search(searchRequest, Document.class).hits().hits().stream()
                    .map(this::toDocument).collect(Collectors.toList());
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
            BooleanResponse response = this.elasticsearchClient.indices()
                    .exists(existRequestBuilder -> existRequestBuilder.index(targetIndex));
            return response.value();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CreateIndexResponse createIndexMapping(String index, String mappingJson) {
        try {
            return this.elasticsearchClient.indices().create(createIndexBuilder -> createIndexBuilder.index(index)
                    .mappings(typeMappingBuilder -> typeMappingBuilder.withJson(new StringReader(mappingJson))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (!exists(this.index)) {
            createIndexMapping(this.index, """
                    {
                          "properties": {
                              "embedding": {
                                  "type": "dense_vector",
                                  "dims": 1536,
                                  "index": true,
                                  "similarity": "cosine"
                              }
                          }
                      }
                    """);
        }
    }

}
