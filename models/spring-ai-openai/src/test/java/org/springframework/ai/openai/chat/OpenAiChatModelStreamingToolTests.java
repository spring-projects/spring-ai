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

package org.springframework.ai.openai.chat;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for streaming tool calls in OpenAiChatModel.
 */

class OpenAiChatModelStreamingToolTests {

	@Test
	void ensureContentIsNotSwallowedWhenToolCallIsInSameChunk() throws Exception {
		// 1. Prepare a chunk that contains BOTH content ("Thinking...") AND a tool call
		var toolCall = new ToolCall(0, "call_1", "function", new ChatCompletionFunction("get_weather", "{}"));
		var messageWithToolAndContent = new ChatCompletionMessage("Thinking...", Role.ASSISTANT, null, null,
				List.of(toolCall), null, null, null, null);

		var chunkMixed = new ChatCompletionChunk("id1",
				List.of(new ChunkChoice(ChatCompletionFinishReason.TOOL_CALLS, 0, messageWithToolAndContent, null)),
				123L, "model", "tier", "fp", "object", null);

		Flux<ChatCompletionChunk> bugResponse = Flux.just(chunkMixed);

		// 2. Prepare a STOP chunk to end the stream
		var chunkStop = new ChatCompletionChunk("id1",
				List.of(new ChunkChoice(ChatCompletionFinishReason.STOP, 0,
						new ChatCompletionMessage("", Role.ASSISTANT), null)),
				123L, "model", "tier", "fp", "object", null);

		Flux<ChatCompletionChunk> stopResponse = Flux.just(chunkStop);

		// 3. Mock the OpenAiApi to return the mixed chunk first, then the stop chunk
		AtomicBoolean isFirstCall = new AtomicBoolean(true);

		OpenAiApi mockApi = mock(OpenAiApi.class, invocation -> {
			if (invocation.getMethod().getName().equals("chatCompletionStream")
					&& invocation.getMethod().getReturnType().equals(Flux.class)) {

				if (isFirstCall.getAndSet(false)) {
					return bugResponse;
				}
				else {
					return stopResponse;
				}
			}
			return Answers.RETURNS_DEFAULTS.answer(invocation);
		});

		// 4. Instantiate OpenAiChatModel using reflection (to mock RetryTemplate)
		Constructor<?> constructor = null;
		for (Constructor<?> c : OpenAiChatModel.class.getConstructors()) {
			if (c.getParameterCount() == 5) {
				constructor = c;
				break;
			}
		}

		if (constructor == null) {
			throw new IllegalStateException("Constructor not found");
		}

		Class<?> retryTemplateType = constructor.getParameterTypes()[3];
		Object retryTemplateMock = mock(retryTemplateType);
		ToolCallingManager toolCallingManager = mock(ToolCallingManager.class, Answers.RETURNS_DEEP_STUBS);

		OpenAiChatModel chatModel = (OpenAiChatModel) constructor.newInstance(mockApi,
				OpenAiChatOptions.builder().build(), toolCallingManager, retryTemplateMock, ObservationRegistry.NOOP);

		// 5. Execute streaming
		Flux<ChatResponse> chatResponseFlux = chatModel.stream(new Prompt(new UserMessage("test")));

		String fullContent = chatResponseFlux.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(text -> text != null)
			.reduce("", (a, b) -> a + b);

		// 6. Verify: The content "Thinking..." should be present
		assertThat(fullContent).as("Content accompanying a tool call should not be swallowed").contains("Thinking");
	}

}
