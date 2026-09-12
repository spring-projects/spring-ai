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

package org.springframework.ai.model.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultToolCallingManager}.
 *
 * @author Thomas Vitale
 * @author Sun Yuhan
 */
class DefaultToolCallingManagerTests {

	// BUILD

	@Test
	void whenDefaultArgumentsThenReturn() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThat(defaultToolExecutor).isNotNull();
	}

	@Test
	void whenObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(null)
			.toolCallbackResolver(mock(ToolCallbackResolver.class))
			.toolExecutionExceptionProcessor(mock(ToolExecutionExceptionProcessor.class))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("observationRegistry cannot be null");
	}

	@Test
	void whenToolCallbackResolverIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(mock(ObservationRegistry.class))
			.toolCallbackResolver(null)
			.toolExecutionExceptionProcessor(mock(ToolExecutionExceptionProcessor.class))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("toolCallbackResolver cannot be null");
	}

	@Test
	void whenToolCallExceptionConverterIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(mock(ObservationRegistry.class))
			.toolCallbackResolver(mock(ToolCallbackResolver.class))
			.toolExecutionExceptionProcessor(null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallExceptionConverter cannot be null");
	}

	// RESOLVE TOOL DEFINITIONS

	@Test
	void whenChatOptionsIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.resolveToolDefinitions(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatOptions cannot be null");
	}

	@Test
	void whenToolCallbackExistsThenResolve() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		List<ToolDefinition> toolDefinitions = toolCallingManager
			.resolveToolDefinitions(ToolCallingChatOptions.builder().toolCallbacks(toolCallback).build());

		assertThat(toolDefinitions).containsExactly(toolCallback.getToolDefinition());
	}

	// EXECUTE TOOL CALLS

	@Test
	void whenPromptIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(null, mock(ChatResponse.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenChatResponseIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(mock(Prompt.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatResponse cannot be null");
	}

	@Test
	void whenNoToolCallInChatResponseThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(mock(Prompt.class),
				ChatResponse.builder().generations(List.of()).build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No tool call requested by the chat model");
	}

	@Test
	void whenToolCallbackFallbackIsDefaultThenUnlistedToolErrorIsProcessed() {
		ToolCallback resolverOnlyTool = new TestToolCallback("resolverOnlyTool");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(resolverOnlyTool));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.toolExecutionExceptionProcessor(exception -> {
				assertThat(exception.getToolDefinition().name()).isEqualTo("resolverOnlyTool");
				assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
				return "Tool is not available";
			})
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List
					.of(new AssistantMessage.ToolCall("resolverOnlyTool", "function", "resolverOnlyTool", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("resolverOnlyTool", "resolverOnlyTool", "Tool is not available")))
			.build();
		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenToolCallbackResolverEnabledThenUnlistedToolIsResolved() {
		ToolCallback resolverOnlyTool = new TestToolCallback("resolverOnlyTool");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(resolverOnlyTool));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List
					.of(new AssistantMessage.ToolCall("resolverOnlyTool", "function", "resolverOnlyTool", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("resolverOnlyTool", "resolverOnlyTool", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenSingleToolCallInChatResponseThenExecute() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenSingleToolCallWithReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallback = new TestToolCallback("toolA", true);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isTrue();
	}

	@Test
	void whenMultipleToolCallsInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA");
		ToolCallback toolCallbackB = new TestToolCallback("toolB");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"),
						new AssistantMessage.ToolCall("toolB", "function", "toolB", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!"),
					new ToolResponse("toolB", "toolB", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenDuplicateMixedToolCallsInChatResponseThenExecute() {
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt(new UserMessage("Hello"),
				ToolCallingChatOptions.builder().toolCallbacks(new TestToolCallback("toolA")).build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenMultipleToolCallsWithReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA", true);
		ToolCallback toolCallbackB = new TestToolCallback("toolB", true);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"),
						new AssistantMessage.ToolCall("toolB", "function", "toolB", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!"),
					new ToolResponse("toolB", "toolB", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isTrue();
	}

	@Test
	void whenMultipleToolCallsWithMixedReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA", true);
		ToolCallback toolCallbackB = new TestToolCallback("toolB", false);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"),
						new AssistantMessage.ToolCall("toolB", "function", "toolB", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!"),
					new ToolResponse("toolB", "toolB", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isFalse();
	}

	@Test
	void whenToolCallWithExceptionThenReturnError() {
		ToolCallback toolCallback = new FailingToolCallback("toolC");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolC", "function", "toolC", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolC", "toolC", "You failed this city!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenBlockingExecutionThenToolCallObservationHasCurrentObservationAsParent() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));

		ObservationRegistry observationRegistry = ObservationRegistry.create();
		List<ObservationView> capturedParents = new ArrayList<>();
		observationRegistry.observationConfig()
			.observationHandler(new ObservationHandler<ToolCallingObservationContext>() {
				@Override
				public void onStart(ToolCallingObservationContext context) {
					capturedParents.add(context.getParentObservation());
				}

				@Override
				public boolean supportsContext(Observation.Context context) {
					return context instanceof ToolCallingObservationContext;
				}
			});

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.observationRegistry(observationRegistry)
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		// Simulate the blocking ChatClient flow where an outer observation holds an open
		// scope on the calling thread (ToolCallReactiveContextHolder is never populated).
		Observation parentObservation = Observation.start("parent", observationRegistry);
		try (Observation.Scope ignored = parentObservation.openScope()) {
			toolCallingManager.executeToolCalls(prompt, chatResponse);
		}
		finally {
			parentObservation.stop();
		}

		assertThat(capturedParents).containsExactly(parentObservation);
	}

	@Test
	void whenMixedMethodToolCallsInChatResponseThenExecute() throws NoSuchMethodException {
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		ToolDefinition toolDefinitionA = ToolDefinition.builder().name("toolA").inputSchema("{}").build();
		Method methodA = TestGenericClass.class.getMethod("call", String.class);
		MethodToolCallback methodToolCallback = MethodToolCallback.builder()
			.toolDefinition(toolDefinitionA)
			.toolMethod(methodA)
			.toolObject(new TestGenericClass())
			.build();

		ToolDefinition toolDefinitionB = ToolDefinition.builder().name("toolB").inputSchema("{}").build();
		Method methodB = TestGenericClass.class.getMethod("callWithToolContext", ToolContext.class);
		MethodToolCallback methodToolCallbackNeedToolContext = MethodToolCallback.builder()
			.toolDefinition(toolDefinitionB)
			.toolMethod(methodB)
			.toolObject(new TestGenericClass())
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"),
				ToolCallingChatOptions.builder()
					.toolCallbacks(methodToolCallback, methodToolCallbackNeedToolContext)
					.toolContext("key", "value")
					.build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"),
						new AssistantMessage.ToolCall("toolB", "function", "toolB", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", TestGenericClass.CALL_RESULT_JSON),
					new ToolResponse("toolB", "toolB", TestGenericClass.CALL_WITH_TOOL_CONTEXT_RESULT_JSON)))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	// TOOL CALL LIMITS

	@Test
	void whenMaxCallsPerToolExceededThenThrowWithPartialResult() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxCallsPerTool("toolA", 1)
			.build();

		// One prior call to toolA already recorded in the conversation history.
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("priorId", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> {
				assertThat(ex.getToolName()).isEqualTo("toolA");
				assertThat(ex.getLimit()).isEqualTo(1);
				assertThat(ex.getPartialToolExecutionResult()).isNotNull();
			});
	}

	@Test
	void whenMaxCallsPerToolExceededAndReturnErrorResponseThenSynthesizeErrorResponse() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxCallsPerTool("toolA", 1)
			.onLimitExceeded(ToolCallLimitBehavior.RETURN_ERROR_RESPONSE)
			.build();

		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("priorId", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		ToolResponseMessage lastMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory()
			.get(toolExecutionResult.conversationHistory().size() - 1);
		assertThat(lastMessage.getResponses()).singleElement().satisfies(response -> {
			assertThat(response.name()).isEqualTo("toolA");
			assertThat(response.responseData()).contains("limit").doesNotContain("Mission accomplished!");
		});
	}

	@Test
	void whenToolExcludedFromLimitThenNotCounted() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxCallsPerTool(1)
			.excludeToolFromLimit("toolA")
			.resolutionFallbackEnabled(true)
			.build();

		// Several prior calls to toolA already recorded; would normally exceed the
		// default per-tool limit of 1.
		ToolResponseMessage priorToolResponses = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("priorId1", "toolA", "Mission accomplished!"),
					new ToolResponse("priorId2", "toolA", "Mission accomplished!"),
					new ToolResponse("priorId3", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponses),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolResponseMessage expectedToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!")))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenMaxTotalToolCallsExceededThenThrowRegardlessOfToolName() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxTotalToolCalls(1)
			.build();

		// One prior call to a different tool (toolB) already recorded.
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("priorId", "toolB", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> {
				assertThat(ex.getToolName()).isNull();
				assertThat(ex.getLimit()).isEqualTo(1);
			});
	}

	@Test
	void whenBuilderUnconfiguredThenDefaultMaxCallsPerToolApplies() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		// DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL prior calls already
		// recorded; the next one should breach the baked-in default.
		List<ToolResponse> priorResponses = new ArrayList<>();
		for (int i = 0; i < DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL; i++) {
			priorResponses.add(new ToolResponse("priorId" + i, "toolA", "Mission accomplished!"));
		}
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder().responses(priorResponses).build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> {
				assertThat(ex.getToolName()).isEqualTo("toolA");
				assertThat(ex.getLimit()).isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL);
			});
	}

	@Test
	void whenBuilderUnconfiguredThenDefaultMaxTotalToolCallsApplies() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		// DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS prior calls to a
		// different tool already recorded; the next call to any tool should breach
		// the baked-in total default.
		List<ToolResponse> priorResponses = new ArrayList<>();
		for (int i = 0; i < DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS; i++) {
			priorResponses.add(new ToolResponse("priorId" + i, "toolB", "Mission accomplished!"));
		}
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder().responses(priorResponses).build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> {
				assertThat(ex.getToolName()).isNull();
				assertThat(ex.getLimit()).isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS);
			});
	}

	@Test
	void whenUnlimitedCallsPerToolThenDefaultLimitIsDisabled() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.unlimitedCallsPerTool()
			.resolutionFallbackEnabled(true)
			.build();

		// Well beyond DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL; should
		// still succeed since the per-tool limit was explicitly disabled.
		List<ToolResponse> priorResponses = new ArrayList<>();
		for (int i = 0; i < DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL + 5; i++) {
			priorResponses.add(new ToolResponse("priorId" + i, "toolA", "Mission accomplished!"));
		}
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder().responses(priorResponses).build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
		assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
	}

	@Test
	void whenUnlimitedTotalToolCallsThenDefaultLimitIsDisabled() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.unlimitedCallsPerTool()
			.unlimitedTotalToolCalls()
			.resolutionFallbackEnabled(true)
			.build();

		// Well beyond DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS; should
		// still succeed since both limits were explicitly disabled.
		List<ToolResponse> priorResponses = new ArrayList<>();
		for (int i = 0; i < DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS + 5; i++) {
			priorResponses.add(new ToolResponse("priorId" + i, "toolA", "Mission accomplished!"));
		}
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder().responses(priorResponses).build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
		assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
	}

	@Test
	void whenPriorTurnHasToolCallsThenNotCountedTowardCurrentTurnPerToolLimit() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxCallsPerTool("toolA", 1)
			.resolutionFallbackEnabled(true)
			.build();

		// An earlier turn already made a call to toolA (already at the limit for that
		// earlier turn), but a new UserMessage starts a fresh turn; only messages from
		// the last UserMessage onward should count.
		ToolResponseMessage earlierTurnToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("earlierId", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("Earlier turn"), earlierTurnToolResponse, new UserMessage("New turn")),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
		assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
	}

	@Test
	void whenPriorTurnHasManyToolCallsThenNotCountedTowardCurrentTurnTotalLimit() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxTotalToolCalls(1)
			.resolutionFallbackEnabled(true)
			.build();

		// An earlier turn already made many tool calls, well past the total limit,
		// but a new UserMessage starts a fresh turn whose count should start at zero.
		List<ToolResponse> earlierTurnResponses = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			earlierTurnResponses.add(new ToolResponse("earlierId" + i, "toolB", "Mission accomplished!"));
		}
		ToolResponseMessage earlierTurnToolResponse = ToolResponseMessage.builder()
			.responses(earlierTurnResponses)
			.build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("Earlier turn"), earlierTurnToolResponse, new UserMessage("New turn")),
				ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
		assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
	}

	@Test
	void whenCurrentTurnExceedsLimitThenThrowsRegardlessOfPriorTurns() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.maxCallsPerTool("toolA", 1)
			.build();

		// An earlier turn's tool response (a different tool, irrelevant to this
		// check) is followed by a new turn that already made one call to toolA.
		ToolResponseMessage earlierTurnToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("earlierId", "toolB", "Mission accomplished!")))
			.build();
		ToolResponseMessage currentTurnToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("currentId", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Earlier turn"), earlierTurnToolResponse,
				new UserMessage("New turn"), currentTurnToolResponse), ToolCallingChatOptions.builder().build());

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> assertThat(ex.getToolName()).isEqualTo("toolA"));
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

	static class FailingToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		FailingToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			throw new ToolExecutionException(this.toolDefinition, new IllegalStateException("You failed this city!"));
		}

	}

	/**
	 * Test class with methods that use generic types.
	 */
	static class TestGenericClass {

		public final static String CALL_RESULT_JSON = """
				{
					"result": "Mission accomplished!"
				}
				""";

		public final static String CALL_WITH_TOOL_CONTEXT_RESULT_JSON = """
				{
					"result": "ToolContext mission accomplished!"
				}
				""";

		public String call(String toolInput) {
			return CALL_RESULT_JSON;
		}

		public String callWithToolContext(ToolContext toolContext) {
			return CALL_WITH_TOOL_CONTEXT_RESULT_JSON;
		}

	}

}
