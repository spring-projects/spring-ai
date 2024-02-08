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

package org.springframework.ai.autoconfigure.ollama;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
@Disabled("For manual smoke testing only.")
@Testcontainers
public class OllamaEmbeddingAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OllamaEmbeddingAutoConfigurationIT.class);

	private static String MODEL_NAME = "orca-mini";

	@Container
	static GenericContainer<?> ollamaContainer = new GenericContainer<>("ollama/ollama:0.1.23").withExposedPorts(11434);

	static String baseUrl;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL_NAME + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL_NAME);
		logger.info(MODEL_NAME + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.ollama.embedding.options.model=" + MODEL_NAME,
				"spring.ai.ollama.base-url=" + baseUrl)
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, OllamaAutoConfiguration.class));

	@Test
	public void singleTextEmbedding() {
		contextRunner.run(context -> {
			OllamaEmbeddingClient embeddingClient = context.getBean(OllamaEmbeddingClient.class);
			assertThat(embeddingClient).isNotNull();
			EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingClient.dimensions()).isEqualTo(3200);
		});
	}

}
