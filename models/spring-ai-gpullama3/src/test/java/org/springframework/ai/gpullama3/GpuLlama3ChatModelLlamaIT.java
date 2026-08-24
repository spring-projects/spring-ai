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

package org.springframework.ai.gpullama3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

class GpuLlama3ChatModelLlamaIT {

	private static final String MODEL_PATH_PROPERTY = "gpullama3.llama.model";

	private static final String CONTEXT_LENGTH_PROPERTY = "gpullama3.llama.context-length";

	private static final String MAX_TOKENS_PROPERTY = "gpullama3.llama.max-tokens";

	private static final String PROMPT_PROPERTY = "gpullama3.llama.prompt";

	private static final String DEFAULT_PROMPT = "Briefly describe the University of Manchester.";

	@Test
	void generatesTextForConfiguredPromptWithLlamaModel() {
		String modelPathProperty = System.getProperty(MODEL_PATH_PROPERTY);
		Assumptions.assumeTrue(modelPathProperty != null && !modelPathProperty.isBlank(),
				() -> "Set -D" + MODEL_PATH_PROPERTY + "=/path/to/llama.gguf to run this smoke test");
		Path modelPath = Path.of(modelPathProperty);
		Assumptions.assumeTrue(Files.isRegularFile(modelPath), () -> "Llama model file does not exist: " + modelPath);
		String prompt = System.getProperty(PROMPT_PROPERTY, DEFAULT_PROMPT);

		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(modelPath)
			.model("llama-3.2-1b-instruct")
			.onGpu(false)
			.contextLength(Integer.getInteger(CONTEXT_LENGTH_PROPERTY, 1024))
			.maxTokens(Integer.getInteger(MAX_TOKENS_PROPERTY, 512))
			.temperature(0.0)
			.topP(1.0)
			.seed(7L)
			.build();

		try (GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(options)) {
			ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
			Generation generation = Objects.requireNonNull(response.getResult(), "response result must not be null");
			String answer = generation.getOutput().getText();

			System.out.println("GPULlama3 Llama prompt: " + prompt);
			System.out.println("GPULlama3 Llama answer: " + answer);

			assertThat(answer).isNotBlank();
			assertThat(response.getMetadata().getModel()).isEqualTo("llama-3.2-1b-instruct");
			assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
			assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		}
	}

}
