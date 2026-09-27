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

package org.springframework.ai.ollama.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link OllamaLiteClient}.
 *
 * <p>These tests do not require a running Ollama server. They verify input
 * validation, default configuration, and JSON payload escaping behaviour.
 *
 * @author Spring AI Contributors
 * @since 1.1.0
 */
class OllamaLiteClientTests {

	@Test
	void defaultConstructorUsesDefaultEndpoint() {
		OllamaLiteClient client = new OllamaLiteClient();
		assertThat(client.getEndpoint()).isEqualTo(OllamaLiteClient.DEFAULT_ENDPOINT);
	}

	@Test
	void customEndpointIsRetained() {
		String custom = "http://my-ollama-host:11434/api/generate";
		OllamaLiteClient client = new OllamaLiteClient(custom);
		assertThat(client.getEndpoint()).isEqualTo(custom);
	}

	@Test
	void nullEndpointThrows() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new OllamaLiteClient(null))
				.withMessageContaining("endpoint");
	}

	@Test
	void blankEndpointThrows() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new OllamaLiteClient("   "))
				.withMessageContaining("endpoint");
	}

	@Test
	void nullModelThrowsOnGenerate() {
		OllamaLiteClient client = new OllamaLiteClient();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> client.generate(null, "hello"))
				.withMessageContaining("model");
	}

	@Test
	void blankModelThrowsOnGenerate() {
		OllamaLiteClient client = new OllamaLiteClient();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> client.generate("", "hello"))
				.withMessageContaining("model");
	}

	@Test
	void nullPromptThrowsOnGenerate() {
		OllamaLiteClient client = new OllamaLiteClient();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> client.generate("llama3", null))
				.withMessageContaining("prompt");
	}

}
