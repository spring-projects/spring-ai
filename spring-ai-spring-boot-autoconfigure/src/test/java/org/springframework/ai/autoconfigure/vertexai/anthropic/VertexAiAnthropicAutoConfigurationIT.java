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
package org.springframework.ai.autoconfigure.vertexai.anthropic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_LOCATION", matches = ".*")
public class VertexAiAnthropicAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(VertexAiAnthropicAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues(
				"spring.ai.vertex.ai.anthropic.project-id=" + System.getenv("VERTEX_AI_ANTHROPIC_PROJECT_ID"),
				"spring.ai.vertex.ai.anthropic.location=" + System.getenv("VERTEX_AI_ANTHROPIC_LOCATION"))
		.withConfiguration(AutoConfigurations.of(VertexAiAnthropicAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			VertexAiAnthropicChatModel chatModel = context.getBean(VertexAiAnthropicChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateStreaming() {
		contextRunner.run(context -> {
			VertexAiAnthropicChatModel chatModel = context.getBean(VertexAiAnthropicChatModel.class);
			Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList().block().stream().map(chatResponse -> {
				return chatResponse.getResults().get(0).getOutput().getContent();
			}).collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}
