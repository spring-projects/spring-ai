/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.deepseek.autoconfigure;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Hyunsang Han
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".*")
public class DeepSeekAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(DeepSeekAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.deepseek.apiKey=" + System.getenv("DEEPSEEK_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.run(context -> {
			DeepSeekChatModel client = context.getBean(DeepSeekChatModel.class);
			String response = client.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.run(context -> {
			DeepSeekChatModel client = context.getBean(DeepSeekChatModel.class);
			Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
			String response = Objects.requireNonNull(responseFlux.collectList().block())
				.stream()
				.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
				.collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateWithCustomTimeout() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.apiKey=" + System.getenv("DEEPSEEK_API_KEY"),
					"spring.ai.deepseek.connect-timeout=5s", "spring.ai.deepseek.read-timeout=30s")
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				DeepSeekChatModel client = context.getBean(DeepSeekChatModel.class);

				// Verify that the HTTP client configuration is applied
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));

				// Verify that the client can actually make requests with the configured
				// timeout
				String response = client.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response with custom timeout: " + response);
			});
	}

	@Test
	void generateStreamingWithCustomTimeout() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.apiKey=" + System.getenv("DEEPSEEK_API_KEY"),
					"spring.ai.deepseek.connect-timeout=1s", "spring.ai.deepseek.read-timeout=1s")
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				DeepSeekChatModel client = context.getBean(DeepSeekChatModel.class);

				// Verify that the HTTP client configuration is applied
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(1));

				Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
				String response = Objects.requireNonNull(responseFlux.collectList().block())
					.stream()
					.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response with custom timeout: " + response);
			});
	}

}
