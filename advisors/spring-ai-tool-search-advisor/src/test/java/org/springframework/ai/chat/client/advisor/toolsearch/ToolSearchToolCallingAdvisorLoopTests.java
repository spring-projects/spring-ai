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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.eviction.LruEvictionStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolSearchToolCallingAdvisorLoopTests {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void alwaysDeclaredToolRemainsExecutableAcrossSearchIterations(boolean streaming) {
		ToolIndex index = mock(ToolIndex.class);
		when(index.search(any())).thenReturn(new ToolSearchResponse(
				List.of(ToolReference.builder().toolName("optionalTool").summary("Optional tool").build()), null,
				null));
		AtomicInteger executions = new AtomicInteger();
		ToolCallback core = FunctionToolCallback.builder("coreTool", () -> executions.incrementAndGet())
			.description("Core tool")
			.build();
		ToolCallback optional = FunctionToolCallback.builder("optionalTool", () -> "optional")
			.description("Optional tool")
			.build();
		ToolSearchToolCallingAdvisor advisor = ToolSearchToolCallingAdvisor.builder()
			.toolIndex(index)
			.alwaysDeclared(tool -> tool == core)
			.build();
		ScriptedModel model = new ScriptedModel(List.of(toolCall("core-1", "coreTool", "{}"),
				toolCall("search-1", "toolSearchTool", "{\"query\":\"optional\"}"),
				toolCall("core-2", "coreTool", "{}"), new AssistantMessage("done")));

		run(advisor, model, List.of(core, optional), streaming);

		assertThat(executions.get()).isEqualTo(2);
		assertThat(model.declarations).containsExactly(List.of("coreTool", "toolSearchTool"),
				List.of("coreTool", "toolSearchTool"), List.of("coreTool", "optionalTool", "toolSearchTool"),
				List.of("coreTool", "optionalTool", "toolSearchTool"));
		assertIndexedOptionalTool(index);
		verify(index).search(any());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void legacyConstructorPreservesDeferredToolsByDefault(boolean streaming) {
		ToolIndex index = mock(ToolIndex.class);
		ToolCallback optional = FunctionToolCallback.builder("optionalTool", () -> "optional")
			.description("Optional tool")
			.build();
		ScriptedModel model = new ScriptedModel(List.of(new AssistantMessage("done")));

		run(new LegacyAdvisor(index), model, List.of(optional), streaming);

		assertThat(model.declarations).containsExactly(List.of("toolSearchTool"));
		assertIndexedOptionalTool(index);
	}

	private static void assertIndexedOptionalTool(ToolIndex index) {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ToolReference>> captor = ArgumentCaptor.forClass(List.class);
		verify(index).indexTools(eq("loop-session"), captor.capture());
		assertThat(captor.getValue()).extracting(ToolReference::toolName).containsExactly("optionalTool");
	}

	private static AssistantMessage toolCall(String id, String name, String arguments) {
		return AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
			.build();
	}

	private static void run(ToolSearchToolCallingAdvisor advisor, ScriptedModel model, List<ToolCallback> callbacks,
			boolean streaming) {
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt("test", ToolCallingChatOptions.builder().toolCallbacks(callbacks).build()))
			.build()
			.mutate()
			.context(Map.of(ChatMemory.CONVERSATION_ID, "loop-session"))
			.build();
		DefaultAroundAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, model))
			.build();
		if (streaming) {
			List<ChatClientResponse> responses = advisor.adviseStream(request, chain)
				.collectList()
				.block(Duration.ofSeconds(10));
			assertThat(responses).isNotEmpty();
			assertThat(responses.get(responses.size() - 1).chatResponse().getResult().getOutput().getText())
				.isEqualTo("done");
		}
		else {
			assertThat(advisor.adviseCall(request, chain).chatResponse().getResult().getOutput().getText())
				.isEqualTo("done");
		}
	}

	private static final class LegacyAdvisor extends ToolSearchToolCallingAdvisor {

		private LegacyAdvisor(ToolIndex index) {
			super(ToolCallingManager.builder().build(), DEFAULT_ORDER, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER,
					index, "Test suffix", true, null, true, ChatMemory.CONVERSATION_ID, new LruEvictionStrategy(10));
		}

	}

	private static final class ScriptedModel implements CallAdvisor, StreamAdvisor {

		private final List<AssistantMessage> responses;

		private final List<List<String>> declarations = new ArrayList<>();

		private ScriptedModel(List<AssistantMessage> responses) {
			this.responses = responses;
		}

		private ChatClientResponse respond(ChatClientRequest request) {
			ToolCallingChatOptions options = (ToolCallingChatOptions) request.prompt().getOptions();
			this.declarations.add(
					options.getToolCallbacks().stream().map(tool -> tool.getToolDefinition().name()).sorted().toList());
			return ChatClientResponse.builder()
				.chatResponse(
						new ChatResponse(List.of(new Generation(this.responses.get(this.declarations.size() - 1)))))
				.context(request.context())
				.build();
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
			return respond(request);
		}

		@Override
		public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
			return Flux.defer(() -> Flux.just(respond(request)));
		}

		@Override
		public String getName() {
			return "scriptedModel";
		}

		@Override
		public int getOrder() {
			return 0;
		}

	}

}
