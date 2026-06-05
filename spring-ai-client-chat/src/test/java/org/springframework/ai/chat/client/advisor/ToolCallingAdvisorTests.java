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
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.model.tool.ToolExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolCallingAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ToolCallingAdvisorTests {

	@Mock
	private ToolCallingManager toolCallingManager;

	@Mock
	private CallAdvisorChain callAdvisorChain;

	@Mock
	private StreamAdvisorChain streamAdvisorChain;

	@Test
	void whenToolCallingManagerIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallingAdvisor.builder().toolCallingManager(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallingManager must not be null");
	}

	@Test
	void whenAdvisorOrderIsOutOfRangeThenThrow() {
		assertThatThrownBy(() -> ToolCallingAdvisor.builder().advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		assertThatThrownBy(() -> ToolCallingAdvisor.builder().advisorOrder(BaseAdvisor.LOWEST_PRECEDENCE).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
	}

	@Test
	void testBuilderMethodChaining() {
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;

		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void testDefaultValues() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 300);
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void whenToolExecutionEligibilityCheckerIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallingAdvisor.builder()
			.toolExecutionEligibilityChecker((ToolExecutionEligibilityChecker) null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolExecutionEligibilityChecker must not be null");
	}

	@Test
	void customCheckerSuppressesToolExecutionInCallPath() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityChecker(chatResponse -> false)
			.build();

		ChatClientRequest request = createMockRequest();
		ChatClientResponse responseWithToolCall = createMockResponse(true);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> responseWithToolCall);
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		assertThat(result).isEqualTo(responseWithToolCall);
		verify(this.toolCallingManager, times(0)).executeToolCalls(any(), any());
	}

	@Test
	void customCheckerForcesToolExecutionOnResponseWithoutToolCalls() {
		// Checker says "tool call" on the first invocation, "done" on the second —
		// regardless of what hasToolCalls() returns on the response.
		int[] checkCount = { 0 };
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityChecker(chatResponse -> ++checkCount[0] == 1)
			.build();

		ChatClientRequest request = createMockRequest();
		ChatClientResponse responseWithoutToolCall = createMockResponse(false);

		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> {
			callCount[0]++;
			return responseWithoutToolCall;
		});
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		List<Message> conversationHistory = List.of(new UserMessage("test"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.build();
		when(this.toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(toolExecutionResult);

		ChatClientResponse result = advisor.adviseCall(request, realChain);

		// Loop ran twice: checker returned true on first call (execute tools), false on
		// second (stop).
		assertThat(callCount[0]).isEqualTo(2);
		verify(this.toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
		assertThat(result).isEqualTo(responseWithoutToolCall);
	}

	@Test
	void customCheckerSuppressesToolExecutionInStreamPath() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityChecker(chatResponse -> false)
			.streamToolCallResponses(true)
			.build();

		ChatClientRequest request = createMockRequest();
		ChatClientResponse responseWithToolCall = createMockResponse(true);

		TerminalStreamAdvisor terminalAdvisor = new TerminalStreamAdvisor(
				(req, chain) -> Flux.just(responseWithToolCall));
		StreamAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		List<ChatClientResponse> results = advisor.adviseStream(request, realChain).collectList().block();

		assertThat(results).isNotNull().hasSize(1);
		verify(this.toolCallingManager, times(0)).executeToolCalls(any(), any());
	}

	@Test
	void whenChatClientRequestIsNullThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

		assertThatThrownBy(() -> advisor.adviseCall(null, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientRequest must not be null");
	}

	@Test
	void whenCallAdvisorChainIsNullThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();
		ChatClientRequest request = createMockRequest();

		assertThatThrownBy(() -> advisor.adviseCall(request, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("callAdvisorChain must not be null");
	}

	@Test
	void whenOptionsAreNullThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

		Prompt prompt = new Prompt(List.of(new UserMessage("test")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		assertThatThrownBy(() -> advisor.adviseCall(request, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolCall Advisor requires ToolCallingChatOptions");
	}

	@Test
	void whenOptionsAreNotToolCallingChatOptionsThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

		ChatOptions nonToolOptions = mock(ChatOptions.class);
		Prompt prompt = new Prompt(List.of(new UserMessage("test")), nonToolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		assertThatThrownBy(() -> advisor.adviseCall(request, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolCall Advisor requires ToolCallingChatOptions");
	}

	@Test
	void testAdviseCallWithoutToolCalls() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
	void testAdviseStreamWithoutToolCalls() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();
		ChatClientRequest request = createMockRequest();

		assertThatThrownBy(() -> advisor.adviseStream(request, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("streamAdvisorChain must not be null");
	}

	@Test
	void whenStreamChatClientRequestIsNullThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

		assertThatThrownBy(() -> advisor.adviseStream(null, this.streamAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientRequest must not be null");
	}

	@Test
	void whenStreamOptionsAreNotToolCallingChatOptionsThenThrow() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();

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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().build();
		assertThat(advisor.getName()).isEqualTo("Tool Calling Advisor");
	}

	@Test
	void testGetOrder() {
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 400;
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().advisorOrder(customOrder).build();

		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	@Test
	void testBuilderGetters() {
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;

		ToolCallingAdvisor.Builder<?> builder = ToolCallingAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder);

		assertThat(builder.getToolCallingManager()).isEqualTo(customManager);
		assertThat(builder.getAdvisorOrder()).isEqualTo(customOrder);
	}

	@Test
	void testConversationHistoryEnabledDefaultValue() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build();

		// By default, conversationHistoryEnabled should be true
		// Verify via the tool call iteration behavior - with history enabled, the full
		// conversation history is used
		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.conversationHistoryEnabled(false)
			.build();

		ChatClientRequest request = createMockRequest();
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
		ToolCallingAdvisor.Builder<?> builder = ToolCallingAdvisor.builder();

		// By default, streamToolCallResponses should be false
		assertThat(builder.isStreamToolCallResponses()).isFalse();
	}

	@Test
	void testStreamToolCallResponsesBuilderMethod() {
		ToolCallingAdvisor.Builder<?> builder = ToolCallingAdvisor.builder().streamToolCallResponses(false);

		assertThat(builder.isStreamToolCallResponses()).isFalse();
	}

	@Test
	void testAdviseStreamWithToolCallResponsesEnabled() {
		// Create advisor with tool call streaming explicitly enabled
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.streamToolCallResponses(true)
			.build();

		ChatClientRequest request = createMockRequest();
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
	void testDisableInternalConversationHistoryBuilderMethod() {
		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.disableInternalConversationHistory()
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
		TestableToolCallingAdvisor advisor = new TestableToolCallingAdvisor(this.toolCallingManager,
				BaseAdvisor.HIGHEST_PRECEDENCE + 300, hookCallCounts);

		ChatClientRequest request = createMockRequest();
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

		TestableToolCallingAdvisor advisor = new TestableToolCallingAdvisor(this.toolCallingManager,
				BaseAdvisor.HIGHEST_PRECEDENCE + 300, hookCallCounts);

		ChatClientRequest request = createMockRequest();
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

		TestableToolCallingAdvisor advisor = TestableToolCallingAdvisor.testBuilder()
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

		ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder().build();

		Prompt prompt = new Prompt(instructions, toolOptions);

		ChatClientRequest mockRequest = mock(ChatClientRequest.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(mockRequest.prompt()).thenReturn(prompt);
		when(mockRequest.context()).thenReturn(Map.of());

		when(mockRequest.copy()).thenAnswer(invocation -> {
			Prompt copiedPrompt = new Prompt(instructions, toolOptions);
			return ChatClientRequest.builder().prompt(copiedPrompt).build();
		});

		return mockRequest;
	}

	private ChatClientRequest createMockRequest() {
		List<Message> instructions = List.of(new UserMessage("test message"));

		ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder().build();

		Prompt prompt = new Prompt(instructions, toolOptions);

		ChatClientRequest mockRequest = mock(ChatClientRequest.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(mockRequest.prompt()).thenReturn(prompt);
		when(mockRequest.context()).thenReturn(Map.of());

		when(mockRequest.copy()).thenAnswer(invocation -> {
			Prompt copiedPrompt = new Prompt(instructions, toolOptions);
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
	 * Test subclass of ToolCallingAdvisor to verify extensibility and hook methods.
	 */
	private static class TestableToolCallingAdvisor extends ToolCallingAdvisor {

		private final int[] hookCallCounts;

		TestableToolCallingAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, int[] hookCallCounts) {
			super(toolCallingManager, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, advisorOrder, true, true);
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

		static class TestableBuilder extends ToolCallingAdvisor.Builder<TestableBuilder> {

			@Override
			protected TestableBuilder self() {
				return this;
			}

			@Override
			public TestableToolCallingAdvisor build() {
				return new TestableToolCallingAdvisor(getToolCallingManager(), getAdvisorOrder(), null);
			}

		}

	}

}
