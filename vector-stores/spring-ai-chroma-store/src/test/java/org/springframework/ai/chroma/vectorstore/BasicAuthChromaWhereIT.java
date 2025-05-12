/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chroma.vectorstore;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.chroma.ChromaImage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChromaDB with Basic Authentication:
 * https://docs.trychroma.com/usage-guide#basic-authentication
 *
 * The scr/test/resource/server.htpasswd file is generated with:
 * <code>htpasswd -Bbn admin admin > server.htpasswd</code>
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Jonghoon Park
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class BasicAuthChromaWhereIT {

	/**
	 * ChromaDB with Basic Authentication:
	 * https://docs.trychroma.com/usage-guide#basic-authentication
	 */
	@Container
	static ChromaDBContainer chromaContainer = new ChromaDBContainer(ChromaImage.DEFAULT_IMAGE)
		.withEnv("CHROMA_SERVER_AUTHN_CREDENTIALS_FILE", "/chroma/server.htpasswd")
		.withEnv("CHROMA_SERVER_AUTHN_PROVIDER", "chromadb.auth.basic_authn.BasicAuthenticationServerProvider")
		.withCopyToContainer(MountableFile.forClasspathResource("server.htpasswd"), "/chroma/server.htpasswd");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	public void withInFiltersExpressions1() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(List.of(new Document("1", "Article by john", Map.of("author", "john")),
					new Document("2", "Article by Jack", Map.of("author", "jack")),
					new Document("3", "Article by Jill", Map.of("author", "jill"))));

			String query = "Give me articles by john";

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build());
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("author in ['john', 'jill']")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(d -> d.getId()).toList()).containsExactlyInAnyOrder("1", "3");
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public RestClient.Builder builder() {
			return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
		}

		@Bean
		public ChromaApi chromaApi(RestClient.Builder builder) {
			return ChromaApi.builder()
				.baseUrl(chromaContainer.getEndpoint())
				.restClientBuilder(builder)
				.build()
				.withBasicAuthCredentials("admin", "password");
		}

		@Bean
		public VectorStore chromaVectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi) {
			return ChromaVectorStore.builder(chromaApi, embeddingModel)
				.collectionName("TestCollection")
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
