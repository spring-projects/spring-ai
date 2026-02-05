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

package org.springframework.ai.deepseek;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion.Choice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionFinishReason;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that DeepSeekChatModel resets tool_choice to AUTO when resubmitting tool
 * results (returnDirect=false) to avoid infinite tool call loops.
 *
 * @author : kuntal maity
 */
class DeepSeekChatModelToolChoiceResetTests {

	@Test
	void resetsToolChoiceToAutoOnToolResultPushback() {
		// Arrange: mock API to return a tool call first, then a normal assistant message
		DeepSeekApi api = mock(DeepSeekApi.class);

		// Capture requests to verify tool_choice on the second call
		ArgumentCaptor<ChatCompletionRequest> reqCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);

		AtomicInteger apiCalls = new AtomicInteger(0);
		when(api.chatCompletionEntity(reqCaptor.capture())).thenAnswer(invocation -> {
			int call = apiCalls.incrementAndGet();
			if (call == 1) {
				// First response: model requests tool call
				ChatCompletionMessage msg = new ChatCompletionMessage("", // content
						ChatCompletionMessage.Role.ASSISTANT, null, null, List.of(new ToolCall("call_1", "function",
								new ChatCompletionFunction("getMarineYetiDescription", "{}"))),
						null, null);
				ChatCompletion cc = new ChatCompletion("id-1",
						List.of(new Choice(ChatCompletionFinishReason.TOOL_CALLS, 0, msg, null)),
						Instant.now().getEpochSecond(), DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getName(), null,
						"chat.completion", null);
				return ResponseEntity.ok(cc);
			}
			else {
				// Second response: normal assistant message
				ChatCompletionMessage msg = new ChatCompletionMessage("Marine yeti is orange.",
						ChatCompletionMessage.Role.ASSISTANT, null, null, null, null, null);
				ChatCompletion cc = new ChatCompletion("id-2",
						List.of(new Choice(ChatCompletionFinishReason.STOP, 0, msg, null)),
						Instant.now().getEpochSecond(), DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getName(), null,
						"chat.completion", null);
				return ResponseEntity.ok(cc);
			}
		});

		// Tool callback increments counter; returnDirect defaults to false
		AtomicInteger toolInvocations = new AtomicInteger(0);
		var tool = FunctionToolCallback.builder("getMarineYetiDescription", () -> {
			toolInvocations.incrementAndGet();
			return "Marine yeti is orange";
		}).build();

		DeepSeekChatOptions options = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT)
			.toolCallbacks(List.of(tool))
			.toolChoice(ChatCompletionRequest.ToolChoiceBuilder.FUNCTION("getMarineYetiDescription"))
			.build();

		DeepSeekChatModel model = DeepSeekChatModel.builder().deepSeekApi(api).defaultOptions(options).build();

		// Act
		ChatResponse response = model.call(new Prompt("What is the color of a marine yeti?"));

		// Assert: API was called twice (tool call, then final text)
		assertThat(apiCalls.get()).isEqualTo(2);
		// Second request tool_choice should be AUTO
		assertThat(reqCaptor.getAllValues()).hasSize(2);
		Object secondToolChoice = reqCaptor.getAllValues().get(1).toolChoice();
		assertThat(secondToolChoice).isEqualTo(ChatCompletionRequest.ToolChoiceBuilder.AUTO);
		// Tool executes exactly once
		assertThat(toolInvocations.get()).isEqualTo(1);
		// And final content is normal text
		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("orange");
	}

}
