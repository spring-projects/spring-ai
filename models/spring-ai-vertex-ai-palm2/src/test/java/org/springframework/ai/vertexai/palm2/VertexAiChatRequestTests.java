/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.vertexai.palm2;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class VertexAiChatRequestTests {

	VertexAiChatClient client = new VertexAiChatClient(new VertexAiApi("bla"));

	@Test
	public void createRequestWithDefaultOptions() {

		var request = client.createRequest(new Prompt("Test message content"));

		assertThat(request.prompt().messages()).hasSize(1);

		assertThat(request.candidateCount()).isEqualTo(1);
		assertThat(request.temperature()).isEqualTo(0.7f);
		assertThat(request.topK()).isEqualTo(20);
		assertThat(request.topP()).isNull();
	}

	@Test
	public void createRequestWithPromptVertexAiOptions() {

		// Runtime options should override the default options.
		VertexAiChatOptions promptOptions = VertexAiChatOptions.builder()
			.withTemperature(0.8f)
			.withTopP(0.5f)
			.withTopK(99)
			// .withCandidateCount(2)
			.build();

		var request = client.createRequest(new Prompt("Test message content", promptOptions));

		assertThat(request.prompt().messages()).hasSize(1);

		assertThat(request.candidateCount()).isEqualTo(1);
		assertThat(request.temperature()).isEqualTo(0.8f);
		assertThat(request.topK()).isEqualTo(99);
		assertThat(request.topP()).isEqualTo(0.5f);
	}

	@Test
	public void createRequestWithPromptPortableChatOptions() {

		// runtime options.
		ChatOptions portablePromptOptions = ChatOptionsBuilder.builder()
			.withTemperature(0.9f)
			.withTopK(100)
			.withTopP(0.6f)
			.build();

		var request = client.createRequest(new Prompt("Test message content", portablePromptOptions));

		assertThat(request.prompt().messages()).hasSize(1);

		assertThat(request.candidateCount()).isEqualTo(1);
		assertThat(request.temperature()).isEqualTo(0.9f);
		assertThat(request.topK()).isEqualTo(100);
		assertThat(request.topP()).isEqualTo(0.6f);
	}

}
