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
package org.springframework.ai.ollama;

import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApiIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Disabled("For manual smoke testing only.")
@Testcontainers
class OllamaEmbeddingClientIT {

	private static final Log logger = LogFactory.getLog(OllamaApiIT.class);

	@Container
	static GenericContainer<?> ollamaContainer = new GenericContainer<>("ollama/ollama:0.1.23").withExposedPorts(11434);

	static String baseUrl;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the 'orca-mini' generative (3GB) ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", "orca-mini");
		logger.info("orca-mini pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	@Autowired
	private OllamaEmbeddingClient embeddingClient;

	@Test
	void singleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(3200);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return new OllamaApi(baseUrl);
		}

		@Bean
		public OllamaEmbeddingClient ollamaEmbedding(OllamaApi ollamaApi) {
			return new OllamaEmbeddingClient(ollamaApi).withModel("orca-mini");
		}

	}

}