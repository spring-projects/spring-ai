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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link ToolCallingManager}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class DefaultToolCallingManager implements ToolCallingManager {

	private static final Logger logger = LoggerFactory.getLogger(DefaultToolCallingManager.class);

	// @formatter:off

	private static final ObservationRegistry DEFAULT_OBSERVATION_REGISTRY
			= ObservationRegistry.NOOP;

	private static final ToolCallingObservationConvention DEFAULT_OBSERVATION_CONVENTION
			= new DefaultToolCallingObservationConvention();

	private static final ToolCallbackResolver DEFAULT_TOOL_CALLBACK_RESOLVER
			= new DelegatingToolCallbackResolver(List.of());

	private static final ToolExecutionExceptionProcessor DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR
			= DefaultToolExecutionExceptionProcessor.builder().build();

	private static final String POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING
			= "LLM may have adapted the tool name '{}', especially if the name was truncated due to length limits. If this is the case, you can customize the prefixing and processing logic using McpToolNamePrefixGenerator";


	// @formatter:on

	private final ObservationRegistry observationRegistry;

	private final ToolCallbackResolver toolCallbackResolver;

	private final ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

	private ToolCallingObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private boolean parallelToolExecution = false;

	public DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolCallbackResolver, "toolCallbackResolver cannot be null");
		Assert.notNull(toolExecutionExceptionProcessor, "toolCallExceptionConverter cannot be null");

		this.observationRegistry = observationRegistry;
		this.toolCallbackResolver = toolCallbackResolver;
		this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
	}

	public void setParallelToolExecution(boolean parallelToolExecution) {
		this.parallelToolExecution = parallelToolExecution;
	}

	public boolean isParallelToolExecution() {
		return parallelToolExecution;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");

		List<ToolCallback> toolCallbacks = new ArrayList<>(chatOptions.getToolCallbacks());
		for (String toolName : chatOptions.getToolNames()) {
			// Skip the tool if it is already present in the request toolCallbacks.
			// That might happen if a tool is defined in the options
			// both as a ToolCallback and as a tool name.
			if (chatOptions.getToolCallbacks()
				.stream()
				.anyMatch(tool -> tool.getToolDefinition().name().equals(toolName))) {
				continue;
			}
			ToolCallback toolCallback = this.toolCallbackResolver.resolve(toolName);
			if (toolCallback == null) {
				logger.warn(POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING, toolName);
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}
			toolCallbacks.add(toolCallback);
		}

		return toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList();
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

		InternalToolExecutionResult internalToolExecutionResult = executeToolCall(prompt, assistantMessage,
				toolContext);

		List<Message> conversationHistory = buildConversationHistoryAfterToolExecution(prompt.getInstructions(),
				assistantMessage, internalToolExecutionResult.toolResponseMessage());

		return ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(internalToolExecutionResult.returnDirect())
			.build();
	}

	private static ToolContext buildToolContext(Prompt prompt, AssistantMessage assistantMessage) {
		Map<String, Object> toolContextMap = Map.of();

		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions
				&& !CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
			toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());
		}

		return new ToolContext(toolContextMap);
	}

	/**
	 * Execute the tool call and return the response message.
	 */
	private InternalToolExecutionResult executeToolCall(Prompt prompt, AssistantMessage assistantMessage,
			ToolContext toolContext) {
		List<ToolCallback> toolCallbacks = List.of();
		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
			toolCallbacks = toolCallingChatOptions.getToolCallbacks();
		}

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		Boolean returnDirect = null;

		List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

		if (this.parallelToolExecution && toolCalls.size() > 1) {
			// Parallel execution using CompletableFuture
			ExecutorService executor = Executors.newFixedThreadPool(Math.min(toolCalls.size(), 10));
			try {
				AtomicBoolean returnDirectAccumulator = new AtomicBoolean(true);
				AtomicInteger responseIndex = new AtomicInteger(0);
				ToolResponseMessage.ToolResponse[] responseArray = new ToolResponseMessage.ToolResponse[toolCalls
					.size()];

				AtomicBoolean returnDirectResult = new AtomicBoolean(true);

				List<CompletableFuture<Void>> futures = toolCalls.stream()
					.map(toolCall -> CompletableFuture.runAsync(() -> {
						try {
							ToolResponseMessage.ToolResponse response = executeSingleToolCall(toolCall, toolCallbacks,
									toolContext, returnDirectAccumulator);
							int index = responseIndex.getAndIncrement();
							responseArray[index] = response;
						}
						catch (Exception e) {
							throw new RuntimeException("Tool execution failed for: " + toolCall.name(), e);
						}
					}, executor))
					.toList();

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				for (ToolResponseMessage.ToolResponse response : responseArray) {
					if (response != null) {
						toolResponses.add(response);
					}
				}

				returnDirectResult.set(returnDirectAccumulator.get());
				returnDirect = returnDirectResult.get();
			}
			finally {
				executor.shutdown();
				try {
					if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
						executor.shutdownNow();
					}
				}
				catch (InterruptedException e) {
					executor.shutdownNow();
					Thread.currentThread().interrupt();
				}
			}
		}
		else {
			// Sequential execution (default behavior for backward compatibility)
			for (AssistantMessage.ToolCall toolCall : toolCalls) {
				ToolResponseMessage.ToolResponse response = executeSingleToolCall(toolCall, toolCallbacks, toolContext,
						null);
				toolResponses.add(response);

				boolean toolReturnDirect = toolCallbacks.stream()
					.filter(tool -> toolCall.name().equals(tool.getToolDefinition().name()))
					.findFirst()
					.orElseGet(() -> this.toolCallbackResolver.resolve(toolCall.name()))
					.getToolMetadata()
					.returnDirect();

				if (returnDirect == null) {
					returnDirect = toolReturnDirect;
				}
				else {
					returnDirect = returnDirect && toolReturnDirect;
				}
			}
		}

		return new InternalToolExecutionResult(ToolResponseMessage.builder().responses(toolResponses).build(),
				Objects.requireNonNullElse(returnDirect, false));
	}

	/**
	 * Execute a single tool call.
	 */
	private ToolResponseMessage.ToolResponse executeSingleToolCall(AssistantMessage.ToolCall toolCall,
			List<ToolCallback> toolCallbacks, ToolContext toolContext, AtomicBoolean returnDirectAccumulator) {

		logger.debug("Executing tool call: {}", toolCall.name());

		String toolName = toolCall.name();
		String toolInputArguments = toolCall.arguments();

		// Handle the possible null parameter situation in streaming mode.
		final String finalToolInputArguments;
		if (!StringUtils.hasText(toolInputArguments)) {
			logger.warn("Tool call arguments are null or empty for tool: {}. Using empty JSON object as default.",
					toolName);
			finalToolInputArguments = "{}";
		}
		else {
			finalToolInputArguments = toolInputArguments;
		}

		ToolCallback toolCallback = toolCallbacks.stream()
			.filter(tool -> toolName.equals(tool.getToolDefinition().name()))
			.findFirst()
			.orElseGet(() -> this.toolCallbackResolver.resolve(toolName));

		if (toolCallback == null) {
			logger.warn(POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING, toolName);
			throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
		}

		if (returnDirectAccumulator != null) {
			// In parallel mode, use atomic accumulator
			returnDirectAccumulator.set(returnDirectAccumulator.get() && toolCallback.getToolMetadata().returnDirect());
		}

		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(toolCallback.getToolDefinition())
			.toolMetadata(toolCallback.getToolMetadata())
			.toolCallArguments(finalToolInputArguments)
			.build();

		String toolCallResult = ToolCallingObservationDocumentation.TOOL_CALL
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				String toolResult;
				try {
					toolResult = toolCallback.call(finalToolInputArguments, toolContext);
				}
				catch (ToolExecutionException ex) {
					toolResult = this.toolExecutionExceptionProcessor.process(ex);
				}
				observationContext.setToolCallResult(toolResult);
				return toolResult;
			});

		return new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
				toolCallResult != null ? toolCallResult : "");
	}

	private List<Message> buildConversationHistoryAfterToolExecution(List<Message> previousMessages,
			AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
	}

	public void setObservationConvention(ToolCallingObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	private record InternalToolExecutionResult(ToolResponseMessage toolResponseMessage, boolean returnDirect) {
	}

	public final static class Builder {

		private ObservationRegistry observationRegistry = DEFAULT_OBSERVATION_REGISTRY;

		private ToolCallbackResolver toolCallbackResolver = DEFAULT_TOOL_CALLBACK_RESOLVER;

		private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor = DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR;

		private boolean parallelToolExecution = false;

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

		public Builder toolExecutionExceptionProcessor(
				ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
			this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
			return this;
		}

		public Builder parallelToolExecution(boolean parallelToolExecution) {
			this.parallelToolExecution = parallelToolExecution;
			return this;
		}

		public DefaultToolCallingManager build() {
			DefaultToolCallingManager manager = new DefaultToolCallingManager(this.observationRegistry,
					this.toolCallbackResolver, this.toolExecutionExceptionProcessor);
			manager.setParallelToolExecution(this.parallelToolExecution);
			return manager;
		}

	}

}
