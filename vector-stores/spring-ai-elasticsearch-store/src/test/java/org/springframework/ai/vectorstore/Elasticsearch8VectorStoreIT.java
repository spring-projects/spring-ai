package org.springframework.ai.vectorstore;

import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
//@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class Elasticsearch8VectorStoreIT {

    @Container
    private static final ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.12.2").withEnv(
                    "xpack.security.enabled", "false");

    private static final String DEFAULT = "default cosine similarity";

    protected final ObjectMapper objectMapper = new ObjectMapper();

    private List<Document> documents = List.of(
            new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
            new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
            new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

    @BeforeAll
    public static void beforeAll() {
        Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
    }

    private String getText(String uri) {
        var resource = new DefaultResourceLoader().getResource(uri);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ApplicationContextRunner getContextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    }

    @BeforeEach
    void cleanDatabase() {
        getContextRunner().run(context -> {
            // Generating test data for documents with embedding values
//            EmbeddingClient embeddingClient = context.getBean(EmbeddingClient.class);
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/documents.json"),
//                    objectMapper.writeValueAsString(documents.stream()
//                            .peek(document -> document.setEmbedding(embeddingClient.embed(document.getContent())))
//                            .collect(Collectors.toList())));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/greatDepressionEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("Great Depression")));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/updateEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("FooBar")));
//            Document updateDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
//                    Map.of("meta2", "meta2"));
//            updateDocument.setEmbedding(
//                    embeddingClient.embed("The World is Big and Salvation Lurks Around the Corner"));
//            Files.writeString(Path.of(System.getProperty("user.dir"), "src/test/resources/updateDocument.json"),
//                    objectMapper.writeValueAsString(updateDocument));
//
//            var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
//                    Map.of("country", "BG", "year", 2020, "activationDate", new Date(1000)));
//            var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner",
//                    Map.of("country", "NL", "activationDate", new Date(2000)));
//            var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
//                    Map.of("country", "BG", "year", 2023, "activationDate", new Date(3000)));
//            Files.writeString(Path.of(System.getProperty("user.dir"),
//                            "src/test/resources/searchDocuments.json"),
//                    objectMapper.writeValueAsString(List.of(bgDocument, nlDocument, bgDocument2).stream()
//                            .peek(document -> document.setEmbedding(embeddingClient.embed(document.getContent())))
//                            .collect(Collectors.toList())));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/theWorldEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("The World")));
//
//            Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
//                    Collections.singletonMap("meta1", "meta1"));
//            document.setEmbedding(embeddingClient.embed(document.getContent()));
//            Files.writeString(Path.of(System.getProperty("user.dir"), "src/test/resources/springAIRocksDocuments.json"),
//                    objectMapper.writeValueAsString(document));
//
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/springEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("Spring")));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/depressionEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("Depression")));

            VectorStore vectorStore = context.getBean(VectorStore.class);
            vectorStore.delete(List.of("_all"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT,
            """
                      double value = dotProduct(params.query_vector, 'embedding');
                      return sigmoid(1, Math.E, -value);
                    """,
            "1 / (1 + l1norm(params.query_vector, 'embedding'))",
            "1 / (1 + l2norm(params.query_vector, 'embedding'))"})
    public void addAndSearchTest(String similarityFunction) {

        getContextRunner().run(context -> {
            ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
            if (!DEFAULT.equals(similarityFunction))
                vectorStore.withSimilarityFunction(similarityFunction);

            this.documents = objectMapper.readValue(ResourceUtils.getText("documents.json"),
                    new TypeReference<List<Document>>() {});
            vectorStore.add(documents);

            // List<Double> embedding = vectorStore.similaritySearch(SearchRequest.query("Great Depression").withTopK(1))
            List<Double> embedding =
                    objectMapper.readValue(ResourceUtils.getText("greatDepressionEmbeddingValues.json"),
                            new TypeReference<List<Double>>() {});

            Awaitility.await().until(() -> vectorStore.similaritySearch(embedding, 1, 0, null), hasSize(1));

            List<Document> results = vectorStore.similaritySearch(embedding, 1, 0, null);

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
            assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
            assertThat(resultDoc.getMetadata()).hasSize(2);
            assertThat(resultDoc.getMetadata()).containsKey("meta2");
            assertThat(resultDoc.getMetadata()).containsKey("distance");

            // Remove all documents from the store
            vectorStore.delete(documents.stream().map(Document::getId).toList());

            Awaitility.await()
                    .until(() -> vectorStore.similaritySearch(embedding, 1, 0, null), hasSize(0));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT,
            """
                      double value = dotProduct(params.query_vector, 'embedding');
                      return sigmoid(1, Math.E, -value);
                    """,
            "1 / (1 + l1norm(params.query_vector, 'embedding'))",
            "1 / (1 + l2norm(params.query_vector, 'embedding'))"})
    public void searchWithFilters(String similarityFunction) {

        getContextRunner().run(context -> {
            ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
            if (!DEFAULT.equals(similarityFunction))
                vectorStore.withSimilarityFunction(similarityFunction);

            var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
                    Map.of("country", "BG", "year", 2020, "activationDate", new Date(1000)));
            var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner",
                    Map.of("country", "NL", "activationDate", new Date(2000)));
            var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
                    Map.of("country", "BG", "year", 2023, "activationDate", new Date(3000)));

//            vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));
            this.documents = objectMapper.readValue(ResourceUtils.getText("searchDocuments.json"),
                    new TypeReference<List<Document>>() {});
            vectorStore.add(documents);

            List<Double> embedding = objectMapper.readValue(ResourceUtils.getText("theWorldEmbeddingValues.json"),
                    new TypeReference<List<Double>>() {});

//            Awaitility.await().until(() -> vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(5)),
//                    hasSize(3));
            Awaitility.await().until(() -> vectorStore.similaritySearch(embedding, 5, 0, null), hasSize(3));

//            List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country == 'NL'"));
            List<Document> results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country == 'NL'"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country == 'BG'"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country == 'BG'"));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
            assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country == 'BG' && year == 2020"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country == 'BG' && year == 2020"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country in ['BG']"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country in ['BG']"));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
            assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country in ['BG','NL']"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country in ['BG','NL']"));

            assertThat(results).hasSize(3);

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("country not in ['BG']"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("country not in ['BG']"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

//            results = vectorStore.similaritySearch(SearchRequest.query("The World")
//                    .withTopK(5)
//                    .withSimilarityThresholdAll()
//                    .withFilterExpression("NOT(country not in ['BG'])"));
            results = vectorStore.similaritySearch(embedding, 5,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(),
                    Filter.parser().parse("NOT(country not in ['BG'])"));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
            assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

            // List<Document> results =
            // vectorStore.similaritySearch(SearchRequest.query("The World")
            // .withTopK(5)
            // .withSimilarityThresholdAll()
            // .withFilterExpression("activationDate > '1970-01-01T00:00:02Z'"));

            // assertThat(results).hasSize(1);
            // assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

            vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId(), bgDocument2.getId()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT,
            """
                      double value = dotProduct(params.query_vector, 'embedding');
                      return sigmoid(1, Math.E, -value);
                    """,
            "1 / (1 + l1norm(params.query_vector, 'embedding'))",
            "1 / (1 + l2norm(params.query_vector, 'embedding'))"})
    public void documentUpdateTest(String similarityFunction) {

        getContextRunner().run(context -> {
            ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
            if (!DEFAULT.equals(similarityFunction))
                vectorStore.withSimilarityFunction(similarityFunction);

//            Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
//                    Collections.singletonMap("meta1", "meta1"));
            Document document =
                    objectMapper.readValue(ResourceUtils.getText("springAIRocksDocuments.json"), Document.class);
            vectorStore.add(List.of(document));

//            SearchRequest springSearchRequest = SearchRequest.query("Spring").withTopK(5);
            List<Double> springEmbedding = objectMapper.readValue(ResourceUtils.getText("springEmbeddingValues.json"),
                    new TypeReference<List<Double>>() {});


            Awaitility.await().until(() -> vectorStore.similaritySearch(springEmbedding, 5, 0, null), hasSize(1));

            List<Document> results = vectorStore.similaritySearch(springEmbedding, 5, 0, null);

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(document.getId());
            assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
            assertThat(resultDoc.getMetadata()).containsKey("meta1");
            assertThat(resultDoc.getMetadata()).containsKey("distance");

//            Document sameIdDocument = new Document(document.getId(),
//                    "The World is Big and Salvation Lurks Around the Corner",
//                    Collections.singletonMap("meta2", "meta2"));
            Document sameIdDocument = objectMapper.readValue(
                    ResourceUtils.getText("updateDocument.json").replace("\"1\"", "\"" + document.getId() + "\""),
                    Document.class);

            vectorStore.add(List.of(sameIdDocument));
//            SearchRequest fooBarSearchRequest = SearchRequest.query("FooBar").withTopK(5);
            List<Double> updateEmbedding = objectMapper.readValue(ResourceUtils.getText("updateEmbeddingValues.json"),
                    new TypeReference<List<Double>>() {});
            Awaitility.await()
                    .until(() -> vectorStore.similaritySearch(updateEmbedding, 5, 0, null).get(0).getContent(),
                            equalTo("The World is Big and Salvation Lurks Around the Corner"));

            results = vectorStore.similaritySearch(updateEmbedding, 5, 0, null);

            assertThat(results).hasSize(1);
            resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(document.getId());
            assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
            assertThat(resultDoc.getMetadata()).containsKey("meta2");
            assertThat(resultDoc.getMetadata()).containsKey("distance");

            // Remove all documents from the store
            vectorStore.delete(List.of(document.getId()));

            Awaitility.await().until(() -> vectorStore.similaritySearch(updateEmbedding, 5, 0, null), hasSize(0));

        });
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT,
            """
                      double value = dotProduct(params.query_vector, 'embedding');
                      return sigmoid(1, Math.E, -value);
                    """,
            "1 / (1 + l1norm(params.query_vector, 'embedding'))",
            "1 / (1 + l2norm(params.query_vector, 'embedding'))"})
    public void searchThresholdTest(String similarityFunction) {

        getContextRunner().run(context -> {
            ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
            if (!DEFAULT.equals(similarityFunction))
                vectorStore.withSimilarityFunction(similarityFunction);

            this.documents = objectMapper.readValue(ResourceUtils.getText("documents.json"),
                    new TypeReference<List<Document>>() {});
            vectorStore.add(documents);

            List<Double> embedding = objectMapper.readValue(ResourceUtils.getText("depressionEmbeddingValues.json"),
                    new TypeReference<List<Double>>() {});

            Awaitility.await().until(() -> vectorStore.similaritySearch(embedding, 50,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(), null), hasSize(3));

            List<Document> fullResult = vectorStore.similaritySearch(embedding, 50,
                    Double.valueOf(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL).floatValue(), null);

            List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

            assertThat(distances).hasSize(3);

            float threshold = (distances.get(0) + distances.get(1)) / 2;

            List<Document> results = vectorStore.similaritySearch(embedding, 50, 1 - threshold, null);

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
            assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
            assertThat(resultDoc.getMetadata()).containsKey("meta2");
            assertThat(resultDoc.getMetadata()).containsKey("distance");

            // Remove all documents from the store
            vectorStore.delete(documents.stream().map(Document::getId).toList());

            Awaitility.await()
                    .until(() -> vectorStore.similaritySearch(embedding, 1, 0, null), hasSize(0));
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    public static class TestApplication {

        @Bean
        public ElasticsearchVectorStore vectorStore(EmbeddingClient embeddingClient) {
            return new ElasticsearchVectorStore(
                    RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build(),
                    embeddingClient);
        }

        @Bean
        public EmbeddingClient embeddingClient() {
            return new OpenAiEmbeddingClient(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
        }

    }
}
