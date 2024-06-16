/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.pgvector;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Muthukumaran Navaneethakrishnan
 */
@Testcontainers
public class PgVectorStoreAutoConfigurationCustomNamesIT {

	@Container
	static GenericContainer<?> postgresContainer = new GenericContainer<>("pgvector/pgvector:0.7.2-pg16")
		.withEnv("POSTGRES_USER", "postgres")
		.withEnv("POSTGRES_PASSWORD", "postgres")
		.withExposedPorts(5432);

	private static boolean isTableExists(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '" + tableName + "')",
				Boolean.class);
	}

	private static boolean isIndexExists(ApplicationContext context, String indexName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		// Query to check if the specified index exists in the database
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = '" + indexName + "')", Boolean.class);
	}

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PgVectorStoreAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",
				// JdbcTemplate configuration
				String.format("spring.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"spring.datasource.username=postgres", "spring.datasource.password=postgres");

	@ParameterizedTest(name = "{index}: Test with tableName={0}, indexName={1} - Expecting table={2}, index={3}")
	@CsvSource({
			// Passing null for both tableName and indexName
			",,vector_store,spring_ai_vector_index",
			// Passing valid tableName and null for indexName
			"customvectortable_no_index,,customvectortable_no_index,customvectortable_no_index_index",
			// Passing valid values for both tableName and indexName
			"customvectortable,customvectortableindex,customvectortable,customvectortableindex"

	})
	public void testCustomTableNameAndIndexCreation(String tableName, String indexName, String expectedTable,
			String expectedIndex) {

		List<String> items = new ArrayList<>();

		if (null != tableName) {

			items.add("spring.ai.vectorstore.pgvector.vectorTableName=" + tableName);
		}
		if (null != indexName) {
			items.add("spring.ai.vectorstore.pgvector.vectorIndexName=" + indexName);
		}

		contextRunner.withPropertyValues(items.toArray(new String[0])).run(context -> {

			boolean tableExists = isTableExists(context, expectedTable);
			boolean indexExists = isIndexExists(context, expectedIndex);

			System.out.println("Table '" + expectedTable + "' exists: " + tableExists);
			System.out.println("Index '" + expectedIndex + "' exists: " + indexExists);

			assertThat(tableExists).withFailMessage("Expected table '" + expectedTable + "' to exist.").isTrue();
			assertThat(indexExists).withFailMessage("Expected index '" + expectedIndex + "' to exist.").isTrue();

			// Clean up by dropping the created table
			dropTableByName(context, expectedTable);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
