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

package org.springframework.ai.model.tool;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link ToolCallingManager} supporting the migration from
 * {@link AbstractToolCallSupport} to {@link ToolCallingManager} and ensuring AI
 * compatibility for all the ChatModel implementations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @deprecated Only to help moving away from {@link AbstractToolCallSupport}. It will be
 * removed in the next milestone.
 */
@Deprecated
public class LegacyToolCallingManager implements ToolCallingManager {

	private final FunctionCallbackResolver functionCallbackResolver;

	private final Map<String, FunctionCallback> functionCallbacks = new HashMap<>();

	private final ToolExecutionExceptionProcessor toolExecutionExceptionProcessor = DefaultToolExecutionExceptionProcessor
		.builder()
		.build();

	public LegacyToolCallingManager(@Nullable FunctionCallbackResolver functionCallbackResolver,
			List<FunctionCallback> functionCallbacks) {
		Assert.notNull(functionCallbacks, "functionCallbacks cannot be null");
		Assert.noNullElements(functionCallbacks.toArray(), "functionCallbacks cannot contain null elements");
		this.functionCallbackResolver = functionCallbackResolver;
		functionCallbacks.forEach(toolCallback -> this.functionCallbacks.put(toolCallback.getName(), toolCallback));
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");

		List<FunctionCallback> toolCallbacks = new ArrayList<>(chatOptions.getToolCallbacks());
		for (String toolName : chatOptions.getToolNames()) {
			FunctionCallback toolCallback = resolveFunctionCallback(toolName);
			if (toolCallback == null) {
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}
			toolCallbacks.add(toolCallback);
		}

		return toolCallbacks.stream().map(functionCallback -> {
			if (functionCallback instanceof ToolCallback toolCallback) {
				return toolCallback.getToolDefinition();
			}
			else {
				return ToolDefinition.builder()
					.name(functionCallback.getName())
					.description(functionCallback.getDescription())
					.inputSchema(functionCallback.getInputTypeSchema())
					.build();
			}
		}).toList();
	}

	@Nullable
	private FunctionCallback resolveFunctionCallback(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");
		if (functionCallbacks.get(toolName) != null) {
			return functionCallbacks.get(toolName);
		}
		return functionCallbackResolver != null ? functionCallbackResolver.resolve(toolName) : null;
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		Assert.notNull(prompt, "prompt cannot be null");
		Assert.notNull(chatResponse, "chatResponse cannot be null");

		Optional<Generation> toolCallGeneration = chatResponse.getResults()
			.stream()
			.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
			.findFirst();

		if (toolCallGeneration.isEmpty()) {
			throw new IllegalStateException("No tool call requested by the chat model");
		}

		AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

		ToolContext toolContext = buildToolContext(prompt, assistantMessage);

		ToolResponseMessage toolMessageResponse = executeToolCall(prompt, assistantMessage, toolContext);

		List<Message> conversationHistory = buildConversationHistoryAfterToolExecution(prompt.getInstructions(),
				assistantMessage, toolMessageResponse);

		return ToolExecutionResult.builder().conversationHistory(conversationHistory).returnDirect(false).build();
	}

	private static ToolContext buildToolContext(Prompt prompt, AssistantMessage assistantMessage) {
		Map<String, Object> toolContextMap = Map.of();

		if (prompt.getOptions() instanceof FunctionCallingOptions functionOptions
				&& !CollectionUtils.isEmpty(functionOptions.getToolContext())) {
			toolContextMap = new HashMap<>(functionOptions.getToolContext());

			List<Message> messageHistory = new ArrayList<>(prompt.copy().getInstructions());
			messageHistory.add(new AssistantMessage(assistantMessage.getText(), assistantMessage.getMetadata(),
					assistantMessage.getToolCalls()));

			toolContextMap.put(ToolContext.TOOL_CALL_HISTORY,
					buildConversationHistoryBeforeToolExecution(prompt, assistantMessage));
		}

		return new ToolContext(toolContextMap);
	}

	private static List<Message> buildConversationHistoryBeforeToolExecution(Prompt prompt,
			AssistantMessage assistantMessage) {
		List<Message> messageHistory = new ArrayList<>(prompt.copy().getInstructions());
		messageHistory.add(new AssistantMessage(assistantMessage.getText(), assistantMessage.getMetadata(),
				assistantMessage.getToolCalls()));
		return messageHistory;
	}

	/**
	 * Execute the tool call and return the response message. To ensure backward
	 * compatibility, both {@link ToolCallback} and {@link FunctionCallback} are
	 * supported.
	 */
	private ToolResponseMessage executeToolCall(Prompt prompt, AssistantMessage assistantMessage,
			ToolContext toolContext) {
		List<FunctionCallback> toolCallbacks = List.of();
		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
			toolCallbacks = toolCallingChatOptions.getToolCallbacks();
		}
		else if (prompt.getOptions() instanceof FunctionCallingOptions functionOptions) {
			toolCallbacks = functionOptions.getFunctionCallbacks();
		}

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			String toolName = toolCall.name();
			String toolInputArguments = toolCall.arguments();

			FunctionCallback toolCallback = toolCallbacks.stream()
				.filter(tool -> toolName.equals(tool.getName()))
				.findFirst()
				.orElseGet(() -> resolveFunctionCallback(toolName));

			if (toolCallback == null) {
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}

			String toolResult;
			try {
				toolResult = toolCallback.call(toolInputArguments, toolContext);
			}
			catch (ToolExecutionException ex) {
				toolResult = toolExecutionExceptionProcessor.process(ex);
			}

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, toolResult));
		}

		return new ToolResponseMessage(toolResponses, Map.of());
	}

	private List<Message> buildConversationHistoryAfterToolExecution(List<Message> previousMessages,
			AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private FunctionCallbackResolver functionCallbackResolver;

		private List<FunctionCallback> functionCallbacks = new ArrayList<>();

		private Builder() {
		}

		public Builder functionCallbackResolver(FunctionCallbackResolver functionCallbackResolver) {
			this.functionCallbackResolver = functionCallbackResolver;
			return this;
		}

		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.functionCallbacks = functionCallbacks;
			return this;
		}

		public LegacyToolCallingManager build() {
			return new LegacyToolCallingManager(functionCallbackResolver, functionCallbacks);
		}

	}

}
