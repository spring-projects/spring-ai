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
package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_PASSWORD", matches = ".+")
public class HanaCloudVectorStoreIT {

	private static final Logger logger = LoggerFactory.getLogger(HanaCloudVectorStoreIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(HanaTestApplication.class);

	@Test
	public void vectorStoreTest() {
		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(HanaCloudVectorStore.class);
			int deleteCount = ((HanaCloudVectorStore) vectorStore).purgeEmbeddings();
			logger.info("Purged all embeddings: count={}", deleteCount);

			Supplier<List<Document>> reader = new PagePdfDocumentReader("classpath:Cricket_World_Cup.pdf");
			Function<List<Document>, List<Document>> splitter = new TokenTextSplitter();
			List<Document> documents = splitter.apply(reader.get());
			vectorStore.accept(documents);

			List<Document> results = vectorStore.similaritySearch("Who won the 2023 cricket world cup finals?");
			Assertions.assertEquals(1, results.size());
			Assertions.assertTrue(results.get(0).getContent().contains("Australia"));

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).toList());
			List<Document> results2 = vectorStore.similaritySearch("Who won the 2023 cricket world cup finals?");
			Assertions.assertEquals(0, results2.size());
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class HanaTestApplication {

		@Bean
		public VectorStore hanaCloudVectorStore(CricketWorldCupRepository cricketWorldCupRepository,
				EmbeddingClient embeddingClient) {
			return new HanaCloudVectorStore(cricketWorldCupRepository, embeddingClient,
					HanaCloudVectorStoreConfig.builder().tableName("CRICKET_WORLD_CUP").topK(1).build());
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
		public EmbeddingClient embeddingClient() {
			return new OpenAiEmbeddingClient(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}