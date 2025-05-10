/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.pgvector;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.knuddels.jtokkit.api.EncodingType;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration tests for PgVectorStore with auto-truncation enabled. Tests the behavior
 * when using artificially high token limits with Vertex AI's auto-truncation feature.
 *
 * @author Soby Chacko
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class PgVectorStoreAutoTruncationIT {

	private static final int ARTIFICIAL_TOKEN_LIMIT = 132_900;

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PgVectorStoreAutoTruncationIT.TestApplication.class)
		.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private static void dropTable(ApplicationContext context) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
	}

	@Test
	public void testAutoTruncationWithLargeDocument() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// Test with a document that exceeds normal token limits but is within our
			// artificially high limit
			String largeContent = "This is a test document. ".repeat(5000); // ~25,000
																			// tokens
			Document largeDocument = new Document(largeContent);
			largeDocument.getMetadata().put("test", "auto-truncation");

			// This should not throw an exception due to our high token limit in
			// BatchingStrategy
			assertDoesNotThrow(() -> vectorStore.add(List.of(largeDocument)));

			// Verify the document was stored
			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("test document").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getMetadata()).containsEntry("test", "auto-truncation");

			// Test with multiple large documents to ensure batching still works
			List<Document> largeDocs = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				Document doc = new Document("Large content " + i + " ".repeat(4000));
				doc.getMetadata().put("batch", String.valueOf(i));
				largeDocs.add(doc);
			}

			assertDoesNotThrow(() -> vectorStore.add(largeDocs));

			// Verify all documents were processed
			List<Document> batchResults = vectorStore
				.similaritySearch(SearchRequest.builder().query("Large content").topK(5).build());

			assertThat(batchResults).hasSizeGreaterThanOrEqualTo(5);

			// Clean up
			vectorStore.delete(List.of(largeDocument.getId()));
			largeDocs.forEach(doc -> vectorStore.delete(List.of(doc.getId())));

			dropTable(context);
		});
	}

	@Test
	public void testExceedingArtificialLimit() {
		this.contextRunner.run(context -> {
			BatchingStrategy batchingStrategy = context.getBean(BatchingStrategy.class);

			// Create a document that exceeds even our artificially high limit
			String massiveContent = "word ".repeat(150000); // ~150,000 tokens (exceeds
															// 132,900)
			Document massiveDocument = new Document(massiveContent);

			// This should throw an exception as it exceeds our configured limit
			assertThrows(IllegalArgumentException.class, () -> {
				batchingStrategy.batch(List.of(massiveDocument));
			});

			dropTable(context);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.distanceType}")
		PgVectorStore.PgDistanceType distanceType;

		@Value("${test.spring.ai.vectorstore.pgvector.initializeSchema:true}")
		boolean initializeSchema;

		@Value("${test.spring.ai.vectorstore.pgvector.idType:UUID}")
		PgVectorStore.PgIdType idType;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
				BatchingStrategy batchingStrategy) {
			return PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.dimensions(PgVectorStore.INVALID_EMBEDDING_DIMENSION)
				.batchingStrategy(batchingStrategy)
				.idType(this.idType)
				.distanceType(this.distanceType)
				.initializeSchema(this.initializeSchema)
				.indexType(PgVectorStore.PgIndexType.HNSW)
				.removeExistingVectorStoreTable(true)
				.build();
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("app.datasource")
		public DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

		@Bean
		public VertexAiTextEmbeddingModel vertexAiEmbeddingModel(VertexAiEmbeddingConnectionDetails connectionDetails) {
			VertexAiTextEmbeddingOptions options = VertexAiTextEmbeddingOptions.builder()
				.model(VertexAiTextEmbeddingOptions.DEFAULT_MODEL_NAME)
				// Although this might be the default in Vertex, we are explicitly setting
				// this to true to ensure
				// that auto truncate is turned on as this is crucial for the
				// verifications in this test suite.
				.autoTruncate(true)
				.build();

			return new VertexAiTextEmbeddingModel(connectionDetails, options);
		}

		@Bean
		public VertexAiEmbeddingConnectionDetails connectionDetails() {
			return VertexAiEmbeddingConnectionDetails.builder()
				.projectId(System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"))
				.location(System.getenv("VERTEX_AI_GEMINI_LOCATION"))
				.build();
		}

		@Bean
		BatchingStrategy pgVectorStoreBatchingStrategy() {
			return new TokenCountBatchingStrategy(EncodingType.CL100K_BASE, ARTIFICIAL_TOKEN_LIMIT, 0.1);
		}

	}

}
