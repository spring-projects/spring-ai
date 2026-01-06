/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.anthropic;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VertexAiAnthropicChatAutoConfiguration}.
 */
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_LOCATION", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_CHAT_MODEL", matches = ".*")
public class VertexAiAnthropicAutoConfigurationIT {

	private static final Log LOG = LogFactory.getLog(VertexAiAnthropicAutoConfigurationIT.class);

	ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues(
				"spring.ai.vertex.ai.anthropic.project-id=%s"
					.formatted(System.getenv("VERTEX_AI_ANTHROPIC_PROJECT_ID")),
				"spring.ai.vertex.ai.anthropic.location=%s".formatted(System.getenv("VERTEX_AI_ANTHROPIC_LOCATION")),
				"spring.ai.vertex.ai.anthropic.chat.options.model=%s"
					.formatted(System.getenv("VERTEX_AI_ANTHROPIC_CHAT_MODEL")))
		.withConfiguration(SpringAiTestAutoConfigurations.of(VertexAiAnthropicChatAutoConfiguration.class));

	/**
	 * Tests that the Anthropic chat model is properly configured and can generate
	 * responses.
	 */
	@Test
	void testChatModelConfiguration() {
		this.contextRunner.run(context -> {
			var chatModel = context.getBean(AnthropicChatModel.class);
			String response = chatModel.call("hi!");
			assertThat(response).isNotEmpty();
			LOG.info("Response: %s".formatted(response));
		});
	}

	/**
	 * Tests chat model functionality with tool calling enabled.
	 */
	@Test
	void testChatModelWithToolCalling() {
		this.contextRunner.run(context -> {
			var chatModel = context.getBean(AnthropicChatModel.class);
			ToolCallback tool = new ToolCallback() {
				@Override
				public ToolDefinition getToolDefinition() {
					return ToolDefinition.builder()
						.name("get_weather")
						.description("Get current weather for a location")
						.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
						.build();
				}

				@Override
				public String call(String toolInput) {
					return "Sunny, 25°C";
				}
			};
			var options = AnthropicChatOptions.builder().toolCallbacks(List.of(tool)).build();
			var prompt = new Prompt("What's the weather in Paris?", options);
			var response = chatModel.call(prompt);
			assertThat(response).isNotNull();
			assertThat(response.getResult().getOutput().getText()).contains("25");
			LOG.info("Tool calling response: %s".formatted(response.getResult().getOutput().getText()));
		});
	}

}
