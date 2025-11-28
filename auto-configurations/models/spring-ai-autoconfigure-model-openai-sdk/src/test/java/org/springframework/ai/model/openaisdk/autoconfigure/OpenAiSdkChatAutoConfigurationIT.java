/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.openaisdk.autoconfigure;

import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiSdkChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiSdkChatAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai-sdk.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void chatCall() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkChatModel chatModel = context.getBean(OpenAiSdkChatModel.class);
				String response = chatModel.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkChatModel chatModel = context.getBean(OpenAiSdkChatModel.class);
				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
				String response = responseFlux.collectList()
					.block()
					.stream()
					.map(chatResponse -> chatResponse.getResult() != null
							? chatResponse.getResult().getOutput().getText() : "")
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void streamingWithTokenUsage() {
		this.contextRunner.withPropertyValues("spring.ai.openai-sdk.chat.options.stream-usage=true")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkChatModel chatModel = context.getBean(OpenAiSdkChatModel.class);

				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));

				Usage[] streamingTokenUsage = new Usage[1];
				String response = responseFlux.collectList().block().stream().map(chatResponse -> {
					streamingTokenUsage[0] = chatResponse.getMetadata().getUsage();
					return (chatResponse.getResult() != null) ? chatResponse.getResult().getOutput().getText() : "";
				}).collect(Collectors.joining());

				assertThat(streamingTokenUsage[0].getPromptTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getCompletionTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getTotalTokens()).isGreaterThan(0);

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void chatActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY", "spring.ai.openai-sdk.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkChatModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://test.base.url")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkChatModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://test.base.url", "spring.ai.model.chat=openai-sdk")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkChatModel.class)).isNotEmpty();
			});

	}

}
