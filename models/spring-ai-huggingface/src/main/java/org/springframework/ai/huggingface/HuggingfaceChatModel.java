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

package org.springframework.ai.huggingface;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.common.HuggingfaceApiConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} implementation for HuggingFace Inference API. HuggingFace provides
 * access to thousands of pre-trained models for various NLP tasks including chat
 * completions.
 *
 * @author Mark Pollack
 * @author Josh Long
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Myeongdeok Kang
 */
public class HuggingfaceChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(HuggingfaceChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final HuggingfaceApi huggingfaceApi;

	private final HuggingfaceChatOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private final RetryTemplate retryTemplate;

	private final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Constructor for HuggingfaceChatModel.
	 * @param huggingfaceApi The HuggingFace API client.
	 * @param defaultOptions Default chat options.
	 * @param toolCallingManager Tool calling manager for executing tools.
	 * @param observationRegistry Observation registry for metrics.
	 * @param retryTemplate Retry template for handling transient errors.
	 * @param toolExecutionEligibilityPredicate Predicate to determine if tool execution
	 * is required.
	 */
	public HuggingfaceChatModel(HuggingfaceApi huggingfaceApi, HuggingfaceChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry, RetryTemplate retryTemplate,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(huggingfaceApi, "huggingfaceApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate must not be null");

		this.huggingfaceApi = huggingfaceApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.observationRegistry = observationRegistry;
		this.retryTemplate = retryTemplate;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	/**
	 * Create a new builder for HuggingfaceChatModel.
	 * @return A new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Assert.notEmpty(prompt.getInstructions(), "At least one message is required!");

		// Build the final request, merging runtime and default options
		Prompt requestPrompt = buildChatRequest(prompt);
		return this.internalCall(requestPrompt, null);
	}

	/**
	 * Internal method for making chat completion calls with tool execution support.
	 * @param prompt The prompt to send to the model.
	 * @param previousChatResponse Previous chat response for cumulative usage tracking.
	 * @return The chat response from the model.
	 */
	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		HuggingfaceApi.ChatRequest apiRequest = createApiRequest(prompt);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(HuggingfaceApiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				HuggingfaceApi.ChatResponse apiResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.huggingfaceApi.chat(apiRequest));

				List<Generation> generations = apiResponse.choices().stream().map(choice -> {
					List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(choice.message().toolCalls());
					AssistantMessage.Builder messageBuilder = AssistantMessage.builder()
						.content(choice.message().content());
					if (toolCalls != null) {
						messageBuilder.toolCalls(toolCalls);
					}
					return new Generation(messageBuilder.build(),
							ChatGenerationMetadata.builder().finishReason(choice.finishReason()).build());
				}).collect(Collectors.toList());

				ChatResponseMetadata metadata = ChatResponseMetadata.builder()
					.model(apiResponse.model())
					.usage(apiResponse.usage() != null ? new DefaultUsage(apiResponse.usage().promptTokens(),
							apiResponse.usage().completionTokens()) : new DefaultUsage(0, 0))
					.build();

				ChatResponse chatResponse = new ChatResponse(generations, metadata);

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		// Tool execution handling
		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						response);
			}
		}

		return response;
	}

	/**
	 * Build the chat request by merging runtime and default options.
	 * @param chatRequest The original chat request.
	 * @return A new chat request with merged options.
	 */
	Prompt buildChatRequest(Prompt chatRequest) {
		// Process runtime options
		HuggingfaceChatOptions runtimeOptions = null;
		if (chatRequest.getOptions() != null) {
			if (chatRequest.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						HuggingfaceChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(chatRequest.getOptions(), ChatOptions.class,
						HuggingfaceChatOptions.class);
			}
		}

		// Merge runtime and default options
		HuggingfaceChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				HuggingfaceChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));
		}

		// Validate
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new Prompt(chatRequest.getInstructions(), requestOptions);
	}

	/**
	 * Create the API request from the chat request.
	 * @param prompt The chat request.
	 * @return The API request.
	 */
	private HuggingfaceApi.ChatRequest createApiRequest(Prompt prompt) {
		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();

		List<HuggingfaceApi.Message> messages = prompt.getInstructions()
			.stream()
			.flatMap(message -> toHuggingfaceMessage(message).stream())
			.toList();

		// Add tool definitions to the request if present
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(options);

		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			List<HuggingfaceApi.FunctionTool> tools = getFunctionTools(toolDefinitions);
			return new HuggingfaceApi.ChatRequest(options.getModel(), messages, tools, "auto", options.toMap());
		}

		return new HuggingfaceApi.ChatRequest(options.getModel(), messages, options.toMap());
	}

	/**
	 * Convert Spring AI message to HuggingFace API message(s). Tool response messages may
	 * produce multiple API messages (one per tool response).
	 * @param message The Spring AI message.
	 * @return The list of HuggingFace API messages.
	 */
	private List<HuggingfaceApi.Message> toHuggingfaceMessage(Message message) {
		if (message.getMessageType() == MessageType.TOOL) {
			// Tool response messages need special handling
			ToolResponseMessage toolMessage = (ToolResponseMessage) message;
			return toolMessage.getResponses()
				.stream()
				.map(response -> new HuggingfaceApi.Message(response.responseData(), MessageType.TOOL.getValue(),
						response.name(), response.id()))
				.toList();
		}
		else if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null
				&& !assistantMessage.getToolCalls().isEmpty()) {
			// Assistant message with tool calls
			List<HuggingfaceApi.ToolCall> toolCalls = assistantMessage.getToolCalls()
				.stream()
				.map(toolCall -> new HuggingfaceApi.ToolCall(toolCall.id(), toolCall.type(),
						new HuggingfaceApi.ChatCompletionFunction(toolCall.name(), toolCall.arguments())))
				.toList();
			return List
				.of(new HuggingfaceApi.Message(message.getMessageType().getValue(), message.getText(), toolCalls));
		}
		else {
			// Regular user/system/assistant message
			return List.of(new HuggingfaceApi.Message(message.getMessageType().getValue(), message.getText()));
		}
	}

	/**
	 * Convert tool definitions to HuggingFace API function tools.
	 * @param toolDefinitions The tool definitions.
	 * @return The list of function tools.
	 */
	private List<HuggingfaceApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new HuggingfaceApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					ModelOptionsUtils.jsonToMap(toolDefinition.inputSchema()));
			return new HuggingfaceApi.FunctionTool(function);
		}).toList();
	}

	/**
	 * Extract tool calls from HuggingFace API response and convert to Spring AI format.
	 * @param apiToolCalls The tool calls from the API response.
	 * @return The list of tool calls in Spring AI format, or null if no tool calls.
	 */
	private List<AssistantMessage.ToolCall> extractToolCalls(List<HuggingfaceApi.ToolCall> apiToolCalls) {
		if (apiToolCalls == null || apiToolCalls.isEmpty()) {
			return null;
		}

		return apiToolCalls.stream()
			.map(apiToolCall -> new AssistantMessage.ToolCall(apiToolCall.id(), apiToolCall.type(),
					apiToolCall.function().name(), apiToolCall.function().arguments()))
			.toList();
	}

	/**
	 * Set the observation convention for reporting metrics.
	 * @param observationConvention The observation convention.
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions.copy();
	}

	/**
	 * Builder for creating HuggingfaceChatModel instances.
	 */
	public static final class Builder {

		private HuggingfaceApi huggingfaceApi;

		private HuggingfaceChatOptions defaultOptions = HuggingfaceChatOptions.builder()
			.model(HuggingfaceApi.DEFAULT_CHAT_MODEL)
			.build();

		private ToolCallingManager toolCallingManager;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private Builder() {
		}

		/**
		 * Set the HuggingFace API client.
		 * @param huggingfaceApi The API client.
		 * @return This builder.
		 */
		public Builder huggingfaceApi(HuggingfaceApi huggingfaceApi) {
			this.huggingfaceApi = huggingfaceApi;
			return this;
		}

		/**
		 * Set the default chat options.
		 * @param defaultOptions The default options.
		 * @return This builder.
		 */
		public Builder defaultOptions(HuggingfaceChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Set the tool calling manager.
		 * @param toolCallingManager The tool calling manager.
		 * @return This builder.
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Set the observation registry.
		 * @param observationRegistry The observation registry.
		 * @return This builder.
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Set the retry template.
		 * @param retryTemplate The retry template.
		 * @return This builder.
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Set the tool execution eligibility predicate.
		 * @param toolExecutionEligibilityPredicate The predicate.
		 * @return This builder.
		 */
		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
			return this;
		}

		/**
		 * Build the HuggingfaceChatModel instance.
		 * @return A new HuggingfaceChatModel.
		 */
		public HuggingfaceChatModel build() {
			Assert.notNull(this.huggingfaceApi, "huggingfaceApi must not be null");
			Assert.notNull(this.toolExecutionEligibilityPredicate,
					"toolExecutionEligibilityPredicate must not be null");
			if (this.toolCallingManager != null) {
				return new HuggingfaceChatModel(this.huggingfaceApi, this.defaultOptions, this.toolCallingManager,
						this.observationRegistry, this.retryTemplate, this.toolExecutionEligibilityPredicate);
			}
			return new HuggingfaceChatModel(this.huggingfaceApi, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.observationRegistry, this.retryTemplate, this.toolExecutionEligibilityPredicate);
		}

	}

}
