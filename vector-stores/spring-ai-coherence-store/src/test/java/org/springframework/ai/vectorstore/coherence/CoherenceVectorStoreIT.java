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

package org.springframework.ai.vectorstore.coherence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.oracle.bedrock.junit.CoherenceClusterExtension;
import com.oracle.bedrock.runtime.coherence.CoherenceClusterMember;
import com.oracle.bedrock.runtime.coherence.options.ClusterName;
import com.oracle.bedrock.runtime.coherence.options.LocalHost;
import com.oracle.bedrock.runtime.coherence.options.RoleName;
import com.oracle.bedrock.runtime.coherence.options.WellKnownAddress;
import com.oracle.bedrock.runtime.java.options.IPv4Preferred;
import com.oracle.bedrock.runtime.java.options.SystemProperty;
import com.oracle.bedrock.runtime.options.DisplayName;
import com.oracle.bedrock.testsupport.junit.TestLogsExtension;
import com.tangosol.net.Coherence;
import com.tangosol.net.Session;
import org.junit.Assert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Crashes on github actions run")
public class CoherenceVectorStoreIT {

	@RegisterExtension
	static TestLogsExtension testLogs = new TestLogsExtension();

	@RegisterExtension
	static CoherenceClusterExtension cluster = new CoherenceClusterExtension()
		.with(ClusterName.of("CoherenceVectorStoreIT"), WellKnownAddress.loopback(), LocalHost.only(),
				IPv4Preferred.autoDetect(), SystemProperty.of("coherence.serializer", "pof"))
		.include(3, CoherenceClusterMember.class, DisplayName.of("storage"), RoleName.of("storage"), testLogs);

	final List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(final String uri) {
		try {
			return new DefaultResourceLoader().getResource(uri).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestClient.class)
		.withPropertyValues("test.spring.ai.vectorstore.coherence.distanceType=COSINE",
				"test.spring.ai.vectorstore.coherence.indexType=NONE");

	private static void truncateMap(ApplicationContext context, String mapName) {
		Session session = context.getBean(Session.class);
		session.getMap(mapName).truncate();
	}

	public static Stream<Arguments> distanceAndIndex() {
		List<Arguments> argumentList = new ArrayList<>();
		for (var distanceType : CoherenceVectorStore.DistanceType.values()) {
			for (var indexType : CoherenceVectorStore.IndexType.values()) {
				argumentList.add(Arguments.of(distanceType, indexType));
			}
		}

		return argumentList.stream();
	}

	@ParameterizedTest(name = "Distance {0}, Index {1} : {displayName}")
	@MethodSource("distanceAndIndex")
	public void addAndSearch(CoherenceVectorStore.DistanceType distanceType, CoherenceVectorStore.IndexType indexType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.coherence.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.coherence.indexType=" + indexType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("What is Great Depression").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				List<Document> results2 = vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build());
				assertThat(results2).hasSize(0);

				truncateMap(context, ((CoherenceVectorStore) vectorStore).getMapName());
			});
	}

	@ParameterizedTest(name = "Distance {0}, Index {1} : {displayName}")
	@MethodSource("distanceAndIndex")
	public void searchWithFilters(CoherenceVectorStore.DistanceType distanceType,
			CoherenceVectorStore.IndexType indexType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.coherence.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.coherence.indexType=" + indexType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020, "foo bar 1", "bar.foo"));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				SearchRequest searchRequest = SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.build();

				List<Document> results = vectorStore.similaritySearch(searchRequest);

				assertThat(results).hasSize(3);

				results = vectorStore
					.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == 'NL'").build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = vectorStore
					.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == 'BG'").build());

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = vectorStore.similaritySearch(
						SearchRequest.from(searchRequest).filterExpression("country == 'BG' && year == 2020").build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.from(searchRequest)
					.filterExpression("(country == 'BG' && year == 2020) || (country == 'NL')")
					.build());

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), nlDocument.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), nlDocument.getId());

				results = vectorStore.similaritySearch(SearchRequest.from(searchRequest)
					.filterExpression("NOT((country == 'BG' && year == 2020) || (country == 'NL'))")
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

				try {
					vectorStore
						.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == NL").build());
					Assert.fail("Invalid filter expression should have been cached!");
				}
				catch (FilterExpressionTextParser.FilterExpressionParseException e) {
					assertThat(e.getMessage()).contains("Line: 1:17, Error: no viable alternative at input 'NL'");
				}

				// Remove all documents from the store
				truncateMap(context, ((CoherenceVectorStore) vectorStore).getMapName());
			});
	}

	@Test
	public void documentUpdate() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());

			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());
			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

			truncateMap(context, ((CoherenceVectorStore) vectorStore).getMapName());
		});
	}

	@Test
	public void searchWithThreshold() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> fullResult = vectorStore.similaritySearch(
					SearchRequest.builder().query("Time Shelter").topK(5).similarityThresholdAll().build());

			assertThat(fullResult).hasSize(3);

			assertThat(isSortedByDistance(fullResult)).isTrue();

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Time Shelter")
				.topK(5)
				.similarityThreshold(similarityThreshold)
				.build());

			// Debug: print all returned document IDs and metadata
			for (Document doc : results) {
				System.out.println("Returned doc ID: " + doc.getId() + ", metadata: " + doc.getMetadata());
			}

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(1).getId());
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			truncateMap(context, ((CoherenceVectorStore) vectorStore).getMapName());
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			CoherenceVectorStore vectorStore = context.getBean(CoherenceVectorStore.class);
			Optional<Session> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	public void similaritySearchReturnsMetadata() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);

			// Query that matches the first document, which has meta1
			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("spring ai").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
		});
	}

	private static boolean isSortedByDistance(final List<Document> documents) {
		final List<Double> distances = documents.stream()
			.map(doc -> (Double) doc.getMetadata().get(DocumentMetadata.DISTANCE.value()))
			.toList();

		if (CollectionUtils.isEmpty(distances) || distances.size() == 1) {
			return true;
		}

		Iterator<Double> iter = distances.iterator();
		Double current;
		Double previous = iter.next();
		while (iter.hasNext()) {
			current = iter.next();
			if (previous > current) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestClient {

		@Value("${test.spring.ai.vectorstore.coherence.distanceType}")
		CoherenceVectorStore.DistanceType distanceType;

		@Value("${test.spring.ai.vectorstore.coherence.indexType}")
		CoherenceVectorStore.IndexType indexType;

		@Bean
		public VectorStore vectorStore(EmbeddingModel embeddingModel, Session session) {
			return CoherenceVectorStore.builder(session, embeddingModel)
				.distanceType(this.distanceType)
				.indexType(this.indexType)
				.forcedNormalization(this.distanceType == CoherenceVectorStore.DistanceType.COSINE
						|| this.distanceType == CoherenceVectorStore.DistanceType.IP)
				.build();
		}

		@Bean
		public Session session(Coherence coherence) {
			return coherence.getSession();
		}

		@Bean
		public Coherence coherence() {
			return Coherence.clusterMember().start().join();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			try {
				TransformersEmbeddingModel tem = new TransformersEmbeddingModel();
				tem.afterPropertiesSet();
				return tem;
			}
			catch (Exception e) {
				throw new RuntimeException("Failed initializing embedding model", e);
			}
		}

	}

}
