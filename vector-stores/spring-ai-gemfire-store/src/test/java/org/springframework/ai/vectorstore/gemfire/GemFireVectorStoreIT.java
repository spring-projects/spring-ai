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

package org.springframework.ai.vectorstore.gemfire;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Geet Rawat
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Jason Huynh
 * @author Nabarun Nag
 * @since 1.0.0
 */
@Disabled
@Testcontainers
public class GemFireVectorStoreIT {

	public static final String INDEX_NAME = "spring-ai-index1";

	private static final int HTTP_SERVICE_PORT = 9090;

	private static final int LOCATOR_COUNT = 1;

	private static final int SERVER_COUNT = 1;

	private static GemFireCluster gemFireCluster;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@AfterAll
	public static void stopGemFireCluster() {
		gemFireCluster.close();
	}

	@BeforeAll
	public static void startGemFireCluster() {
		Ports.Binding hostPort = Ports.Binding.bindPort(HTTP_SERVICE_PORT);
		ExposedPort exposedPort = new ExposedPort(HTTP_SERVICE_PORT);
		PortBinding mappedPort = new PortBinding(hostPort, exposedPort);
		gemFireCluster = new GemFireCluster(GemFireImage.DEFAULT_IMAGE, LOCATOR_COUNT, SERVER_COUNT);
		gemFireCluster.withConfiguration(GemFireCluster.SERVER_GLOB,
				container -> container.withExposedPorts(HTTP_SERVICE_PORT)
					.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(mappedPort)));
		gemFireCluster.withGemFireProperty(GemFireCluster.SERVER_GLOB, "http-service-port",
				Integer.toString(HTTP_SERVICE_PORT));
		gemFireCluster.acceptLicense().start();

		System.setProperty("spring.data.gemfire.pool.locators",
				String.format("localhost[%d]", gemFireCluster.getLocatorPort()));
	}

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void addAndDeleteEmbeddingTest() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
			Awaitility.await()
				.atMost(1, java.util.concurrent.TimeUnit.MINUTES)
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(3).build()), hasSize(0));
		});
	}

	@Test
	public void addAndSearchTest() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);

			Awaitility.await()
				.atMost(1, java.util.concurrent.TimeUnit.MINUTES)
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build()), hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great Depression").topK(5).build());
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939)" + " was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
		});
	}

	@Test
	public void documentUpdateTest() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));
			vectorStore.add(List.of(document));
			SearchRequest springSearchRequest = SearchRequest.builder().query("Spring").topK(5).build();
			Awaitility.await()
				.atMost(1, java.util.concurrent.TimeUnit.MINUTES)
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build()), hasSize(1));
			List<Document> results = vectorStore.similaritySearch(springSearchRequest);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks " + "Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));
			SearchRequest fooBarSearchRequest = SearchRequest.builder().query("FooBar").topK(5).build();
			results = vectorStore.similaritySearch(fooBarSearchRequest);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation" + " Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
		});
	}

	@Test
	public void searchThresholdTest() {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);

			Awaitility.await()
				.atMost(1, java.util.concurrent.TimeUnit.MINUTES)
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(5).similarityThresholdAll().build()),
						hasSize(3));

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Depression").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();
			assertThat(scores).hasSize(3);
			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Depression")
				.topK(5)
				.similarityThreshold(similarityThreshold)
				.build());
			for (Document result : results) {
				assertThat(result.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
			}
		});
	}

	@Test
	public void searchWithFilters() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", "2020", "activationDate",
							String.valueOf(new Date(1000).toInstant().toEpochMilli())));
			var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner", Map
				.of("country", "NL", "activationDate", String.valueOf(new Date(2000).toInstant().toEpochMilli())));
			var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", "2023", "activationDate",
							String.valueOf(new Date(3000).toInstant().toEpochMilli())));
			var usDocument = new Document("4", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "US", "year", "2025", "activationDate",
							String.valueOf(new Date(4000).toInstant().toEpochMilli())));

			List<Document> filterDocuments = List.of(bgDocument, nlDocument, bgDocument2, usDocument);

			vectorStore.add(filterDocuments);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build()),
						hasSize(4));

			List<Document> tresults = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great Depression").topK(5).build());
			Document tresultDoc = tresults.get(0);
			assertThat(tresultDoc.getFormattedContent()).contains("The World");

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
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
				.filterExpression("country == 'BG' && year == '2020'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' AND year == '2020'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' || year == '2025'")
				.build());
			assertThat(results).hasSize(3);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId(), usDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' OR year == '2025'")
				.build());
			assertThat(results).hasSize(3);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId(), usDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG']")
				.build());
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG','NL']")
				.build());
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['*'] AND country not in ['BG']")
				.build());
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), usDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("NOT(country not in ['BG'])")
				.build());
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(
						"activationDate > " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli())
				.build());
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument2.getId(), usDocument.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument2.getId(), usDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(
						"activationDate <= " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli())
				.build());
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("activationDate < " + new Date(3000).toInstant().toEpochMilli()
						+ " AND activationDate > " + new Date(1000).toInstant().toEpochMilli())
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(filterDocuments.stream().map(Document::getId).toList());
			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(1).build()),
						hasSize(0));
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public GemFireVectorStore vectorStore(EmbeddingModel embeddingModel) {
			return GemFireVectorStore.builder(embeddingModel)
				.host("localhost")
				.port(HTTP_SERVICE_PORT)
				.indexName(INDEX_NAME)
				.fields(new String[] { "year", "country", "activationDate" })
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
