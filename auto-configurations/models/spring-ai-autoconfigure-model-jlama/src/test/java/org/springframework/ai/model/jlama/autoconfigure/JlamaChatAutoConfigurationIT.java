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

package org.springframework.ai.model.jlama.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.jlama.JlamaChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JlamaChatAutoConfiguration}. These tests require actual
 * Jlama model files and Vector API support, so they are disabled by default and only run
 * when specific environment variables are set.
 *
 * <p>
 * To run these tests:
 * <ul>
 * <li>Set JLAMA_MODEL_PATH to a valid model path or HuggingFace model ID (e.g.,
 * "tjake/TinyLLama-1.1B-Chat-v1.0-Jlama-Q4")</li>
 * <li>Optionally set JLAMA_WORKING_DIR for model downloads</li>
 * <li>Run with --add-modules jdk.incubator.vector</li>
 * </ul>
 *
 * @author chabinhwang
 */
@EnabledIfEnvironmentVariable(named = "JLAMA_MODEL_PATH", matches = ".+")
class JlamaChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JlamaChatAutoConfiguration.class));

	@Test
	void contextLoads() {
		String modelPath = System.getenv("JLAMA_MODEL_PATH");
		String workingDir = System.getenv("JLAMA_WORKING_DIR");

		ApplicationContextRunner runner = this.contextRunner
			.withPropertyValues("spring.ai.jlama.chat.model=" + modelPath);

		if (workingDir != null && !workingDir.isEmpty()) {
			runner = runner.withPropertyValues("spring.ai.jlama.chat.working-directory=" + workingDir);
		}

		runner.run(context -> {
			assertThat(context).hasSingleBean(JlamaChatModel.class);
			JlamaChatModel chatModel = context.getBean(JlamaChatModel.class);
			assertThat(chatModel).isNotNull();
			assertThat(chatModel.getDefaultOptions()).isNotNull();
		});
	}

	@Test
	void chatModelCallWithRealModel() {
		String modelPath = System.getenv("JLAMA_MODEL_PATH");
		String workingDir = System.getenv("JLAMA_WORKING_DIR");

		ApplicationContextRunner runner = this.contextRunner
			.withPropertyValues("spring.ai.jlama.chat.model=" + modelPath);

		if (workingDir != null && !workingDir.isEmpty()) {
			runner = runner.withPropertyValues("spring.ai.jlama.chat.working-directory=" + workingDir);
		}

		runner.run(context -> {
			JlamaChatModel chatModel = context.getBean(JlamaChatModel.class);

			var response = chatModel.call(new Prompt("Say 'Hello World' and nothing else"));

			assertThat(response).isNotNull();
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResults().get(0).getOutput().getText()).isNotEmpty();
		});
	}

	@Test
	void chatModelStreamWithRealModel() {
		String modelPath = System.getenv("JLAMA_MODEL_PATH");
		String workingDir = System.getenv("JLAMA_WORKING_DIR");

		ApplicationContextRunner runner = this.contextRunner
			.withPropertyValues("spring.ai.jlama.chat.model=" + modelPath);

		if (workingDir != null && !workingDir.isEmpty()) {
			runner = runner.withPropertyValues("spring.ai.jlama.chat.working-directory=" + workingDir);
		}

		runner.run(context -> {
			JlamaChatModel chatModel = context.getBean(JlamaChatModel.class);

			Flux<String> responseStream = chatModel.stream(new Prompt("Say 'Hello World' and nothing else"))
				.map(response -> response.getResults().get(0).getOutput().getText());

			var responses = responseStream.collectList().block();

			assertThat(responses).isNotNull();
			assertThat(responses).isNotEmpty();
			assertThat(String.join("", responses)).isNotEmpty();
		});
	}

}
