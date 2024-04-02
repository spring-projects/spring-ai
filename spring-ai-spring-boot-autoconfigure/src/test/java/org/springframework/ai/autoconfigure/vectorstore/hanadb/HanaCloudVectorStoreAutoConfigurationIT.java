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
package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HANA_DATASOURCE_PASSWORD", matches = ".+")
@Disabled
public class HanaCloudVectorStoreAutoConfigurationIT {

	@Test
	public void addAndSearch() {
		contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(documents);

			List<Document> results = vectorStore.similaritySearch("What is Great Depression?");
			Assertions.assertEquals(1, results.size());

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).toList());
			List<Document> results2 = vectorStore.similaritySearch("Great Depression");
			Assertions.assertEquals(0, results2.size());
		});
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(HanaCloudVectorStoreAutoConfiguration.class,
				OpenAiAutoConfiguration.class, RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
				JdbcRepositoriesAutoConfiguration.class))
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.embedding.options.model=text-embedding-ada-002",
				"spring.datasource.url=" + System.getenv("HANA_DATASOURCE_URL"),
				"spring.datasource.username=" + System.getenv("HANA_DATASOURCE_USERNAME"),
				"spring.datasource.password=" + System.getenv("HANA_DATASOURCE_PASSWORD"),
				"spring.ai.vectorstore.hanadb.tableName=CRICKET_WORLD_CUP", "spring.ai.vectorstore.hanadb.topK=1");

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!"),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression"));

}
