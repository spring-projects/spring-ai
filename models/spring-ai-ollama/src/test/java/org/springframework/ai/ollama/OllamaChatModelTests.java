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
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Jihoon Kim
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public class OllamaChatModelTests {

	@Mock
	OllamaApi ollamaApi;

	@Test
	public void buildOllamaChatModel() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> OllamaChatModel.builder()
					.ollamaApi(this.ollamaApi)
					.defaultOptions(OllamaOptions.builder().model(OllamaModel.LLAMA2).build())
					.modelManagementOptions(null)
					.build());
		assertEquals("modelManagementOptions must not be null", exception.getMessage());
	}

	@Test
	public void buildChatResponseMetadata() {

		Long evalDuration = 1000L;
		Integer evalCount = 101;

		Integer promptEvalCount = 808;
		Long promptEvalDuration = 8L;

		Long loadDuration = 100L;
		Long totalDuration = 2000L;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null,
				totalDuration, loadDuration, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponseMetadata metadata = OllamaChatModel.from(response, null);

		assertEquals(Duration.ofNanos(evalDuration), metadata.get("eval-duration"));
		assertEquals(evalCount, metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration), metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount, metadata.get("prompt-eval-count"));
	}

	@Test
	public void buildChatResponseMetadataAggregationWithNonEmptyMetadata() {

		Long evalDuration = 1000L;
		Integer evalCount = 101;

		Integer promptEvalCount = 808;
		Long promptEvalDuration = 8L;

		Long loadDuration = 100L;
		Long totalDuration = 2000L;

		OllamaApi.ChatResponse response = new OllamaApi.ChatResponse("model", Instant.now(), null, null, null,
				totalDuration, loadDuration, promptEvalCount, promptEvalDuration, evalCount, evalDuration);

		ChatResponse previousChatResponse = ChatResponse.builder()
			.generations(List.of())
			.metadata(ChatResponseMetadata.builder()
				.usage(new DefaultUsage(66, 99))
				.keyValue("eval-duration", Duration.ofSeconds(2))
				.keyValue("prompt-eval-duration", Duration.ofSeconds(2))
				.build())
			.build();

		ChatResponseMetadata metadata = OllamaChatModel.from(response, previousChatResponse);

		assertThat(metadata.getUsage()).isEqualTo(new DefaultUsage(808 + 66, 101 + 99));

		assertEquals(Duration.ofNanos(evalDuration).plus(Duration.ofSeconds(2)), metadata.get("eval-duration"));
		assertEquals((evalCount + 99), (Integer) metadata.get("eval-count"));
		assertEquals(Duration.ofNanos(promptEvalDuration).plus(Duration.ofSeconds(2)),
				metadata.get("prompt-eval-duration"));
		assertEquals(promptEvalCount + 66, (Integer) metadata.get("prompt-eval-count"));
	}

}
