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

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallExceptionConverter;
import org.springframework.ai.tool.execution.ToolCallExceptionConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link ToolCallingManager}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultToolCallingManager implements ToolCallingManager {

	// @formatter:off

	private static final ObservationRegistry DEFAULT_OBSERVATION_REGISTRY
			= ObservationRegistry.NOOP;

	private static final ToolCallbackResolver DEFAULT_TOOL_CALLBACK_RESOLVER
			= new DelegatingToolCallbackResolver(List.of());

	private static final ToolCallExceptionConverter DEFAULT_TOOL_CALL_EXCEPTION_CONVERTER
			= DefaultToolCallExceptionConverter.builder().build();

	// @formatter:on

	private final ObservationRegistry observationRegistry;

	private final ToolCallbackResolver toolCallbackResolver;

	private final ToolCallExceptionConverter toolCallExceptionConverter;

	public DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolCallExceptionConverter toolCallExceptionConverter) {
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolCallbackResolver, "toolCallbackResolver cannot be null");
		Assert.notNull(toolCallExceptionConverter, "toolCallExceptionConverter cannot be null");

		this.observationRegistry = observationRegistry;
		this.toolCallbackResolver = toolCallbackResolver;
		this.toolCallExceptionConverter = toolCallExceptionConverter;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");

		List<FunctionCallback> toolCallbacks = new ArrayList<>(chatOptions.getToolCallbacks());
		for (String toolName : chatOptions.getTools()) {
			ToolCallback toolCallback = toolCallbackResolver.resolve(toolName);
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

	@Override
	public List<Message> executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
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

		return buildConversationHistoryAfterToolExecution(prompt.getInstructions(), assistantMessage,
				toolMessageResponse);
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
				.orElse(toolCallbackResolver.resolve(toolName));

			if (toolCallback == null) {
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}

			String toolResult;
			try {
				toolResult = toolCallback.call(toolInputArguments, toolContext);
			}
			catch (ToolExecutionException ex) {
				toolResult = toolCallExceptionConverter.convert(ex);
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

		private ObservationRegistry observationRegistry = DEFAULT_OBSERVATION_REGISTRY;

		private ToolCallbackResolver toolCallbackResolver = DEFAULT_TOOL_CALLBACK_RESOLVER;

		private ToolCallExceptionConverter toolCallExceptionConverter = DEFAULT_TOOL_CALL_EXCEPTION_CONVERTER;

		private Builder() {
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
			this.toolCallbackResolver = toolCallbackResolver;
			return this;
		}

		public Builder toolCallExceptionConverter(ToolCallExceptionConverter toolCallExceptionConverter) {
			this.toolCallExceptionConverter = toolCallExceptionConverter;
			return this;
		}

		public DefaultToolCallingManager build() {
			return new DefaultToolCallingManager(observationRegistry, toolCallbackResolver, toolCallExceptionConverter);
		}

	}

}
