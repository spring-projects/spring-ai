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

package org.springframework.ai.openai;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class OpenAiChatModelStreamingTest {

	@Test
	void shouldThrowExceptionOnInvalidJsonChunk() {
		OpenAiApi mockApi = Mockito.mock(OpenAiApi.class);

		OpenAiApi.ChatCompletionChunk invalidChunk = new OpenAiApi.ChatCompletionChunk("invalid-id", null,
				System.currentTimeMillis() / 1000L, "gpt-test-model", null, null, null, null);

		when(mockApi.chatCompletionStream(any(), any())).thenReturn(Flux.just(invalidChunk));

		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-test-model").build();
		OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(mockApi).defaultOptions(options).build();

		assertThatThrownBy(() -> model.stream(new Prompt("Hello")).collectList().block())
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to parse ChatCompletionChunk");
	}

}
