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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.vectorsore.ChromaVectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

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
 */
@Testcontainers
public class BasicAuthChromaWhereIT {

	/**
	 * ChromaDB with Basic Authentication:
	 * https://docs.trychroma.com/usage-guide#basic-authentication
	 */
	@Container
	static ChromaDBContainer chromaContainer = new ChromaDBContainer("ghcr.io/chroma-core/chroma:0.4.22")
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS_FILE", "server.htpasswd")
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS_PROVIDER",
				"chromadb.auth.providers.HtpasswdFileServerAuthCredentialsProvider")
		.withEnv("CHROMA_SERVER_AUTH_PROVIDER", "chromadb.auth.basic.BasicAuthServerProvider")
		.withCopyToContainer(Transferable.of("src/test/resources/server.htpasswd"), "server.htpasswd");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	public void withInFiltersExpressions1() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(List.of(new Document("1", "Article by john", Map.of("author", "john")),
					new Document("2", "Article by Jack", Map.of("author", "jack")),
					new Document("3", "Article by Jill", Map.of("author", "jill"))));

			String query = "Give me articles by john";

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query(query).withTopK(5));
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.query(query)
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("author in ['john', 'jill']"));

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(d -> d.getId()).toList()).containsExactlyInAnyOrder("1", "3");
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		public ChromaApi chromaApi(RestTemplate restTemplate) {
			return new ChromaApi(chromaContainer.getEndpoint(), restTemplate).withBasicAuthCredentials("admin",
					"admin");
		}

		@Bean
		public VectorStore chromaVectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi) {
			return new ChromaVectorStore(embeddingClient, chromaApi, "TestCollection");
		}

		@Bean
		public EmbeddingClient embeddingClient() {
			return new OpenAiEmbeddingClient(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
