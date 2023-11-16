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

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.client.OllamaClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class OllamaAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OllamaAutoConfiguration.class));

	@Test
	void defaults() {
		contextRunner.run(context -> {
			OllamaProperties properties = context.getBean(OllamaProperties.class);
			assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:11434");
			assertThat(properties.getModel()).isEqualTo("llama2");

			OllamaClient client = context.getBean(OllamaClient.class);
			assertThat(client.getBaseUrl()).isEqualTo("http://localhost:11434");
			assertThat(client.getModel()).isEqualTo("llama2");
		});
	}

	@Test
	void overrideProperties() {
		contextRunner
			.withPropertyValues("spring.ai.ollama.base-url=http://localhost:8080", "spring.ai.ollama.model=myModel")
			.run(context -> {
				OllamaProperties properties = context.getBean(OllamaProperties.class);
				assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8080");
				assertThat(properties.getModel()).isEqualTo("myModel");

				OllamaClient client = context.getBean(OllamaClient.class);
				assertThat(client.getBaseUrl()).isEqualTo("http://localhost:8080");
				assertThat(client.getModel()).isEqualTo("myModel");
			});
	}

	@Test
	void customConfig() {
		contextRunner.withUserConfiguration(CustomConfig.class).run(context -> {
			OllamaClient ollamaClient = context.getBean(OllamaClient.class);
			assertThat(ollamaClient.getBaseUrl()).isEqualTo("http://localhost:8080");
			assertThat(ollamaClient.getModel()).isEqualTo("myModel");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConfig {

		@Bean
		OllamaClient myClient() {
			return new OllamaClient("http://localhost:8080", "myModel");
		}

	}

}
