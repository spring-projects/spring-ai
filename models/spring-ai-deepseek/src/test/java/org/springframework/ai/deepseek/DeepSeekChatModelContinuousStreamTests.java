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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionFinishReason;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeepSeekChatModelContinuousStreamTests {

	@Mock
	private DeepSeekApi deepSeekApi;

	@Mock
	private ToolCallingManager toolCallingManager;

	@Test
	void whenContinuousStreamEnabledThenPrefixIsInjectedBeforeRecursiveStream() {
		RunResult result = runScenario(true);
		assertThat(result.output()).isEqualTo("Hello OK");
		assertThat(toolCallAssistantContent(result.secondRequest())).isEqualTo("Hello ");
	}

	@Test
	void whenContinuousStreamDisabledThenPrefixIsNotInjectedBeforeRecursiveStream() {
		RunResult result = runScenario(false);
		assertThat(result.output()).isEqualTo("Hello Hello OK");
		assertThat(toolCallAssistantContent(result.secondRequest())).isEmpty();
	}

	private RunResult runScenario(boolean continuousStream) {
		var chatModel = DeepSeekChatModel.builder()
				.deepSeekApi(this.deepSeekApi)
				.toolCallingManager(this.toolCallingManager)
				.defaultOptions(DeepSeekChatOptions.builder().internalToolExecutionEnabled(true).build())
				.toolExecutionEligibilityPredicate((options, response) -> response.getResults()
						.stream()
						.anyMatch(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls())))
				.build();

		given(this.toolCallingManager.resolveToolDefinitions(any())).willReturn(List.of());

		given(this.toolCallingManager.executeToolCalls(any(), any())).willAnswer(invocation -> {
			Prompt prompt = invocation.getArgument(0);
			ChatResponse chatResponse = invocation.getArgument(1);
			return toolExecutionResult(prompt, chatResponse, continuousStream);
		});

		AtomicInteger requestCounter = new AtomicInteger();
		given(this.deepSeekApi.chatCompletionStream(any())).willAnswer(invocation -> {
			ChatCompletionRequest request = invocation.getArgument(0);
			if (requestCounter.getAndIncrement() == 0) {
				return Flux.just(chunk("Hello ", null, null),
						chunk("", List.of(toolCall("call_1")), ChatCompletionFinishReason.TOOL_CALLS));
			}

			return hasToolCallPrefix(request) ? Flux.just(chunk("OK", null, ChatCompletionFinishReason.STOP))
					: Flux.just(chunk("Hello OK", null, ChatCompletionFinishReason.STOP));
		});

		List<ChatResponse> responses = chatModel.stream(new Prompt("Hi")).collectList().block(Duration.ofSeconds(5));
		String output = responses.stream()
				.map(r -> r.getResult().getOutput().getText())
				.filter(Objects::nonNull)
				.reduce("", String::concat);

		ArgumentCaptor<ChatCompletionRequest> requestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
		verify(this.deepSeekApi, times(2)).chatCompletionStream(requestCaptor.capture());
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(), any());

		return new RunResult(output, requestCaptor.getAllValues().get(1));
	}

	private static ToolExecutionResult toolExecutionResult(Prompt prompt, ChatResponse chatResponse,
														   boolean continuousStream) {
		AssistantMessage toolCallMessage = chatResponse.getResults().get(0).getOutput();

		List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
		conversationHistory.add(AssistantMessage.builder()
				.content("")
				.properties(toolCallMessage.getMetadata())
				.toolCalls(toolCallMessage.getToolCalls())
				.media(toolCallMessage.getMedia())
				.build());
		conversationHistory.add(ToolResponseMessage.builder()
				.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "weather", "{}")))
				.build());

		return ToolExecutionResult.builder()
				.conversationHistory(conversationHistory)
				.returnDirect(false)
				.continuousStream(continuousStream)
				.build();
	}

	private static boolean hasToolCallPrefix(ChatCompletionRequest request) {
		return request.messages()
				.stream()
				.anyMatch(m -> m.role() == Role.ASSISTANT && !CollectionUtils.isEmpty(m.toolCalls())
						&& Objects.equals(m.content(), "Hello "));
	}

	private static String toolCallAssistantContent(ChatCompletionRequest request) {
		return request.messages()
				.stream()
				.filter(m -> m.role() == Role.ASSISTANT && !CollectionUtils.isEmpty(m.toolCalls()))
				.map(ChatCompletionMessage::content)
				.findFirst()
				.orElse("");
	}

	private static ToolCall toolCall(String id) {
		return new ToolCall(id, "function", new ChatCompletionFunction("weather", "{}"));
	}

	private static ChatCompletionChunk chunk(String content, List<ToolCall> toolCalls,
											 ChatCompletionFinishReason finishReason) {
		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT, null, null, toolCalls);
		ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice(finishReason, 0, message, null);
		return new ChatCompletionChunk("id", List.of(choice), 1L, "model", null, null, null, null);
	}

	private record RunResult(String output, ChatCompletionRequest secondRequest) {
	}

}
