/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AnthropicChatAutoConfiguration}.
 *
 * @author Soby Chacko
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatAutoConfigurationIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.anthropic.api-key=" + System.getenv("ANTHROPIC_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void call() {
		this.contextRunner.run(context -> {
			AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: {}", response);
		});
	}

	@Test
	void callWithOptions() {
		this.contextRunner.run(context -> {
			AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
			var options = AnthropicChatOptions.builder().maxTokens(100).build();
			var response = chatModel.call(new Prompt("Tell me a joke", options));
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			logger.info("Response: {}", response);
		});
	}

	@Test
	void stream() {
		this.contextRunner.run(context -> {
			AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
			Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));

			String response = responseFlux.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: {}", response);
		});
	}

}
