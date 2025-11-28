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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolCallAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ToolCallAdvisorTests {

	@Mock
	private ToolCallingManager toolCallingManager;

	@Mock
	private CallAdvisorChain callAdvisorChain;

	@Mock
	private StreamAdvisorChain streamAdvisorChain;

	@Test
	void whenToolCallingManagerIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallAdvisor.builder().toolCallingManager(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallingManager must not be null");
	}

	@Test
	void whenAdvisorOrderIsOutOfRangeThenThrow() {
		assertThatThrownBy(() -> ToolCallAdvisor.builder().advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		assertThatThrownBy(() -> ToolCallAdvisor.builder().advisorOrder(BaseAdvisor.LOWEST_PRECEDENCE).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
	}

	@Test
	void testBuilderMethodChaining() {
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;

		ToolCallAdvisor advisor = ToolCallAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void testDefaultValues() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 300);
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void whenChatClientRequestIsNullThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		assertThatThrownBy(() -> advisor.adviseCall(null, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientRequest must not be null");
	}

	@Test
	void whenCallAdvisorChainIsNullThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();
		ChatClientRequest request = createMockRequest(true);

		assertThatThrownBy(() -> advisor.adviseCall(request, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("callAdvisorChain must not be null");
	}

	@Test
	void whenOptionsAreNullThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		Prompt prompt = new Prompt(List.of(new UserMessage("test")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		assertThatThrownBy(() -> advisor.adviseCall(request, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolCall Advisor requires ToolCallingChatOptions");
	}

	@Test
	void whenOptionsAreNotToolCallingChatOptionsThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		ChatOptions nonToolOptions = mock(ChatOptions.class);
		Prompt prompt = new Prompt(List.of(new UserMessage("test")), nonToolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		assertThatThrownBy(() -> advisor.adviseCall(request, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolCall Advisor requires ToolCallingChatOptions");
	}

	@Test
	void testAdviseCallWithoutToolCalls() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		// Create a terminal advisor that returns the response
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		// Create a real chain with both advisors
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(response);
		verify(this.toolCallingManager, times(0)).executeToolCalls(any(), any());
	}

	@Test
	void testAdviseCallWithNullChatResponse() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithNullChatResponse = ChatClientResponse.builder().build();

		// Create a terminal advisor that returns the response with null chatResponse
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> responseWithNullChatResponse);

		// Create a real chain with both advisors
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(responseWithNullChatResponse);
		verify(this.toolCallingManager, times(0)).executeToolCalls(any(), any());
	}

	@Test
	void testAdviseCallWithSingleToolCallIteration() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		// Create a terminal advisor that returns responses in sequence
		int[] callCount = { 0 };

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			return callCount[0] == 1 ? responseWithToolCall : finalResponse;
		});

		// Create a real chain with both advisors
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(finalResponse);
		assertThat(callCount[0]).isEqualTo(2);
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	@Test
	void testAdviseCallWithMultipleToolCallIterations() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse firstToolCallResponse = createMockResponse(true);
		ChatClientResponse secondToolCallResponse = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		// Create a terminal advisor that returns responses in sequence
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			if (callCount[0] == 1) {
				return firstToolCallResponse;
			}
			else if (callCount[0] == 2) {
				return secondToolCallResponse;
			}
			else {
				return finalResponse;
			}
		});

		// Create a real chain with both advisors
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution results
		AssistantMessage.builder().build();
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(finalResponse);
		assertThat(callCount[0]).isEqualTo(3);
		verify(this.toolCallingManager, times(2)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	@Test
	void testAdviseCallWithReturnDirectToolExecution() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);

		// Create a terminal advisor that returns the response
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> responseWithToolCall);

		// Create a real chain with both advisors
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result with returnDirect = true
		ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse("tool-1", "testTool",
				"Tool result data");
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolResponse))
			.build();
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), toolResponseMessage);
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		// Verify that the tool execution was called only once (no loop continuation)
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));

		// Verify that the result contains the tool execution result as generations
		assertThat(result.chatResponse()).isNotNull();
		assertThat(result.chatResponse().getResults()).hasSize(1);
		assertThat(result.chatResponse().getResults().get(0).getOutput().getText()).isEqualTo("Tool result data");
		assertThat(result.chatResponse().getResults().get(0).getMetadata().getFinishReason())
			.isEqualTo(ToolExecutionResult.FINISH_REASON);
	}

	@Test
	void testInternalToolExecutionIsDisabled() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		// Use a simple holder to capture the request
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];

		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain capturingChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, capturingChain);

		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();

		assertThat(capturedOptions.getInternalToolExecutionEnabled()).isFalse();
	}

	@Test
	void testAdviseStreamThrowsUnsupportedOperationException() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();
		ChatClientRequest request = createMockRequest(true);

		Flux<ChatClientResponse> result = advisor.adviseStream(request, this.streamAdvisorChain);

		assertThatThrownBy(() -> result.blockFirst()).isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("Unimplemented method 'adviseStream'");
	}

	@Test
	void testGetName() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void testGetOrder() {
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 400;
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().advisorOrder(customOrder).build();

		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	// Helper methods

	private ChatClientRequest createMockRequest(boolean withToolCallingOptions) {
		List<Message> instructions = List.of(new UserMessage("test message"));

		ChatOptions options = null;
		if (withToolCallingOptions) {
			ToolCallingChatOptions toolOptions = mock(ToolCallingChatOptions.class,
					Mockito.withSettings().strictness(Strictness.LENIENT));
			// Create a separate mock for the copy that tracks the internal state
			ToolCallingChatOptions copiedOptions = mock(ToolCallingChatOptions.class,
					Mockito.withSettings().strictness(Strictness.LENIENT));

			// Use a holder to track the state
			boolean[] internalToolExecutionEnabled = { true };

			when(toolOptions.copy()).thenReturn(copiedOptions);
			when(toolOptions.getInternalToolExecutionEnabled()).thenReturn(true);

			// When getInternalToolExecutionEnabled is called on the copy, return the
			// current state
			when(copiedOptions.getInternalToolExecutionEnabled())
				.thenAnswer(invocation -> internalToolExecutionEnabled[0]);

			// When setInternalToolExecutionEnabled is called on the copy, update the
			// state
			Mockito.doAnswer(invocation -> {
				internalToolExecutionEnabled[0] = invocation.getArgument(0);
				return null;
			}).when(copiedOptions).setInternalToolExecutionEnabled(org.mockito.ArgumentMatchers.anyBoolean());

			options = toolOptions;
		}

		Prompt prompt = new Prompt(instructions, options);

		return ChatClientRequest.builder().prompt(prompt).build();
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls) {
		Generation generation = mock(Generation.class, Mockito.withSettings().strictness(Strictness.LENIENT));
		when(generation.getOutput()).thenReturn(new AssistantMessage("response"));

		// Mock metadata to avoid NullPointerException in ChatResponse.Builder.from()
		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(metadata.getModel()).thenReturn("");
		when(metadata.getId()).thenReturn("");
		when(metadata.getRateLimit()).thenReturn(null);
		when(metadata.getUsage()).thenReturn(null);
		when(metadata.getPromptMetadata()).thenReturn(null);
		when(metadata.entrySet()).thenReturn(java.util.Collections.emptySet());

		// Create a real ChatResponse instead of mocking it to avoid issues with
		// ChatResponse.Builder.from()
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of(generation)).metadata(metadata).build();

		// Mock hasToolCalls since it's not part of the builder
		ChatResponse spyChatResponse = Mockito.spy(chatResponse);
		when(spyChatResponse.hasToolCalls()).thenReturn(hasToolCalls);

		ChatClientResponse response = mock(ChatClientResponse.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(response.chatResponse()).thenReturn(spyChatResponse);

		// Mock mutate() to return a real builder that can handle the mutation
		when(response.mutate())
			.thenAnswer(invocation -> ChatClientResponse.builder().chatResponse(spyChatResponse).context(Map.of()));

		return response;
	}

	private static class TerminalCallAdvisor implements CallAdvisor {

		private final BiFunction<ChatClientRequest, CallAdvisorChain, ChatClientResponse> responseFunction;

		TerminalCallAdvisor(BiFunction<ChatClientRequest, CallAdvisorChain, ChatClientResponse> responseFunction) {
			this.responseFunction = responseFunction;
		}

		@Override
		public String getName() {
			return "terminal";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
			return this.responseFunction.apply(req, chain);
		}

	};

}
