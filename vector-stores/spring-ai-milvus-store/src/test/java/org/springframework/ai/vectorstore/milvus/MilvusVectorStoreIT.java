/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.milvus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.milvus.client.AbstractMilvusGrpcClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Soby Chacko
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class MilvusVectorStoreIT extends BaseVectorStoreTests {

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

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + "COSINE")
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				testFunction.accept(vectorStore);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "L2", "IP" })
	public void addAndSearch(String metricType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
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

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'NL'")
					.build());
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'BG'")
					.build());

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'BG' && year == 2020")
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("NOT(country == 'BG' && year == 2020)")
					.build());

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

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKey("meta1");
				assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				vectorStore.add(List.of(sameIdDocument));

				results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
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
					.similaritySearch(SearchRequest.builder().query("Spring").topK(5).similarityThresholdAll().build());

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				assertThat(scores).hasSize(3);

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Spring")
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
			});
	}

	@Test
	public void deleteWithComplexFilterExpression() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=COSINE").run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			var doc1 = new Document("Content 1", Map.of("type", "A", "priority", 1));
			var doc2 = new Document("Content 2", Map.of("type", "A", "priority", 2));
			var doc3 = new Document("Content 3", Map.of("type", "B", "priority", 1));

			vectorStore.add(List.of(doc1, doc2, doc3));

			// Complex filter expression: (type == 'A' AND priority > 1)
			Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
					new Filter.Key("priority"), new Filter.Value(1));
			Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
					new Filter.Value("A"));
			Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
					priorityFilter);

			vectorStore.delete(complexFilter);

			var results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("A", "B");
			assertThat(results.stream().map(doc -> doc.getMetadata().get("priority")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(1.0, 1.0);
		});
	}

	@Test
	void initializeSchema() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=COSINE").run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			Logger logger = (Logger) LoggerFactory.getLogger(AbstractMilvusGrpcClient.class);
			LogAppender logAppender = new LogAppender();
			logger.addAppender(logAppender);
			logAppender.start();

			resetCollection(vectorStore);

			assertThat(logAppender.capturedLogs).isEmpty();
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=COSINE").run(context -> {
			MilvusVectorStore vectorStore = context.getBean(MilvusVectorStore.class);
			Optional<MilvusServiceClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.milvus.metricType}")
		private MetricType metricType;

		@Bean
		public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
			return MilvusVectorStore.builder(milvusClient, embeddingModel)
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
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
			// return new OpenAiEmbeddingModel(new
			// OpenAiApi(System.getenv("OPENAI_API_KEY")), MetadataMode.EMBED,
			// OpenAiEmbeddingOptions.builder().withModel("text-embedding-ada-002").build());
		}

	}

	static class LogAppender extends AppenderBase<ILoggingEvent> {

		private final List<String> capturedLogs = new ArrayList<>();

		@Override
		protected void append(ILoggingEvent eventObject) {
			this.capturedLogs.add(eventObject.getFormattedMessage());
		}

		public List<String> getCapturedLogs() {
			return this.capturedLogs;
		}

	}

}
