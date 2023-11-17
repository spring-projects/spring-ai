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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.ollama.client.OllamaClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("As it downloads the 3GB 'orca-mini' it can take couple of minutes to initialize.")
@Testcontainers
public class OllamaAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OllamaAutoConfigurationIT.class);

	@Container
	static GenericContainer<?> ollamaContainer = new GenericContainer<>("ollama/ollama:0.1.10").withExposedPorts(11434);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.ollama.baseUrl=http://" + ollamaContainer.getHost() + ":"
				+ ollamaContainer.getMappedPort(11434), "spring.ai.ollama.model=orca-mini")
		.withConfiguration(AutoConfigurations.of(OllamaAutoConfiguration.class));

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the 'orca-mini' model (3GB) ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", "orca-mini");
		logger.info("orca-mini pulling competed!");
	}

	@Test
	void generate() {
		contextRunner.run(context -> {
			OllamaClient client = context.getBean(OllamaClient.class);
			assertThat(client.getBaseUrl())
				.isEqualTo("http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434));
			assertThat(client.getModel()).isEqualTo("orca-mini");

			String response = client.generate("Hello");

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}
