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

package org.springframework.ai.jlama;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.jlama.api.JlamaChatOptions;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JlamaChatModel} with a real Jlama model.
 *
 * @author chabinhwang
 */
@EnabledIfEnvironmentVariable(named = "JLAMA_MODEL_PATH", matches = ".+")
class JlamaChatModelIT {

	@Test
	void callWithRealModel() throws Exception {
		assumeVectorApiAvailable();

		JlamaChatModel chatModel = createChatModel();
		try {
			var response = chatModel.call(new Prompt("Say 'Hello World' and nothing else."));

			assertThat(response).isNotNull();
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResult().getOutput().getText()).isNotBlank();
		}
		finally {
			chatModel.destroy();
		}
	}

	@Test
	void streamWithRealModel() throws Exception {
		assumeVectorApiAvailable();

		JlamaChatModel chatModel = createChatModel();
		try {
			List<String> chunks = chatModel.stream(new Prompt("Say 'Hello World' and nothing else."))
				.map(chatResponse -> chatResponse.getResult().getOutput().getText())
				.collectList()
				.block();

			assertThat(chunks).isNotNull();
			assertThat(chunks).isNotEmpty();
			assertThat(String.join("", chunks)).isNotBlank();
		}
		finally {
			chatModel.destroy();
		}
	}

	private static JlamaChatModel createChatModel() {
		String modelPath = System.getenv("JLAMA_MODEL_PATH");
		String workingDir = System.getenv("JLAMA_WORKING_DIR");
		JlamaChatOptions options = JlamaChatOptions.builder().temperature(0.7).maxTokens(128).build();

		if (StringUtils.hasText(workingDir)) {
			return new JlamaChatModel(modelPath, workingDir, options);
		}

		return new JlamaChatModel(modelPath, options);
	}

	private static void assumeVectorApiAvailable() {
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException ex) {
			Assumptions.assumeTrue(false, "Vector API not available, skipping integration test");
		}
	}

}
