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

package org.springframework.ai.ollama;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/**
 * @author Jihoon Kim
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public class OllamaChatModelTests {

	@Mock
	OllamaApi ollamaApi;

	@Mock
	OllamaApi.ChatResponse response;

	@Test
	public void buildOllamaChatModel() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> OllamaChatModel.builder()
					.withOllamaApi(this.ollamaApi)
					.withDefaultOptions(OllamaOptions.create().withModel(OllamaModel.LLAMA2))
					.withModelManagementOptions(null)
					.build());
		assertEquals("modelManagementOptions must not be null", exception.getMessage());
	}

	@Test
	public void buildChatResponseMetadata() {
		Duration evalDuration = Duration.ofSeconds(1);
		Integer evalCount = 101;

		Duration promptEvalDuration = Duration.ofSeconds(8);
		Integer promptEvalCount = 808;

		given(this.response.evalDuration()).willReturn(evalDuration);
		given(this.response.evalCount()).willReturn(evalCount);
		given(this.response.promptEvalDuration()).willReturn(promptEvalDuration);
		given(this.response.promptEvalCount()).willReturn(promptEvalCount);

		ChatResponseMetadata metadata = OllamaChatModel.from(this.response);

		assertEquals(evalDuration, metadata.get("eval-duration"));
		assertEquals(evalCount, metadata.get("eval-count"));
		assertEquals(promptEvalDuration, metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount, metadata.get("prompt-eval-count"));
	}

}
