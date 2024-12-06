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

package org.springframework.ai.milvus.vectorstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.DocumentMetadata;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.milvus.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class MilvusVectorStoreIT {

	@Container
	private static MilvusContainer milvusContainer = new MilvusContainer(MilvusImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void resetCollection(VectorStore vectorStore) {
		((MilvusVectorStore) vectorStore).dropCollection();
		((MilvusVectorStore) vectorStore).createCollection();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "L2", "IP" })
	public void addAndSearch(String metricType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).hasSize(0);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE" })
	// @ValueSource(strings = { "COSINE", "IP", "L2" })
	public void searchWithFilters(String metricType) throws InterruptedException {

		// https://milvus.io/docs/json_data_type.md

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(5));
				assertThat(results).hasSize(3);

				results = vectorStore.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'NL'"));
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'BG'"));

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = vectorStore.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'BG' && year == 2020"));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("NOT(country == 'BG' && year == 2020)"));

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "L2", "IP" })
	public void documentUpdate(String metricType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				vectorStore.add(List.of(document));

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKey("meta1");
				assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				vectorStore.add(List.of(sameIdDocument));

				results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKey("meta2");
				assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

				vectorStore.delete(List.of(document.getId()));

			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "IP" })
	public void searchWithThreshold(String metricType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThresholdAll());

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				assertThat(scores).hasSize(3);

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

				List<Document> results = vectorStore.similaritySearch(
						SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(similarityThreshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.milvus.metricType}")
		private MetricType metricType;

		@Bean
		public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
			return MilvusVectorStore.builder()
				.milvusClient(milvusClient)
				.embeddingModel(embeddingModel)
				.collectionName("test_vector_store")
				.databaseName("default")
				.indexType(IndexType.IVF_FLAT)
				.metricType(this.metricType)
				.batchingStrategy(new TokenCountBatchingStrategy())
				.initializeSchema(true)
				.build();
		}

		@Bean
		public MilvusServiceClient milvusClient() {
			return new MilvusServiceClient(ConnectParam.newBuilder()
				.withAuthorization("minioadmin", "minioadmin")
				.withUri(milvusContainer.getEndpoint())
				.build());
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
			// return new OpenAiEmbeddingModel(new
			// OpenAiApi(System.getenv("OPENAI_API_KEY")), MetadataMode.EMBED,
			// OpenAiEmbeddingOptions.builder().withModel("text-embedding-ada-002").build());
		}

	}

}
