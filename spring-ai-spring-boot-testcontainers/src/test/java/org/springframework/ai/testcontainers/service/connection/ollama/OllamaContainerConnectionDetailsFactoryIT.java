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

package org.springframework.ai.testcontainers.service.connection.ollama;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 * @author Thomas Vitale
 */
@SpringJUnitConfig
@Disabled("Slow on CPU. Only run manually.")
@Testcontainers
@TestPropertySource(
		properties = "spring.ai.ollama.embedding.options.model=" + OllamaContainerConnectionDetailsFactoryIT.MODEL_NAME)
class OllamaContainerConnectionDetailsFactoryIT {

	static final String MODEL_NAME = "nomic-embed-text";

	private static final Logger logger = LoggerFactory.getLogger(OllamaContainerConnectionDetailsFactoryIT.class);

	@Container
	@ServiceConnection
	static OllamaContainer ollama = new OllamaContainer(OllamaImage.DEFAULT_IMAGE);

	@Autowired
	private OllamaEmbeddingModel embeddingModel;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '{}' model. The operation can take several minutes...", MODEL_NAME);
		ollama.execInContainer("ollama", "pull", MODEL_NAME);
		logger.info("Completed pulling the '{}' model", MODEL_NAME);
	}

	@Test
	public void singleTextEmbedding() {
		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(768);
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ RestClientAutoConfiguration.class, OllamaEmbeddingAutoConfiguration.class })
	static class Config {

	}

}
