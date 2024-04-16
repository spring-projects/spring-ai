/*
 * Copyright 2024 - 2024 the original author or authors.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.SyntaxError;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumn;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumnTags;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraVectorStoreIT`
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Testcontainers
class CassandraVectorStoreIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(DEFAULT_IMAGE_NAME.withTag("5.0"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"),
					Map.of("meta2", "meta2", "something_extra", "blue")));

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
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {
				Assertions.assertNotNull(store);
				store.checkSchemaValid();
			}
		});
	}

	@Test
	void addAndSearch() {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context, new SchemaColumn("meta1", DataTypes.TEXT),
					new SchemaColumn("meta2", DataTypes.TEXT))) {
				store.add(documents);

				List<Document> results = store.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());

				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", CassandraVectorStore.SIMILARITY_FIELD_NAME);

				// Remove all documents from the store
				store.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = store.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).isEmpty();
			}
		});
	}

	@Test
	void searchWithPartitionFilter() throws InterruptedException {
		contextRunner.run(context -> {

			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("BG", "The World is Big and Salvation Lurks Around the Corner",
						Map.of("year", (short) 2020));
				var nlDocument = new Document("NL", "The World is Big and Salvation Lurks Around the Corner",
						emptyMap());
				var bgDocument2 = new Document("BG2", "The World is Big and Salvation Lurks Around the Corner",
						Map.of("year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store.similaritySearch(SearchRequest.query("The World").withTopK(5));
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression(format("%s == 'NL'", CassandraVectorStoreConfig.DEFAULT_ID_NAME)));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression(format("%s == 'BG2'", CassandraVectorStoreConfig.DEFAULT_ID_NAME)));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression(
							format("%s == 'BG' && year == 2020", CassandraVectorStoreConfig.DEFAULT_ID_NAME)));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class, () -> {
					store.similaritySearch(SearchRequest.query("The World")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression(
								format("NOT(%s == 'BG' && year == 2020)", CassandraVectorStoreConfig.DEFAULT_ID_NAME)));
				});
			}
		});
	}

	@Test
	void unsearchableFilters() throws InterruptedException {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store.similaritySearch(SearchRequest.query("The World").withTopK(5));
				assertThat(results).hasSize(3);

				Assertions.assertThrows(InvalidQueryException.class, () -> {
					store.similaritySearch(SearchRequest.query("The World")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("country == 'NL'"));
				});
			}
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {
		contextRunner.run(context -> {

			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("country", DataTypes.TEXT, SchemaColumnTags.INDEXED),
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store.similaritySearch(SearchRequest.query("The World").withTopK(5));
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'NL'"));
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'BG'"));

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = store.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("country == 'BG' && year == 2020"));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class, () -> {
					store.similaritySearch(SearchRequest.query("The World")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("country == 'BG' || year == 2020"));
				});

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class, () -> {
					store.similaritySearch(SearchRequest.query("The World")
						.withTopK(5)
						.withSimilarityThresholdAll()
						.withFilterExpression("NOT(country == 'BG' && year == 2020)"));
				});
			}
		});
	}

	@Test
	void documentUpdate() {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				store.add(List.of(document));

				List<Document> results = store.similaritySearch(SearchRequest.query("Spring").withTopK(5));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKey("meta1");

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				store.add(List.of(sameIdDocument));

				results = store.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", CassandraVectorStore.SIMILARITY_FIELD_NAME);

				store.delete(List.of(document.getId()));
			}
		});
	}

	@Test
	void searchWithThreshold() {
		contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {
				store.add(documents);

				List<Document> fullResult = store
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThresholdAll());

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get(CassandraVectorStore.SIMILARITY_FIELD_NAME))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = store
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());

				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				assertThat(resultDoc.getMetadata()).containsKeys("meta1", CassandraVectorStore.SIMILARITY_FIELD_NAME);
			}
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public CassandraVectorStore store(CqlSession cqlSession, EmbeddingClient embeddingClient) {

			CassandraVectorStoreConfig conf = storeBuilder(cqlSession)
				.addMetadataColumn(new SchemaColumn("meta1", DataTypes.TEXT), new SchemaColumn("meta2", DataTypes.TEXT),
						new SchemaColumn("country", DataTypes.TEXT), new SchemaColumn("year", DataTypes.SMALLINT))
				.build();

			conf.dropKeyspace();
			return new CassandraVectorStore(conf, embeddingClient);
		}

		@Bean
		public EmbeddingClient embeddingClient() {
			return new TransformersEmbeddingClient();
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// comment next two lines out to connect to a local C* cluster
				.addContactPoint(cassandraContainer.getContactPoint())
				.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
				.build();
		}

	}

	static CassandraVectorStoreConfig.Builder storeBuilder(CqlSession cqlSession) {
		return CassandraVectorStoreConfig.builder()
			.withCqlSession(cqlSession)
			.withKeyspaceName("test_" + CassandraVectorStoreConfig.DEFAULT_KEYSPACE_NAME);
	}

	private CassandraVectorStore createTestStore(ApplicationContext context, SchemaColumn... metadataFields) {

		CassandraVectorStoreConfig.Builder builder = storeBuilder(context.getBean(CqlSession.class))
			.addMetadataColumn(metadataFields);

		CassandraVectorStoreConfig conf = builder.build();
		conf.dropKeyspace();
		return new CassandraVectorStore(conf, context.getBean(EmbeddingClient.class));
	}

}
