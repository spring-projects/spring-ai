package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
abstract class BaseElasticsearchVectorStoreIT {


    protected abstract ApplicationContextRunner getContextRunner();


    private List<Document> documents = List.of(
            new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
            new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
            new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));


    public String getText(String uri) {
        var resource = new DefaultResourceLoader().getResource(uri);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void cleanDatabase() {
        getContextRunner().run(context -> {
            VectorStore vectorStore = context.getBean(VectorStore.class);
            vectorStore.delete(List.of("_all"));
        });
    }


    @Test
    void addAndSearchAndDeleteInElasticsearch() {

        getContextRunner().run(context -> {

            ObjectMapper objectMapper = new ObjectMapper();
            ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);

            // Generating test data for documents with embedding values
//            EmbeddingClient embeddingClient = context.getBean(EmbeddingClient.class);
//            List<Document> embeddingDocuments = documents.stream()
//                    .peek(document -> document.setEmbedding(embeddingClient.embed(document.getContent())))
//                    .collect(Collectors.toList());
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/documentsWithEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingDocuments));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/embeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("Spring")));
//            Files.writeString(
//                    Path.of(System.getProperty("user.dir"), "src/test/resources/updateEmbeddingValues.json"),
//                    objectMapper.writeValueAsString(embeddingClient.embed("FooBar")));
//            Document updateDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
//                    Map.of("meta2", "meta2"));
//            updateDocument.setEmbedding(
//                    embeddingClient.embed("The World is Big and Salvation Lurks Around the Corner"));
//            Files.writeString(Path.of(System.getProperty("user.dir"), "src/test/resources/updateDocument.json"),
//                    objectMapper.writeValueAsString(updateDocument));

            this.documents = objectMapper.readValue(ResourceUtils.getText("documentsWithEmbeddingValues.json"),
                    new TypeReference<List<Document>>() {});
            vectorStore.add(documents);
            // Elasticsearch requires indexing time
            Thread.sleep(1000);

            List<Double> embedding = objectMapper.readValue(ResourceUtils.getText("embeddingValues.json"),
                    new TypeReference<List<Double>>() {});
            List<Document> results = vectorStore.similaritySearch(embedding);

            assertThat(results).hasSize(3);

            Document resultDoc = results.stream().max(Comparator.comparingDouble(
                    document -> Double.parseDouble(document.getMetadata().get("distance").toString()))).get();
            assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
            assertThat(resultDoc.getContent()).contains(
                    "Spring AI provides abstractions that serve as the foundation for developing AI applications.");
            assertThat(resultDoc.getMetadata()).hasSize(2);
            assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

            Document sameIdDocument =
                    objectMapper.readValue(ResourceUtils.getText("updateDocument.json"), Document.class);

            vectorStore.add(List.of(sameIdDocument));
            // Elasticsearch requires indexing time
            Thread.sleep(1000);

            List<Double> updateEmbedding = objectMapper.readValue(ResourceUtils.getText("updateEmbeddingValues.json"),
                    new TypeReference<List<Double>>() {});

            results = vectorStore.similaritySearch(updateEmbedding);

            assertThat(results).hasSize(3);
            resultDoc = results.stream().max(Comparator.comparingDouble(
                    document -> Double.parseDouble(document.getMetadata().get("distance").toString()))).get();
            assertThat(resultDoc.getId()).isEqualTo(sameIdDocument.getId());
            assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
            assertThat(resultDoc.getMetadata()).containsKey("meta2");
            assertThat(resultDoc.getMetadata()).containsKey("distance");

            // Remove all documents from the store
            vectorStore.delete(documents.stream().map(Document::getId).toList());
            // Elasticsearch requires indexing time
            Thread.sleep(1000);

            results = vectorStore.similaritySearch(updateEmbedding);
            assertThat(results).isEmpty();
        });
    }

    public static class TestApplication {

        @Bean
        public EmbeddingClient embeddingClient() {
            return new OpenAiEmbeddingClient(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
        }

    }

}
