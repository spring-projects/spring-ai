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

package org.springframework.ai.model.ollama.autoconfigure;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @since 0.8.0
 */
public class OllamaChatAutoConfigurationIT extends BaseOllamaIT {

	private static final String MODEL_NAME = OllamaModel.QWEN_2_5_3B.getName();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.ollama.baseUrl=" + getBaseUrl(),
				"spring.ai.ollama.chat.options.model=" + MODEL_NAME,
				"spring.ai.ollama.chat.options.temperature=0.5",
				"spring.ai.ollama.chat.options.topK=10")
				// @formatter:on
		.withConfiguration(ollamaAutoConfig(OllamaChatAutoConfiguration.class));

	private final UserMessage userMessage = new UserMessage("What's the capital of Denmark?");

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		initializeOllama(MODEL_NAME);
	}

	@Test
	public void chatCompletion() {
		this.contextRunner.run(context -> {
			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);
			ChatResponse response = chatModel.call(new Prompt(this.userMessage));
			assertThat(response.getResult().getOutput().getText()).contains("Copenhagen");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		this.contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			Flux<ChatResponse> response = chatModel.stream(new Prompt(this.userMessage));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(1);

			String stitchedResponseContent = responses.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());

			assertThat(stitchedResponseContent).contains("Copenhagen");
		});
	}

	@Test
	public void chatCompletionWithPull() {
		this.contextRunner.withPropertyValues("spring.ai.ollama.init.pull-model-strategy=when_missing")
			.withPropertyValues("spring.ai.ollama.chat.options.model=tinyllama")
			.run(context -> {
				var model = "tinyllama";
				OllamaApi ollamaApi = context.getBean(OllamaApi.class);
				var modelManager = new OllamaModelManager(ollamaApi);
				assertThat(modelManager.isModelAvailable(model)).isTrue();

				OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);
				ChatResponse response = chatModel.call(new Prompt(this.userMessage));
				assertThat(response.getResult().getOutput().getText()).contains("Copenhagen");
				modelManager.deleteModel(model);
			});
	}

	@Test
	void chatActivation() {
		this.contextRunner.withPropertyValues("spring.ai.model.chat=none").run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(OllamaChatModel.class)).isEmpty();
		});

		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatModel.class)).isNotEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.chat=ollama").run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatModel.class)).isNotEmpty();
		});
	}

	@Test
	void chatCompletionWithCustomTimeout() {
		this.contextRunner.withPropertyValues("spring.ai.ollama.read-timeout=1ms")
			.withPropertyValues("spring.ai.ollama.connect-timeout=1ms")
			.run(context -> {
				OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);
				ChatResponse response = chatModel.call(new Prompt(this.userMessage));
				assertThat(response.getResult().getOutput().getText()).contains("Copenhagen");
			});
	}

}
