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

package org.springframework.ai.deepseek;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion.Choice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionFinishReason;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link DeepSeekChatModel} tool calling.
 *
 * @author Subhash Polisetti
 */
@ExtendWith(MockitoExtension.class)
public class DeepSeekChatModelToolCallTests {

	private @Mock DeepSeekApi deepSeekApi;

	@Test
	public void toolCallConversationContinuesWhenApiReturnsNullContent() {
		var function = new ChatCompletionFunction("getCurrentWeather", "{\"location\":\"Paris\"}");
		var apiMessage = new ChatCompletionMessage(null, Role.ASSISTANT, null, null,
				List.of(new ToolCall("call_1", "function", function)), null, null);
		var completion = new ChatCompletion("chatcmpl-1",
				List.of(new Choice(ChatCompletionFinishReason.TOOL_CALLS, 0, apiMessage, null)), 1L, "deepseek-chat",
				null, "chat.completion", null);
		given(this.deepSeekApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willReturn(ResponseEntity.ok(completion));

		var toolCallback = FunctionToolCallback.builder("getCurrentWeather", (Map<String, Object> request) -> "sunny")
			.inputType(Map.class)
			.description("Get the current weather")
			.build();
		var options = DeepSeekChatOptions.builder().model("deepseek-chat").toolCallbacks(List.of(toolCallback)).build();
		var prompt = new Prompt(List.of(new UserMessage("What is the weather in Paris?")), options);
		var chatModel = DeepSeekChatModel.builder().deepSeekApi(this.deepSeekApi).build();

		ChatResponse response = chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getText()).isNull();
		assertThat(assistantMessage.getToolCalls()).hasSize(1);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("getCurrentWeather");

		ToolExecutionResult toolExecutionResult = ToolCallingManager.builder()
			.build()
			.executeToolCalls(prompt, response);
		var followUpRequest = chatModel.createRequest(new Prompt(toolExecutionResult.conversationHistory(), options),
				false);

		assertThat(followUpRequest.messages()).hasSize(3);
		ChatCompletionMessage assistantParam = followUpRequest.messages().get(1);
		assertThat(assistantParam.role()).isEqualTo(Role.ASSISTANT);
		assertThat(assistantParam.content()).isNull();
		assertThat(assistantParam.toolCalls()).hasSize(1);
		assertThat(assistantParam.toolCalls().get(0).id()).isEqualTo("call_1");
		ChatCompletionMessage toolParam = followUpRequest.messages().get(2);
		assertThat(toolParam.role()).isEqualTo(Role.TOOL);
		assertThat(toolParam.toolCallId()).isEqualTo("call_1");
		assertThat(toolParam.content()).contains("sunny");
	}

}
