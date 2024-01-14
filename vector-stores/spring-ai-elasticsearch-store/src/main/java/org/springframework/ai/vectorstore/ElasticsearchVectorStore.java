package org.springframework.ai.vectorstore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.ai.vectorstore.ElasticsearchVectorStore.ElasticsearchScriptScoreQuery.Query;
import static org.springframework.ai.vectorstore.ElasticsearchVectorStore.ElasticsearchScriptScoreQuery.Query.ScriptScore;
import static org.springframework.ai.vectorstore.ElasticsearchVectorStore.ElasticsearchScriptScoreQuery.Query.ScriptScore.Script;
import static org.springframework.ai.vectorstore.ElasticsearchVectorStore.ElasticsearchScriptScoreQuery.Query.ScriptScore.Script.Params;

public class ElasticsearchVectorStore implements VectorStore, InitializingBean {

    // divided by 2 to get score in the range [0, 1]
    public static final String COSINE_SIMILARITY_FUNCTION =
            "(cosineSimilarity(params.query_vector, 'embedding') + 1.0) / 2";

    public record ElasticsearchBulkIndexId(
            @JsonProperty("_index")
            String index,
            @JsonProperty("_id")
            String id
    ) {
    }

    public record ElasticsearchDeleteBody(
            @JsonProperty("delete")
            Delete delete
    ) {
        public record Delete(

                @JsonProperty("_index")
                String index,

                @JsonProperty("_id")
                String id
        ) {
        }
    }

    public record ElasticsearchUpsertDocumentBody(
            @JsonProperty("doc")
            Document document
    ) {
        @JsonProperty("doc_as_upsert")
        public boolean docAsUpsert() {
            return true;
        }
    }

    public record ElasticsearchScriptScoreQuery(
            @JsonProperty("size")
            int size,

            @JsonProperty("query")
            Query query
    ) {
        public record Query(
                @JsonProperty("script_score")
                ScriptScore scriptScore
        ) {
            @JsonInclude(JsonInclude.Include.NON_NULL)

            public record ScriptScore(
                    @JsonProperty("query")
                    Map<String, Object> query,

                    @JsonProperty("script")
                    Script script,
                    @JsonProperty("min_score")
                    Float minScore,
                    @JsonProperty("boost")
                    Float boost
            ) {
                public record Script(
                        @JsonProperty("source")
                        String source,

                        @JsonProperty("params")
                        Params params
                ) {
                    public record Params(
                            @JsonProperty("query_vector")
                            List<Double> queryVector
                    ) {
                    }
                }
            }
        }
    }

    public record ElasticsearchVectorSearchResponse(

            @JsonProperty("hits")
            Hits hits,

            @JsonProperty("took")
            int took,

            @JsonProperty("timed_out")
            boolean timedOut
    ) {
        public record Hits(
                @JsonProperty("hits")
                List<Hit> hits,
                @JsonProperty("total")
                Total total,
                @JsonProperty("max_score")
                float maxScore
        ) {
            public record Hit(
                    @JsonProperty("_index")
                    String index,
                    @JsonProperty("_type")
                    String type,
                    @JsonProperty("_id")
                    String id,
                    @JsonProperty("_score")
                    float score,
                    @JsonProperty("_source")
                    Document source
            ) {
            }

            public record Total(
                    @JsonProperty("value")
                    int value,
                    @JsonProperty("relation")
                    String relation
            ) {
            }
        }
    }


    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

    private static final String INDEX_NAME = "spring-ai-document-index";

    private static final String NEW_LINE = System.lineSeparator();
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String index;

    private final FilterExpressionConverter filterExpressionConverter;

    private String similarityFunction;

    public ElasticsearchVectorStore(RestClient restClient, EmbeddingClient embeddingClient) {
        this(INDEX_NAME, restClient, embeddingClient);
    }

    public ElasticsearchVectorStore(String index, RestClient restClient, EmbeddingClient embeddingClient) {
        Objects.requireNonNull(embeddingClient, "RestClient must not be null");
        Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
        this.restClient = restClient;
        this.embeddingClient = embeddingClient;
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        for (Document document : documents) {
            if (Objects.isNull(document.getEmbedding()) || document.getEmbedding().isEmpty()) {
                logger.info("Calling EmbeddingClient for document id = " + document.getId());
                document.setEmbedding(this.embeddingClient.embed(document));
            }
        }

        Request request = new Request("POST", "/_bulk");
        request.setJsonEntity(documents.stream()
                .map(document -> buildBulkActionJson(document.getId()) + NEW_LINE +
                        buildJsonString(new ElasticsearchUpsertDocumentBody(document)))
                .collect(Collectors.joining(NEW_LINE, "", NEW_LINE)));
        requestToElasticsearch(request);
    }

    private String buildJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildBulkActionJson(String documentId) {
        return buildJsonString(Map.of("update", new ElasticsearchBulkIndexId(index, documentId)));
    }

    private String buildJsonBody(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Boolean> delete(List<String> idList) {
        Request request = new Request("POST", "/_bulk");
        request.setJsonEntity(idList.stream()
                .map(id -> new ElasticsearchDeleteBody(new ElasticsearchDeleteBody.Delete(index, id)))
                .map(this::buildJsonBody).collect(Collectors.joining(NEW_LINE, "", NEW_LINE)));
        requestToElasticsearch(request);
        return Optional.of(true);
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
        ElasticsearchScriptScoreQuery elasticsearchSimilarityQuery = getElasticsearchSimilarityQuery(embedding, topK,
                similarityThreshold, filterExpression);
        return similaritySearch(elasticsearchSimilarityQuery);
    }

    private ElasticsearchScriptScoreQuery getElasticsearchSimilarityQuery(List<Double> embedding, int topK,
            float similarityThreshold, Filter.Expression filterExpression) {
        return new ElasticsearchScriptScoreQuery(topK, new Query(
                new ScriptScore(Map.of("query_string", Map.of("query", getElasticsearchQueryString(filterExpression))),
                        new Script(this.similarityFunction, new Params(embedding)), similarityThreshold, null))
        );
    }

    private String getElasticsearchQueryString(Filter.Expression filterExpression) {
        return Objects.isNull(filterExpression) ? "*" : this.filterExpressionConverter.convertExpression(
                filterExpression);

    }

    public List<Document> similaritySearch(ElasticsearchScriptScoreQuery elasticsearchScriptScoreQuery) {
        Request request = new Request("GET", "/" + index + "/_search");
        request.setJsonEntity(buildJsonBody(elasticsearchScriptScoreQuery));
        Response response = requestToElasticsearch(request);
        logger.debug("similaritySearch result - " + response);
        try {
            ElasticsearchVectorSearchResponse elasticsearchVectorSearchResponse =
                    objectMapper.readValue(EntityUtils.toString(response.getEntity()),
                            ElasticsearchVectorSearchResponse.class);
            return elasticsearchVectorSearchResponse.hits().hits().stream().map(this::toDocument)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Response requestToElasticsearch(Request request) {
        try {
            return restClient.performRequest(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Document toDocument(ElasticsearchVectorSearchResponse.Hits.Hit hit) {
        Document document = hit.source();
        document.getMetadata().put("distance", 1 - hit.score());
        return document;
    }

    public boolean exists(String targetIndex) {
        try {
            Response response = restClient.performRequest(new Request("HEAD", targetIndex));
            return response.getStatusLine().getStatusCode() == 200;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response putIndexMapping(String index, String mappingJson) {
        Request request = new Request("PUT", index);
        request.setJsonEntity(mappingJson);
        try {
            return restClient.performRequest(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (!exists(this.index)) {
            putIndexMapping(this.index, """
                    {
                      "mappings": {
                        "properties": {
                          "embedding": {
                            "type": "dense_vector",
                            "dims": 1536,
                            "index": true,
                            "similarity": "cosine"
                          }
                        }
                      }
                    }""");
        }
    }

}
