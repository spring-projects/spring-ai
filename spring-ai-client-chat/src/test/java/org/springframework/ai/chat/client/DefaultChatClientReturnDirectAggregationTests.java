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

package org.springframework.ai.chat.client;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * @author: Kuntal Maity
 */
class DefaultChatClientReturnDirectAggregationTests {

	private static Generation generation(String text, String finishReason) {
		var metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(new AssistantMessage(text), metadata);
	}

	@Test
	void aggregatesMultipleReturnDirectGenerationsInContent() {
		var chatResponse = new ChatResponse(List.of(generation("DATE=2025-10-18", ToolExecutionResult.FINISH_REASON),
				generation("TIME=12:34:56.789", ToolExecutionResult.FINISH_REASON)));

		ChatModel stub = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return chatResponse;
			}
		};

		var client = ChatClient.builder(stub).build();
		String content = client.prompt("now").call().content();

		assertThat(content).isEqualTo("DATE=2025-10-18\nTIME=12:34:56.789");
	}

	@Test
	void returnsFirstWhenNotAllReturnDirect() {
		var chatResponse = new ChatResponse(
				List.of(generation("FIRST", ToolExecutionResult.FINISH_REASON), generation("SECOND", "stop")));

		ChatModel stub = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return chatResponse;
			}
		};

		var client = ChatClient.builder(stub).build();
		String content = client.prompt("now").call().content();

		assertThat(content).isEqualTo("FIRST");
	}

}
