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

package org.springframework.ai.chat.client.advisor.toolsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Streaming-path tests for {@link ToolSearchToolCallingAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ToolSearchToolCallingAdvisorStreamTests {

	@Mock
	private ToolCallingManager toolCallingManager;

	@Mock
	private ToolIndex toolIndex;

	@Test
	void streamInitializeLoopIndexesTools() {
		ToolSearchToolCallingAdvisor advisor = advisor("\n\nTest suffix");

		ToolDefinition td1 = toolDef("tool1", "Desc 1");
		ToolDefinition td2 = toolDef("tool2", "Desc 2");
		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(td1, td2));

		drainStream(advisor, (req, ch) -> Flux.just(withContext(response(false), req)));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ToolReference>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.toolIndex).indexTools(anyString(), captor.capture());
		assertThat(captor.getValue()).extracting(ToolReference::toolName).containsExactly("tool1", "tool2");
	}

	@Test
	void streamInitializeLoopAugmentsSystemMessage() {
		String suffix = "\n\nCUSTOM SUFFIX";
		ToolSearchToolCallingAdvisor advisor = advisor(suffix);
		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());

		ChatClientRequest[] captured = new ChatClientRequest[1];
		drainStream(advisor, (req, ch) -> {
			captured[0] = req;
			return Flux.just(withContext(response(false), req));
		});

		SystemMessage sysMsg = (SystemMessage) captured[0].prompt().getInstructions().get(0);
		assertThat(sysMsg.getText()).contains("System message").contains(suffix);
	}

	@Test
	void streamFirstRequest_indexesOnce() {
		ToolSearchToolCallingAdvisor advisor = advisor();
		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());

		drainStream(advisor, (req, ch) -> Flux.just(withContext(response(false), req)));

		// clearIndex is called once for the fingerprint miss; doAfterStream no longer
		// clears — the index persists for the next turn.
		verify(this.toolIndex, times(1)).clearIndex(anyString());
		verify(this.toolIndex, times(1)).indexTools(anyString(), any());
	}

	@Test
	void streamBeforeIterationExtractsToolReferences() {
		ToolSearchToolCallingAdvisor advisor = advisor();
		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());

		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("id1", "toolSearchTool",
					"[\"weatherTool\", \"calculatorTool\"]")))
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("System message"), new UserMessage("test"), toolResponseMessage),
				new TestToolCallingChatOptions());
		Map<String, Object> extractContext = new ConcurrentHashMap<>();
		extractContext.put(ChatMemory.CONVERSATION_ID, "test-session-id");
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.build()
			.mutate()
			.context(extractContext)
			.build();

		ChatClientRequest[] captured = new ChatClientRequest[1];
		StreamAdvisorChain chain = buildChain(advisor, (req, ch) -> {
			captured[0] = req;
			return Flux.just(withContext(response(false), req));
		});
		advisor.adviseStream(request, chain).collectList().block();

		ToolCallingChatOptions opts = (ToolCallingChatOptions) captured[0].prompt().getOptions();
		assertThat(opts.getToolCallbacks()).extracting(cb -> cb.getToolDefinition().name())
			.containsExactly("toolSearchTool");
	}

	@Test
	void streamUsesConversationIdFromContext() {
		String conversationId = "stream-session-42";
		ToolSearchToolCallingAdvisor advisor = advisor();
		when(this.toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());

		Map<String, Object> ctx = new ConcurrentHashMap<>();
		ctx.put(ChatMemory.CONVERSATION_ID, conversationId);
		ChatClientRequest request = createRequest().mutate().context(ctx).build();

		StreamAdvisorChain chain = buildChain(advisor, (req, ch) -> Flux.just(withContext(response(false), req)));
		advisor.adviseStream(request, chain).collectList().block();

		// clearIndex once for the fingerprint miss; doAfterStream does not clear.
		ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.toolIndex, times(1)).clearIndex(idCaptor.capture());
		assertThat(idCaptor.getValue()).isEqualTo(conversationId);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private ToolSearchToolCallingAdvisor advisor() {
		return advisor("\n\nDefault suffix");
	}

	private ToolSearchToolCallingAdvisor advisor(String suffix) {
		return ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolIndex(this.toolIndex)
			.systemMessageSuffix(suffix)
			.build();
	}

	private void drainStream(ToolSearchToolCallingAdvisor advisor,
			BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> terminal) {
		buildChain(advisor, terminal);
		advisor.adviseStream(createRequest(), buildChain(advisor, terminal)).collectList().block();
	}

	private StreamAdvisorChain buildChain(ToolSearchToolCallingAdvisor advisor,
			BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> terminal) {
		return DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, new TerminalStreamAdvisor(terminal)))
			.build();
	}

	private ChatClientRequest createRequest() {
		Map<String, Object> context = new ConcurrentHashMap<>();
		context.put(ChatMemory.CONVERSATION_ID, "test-session-id");
		return ChatClientRequest.builder()
			.prompt(new Prompt(List.of(new SystemMessage("System message"), new UserMessage("test")),
					new TestToolCallingChatOptions()))
			.build()
			.mutate()
			.context(context)
			.build();
	}

	private ChatClientResponse response(boolean hasToolCalls) {
		AssistantMessage msg = hasToolCalls ? AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("id", "tool", "name", "{}")))
			.build() : new AssistantMessage("response");
		return ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder().generations(List.of(new Generation(msg))).build())
			.context(new ConcurrentHashMap<>())
			.build();
	}

	// Merges the request context into the response so doAfterStream can find the session
	// ID.
	private ChatClientResponse withContext(ChatClientResponse response, ChatClientRequest request) {
		Map<String, Object> merged = new ConcurrentHashMap<>(request.context());
		merged.putAll(response.context());
		return response.mutate().context(merged).build();
	}

	private static ToolDefinition toolDef(String name, String description) {
		return DefaultToolDefinition.builder().name(name).description(description).inputSchema("{}").build();
	}

	private static class TerminalStreamAdvisor implements StreamAdvisor {

		private final BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> fn;

		TerminalStreamAdvisor(BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> fn) {
			this.fn = fn;
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
		public Flux<ChatClientResponse> adviseStream(ChatClientRequest req, StreamAdvisorChain chain) {
			return this.fn.apply(req, chain);
		}

	}

	private static class TestToolCallingChatOptions implements ToolCallingChatOptions {

		private List<ToolCallback> toolCallbacks = new ArrayList<>();

		private Map<String, Object> toolContext = new HashMap<>();

		@Override
		public List<ToolCallback> getToolCallbacks() {
			return this.toolCallbacks;
		}

		@Override
		public Map<String, Object> getToolContext() {
			return this.toolContext;
		}

		@Override
		public ToolCallingChatOptions.Builder<?> mutate() {
			return ToolCallingChatOptions.builder().toolCallbacks(this.toolCallbacks).toolContext(this.toolContext);
		}

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
