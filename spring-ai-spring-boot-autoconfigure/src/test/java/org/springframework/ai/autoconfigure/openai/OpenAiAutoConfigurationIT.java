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

package org.springframework.ai.autoconfigure.openai;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.client.OpenAiChatClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			OpenAiChatClient client = context.getBean(OpenAiChatClient.class);
			String response = client.generate("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			OpenAiEmbeddingClient embeddingClient = context.getBean(OpenAiEmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getData()).hasSize(2);
			assertThat(embeddingResponse.getData().get(0).getEmbedding()).isNotEmpty();
			assertThat(embeddingResponse.getData().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getData().get(1).getEmbedding()).isNotEmpty();
			assertThat(embeddingResponse.getData().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(1536);
		});
	}

}
