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

package org.springframework.ai.vectorstore.hanadb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_PASSWORD", matches = ".+")
public class HanaVectorStoreObservationIT {

	private static final String TEST_TABLE_NAME = "CRICKET_WORLD_CUP";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

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

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("%s add".formatted(VectorStoreProvider.HANA.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.HANA.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), TEST_TABLE_NAME)
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString(),
						VectorStoreSimilarityMetric.COSINE.value())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(
						HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString())

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
				.hasContextualNameEqualTo("%s query".formatted(VectorStoreProvider.HANA.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.HANA.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(),
						"What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), TEST_TABLE_NAME)
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString(),
						VectorStoreSimilarityMetric.COSINE.value())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
						"0.0")

				.hasBeenStarted()
				.hasBeenStopped();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public VectorStore hanaCloudVectorStore(CricketWorldCupRepository cricketWorldCupRepository,
				EmbeddingModel embeddingModel, ObservationRegistry observationRegistry) {

			return HanaCloudVectorStore.builder(cricketWorldCupRepository, embeddingModel)
				.tableName(TEST_TABLE_NAME)
				.topK(1)
				.observationRegistry(observationRegistry)
				.customObservationConvention(null)
				.build();
		}

		@Bean
		public CricketWorldCupRepository cricketWorldCupRepository() {
			return new CricketWorldCupRepository();
		}

		@Bean
		public DataSource dataSource() {
			DriverManagerDataSource dataSource = new DriverManagerDataSource();

			dataSource.setDriverClassName("com.sap.db.jdbc.Driver");
			dataSource.setUrl(System.getenv("HANA_DATASOURCE_URL"));
			dataSource.setUsername(System.getenv("HANA_DATASOURCE_USERNAME"));
			dataSource.setPassword(System.getenv("HANA_DATASOURCE_PASSWORD"));

			return dataSource;
		}

		@Bean
		public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
			LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
			em.setDataSource(dataSource());
			em.setPackagesToScan("org.springframework.ai.vectorstore");

			JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
			em.setJpaVendorAdapter(vendorAdapter);

			return em;
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
