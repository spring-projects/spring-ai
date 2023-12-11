/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.vectorsore.ChromaVectorStore;
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
 */
@Testcontainers
public class BasicAuthChromaWhereIT {

	/**
	 * ChromaDB with Basic Authentication:
	 * https://docs.trychroma.com/usage-guide#basic-authentication
	 */
	@Container
	static GenericContainer<?> chromaContainer = new GenericContainer<>("ghcr.io/chroma-core/chroma:0.4.15")
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS_FILE", "server.htpasswd")
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS_PROVIDER",
				"chromadb.auth.providers.HtpasswdFileServerAuthCredentialsProvider")
		.withEnv("CHROMA_SERVER_AUTH_PROVIDER", "chromadb.auth.basic.BasicAuthServerProvider")
		.withCopyToContainer(Transferable.of("src/test/resources/server.htpasswd"), "server.htpasswd")
		.withExposedPorts(8000);

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
			String host = chromaContainer.getHost();
			int port = chromaContainer.getMappedPort(8000);
			String baseUrl = "http://%s:%d".formatted(host, port);
			return new ChromaApi(baseUrl, restTemplate).withBasicAuthCredentials("admin", "admin");
		}

		@Bean
		public VectorStore chromaVectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi) {
			return new ChromaVectorStore(embeddingClient, chromaApi, "TestCollection");
		}

		@Bean
		public EmbeddingClient embeddingClient() {

			Retrofit retrofit = new Retrofit.Builder().baseUrl("https://api.openai.com")
				.client(OpenAiService.defaultClient(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(60)))
				.addConverterFactory(JacksonConverterFactory.create(OpenAiService.defaultObjectMapper()))
				.addCallAdapterFactory(RxJava2CallAdapterFactory.create())
				.build();

			OpenAiApi api = retrofit.create(OpenAiApi.class);

			return new OpenAiEmbeddingClient(new OpenAiService(api), "text-embedding-ada-002");
		}

	}

}
