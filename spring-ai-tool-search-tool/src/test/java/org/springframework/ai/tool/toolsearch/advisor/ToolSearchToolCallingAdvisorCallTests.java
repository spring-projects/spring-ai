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

package org.springframework.ai.tool.toolsearch.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.eviction.LruEvictionStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolSearchToolCallingAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ToolSearchToolCallingAdvisorCallTests {

	@Mock
	private ToolCallingManager toolCallingManager;

	@Mock
	private ToolIndex toolIndex;

	@Test
	void whenToolIndexIsNullThenThrow() {
		assertThatThrownBy(() -> ToolSearchToolCallingAdvisor.builder().build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testBuilderMethodChaining() {
		ToolIndex customSearcher = mock(ToolIndex.class);
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;
		String customSuffix = "\n\nCustom suffix";

		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder)
			.toolIndex(customSearcher)
			.systemMessageSuffix(customSuffix)
			.referenceToolNameAccumulation(false)
			.maxResults(10)
			.evictionStrategy(new LruEvictionStrategy(50))
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("ToolSearchToolCallingAdvisor");
	}

	@Test
	void testDefaultValues() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder().toolIndex(this.toolIndex).build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 300);
		assertThat(advisor.getName()).isEqualTo("ToolSearchToolCallingAdvisor");
	}

	@Test
	void testInitializeLoopIndexesTools() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.systemMessageSuffix("\n\nTest suffix")
			.build();

		// Create mock tool definitions
		ToolDefinition toolDef1 = DefaultToolDefinition.builder()
			.name("tool1")
			.description("Description for tool1")
			.inputSchema("{}")
			.build();
		ToolDefinition toolDef2 = DefaultToolDefinition.builder()
			.name("tool2")
			.description("Description for tool2")
			.inputSchema("{}")
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef1, toolDef2));

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify that indexTools was called once with all tool definitions
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ToolReference>> toolRefsCaptor = ArgumentCaptor.forClass(List.class);

		verify(this.toolIndex).indexTools(anyString(), toolRefsCaptor.capture());

		List<ToolReference> indexedTools = toolRefsCaptor.getValue();
		assertThat(indexedTools).hasSize(2);
		assertThat(indexedTools.get(0).toolName()).isEqualTo("tool1");
		assertThat(indexedTools.get(1).toolName()).isEqualTo("tool2");
	}

	@Test
	void testInitializeLoopAugmentsSystemMessage() {
		String customSuffix = "\n\nCUSTOM SUFFIX FOR TESTING";
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.systemMessageSuffix(customSuffix)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		SystemMessage originalSystemMessage = new SystemMessage("Original system message");
		UserMessage userMessage = new UserMessage("test");

		ToolCallingChatOptions toolOptions = mock(ToolCallingChatOptions.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(toolOptions.getInternalToolExecutionEnabled()).thenReturn(false);
		doReturn(ToolCallingChatOptions.builder()).when(toolOptions).mutate();

		Prompt prompt = new Prompt(List.of(originalSystemMessage, userMessage), toolOptions);
		Map<String, Object> augmentContext = new ConcurrentHashMap<>();
		augmentContext.put(ChatMemory.CONVERSATION_ID, "test-session-id");
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.build()
			.mutate()
			.context(augmentContext)
			.build();

		ChatClientResponse response = createMockResponse(false);

		// Capture the augmented request
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify system message was augmented
		assertThat(capturedRequest[0]).isNotNull();
		List<Message> messages = capturedRequest[0].prompt().getInstructions();
		SystemMessage augmentedSystemMessage = (SystemMessage) messages.get(0);
		assertThat(augmentedSystemMessage.getText()).contains("Original system message");
		assertThat(augmentedSystemMessage.getText()).contains(customSuffix);
	}

	/**
	 * First request for a new session: clearIndex + indexTools are called once
	 * (fingerprint miss). No cleanup happens in doFinalizeLoop — the index stays alive
	 * for the next turn.
	 */
	@Test
	void testFirstRequest_indexesToolsOnce() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// clearIndex once (fingerprint miss) + indexTools once
		verify(this.toolIndex, times(1)).clearIndex(anyString());
		verify(this.toolIndex, times(1)).indexTools(anyString(), any());
	}

	/**
	 * Second request for the same session with the same tool set: clearIndex and
	 * indexTools are NOT called again — the cached index is reused.
	 */
	@Test
	void testSecondRequest_sameTools_skipsReindexing() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		ToolDefinition toolDef = DefaultToolDefinition.builder()
			.name("weatherTool")
			.description("Gets the weather")
			.inputSchema("{}")
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef));

		ChatClientRequest request = createMockRequest(true, "conv-1");
		ChatClientResponse response = createMockResponse(false);
		CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();

		// First request — indexes once
		advisor.adviseCall(request, chain);
		verify(this.toolIndex, times(1)).indexTools(anyString(), any());

		// Second request — same session, same tools → no re-indexing
		chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "conv-1"), chain);

		verify(this.toolIndex, times(1)).indexTools(anyString(), any()); // still only
																			// 1
		verify(this.toolIndex, times(1)).clearIndex(anyString()); // still only 1
	}

	/**
	 * Second request for the same session with a changed tool set: clearIndex and
	 * indexTools are called again to refresh the index.
	 */
	@Test
	void testSecondRequest_differentTools_reindexes() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		ToolDefinition toolDef1 = DefaultToolDefinition.builder()
			.name("weatherTool")
			.description("Gets the weather")
			.inputSchema("{}")
			.build();
		ToolDefinition toolDef2 = DefaultToolDefinition.builder()
			.name("calculatorTool")
			.description("Does math")
			.inputSchema("{}")
			.build();

		// First request: only weatherTool
		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef1));

		ChatClientResponse response = createMockResponse(false);
		CallAdvisorChain chain1 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "conv-1"), chain1);
		verify(this.toolIndex, times(1)).indexTools(anyString(), any());

		// Second request: different tool set → re-index
		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef1, toolDef2));
		CallAdvisorChain chain2 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "conv-1"), chain2);

		verify(this.toolIndex, times(2)).indexTools(anyString(), any()); // re-indexed
		verify(this.toolIndex, times(2)).clearIndex(anyString()); // clear before each
																	// index
	}

	/**
	 * With LruEvictionStrategy(1), accessing a second session evicts the first.
	 */
	@Test
	void testLruEviction_evictsLruSessionWhenCapacityExceeded() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.evictionStrategy(new LruEvictionStrategy(1))
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientResponse response = createMockResponse(false);

		// Session A
		CallAdvisorChain chainA = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "session-A"), chainA);

		// Verify session A was indexed (clearIndex + indexTools for fingerprint miss)
		ArgumentCaptor<String> clearCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.toolIndex, times(1)).clearIndex(clearCaptor.capture());
		assertThat(clearCaptor.getAllValues()).containsExactly("session-A");

		// Session B — should evict session A (capacity = 1)
		CallAdvisorChain chainB = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "session-B"), chainB);

		// clearIndex called twice: once for session-A eviction, once for session-B
		// fingerprint miss
		verify(this.toolIndex, times(3)).clearIndex(clearCaptor.capture());
		List<String> allClears = clearCaptor.getAllValues();
		assertThat(allClears).contains("session-A", "session-B");
	}

	/**
	 * evictSession() clears the index and removes the fingerprint so the next request
	 * re-indexes.
	 */
	@Test
	void testEvictSession_forcesReindexOnNextRequest() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		ToolDefinition toolDef = DefaultToolDefinition.builder()
			.name("tool1")
			.description("desc")
			.inputSchema("{}")
			.build();
		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef));

		ChatClientResponse response = createMockResponse(false);

		// First request — indexes
		CallAdvisorChain chain1 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "conv-1"), chain1);
		verify(this.toolIndex, times(1)).indexTools(anyString(), any());

		// Explicit eviction
		advisor.evictSession("conv-1");
		verify(this.toolIndex, times(2)).clearIndex("conv-1");

		// Next request must re-index (fingerprint was removed)
		CallAdvisorChain chain2 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();
		advisor.adviseCall(createMockRequest(true, "conv-1"), chain2);
		verify(this.toolIndex, times(2)).indexTools(anyString(), any());
	}

	@Test
	void testBeforeCallExtractsToolReferences() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		// Create a request with tool response messages containing tool references
		ToolResponseMessage.ToolResponse toolSearchResponse = new ToolResponseMessage.ToolResponse("id1",
				"toolSearchTool", "[\"weatherTool\", \"calculatorTool\"]");
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolSearchResponse))
			.build();

		SystemMessage systemMessage = new SystemMessage("System message");
		UserMessage userMessage = new UserMessage("test");
		AssistantMessage assistantMessage = AssistantMessage.builder().content("Using tool search").build();

		// Use real TestToolCallingChatOptions instead of mocking
		TestToolCallingChatOptions toolOptions = new TestToolCallingChatOptions();

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage, assistantMessage, toolResponseMessage),
				toolOptions);
		Map<String, Object> extractContext = new ConcurrentHashMap<>();
		extractContext.put(ChatMemory.CONVERSATION_ID, "test-session-id");
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.build()
			.mutate()
			.context(extractContext)
			.build();

		ChatClientResponse response = createMockResponse(false);

		// Capture the request to verify tool names were extracted
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify tool names were extracted
		assertThat(capturedRequest[0]).isNotNull();
		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();
		// The tool search tool callback should be present
		assertThat(capturedOptions.getToolCallbacks()).hasSize(1);
		assertThat(capturedOptions.getToolCallbacks().get(0).getToolDefinition().name()).isEqualTo("toolSearchTool");
		assertThat(capturedOptions.getToolNames()).contains("weatherTool", "calculatorTool");
	}

	@Test
	void testConversationIdFromContext() {
		String expectedConversationId = "test-conversation-123";
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientResponse response = createMockResponse(false);
		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);
		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(createMockRequest(true, expectedConversationId), realChain);

		// clearIndex is called exactly once for the fingerprint miss; doFinalizeLoop no
		// longer clears the index.
		ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.toolIndex, times(1)).clearIndex(sessionIdCaptor.capture());
		assertThat(sessionIdCaptor.getValue()).isEqualTo(expectedConversationId);
	}

	@Test
	void testToolSearchToolCallbackIsRegistered() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		// Capture the request to verify toolSearchTool callback is present
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		assertThat(capturedRequest[0]).isNotNull();
		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();
		assertThat(capturedOptions.getToolCallbacks()).isNotEmpty();

		// Find the toolSearchTool callback
		boolean foundToolSearchTool = capturedOptions.getToolCallbacks()
			.stream()
			.anyMatch(callback -> "toolSearchTool".equals(callback.getToolDefinition().name()));

		assertThat(foundToolSearchTool).isTrue();
	}

	@Test
	void testCachedToolCallbacksAreUsed() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		// Create a mock tool callback
		ToolCallback mockToolCallback = mock(ToolCallback.class);
		ToolDefinition mockToolDef = DefaultToolDefinition.builder()
			.name("weatherTool")
			.description("Gets weather")
			.inputSchema("{}")
			.build();
		when(mockToolCallback.getToolDefinition()).thenReturn(mockToolDef);

		// Use real TestToolCallingChatOptions with the tool callback configured
		TestToolCallingChatOptions toolOptions = new TestToolCallingChatOptions();
		toolOptions.setToolCallbacks(List.of(mockToolCallback));

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(mockToolDef));

		// Create a request with tool response message referencing the weatherTool
		ToolResponseMessage.ToolResponse toolSearchResponse = new ToolResponseMessage.ToolResponse("id1",
				"toolSearchTool", "[\"weatherTool\"]");
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolSearchResponse))
			.build();

		UserMessage userMessage = new UserMessage("test");

		Prompt prompt = new Prompt(List.of(userMessage, toolResponseMessage), toolOptions);
		Map<String, Object> cachedContext = new ConcurrentHashMap<>();
		cachedContext.put(ChatMemory.CONVERSATION_ID, "test-session-id");
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.build()
			.mutate()
			.context(cachedContext)
			.build();

		ChatClientResponse response = createMockResponse(false);

		// Capture the request to verify both toolSearchTool and weatherTool are present
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify the tool was indexed
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ToolReference>> indexedToolsCaptor = ArgumentCaptor.forClass(List.class);
		verify(this.toolIndex).indexTools(anyString(), indexedToolsCaptor.capture());
		assertThat(indexedToolsCaptor.getValue()).extracting(ToolReference::toolName).containsExactly("weatherTool");

		// Verify capturedOptions contains both toolSearchTool and weatherTool
		assertThat(capturedRequest[0]).isNotNull();
		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();

		assertThat(capturedOptions.getToolCallbacks()
			.stream()
			.anyMatch(cb -> "toolSearchTool".equals(cb.getToolDefinition().name()))).isTrue();

		assertThat(capturedOptions.getToolCallbacks()
			.stream()
			.anyMatch(cb -> "weatherTool".equals(cb.getToolDefinition().name()))).isTrue();
	}

	@Test
	void toolSearchToolUsesLlmMaxResultsOverAdvisorDefault() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.maxResults(3)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());
		when(this.toolIndex.search(any())).thenReturn(new ToolSearchResponse(List.of(), null, null));

		ToolCallingChatOptions opts = captureToolOptions(advisor);
		invokeToolSearchTool(opts, "weather", 7);

		ArgumentCaptor<ToolSearchRequest> captor = ArgumentCaptor.forClass(ToolSearchRequest.class);
		verify(this.toolIndex).search(captor.capture());
		assertThat(captor.getValue().maxResults()).isEqualTo(7);
	}

	@Test
	void toolSearchToolFallsBackToAdvisorMaxResultsWhenLlmOmits() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.maxResults(3)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());
		when(this.toolIndex.search(any())).thenReturn(new ToolSearchResponse(List.of(), null, null));

		ToolCallingChatOptions opts = captureToolOptions(advisor);
		invokeToolSearchTool(opts, "weather", null);

		ArgumentCaptor<ToolSearchRequest> captor = ArgumentCaptor.forClass(ToolSearchRequest.class);
		verify(this.toolIndex).search(captor.capture());
		assertThat(captor.getValue().maxResults()).isEqualTo(3);
	}

	/**
	 * doFinalizeLoop must NOT call clearIndex — the index persists for the next turn.
	 */
	@Test
	void testFinalizeLoop_doesNotClearIndex() {
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientResponse response = createMockResponse(false);
		CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, c) -> response)))
			.build();

		advisor.adviseCall(createMockRequest(true), chain);

		// Only the fingerprint-miss clearIndex at init; finalize does NOT add another.
		verify(this.toolIndex, times(1)).clearIndex(anyString());
	}

	// Helper methods

	private ToolCallingChatOptions captureToolOptions(ToolSearchToolCallingAdvisor advisor) {
		ChatClientRequest[] captured = new ChatClientRequest[1];
		CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalCallAdvisor((req, ch) -> {
				captured[0] = req;
				return createMockResponse(false);
			})))
			.build();
		advisor.adviseCall(createMockRequest(true), chain);
		return (ToolCallingChatOptions) captured[0].prompt().getOptions();
	}

	private void invokeToolSearchTool(ToolCallingChatOptions opts, String toolInputQuery, Integer toolInputMaxResults) {
		ToolCallback callback = opts.getToolCallbacks()
			.stream()
			.filter(cb -> "toolSearchTool".equals(cb.getToolDefinition().name()))
			.findFirst()
			.orElseThrow();
		String schema = callback.getToolDefinition().inputSchema();
		String queryParam = schema.contains("\"query\"") ? "query" : "arg0";
		String maxResultsParam = schema.contains("\"maxResults\"") ? "maxResults" : "arg1";

		String toolInput;
		if (toolInputMaxResults != null) {
			toolInput = "{\"" + queryParam + "\":\"" + toolInputQuery + "\",\"" + maxResultsParam + "\":"
					+ toolInputMaxResults + "}";
		}
		else {
			toolInput = "{\"" + queryParam + "\":\"" + toolInputQuery + "\"}";
		}

		String sessionId = opts.getToolContext().get("toolSearchToolSessionId").toString();
		callback.call(toolInput,
				new org.springframework.ai.chat.model.ToolContext(Map.of("toolSearchToolSessionId", sessionId)));
	}

	private ChatClientRequest createMockRequest(boolean withToolCallingOptions) {
		return createMockRequest(withToolCallingOptions, "test-session-id");
	}

	private ChatClientRequest createMockRequest(boolean withToolCallingOptions, String conversationId) {
		List<Message> instructions = List.of(new SystemMessage("System message"), new UserMessage("test message"));

		ChatOptions options = null;
		if (withToolCallingOptions) {
			// Use a real TestToolCallingChatOptions instead of mocking to avoid Byte
			// Buddy issues on Java 25
			options = new TestToolCallingChatOptions();
		}

		Prompt prompt = new Prompt(instructions, options);

		Map<String, Object> context = new ConcurrentHashMap<>();
		context.put(ChatMemory.CONVERSATION_ID, conversationId);

		return ChatClientRequest.builder().prompt(prompt).build().mutate().context(context).build();
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls, Map<String, Object> context) {
		// Create real objects instead of mocking to avoid Byte Buddy issues with Java 25
		AssistantMessage assistantMessage;
		if (hasToolCalls) {
			// Create an assistant message with a tool call to make hasToolCalls() return
			// true
			assistantMessage = AssistantMessage.builder()
				.content("response")
				.toolCalls(List.of(new AssistantMessage.ToolCall("id1", "tool", "toolName", "{}")))
				.build();
		}
		else {
			assistantMessage = new AssistantMessage("response");
		}
		Generation generation = new Generation(assistantMessage);

		// Create a real ChatResponse - hasToolCalls() is derived from generations' tool
		// calls
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of(generation)).build();

		// Create a real ChatClientResponse using the builder with context from request
		return ChatClientResponse.builder()
			.chatResponse(chatResponse)
			.context(context != null ? context : new ConcurrentHashMap<>())
			.build();
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls) {
		return createMockResponse(hasToolCalls, new ConcurrentHashMap<>());
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
			ChatClientResponse response = this.responseFunction.apply(req, chain);
			// Ensure the response has the context from the request (including
			// cachedToolCallbacks, etc.)
			Map<String, Object> mergedContext = new ConcurrentHashMap<>(req.context());
			mergedContext.putAll(response.context());
			return response.mutate().context(mergedContext).build();
		}

	}

	/**
	 * Simple test implementation of ToolCallingChatOptions to avoid Mockito/ByteBuddy
	 * issues on Java 25. Implements the required methods with sensible defaults.
	 */
	private static class TestToolCallingChatOptions implements ToolCallingChatOptions {

		private boolean internalToolExecutionEnabled = true;

		private List<ToolCallback> toolCallbacks = new ArrayList<>();

		private java.util.Set<String> toolNames = new java.util.HashSet<>();

		private Map<String, Object> toolContext = new java.util.HashMap<>();

		@Override
		public List<ToolCallback> getToolCallbacks() {
			return this.toolCallbacks;
		}

		public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks != null ? toolCallbacks : new ArrayList<>();
		}

		@Override
		public java.util.Set<String> getToolNames() {
			return this.toolNames;
		}

		@Override
		public Boolean getInternalToolExecutionEnabled() {
			return this.internalToolExecutionEnabled;
		}

		@Override
		public Map<String, Object> getToolContext() {
			return this.toolContext;
		}

		@Override
		public ToolCallingChatOptions.Builder<?> mutate() {
			return ToolCallingChatOptions.builder()
				.toolCallbacks(this.toolCallbacks)
				.toolNames(this.toolNames)
				.toolContext(this.toolContext)
				.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}

		// ChatOptions methods - return null or defaults for unused options
		@Override
		public String getModel() {
			return null;
		}

		@Override
		public Double getFrequencyPenalty() {
			return null;
		}

		@Override
		public Integer getMaxTokens() {
			return null;
		}

		@Override
		public Double getPresencePenalty() {
			return null;
		}

		@Override
		public List<String> getStopSequences() {
			return null;
		}

		@Override
		public Double getTemperature() {
			return null;
		}

		@Override
		public Integer getTopK() {
			return null;
		}

		@Override
		public Double getTopP() {
			return null;
		}

	}

}
