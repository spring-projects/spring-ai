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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_CHAT_URL", matches = ".+")
public class HuggingfaceChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(HuggingfaceChatAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.huggingface.chat.api-key=" + System.getenv("HUGGINGFACE_API_KEY"),
			"spring.ai.huggingface.chat.url=" + System.getenv("HUGGINGFACE_CHAT_URL"))
			// @formatter:on
		.withConfiguration(AutoConfigurations.of(HuggingfaceChatAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.run(context -> {
			HuggingfaceChatModel chatModel = context.getBean(HuggingfaceChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
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
