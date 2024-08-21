/*
 * Copyright 2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.OracleVectorStore.OracleVectorStoreDistanceType;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import oracle.jdbc.pool.OracleDataSource;

/**
 * @author Christian Tzolov
 */
@Testcontainers
public class OracleVectorStoreObservationIT {

	@Container
	static OracleContainer oracle23aiContainer = new OracleContainer("gvenzl/oracle-free:23-slim")
		.withCopyFileToContainer(MountableFile.forClasspathResource("/initialize.sql"),
				"/container-entrypoint-initdb.d/initialize.sql");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class)
		.withPropertyValues("test.spring.ai.vectorstore.oracle.dimensions=384",
				// JdbcTemplate configuration
				String.format("app.datasource.url=%s", oracle23aiContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", oracle23aiContainer.getUsername()),
				String.format("app.datasource.password=%s", oracle23aiContainer.getPassword()),
				"app.datasource.type=oracle.jdbc.pool.OracleDataSource");

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

	private static void dropTable(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName + " PURGE");
	}

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store oracle add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.ORACLE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(),
						OracleVectorStore.DEFAULT_TABLE_NAME)
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_THRESHOLD.asString(), "none")

				.hasBeenStarted()
				.hasBeenStopped();

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

			assertThat(results).isNotEmpty();

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store oracle query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.ORACLE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(),
						OracleVectorStore.DEFAULT_TABLE_NAME)
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_THRESHOLD.asString(), "0.0")

				.hasBeenStarted()
				.hasBeenStopped();

			dropTable(context, ((OracleVectorStore) vectorStore).getTableName());

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
				ObservationRegistry observationRegistry) {
			return new OracleVectorStore(jdbcTemplate, embeddingModel, OracleVectorStore.DEFAULT_TABLE_NAME,
					OracleVectorStore.OracleVectorStoreIndexType.IVF, OracleVectorStoreDistanceType.COSINE, 384,
					OracleVectorStore.DEFAULT_SEARCH_ACCURACY, true, true, true, observationRegistry, null);
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
		public OracleDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(OracleDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
