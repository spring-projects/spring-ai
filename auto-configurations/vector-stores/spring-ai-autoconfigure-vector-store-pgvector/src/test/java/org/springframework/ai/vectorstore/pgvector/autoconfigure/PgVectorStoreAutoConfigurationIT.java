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

package org.springframework.ai.vectorstore.pgvector.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Muthukumaran Navaneethakrishnan
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
public class PgVectorStoreAutoConfigurationIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PgVectorStoreAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",
				"spring.ai.vectorstore.pgvector.initialize-schema=true",
				// JdbcTemplate configuration
				String.format("spring.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), postgresContainer.getDatabaseName()),
				"spring.datasource.username=" + postgresContainer.getUsername(),
				"spring.datasource.password=" + postgresContainer.getPassword());

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean isFullyQualifiedTableExists(ApplicationContext context, String schemaName,
			String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
	}

	@Test
	public void addAndSearch() {

		this.contextRunner.run(context -> {

			PgVectorStore vectorStore = context.getBean(PgVectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			assertThat(isFullyQualifiedTableExists(context, PgVectorStore.DEFAULT_SCHEMA_NAME,
					PgVectorStore.DEFAULT_TABLE_NAME))
				.isTrue();

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.PG_VECTOR,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("What is Great Depression?").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getMetadata()).containsKeys("depression", "distance");

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.PG_VECTOR,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.PG_VECTOR,
					VectorStoreObservationContext.Operation.DELETE);

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build());
			assertThat(results).hasSize(0);
			observationRegistry.clear();
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "public:vector_store", "my_schema:my_table" })
	public void customSchemaNames(String schemaTableName) {
		String schemaName = schemaTableName.split(":")[0];
		String tableName = schemaTableName.split(":")[1];

		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.pgvector.schema-name=" + schemaName,
					"spring.ai.vectorstore.pgvector.table-name=" + tableName)
			.run(context -> assertThat(isFullyQualifiedTableExists(context, schemaName, tableName)).isTrue());
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "public:vector_store", "my_schema:my_table" })
	public void disableSchemaInitialization(String schemaTableName) {
		String schemaName = schemaTableName.split(":")[0];
		String tableName = schemaTableName.split(":")[1];

		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.pgvector.schema-name=" + schemaName,
					"spring.ai.vectorstore.pgvector.table-name=" + tableName,
					"spring.ai.vectorstore.pgvector.initialize-schema=false")
			.run(context -> assertThat(isFullyQualifiedTableExists(context, schemaName, tableName)).isFalse());
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(PgVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(PgVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(PgVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(PgVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsPgvector() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=pgvector").run(context -> {
			assertThat(context.getBeansOfType(PgVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(PgVectorStore.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
