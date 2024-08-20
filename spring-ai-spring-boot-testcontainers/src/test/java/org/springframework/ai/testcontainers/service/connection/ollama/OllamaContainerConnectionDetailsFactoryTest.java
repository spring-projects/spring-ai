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
package org.springframework.ai.testcontainers.service.connection.ollama;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 */
@SpringJUnitConfig
@Disabled("requires more memory than is often available on dev machines")
@Testcontainers
@TestPropertySource(properties = "spring.ai.ollama.embedding.options.model="
		+ OllamaContainerConnectionDetailsFactoryTest.MODEL_NAME)
class OllamaContainerConnectionDetailsFactoryTest {

	private static final Log logger = LogFactory.getLog(OllamaContainerConnectionDetailsFactoryTest.class);

	static final String MODEL_NAME = "orca-mini";

	@Container
	@ServiceConnection
	static OllamaContainer ollama = new OllamaContainer("ollama/ollama:0.3.6");

	@Autowired
	private OllamaEmbeddingModel embeddingModel;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL_NAME + " ' generative ... would take several minutes ...");
		ollama.execInContainer("ollama", "pull", MODEL_NAME);
		logger.info(MODEL_NAME + " pulling competed!");
	}

	@Test
	public void singleTextEmbedding() {
		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(3200);
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ RestClientAutoConfiguration.class, OllamaAutoConfiguration.class })
	static class Config {

	}

}