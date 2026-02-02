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
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
	void testAdviseStreamWithoutToolCalls() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		// Create a terminal stream advisor that returns the response
		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor((req, chain) -> Flux.just(response));

		// Create a real chain with both advisors
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		List<ChatClientResponse> results = advisor.adviseStream(request, realChain).collectList().block();

		assertThat(results).isNotNull().hasSize(1);
		assertThat(results.get(0).chatResponse()).isEqualTo(response.chatResponse());
		verify(this.toolCallingManager, times(0)).executeToolCalls(any(), any());
	}

	@Test
	void testAdviseStreamWithSingleToolCallIteration() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		// Create a terminal stream advisor that returns responses in sequence
		int[] callCount = { 0 };
		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor((req, chain) -> {
			callCount[0]++;
			return Flux.just(callCount[0] == 1 ? responseWithToolCall : finalResponse);
		});

		// Create a real chain with both advisors
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		List<ChatClientResponse> results = advisor.adviseStream(request, realChain).collectList().block();

		// With default streamToolCallResponses=false, we only get the final response
		// (intermediate tool call responses are filtered out)
		assertThat(results).isNotNull().hasSize(1);
		assertThat(callCount[0]).isEqualTo(2);
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	@Test
	void testAdviseStreamWithReturnDirectToolExecution() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);

		// Create a terminal stream advisor that returns the response
		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor(
				(req, chain) -> Flux.just(responseWithToolCall));

		// Create a real chain with both advisors
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
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

		List<ChatClientResponse> results = advisor.adviseStream(request, realChain).collectList().block();

		// Verify that the tool execution was called only once (no loop continuation)
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));

		// With default streamToolCallResponses=false, we only get the returnDirect result
		// (intermediate tool call response is filtered out)
		assertThat(results).isNotNull().hasSize(1);
		// The result contains the tool execution result
		assertThat(results.get(0).chatResponse()).isNotNull();
		assertThat(results.get(0).chatResponse().getResults()).hasSize(1);
		assertThat(results.get(0).chatResponse().getResults().get(0).getOutput().getText())
			.isEqualTo("Tool result data");
	}

	@Test
	void whenStreamAdvisorChainIsNullThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();
		ChatClientRequest request = createMockRequest(true);

		assertThatThrownBy(() -> advisor.adviseStream(request, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("streamAdvisorChain must not be null");
	}

	@Test
	void whenStreamChatClientRequestIsNullThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		assertThatThrownBy(() -> advisor.adviseStream(null, this.streamAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientRequest must not be null");
	}

	@Test
	void whenStreamOptionsAreNotToolCallingChatOptionsThenThrow() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

		ChatOptions nonToolOptions = mock(ChatOptions.class);
		Prompt prompt = new Prompt(List.of(new UserMessage("test")), nonToolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor(
				(req, chain) -> Flux.just(createMockResponse(false)));
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		assertThatThrownBy(() -> advisor.adviseStream(request, realChain).blockFirst())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolCall Advisor requires ToolCallingChatOptions");
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

	@Test
	void testBuilderGetters() {
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;

		ToolCallAdvisor.Builder<?> builder = ToolCallAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder);

		assertThat(builder.getToolCallingManager()).isEqualTo(customManager);
		assertThat(builder.getAdvisorOrder()).isEqualTo(customOrder);
	}

	@Test
	void testConversationHistoryEnabledDefaultValue() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		// By default, conversationHistoryEnabled should be true
		// Verify via the tool call iteration behavior - with history enabled, the full
		// conversation history is used
		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			return callCount[0] == 1 ? responseWithToolCall : finalResponse;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result with multiple messages in history
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(finalResponse);
	}

	@Test
	void testConversationHistoryEnabledSetToFalse() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.conversationHistoryEnabled(false)
			.build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			return callCount[0] == 1 ? responseWithToolCall : finalResponse;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result with multiple messages in history
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(finalResponse);
		// With conversationHistoryEnabled=false, only the last message from history is
		// used
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	@Test
	void testStreamToolCallResponsesDefaultValue() {
		ToolCallAdvisor.Builder<?> builder = ToolCallAdvisor.builder();

		// By default, streamToolCallResponses should be false
		assertThat(builder.isStreamToolCallResponses()).isFalse();
	}

	@Test
	void testStreamToolCallResponsesBuilderMethod() {
		ToolCallAdvisor.Builder<?> builder = ToolCallAdvisor.builder().streamToolCallResponses(false);

		assertThat(builder.isStreamToolCallResponses()).isFalse();
	}

	@Test
	void testSuppressToolCallStreamingBuilderMethod() {
		ToolCallAdvisor.Builder<?> builder = ToolCallAdvisor.builder().suppressToolCallStreaming();

		assertThat(builder.isStreamToolCallResponses()).isFalse();
	}

	@Test
	void testAdviseStreamWithToolCallResponsesEnabled() {
		// Create advisor with tool call streaming explicitly enabled
		ToolCallAdvisor advisor = ToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.streamToolCallResponses(true)
			.build();

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		// Create a terminal stream advisor that returns responses in sequence
		int[] callCount = { 0 };
		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor((req, chain) -> {
			callCount[0]++;
			return Flux.just(callCount[0] == 1 ? responseWithToolCall : finalResponse);
		});

		// Create a real chain with both advisors
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		List<ChatClientResponse> results = advisor.adviseStream(request, realChain).collectList().block();

		// With streamToolCallResponses(true), we get both the intermediate tool call
		// response (streamed in real-time) and the final response from recursive call
		assertThat(results).isNotNull().hasSize(2);
		assertThat(callCount[0]).isEqualTo(2); // Both iterations still happen
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	@Test
	void testDisableMemoryBuilderMethod() {
		ToolCallAdvisor advisor = ToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.disableMemory()
			.build();

		ChatClientRequest request = createMockRequestWithSystemMessage();
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		// Capture the request passed to the terminal advisor on second call
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			if (callCount[0] == 2) {
				capturedRequest[0] = req;
			}
			return callCount[0] == 1 ? responseWithToolCall : finalResponse;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		// Mock tool execution result
		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("assistant response").build(),
				ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		advisor.adviseCall(request, realChain);

		// Verify second call includes system message and last message from history
		assertThat(capturedRequest[0]).isNotNull();
		List<Message> instructions = capturedRequest[0].prompt().getInstructions();
		assertThat(instructions).hasSize(2);
		assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(instructions.get(1)).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void testExtendedAdvisorWithCustomHooks() {
		int[] hookCallCounts = { 0, 0, 0 }; // initializeLoop, beforeCall, afterCall

		// Create extended advisor to verify hooks are called
		TestableToolCallAdvisor advisor = new TestableToolCallAdvisor(this.toolCallingManager,
				BaseAdvisor.HIGHEST_PRECEDENCE + 300, hookCallCounts);

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify hooks were called
		assertThat(hookCallCounts[0]).isEqualTo(1); // doInitializeLoop called once
		assertThat(hookCallCounts[1]).isEqualTo(1); // doBeforeCall called once
		assertThat(hookCallCounts[2]).isEqualTo(1); // doAfterCall called once
	}

	@Test
	void testExtendedAdvisorHooksCalledMultipleTimesWithToolCalls() {
		int[] hookCallCounts = { 0, 0, 0 }; // initializeLoop, beforeCall, afterCall

		TestableToolCallAdvisor advisor = new TestableToolCallAdvisor(this.toolCallingManager,
				BaseAdvisor.HIGHEST_PRECEDENCE + 300, hookCallCounts);

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse responseWithToolCall = createMockResponse(true);
		ChatClientResponse finalResponse = createMockResponse(false);

		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			return callCount[0] == 1 ? responseWithToolCall : finalResponse;
		});

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

		advisor.adviseCall(request, realChain);

		// Verify hooks were called correct number of times
		assertThat(hookCallCounts[0]).isEqualTo(1); // doInitializeLoop called once
													// (before loop)
		assertThat(hookCallCounts[1]).isEqualTo(2); // doBeforeCall called twice (each
													// iteration)
		assertThat(hookCallCounts[2]).isEqualTo(2); // doAfterCall called twice (each
													// iteration)
	}

	@Test
	void testExtendedBuilderWithCustomBuilder() {
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 450;

		TestableToolCallAdvisor advisor = TestableToolCallAdvisor.testBuilder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	// Helper methods

	private ChatClientRequest createMockRequestWithSystemMessage() {
		SystemMessage systemMessage = new SystemMessage("You are a helpful assistant");
		UserMessage userMessage = new UserMessage("test message");
		List<Message> instructions = List.of(systemMessage, userMessage);

		ToolCallingChatOptions toolOptions = mock(ToolCallingChatOptions.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		ToolCallingChatOptions copiedOptions = mock(ToolCallingChatOptions.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));

		boolean[] internalToolExecutionEnabled = { true };

		when(toolOptions.copy()).thenReturn(copiedOptions);
		when(toolOptions.getInternalToolExecutionEnabled()).thenReturn(true);
		when(copiedOptions.getInternalToolExecutionEnabled()).thenAnswer(invocation -> internalToolExecutionEnabled[0]);
		Mockito.doAnswer(invocation -> {
			internalToolExecutionEnabled[0] = invocation.getArgument(0);
			return null;
		}).when(copiedOptions).setInternalToolExecutionEnabled(org.mockito.ArgumentMatchers.anyBoolean());
		when(copiedOptions.copy()).thenReturn(copiedOptions);

		Prompt prompt = new Prompt(instructions, toolOptions);

		ChatClientRequest mockRequest = mock(ChatClientRequest.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(mockRequest.prompt()).thenReturn(prompt);
		when(mockRequest.context()).thenReturn(Map.of());

		when(mockRequest.copy()).thenAnswer(invocation -> {
			Prompt copiedPrompt = new Prompt(instructions, copiedOptions);
			return ChatClientRequest.builder().prompt(copiedPrompt).build();
		});

		return mockRequest;
	}

	private ChatClientRequest createMockRequest(boolean withToolCallingOptions) {
		List<Message> instructions = List.of(new UserMessage("test message"));

		ChatOptions options = null;
		ToolCallingChatOptions copiedOptions = null;

		if (withToolCallingOptions) {
			ToolCallingChatOptions toolOptions = mock(ToolCallingChatOptions.class,
					Mockito.withSettings().strictness(Strictness.LENIENT));
			// Create a separate mock for the copy that tracks the internal state
			copiedOptions = mock(ToolCallingChatOptions.class, Mockito.withSettings().strictness(Strictness.LENIENT));

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

			// copiedOptions.copy() should also return itself for subsequent copies
			when(copiedOptions.copy()).thenReturn(copiedOptions);

			options = toolOptions;
		}

		Prompt prompt = new Prompt(instructions, options);
		ChatClientRequest originalRequest = ChatClientRequest.builder().prompt(prompt).build();

		// Create a mock request that returns a proper copy with the mocked options chain
		ChatClientRequest mockRequest = mock(ChatClientRequest.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(mockRequest.prompt()).thenReturn(prompt);
		when(mockRequest.context()).thenReturn(Map.of());

		// When copy() is called, return a new request with the copied options properly
		// set up
		final ToolCallingChatOptions finalCopiedOptions = copiedOptions;
		when(mockRequest.copy()).thenAnswer(invocation -> {
			Prompt copiedPrompt = new Prompt(instructions, finalCopiedOptions);
			return ChatClientRequest.builder().prompt(copiedPrompt).build();
		});

		return mockRequest;
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls) {
		// Create AssistantMessage with or without tool calls
		AssistantMessage assistantMessage;
		if (hasToolCalls) {
			// Create a real AssistantMessage with actual tool calls
			AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("tool-call-1", "function", "testTool",
					"{}");
			assistantMessage = AssistantMessage.builder().content("response").toolCalls(List.of(toolCall)).build();
		}
		else {
			assistantMessage = new AssistantMessage("response");
		}

		Generation generation = mock(Generation.class, Mockito.withSettings().strictness(Strictness.LENIENT));
		when(generation.getOutput()).thenReturn(assistantMessage);

		// Mock metadata to avoid NullPointerException in ChatResponse.Builder.from()
		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(metadata.getModel()).thenReturn("");
		when(metadata.getId()).thenReturn("");
		when(metadata.getRateLimit()).thenReturn(null);
		when(metadata.getUsage()).thenReturn(null);
		when(metadata.getPromptMetadata()).thenReturn(null);
		when(metadata.entrySet()).thenReturn(java.util.Collections.emptySet());

		// Create a real ChatResponse
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of(generation)).metadata(metadata).build();

		ChatClientResponse response = mock(ChatClientResponse.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(response.chatResponse()).thenReturn(chatResponse);
		when(response.context()).thenReturn(Map.of());

		// Mock mutate() to return a real builder that can handle the mutation
		when(response.mutate())
			.thenAnswer(invocation -> ChatClientResponse.builder().chatResponse(chatResponse).context(Map.of()));

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

	}

	private static class TerminalStreamAdvisor implements StreamAdvisor {

		private final BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> responseFunction;

		TerminalStreamAdvisor(
				BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> responseFunction) {
			this.responseFunction = responseFunction;
		}

		@Override
		public String getName() {
			return "terminal-stream";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public Flux<ChatClientResponse> adviseStream(ChatClientRequest req, StreamAdvisorChain chain) {
			return this.responseFunction.apply(req, chain);
		}

	}

	/**
	 * Test subclass of ToolCallAdvisor to verify extensibility and hook methods.
	 */
	private static class TestableToolCallAdvisor extends ToolCallAdvisor {

		private final int[] hookCallCounts;

		TestableToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, int[] hookCallCounts) {
			super(toolCallingManager, advisorOrder, true);
			this.hookCallCounts = hookCallCounts;
		}

		@Override
		protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest,
				CallAdvisorChain callAdvisorChain) {
			if (this.hookCallCounts != null) {
				this.hookCallCounts[0]++;
			}
			return super.doInitializeLoop(chatClientRequest, callAdvisorChain);
		}

		@Override
		protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest,
				CallAdvisorChain callAdvisorChain) {
			if (this.hookCallCounts != null) {
				this.hookCallCounts[1]++;
			}
			return super.doBeforeCall(chatClientRequest, callAdvisorChain);
		}

		@Override
		protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse,
				CallAdvisorChain callAdvisorChain) {
			if (this.hookCallCounts != null) {
				this.hookCallCounts[2]++;
			}
			return super.doAfterCall(chatClientResponse, callAdvisorChain);
		}

		static TestableBuilder testBuilder() {
			return new TestableBuilder();
		}

		static class TestableBuilder extends ToolCallAdvisor.Builder<TestableBuilder> {

			@Override
			protected TestableBuilder self() {
				return this;
			}

			@Override
			public TestableToolCallAdvisor build() {
				return new TestableToolCallAdvisor(getToolCallingManager(), getAdvisorOrder(), null);
			}

		}

	}

}
