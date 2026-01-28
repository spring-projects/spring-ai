/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.huggingface.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.huggingface.HuggingfaceChatOptions;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HuggingFace Chat Auto Configuration.
 *
 * @author Mark Pollack
 * @author Myeongdeok Kang
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
public class HuggingfaceChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(HuggingfaceChatAutoConfigurationIT.class);

	private static final String DEFAULT_CHAT_MODEL = "meta-llama/Llama-3.2-3B-Instruct";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.huggingface.api-key=" + System.getenv("HUGGINGFACE_API_KEY"),
				"spring.ai.huggingface.chat.options.model=" + DEFAULT_CHAT_MODEL)
		.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceChatAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.run(context -> {
			HuggingfaceChatModel chatModel = context.getBean(HuggingfaceChatModel.class);
			String response = chatModel.call("Say 'Hello World' and nothing else");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void chatActivation() {
		// Default activation
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isNotEmpty();
		});

		// Disabled via property
		this.contextRunner.withPropertyValues("spring.ai.model.chat=none").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceChatProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isEmpty();
		});

		// Explicitly enabled
		this.contextRunner.withPropertyValues("spring.ai.model.chat=huggingface").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isNotEmpty();
		});
	}

	@Test
	void chatProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.huggingface.chat.options.model=" + DEFAULT_CHAT_MODEL,
					"spring.ai.huggingface.chat.options.temperature=0.8",
					"spring.ai.huggingface.chat.options.maxTokens=500")
			.run(context -> {
				var chatProperties = context.getBean(HuggingfaceChatProperties.class);
				assertThat(chatProperties.getOptions().getModel()).isEqualTo(DEFAULT_CHAT_MODEL);
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.8);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(500);
			});
	}

	@Test
	void chatCallWithOptions() {
		this.contextRunner.run(context -> {
			HuggingfaceChatModel chatModel = context.getBean(HuggingfaceChatModel.class);

			HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
				.model(DEFAULT_CHAT_MODEL)
				.temperature(0.7)
				.maxTokens(100)
				.build();

			ChatResponse response = chatModel.call(new Prompt("Say 'Hello' and nothing else", options));

			assertThat(response).isNotNull();
			assertThat(response.getResult()).isNotNull();
			assertThat(response.getResult().getOutput()).isNotNull();
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();

			logger.info("Response with options: " + response.getResult().getOutput().getText());
		});
	}

	@Disabled("Until streaming support is added")
	@Test
	void generateStreaming() {
		this.contextRunner.run(context -> {
			HuggingfaceChatModel chatModel = context.getBean(HuggingfaceChatModel.class);
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
			logger.info("Response: " + response);
		});
	}

}
