/*
 * Copyright 2023-present the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upsert verification for {@link PgVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link PgVectorStoreIT}, this test needs no {@code OPENAI_API_KEY}:
 * {@code doUpsert} never embeds, and read-back only needs a query vector, so a tiny
 * deterministic local {@link EmbeddingModel} is wired instead of a hosted one. Only
 * Testcontainers Postgres is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class PgVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	// Smaller than MULTI_BATCH_ENTRY_COUNT so the multi-entry upsert genuinely spans
	// several write batches, exercising positional pairing across batch boundaries.
	private static final int MAX_DOCUMENT_BATCH_SIZE = 5;

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		JdbcTemplate jdbcTemplate = schemaOwner.<JdbcTemplate>getNativeClient().orElseThrow();
		return PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
			.build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		JdbcTemplate jdbcTemplate = schemaOwner.<JdbcTemplate>getNativeClient().orElseThrow();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.vectorTableName("upsert_configured_dimensions")
			.dimensions(dimensions)
			.initializeSchema(true)
			.removeExistingVectorStoreTable(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void upsertIntoExistingMixedCaseTableWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			JdbcTemplate jdbcTemplate = vectorStore.<JdbcTemplate>getNativeClient().orElseThrow();
			// Postgres stores the unquoted name as upsert_mixedcase, so the lookup has to
			// fold the configured name the same way to find the table.
			String tableName = "Upsert_MixedCase";
			PgVectorStore.builder(jdbcTemplate, new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.vectorTableName(tableName)
				.dimensions(EMBEDDING_DIMENSIONS)
				.initializeSchema(true)
				.removeExistingVectorStoreTable(true)
				.build()
				.afterPropertiesSet();

			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			PgVectorStore writer = PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.vectorTableName(tableName)
				.build();

			writer.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "mixed case", Map.of("tag", "m"))));
			assertWrongDimensionRejected(writer);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.dimensions(EMBEDDING_DIMENSIONS)
				.maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
				.initializeSchema(true)
				.indexType(PgIndexType.HNSW)
				.removeExistingVectorStoreTable(true)
				.build();
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public DataSourceProperties dataSourceProperties() {
			DataSourceProperties properties = new DataSourceProperties();
			properties.setUrl(postgresContainer.getJdbcUrl());
			properties.setUsername(postgresContainer.getUsername());
			properties.setPassword(postgresContainer.getPassword());
			return properties;
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
