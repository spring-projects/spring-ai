package org.springframework.ai.vectorstore;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.InitializingBean;

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
            public record ScriptScore(
                    @JsonProperty("query")
                    Map<String, Object> query,

                    @JsonProperty("script")
                    Script script
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
        String collect = documents.stream()
                .map(document -> buildBulkActionJson(document.getId()) + NEW_LINE +
                        buildJsonString(new ElasticsearchUpsertDocumentBody(document)))
                .collect(Collectors.joining(NEW_LINE, "", NEW_LINE));
        request.setEntity(new StringEntity(collect, ContentType.APPLICATION_JSON));
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
        String collect = idList.stream()
                .map(id -> new ElasticsearchDeleteBody(new ElasticsearchDeleteBody.Delete(index, id)))
                .map(this::buildJsonBody).collect(Collectors.joining(NEW_LINE, "", NEW_LINE));
        request.setEntity(new StringEntity(collect, ContentType.APPLICATION_JSON));
        requestToElasticsearch(request);
        return Optional.of(true);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        if (searchRequest.getFilterExpression() != null) {
            throw new UnsupportedOperationException(
                    "The [" + this.getClass() + "] doesn't support metadata filtering!");
        }
        return similaritySearch(this.embeddingClient.embed(searchRequest.getQuery()));
    }

    public List<Document> similaritySearch(List<Double> embedding) {
        Request request = new Request("GET", "/" + index + "/_search");
        String matchAll = buildJsonBody(new ElasticsearchScriptScoreQuery(5, new Query(
                new ScriptScore(Map.of("match_all", Map.of()),
                        // divided by 2 to get score in the range [0, 1]
                        new Script("(cosineSimilarity(params.query_vector, 'embedding') + 1.0) / 2",
                                new Params(embedding))))));
        request.setJsonEntity(matchAll);

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
        document.getMetadata().put("distance", hit.score());
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
