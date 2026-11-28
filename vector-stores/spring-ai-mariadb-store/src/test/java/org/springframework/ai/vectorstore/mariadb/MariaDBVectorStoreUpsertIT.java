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

package org.springframework.ai.vectorstore.mariadb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upsert verification for {@link MariaDBVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link MariaDBStoreIT}, this test needs no {@code OPENAI_API_KEY}: upsert never
 * embeds, and read-back only needs a query vector, so a small fixed-size local
 * {@link EmbeddingModel} is used. Only the MariaDB container is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class MariaDBVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	// Smaller than MULTI_BATCH_ENTRY_COUNT so the multi-entry upsert spans several write
	// batches.
	private static final int MAX_DOCUMENT_BATCH_SIZE = 5;

	@Container
	@SuppressWarnings("resource")
	static MariaDBContainer<?> mariadbContainer = new MariaDBContainer<>(MariaDBImage.DEFAULT_IMAGE)
		.withUsername("mariadb")
		.withPassword("mariadbpwd")
		.withDatabaseName("testdb");

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
		return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
			.maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
			.build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		JdbcTemplate jdbcTemplate = schemaOwner.<JdbcTemplate>getNativeClient().orElseThrow();
		MariaDBVectorStore store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
			.vectorTableName("upsert_configured_dimensions")
			.dimensions(dimensions)
			.initializeSchema(true)
			.removeExistingVectorStoreTable(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Override
	@Test
	protected void concurrentFirstUseResolvesTheSizeOnce() {
		// Concurrent writes can deadlock on MariaDB's vector index, so the threads search
		// instead. Searching resolves the size the same way a write does.
		int threads = 8;
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			VectorStore reader = createStoreOverExistingSchema(vectorStore, embeddingModel);
			float[] queryEmbedding = { 0.1f, 0.2f, 0.3f, 0.4f };
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();

			ExecutorService executor = Executors.newFixedThreadPool(threads);
			try {
				CountDownLatch start = new CountDownLatch(1);
				List<Future<?>> futures = new ArrayList<>();
				for (int i = 0; i < threads; i++) {
					futures.add(executor.submit(() -> {
						start.await();
						reader.similaritySearch(queryEmbedding, request);
						return null;
					}));
				}
				start.countDown();
				for (Future<?> future : futures) {
					assertThatNoException().isThrownBy(() -> future.get(30, TimeUnit.SECONDS));
				}
			}
			finally {
				executor.shutdownNow();
			}
			assertWrongDimensionRejected(reader);
			assertThat(embeddingModel.calls()).as("embedding model calls during concurrent first use").isZero();
		});
	}

	@Test
	void initializingOverAnExistingTableWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			JdbcTemplate jdbcTemplate = vectorStore.<JdbcTemplate>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			// The shared store has already created the default table
			MariaDBVectorStore store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void validatingAnExistingTableWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			JdbcTemplate jdbcTemplate = vectorStore.<JdbcTemplate>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			MariaDBVectorStore store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
				.schemaValidation(true)
				.build();
			store.afterPropertiesSet();

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "validated", Map.of("tag", "v"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void failedTableReadLeavesTheSizeUnknownAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			DataSource dataSource = vectorStore.<JdbcTemplate>getNativeClient().orElseThrow().getDataSource();
			AtomicBoolean failColumnReads = new AtomicBoolean();
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource) {

				@Override
				public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
					if (failColumnReads.get() && sql.contains("INFORMATION_SCHEMA.COLUMNS")) {
						throw new DataAccessResourceFailureException("Simulated column read failure");
					}
					return super.queryForList(sql, elementType, args);
				}

			};
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			MariaDBVectorStore store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();
			EmbeddedDocument wrongSize = new EmbeddedDocument(
					new Document(UUID.randomUUID().toString(), "wrong size", Map.of()), new float[] { 1f, 0f, 0f });

			// The size cannot be read, so the store writes without checking it and
			// MariaDB rejects the wrong-sized vector instead
			failColumnReads.set(true);
			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "during outage", Map.of("tag", "o"))));
			assertThatThrownBy(() -> store.upsert(List.of(wrongSize))).hasMessageContaining("Incorrect vector value");

			// Once the table can be read again its size is read and applies
			failColumnReads.set(false);
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
				.dimensions(EMBEDDING_DIMENSIONS)
				.maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
				.initializeSchema(true)
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
			properties.setUrl(mariadbContainer.getJdbcUrl());
			properties.setUsername(mariadbContainer.getUsername());
			properties.setPassword(mariadbContainer.getPassword());
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
