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

package org.springframework.ai.autoconfigure.vertexai.palm2;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vertexai.palm2.VertexAiPaLm2ChatClient;
import org.springframework.ai.vertexai.palm2.VertexAiPaLm2EmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

// NOTE: works only with US location. Use VPN if you are outside US.
@EnabledIfEnvironmentVariable(named = "PALM_API_KEY", matches = ".*")
public class VertexAiPaLm2AutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(VertexAiPaLm2AutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.vertex.ai.baseUrl=https://generativelanguage.googleapis.com/v1beta3",
				"spring.ai.vertex.ai.apiKey=" + System.getenv("PALM_API_KEY"),
				"spring.ai.vertex.ai.chat.model=chat-bison-001", "spring.ai.vertex.ai.chat.options.temperature=0.8",
				"spring.ai.vertex.ai.embedding.model=embedding-gecko-001")
		.withConfiguration(
				AutoConfigurations.of(RestClientAutoConfiguration.class, VertexAiPalm2AutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			VertexAiPaLm2ChatClient client = context.getBean(VertexAiPaLm2ChatClient.class);

			String response = client.call("Hello");

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			VertexAiPaLm2EmbeddingClient embeddingClient = context.getBean(VertexAiPaLm2EmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(768);
		});
	}

	@Test
	public void embeddingActivation() {

		// Disable the embedding auto-configuration.
		contextRunner.withPropertyValues("spring.ai.vertex.ai.embedding.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(VertexAiPalm2EmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2EmbeddingClient.class)).isEmpty();
		});

		// The embedding auto-configuration is enabled by default.
		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(VertexAiPalm2EmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2EmbeddingClient.class)).isNotEmpty();
		});

		// Explicitly enable the embedding auto-configuration.
		contextRunner.withPropertyValues("spring.ai.vertex.ai.embedding.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(VertexAiPalm2EmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2EmbeddingClient.class)).isNotEmpty();
		});
	}

	@Test
	public void chatActivation() {

		// Disable the chat auto-configuration.
		contextRunner.withPropertyValues("spring.ai.vertex.ai.chat.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(VertexAiPlam2ChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2ChatClient.class)).isEmpty();
		});

		// The chat auto-configuration is enabled by default.
		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(VertexAiPlam2ChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2ChatClient.class)).isNotEmpty();
		});

		// Explicitly enable the chat auto-configuration.
		contextRunner.withPropertyValues("spring.ai.vertex.ai.chat.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(VertexAiPlam2ChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiPaLm2ChatClient.class)).isNotEmpty();
		});
	}

}
