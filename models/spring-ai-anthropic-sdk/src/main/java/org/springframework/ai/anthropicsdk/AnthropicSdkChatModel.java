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

package org.springframework.ai.anthropicsdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropicsdk.setup.AnthropicSdkSetup;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.util.Assert;

/**
 * Chat Model implementation using the official Anthropic Java SDK.
 *
 * @author Soby Chacko
 * @since 1.0.0
 */
public class AnthropicSdkChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSdkChatModel.class);

	private static final String DEFAULT_MODEL = AnthropicSdkChatOptions.DEFAULT_MODEL;

	private static final Integer DEFAULT_MAX_TOKENS = AnthropicSdkChatOptions.DEFAULT_MAX_TOKENS;

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final AnthropicClient anthropicClient;

	private final AnthropicClientAsync anthropicClientAsync;

	private final AnthropicSdkChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new AnthropicSdkChatModel with default options.
	 */
	public AnthropicSdkChatModel() {
		this(null, null, null, null, null, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given options.
	 * @param options the chat options
	 */
	public AnthropicSdkChatModel(AnthropicSdkChatOptions options) {
		this(null, null, options, null, null, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given options and observation
	 * registry.
	 * @param options the chat options
	 * @param observationRegistry the observation registry
	 */
	public AnthropicSdkChatModel(AnthropicSdkChatOptions options, ObservationRegistry observationRegistry) {
		this(null, null, options, null, observationRegistry, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given options, tool calling manager,
	 * and observation registry.
	 * @param options the chat options
	 * @param toolCallingManager the tool calling manager
	 * @param observationRegistry the observation registry
	 */
	public AnthropicSdkChatModel(AnthropicSdkChatOptions options, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry) {
		this(null, null, options, toolCallingManager, observationRegistry, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given Anthropic clients.
	 * @param anthropicClient the synchronous Anthropic client
	 * @param anthropicClientAsync the asynchronous Anthropic client
	 */
	public AnthropicSdkChatModel(AnthropicClient anthropicClient, AnthropicClientAsync anthropicClientAsync) {
		this(anthropicClient, anthropicClientAsync, null, null, null, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given Anthropic clients and options.
	 * @param anthropicClient the synchronous Anthropic client
	 * @param anthropicClientAsync the asynchronous Anthropic client
	 * @param options the chat options
	 */
	public AnthropicSdkChatModel(AnthropicClient anthropicClient, AnthropicClientAsync anthropicClientAsync,
			AnthropicSdkChatOptions options) {
		this(anthropicClient, anthropicClientAsync, options, null, null, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with the given Anthropic clients, options, and
	 * observation registry.
	 * @param anthropicClient the synchronous Anthropic client
	 * @param anthropicClientAsync the asynchronous Anthropic client
	 * @param options the chat options
	 * @param observationRegistry the observation registry
	 */
	public AnthropicSdkChatModel(AnthropicClient anthropicClient, AnthropicClientAsync anthropicClientAsync,
			AnthropicSdkChatOptions options, ObservationRegistry observationRegistry) {
		this(anthropicClient, anthropicClientAsync, options, null, observationRegistry, null);
	}

	/**
	 * Creates a new AnthropicSdkChatModel with all configuration options.
	 * @param anthropicClient the synchronous Anthropic client
	 * @param anthropicClientAsync the asynchronous Anthropic client
	 * @param options the chat options
	 * @param toolCallingManager the tool calling manager
	 * @param observationRegistry the observation registry
	 * @param toolExecutionEligibilityPredicate the predicate to determine tool execution
	 * eligibility
	 */
	public AnthropicSdkChatModel(@Nullable AnthropicClient anthropicClient,
			@Nullable AnthropicClientAsync anthropicClientAsync, @Nullable AnthropicSdkChatOptions options,
			@Nullable ToolCallingManager toolCallingManager, @Nullable ObservationRegistry observationRegistry,
			@Nullable ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		if (options == null) {
			this.options = AnthropicSdkChatOptions.builder().model(DEFAULT_MODEL).maxTokens(DEFAULT_MAX_TOKENS).build();
		}
		else {
			this.options = options;
		}

		this.anthropicClient = Objects.requireNonNullElseGet(anthropicClient,
				() -> AnthropicSdkSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.anthropicClientAsync = Objects.requireNonNullElseGet(anthropicClientAsync,
				() -> AnthropicSdkSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
		this.toolCallingManager = Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
		this.toolExecutionEligibilityPredicate = Objects.requireNonNullElse(toolExecutionEligibilityPredicate,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Gets the chat options for this model.
	 * @return the chat options
	 */
	public AnthropicSdkChatOptions getOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		if (this.anthropicClient == null) {
			throw new IllegalStateException("Anthropic sync client is not configured.");
		}
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	/**
	 * Internal method to handle chat completion calls with tool execution support.
	 * @param prompt the prompt for the chat completion
	 * @param previousChatResponse the previous chat response for accumulating usage
	 * @return the chat response
	 */
	public ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		MessageCreateParams request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.ANTHROPIC.value())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				Message message = this.anthropicClient.messages().create(request);

				List<ContentBlock> contentBlocks = message.content();
				if (contentBlocks.isEmpty()) {
					logger.warn("No content blocks returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<Generation> generations = buildGenerations(message);

				// Current usage
				com.anthropic.models.messages.Usage sdkUsage = message.usage();
				Usage currentChatResponseUsage = getDefaultUsage(sdkUsage);
				Usage accumulatedUsage = previousChatResponse != null
						? UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse)
						: currentChatResponseUsage;

				ChatResponse chatResponse = new ChatResponse(generations, from(message, accumulatedUsage));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		ChatOptions promptOptions = prompt.getOptions();
		if (promptOptions != null
				&& this.toolExecutionEligibilityPredicate.isToolExecutionRequired(promptOptions, response)) {
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
	 * Builds the request prompt by merging runtime options with default options.
	 * @param prompt the original prompt
	 * @return the prompt with merged options
	 */
	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		AnthropicSdkChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						AnthropicSdkChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						AnthropicSdkChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		AnthropicSdkChatOptions requestOptions = AnthropicSdkChatOptions.builder()
			.from(this.options)
			.merge(runtimeOptions != null ? runtimeOptions : AnthropicSdkChatOptions.builder().build())
			.build();

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(runtimeOptions.getInternalToolExecutionEnabled() != null
					? runtimeOptions.getInternalToolExecutionEnabled()
					: this.options.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(
					ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(), this.options.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.options.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.options.getToolContext()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.options.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.options.getToolNames());
			requestOptions.setToolCallbacks(this.options.getToolCallbacks());
			requestOptions.setToolContext(this.options.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	/**
	 * Creates a message request from the given prompt.
	 * @param prompt the prompt containing messages and options
	 * @param stream whether this is a streaming request
	 * @return the message create parameters
	 */
	MessageCreateParams createRequest(Prompt prompt, boolean stream) {

		MessageCreateParams.Builder builder = MessageCreateParams.builder();

		ChatOptions options = prompt.getOptions();
		AnthropicSdkChatOptions requestOptions = options instanceof AnthropicSdkChatOptions anthropicOptions
				? anthropicOptions : AnthropicSdkChatOptions.builder().build();

		// Set required fields
		String model = requestOptions.getModel() != null ? requestOptions.getModel() : DEFAULT_MODEL;
		builder.model(model);

		long maxTokens = requestOptions.getMaxTokens() != null ? requestOptions.getMaxTokens() : DEFAULT_MAX_TOKENS;
		builder.maxTokens(maxTokens);

		// Process messages
		for (org.springframework.ai.chat.messages.Message message : prompt.getInstructions()) {
			String text = message.getText();
			if (text == null) {
				continue;
			}
			if (message.getMessageType() == MessageType.SYSTEM) {
				builder.system(text);
			}
			else if (message.getMessageType() == MessageType.USER) {
				builder.addUserMessage(text);
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				builder.addAssistantMessage(text);
			}
			// Tool message type will be handled in Phase 3
		}

		// Set optional parameters
		if (requestOptions.getTemperature() != null) {
			builder.temperature(requestOptions.getTemperature());
		}
		if (requestOptions.getTopP() != null) {
			builder.topP(requestOptions.getTopP());
		}
		if (requestOptions.getTopK() != null) {
			builder.topK(requestOptions.getTopK().longValue());
		}
		if (requestOptions.getStopSequences() != null && !requestOptions.getStopSequences().isEmpty()) {
			builder.stopSequences(requestOptions.getStopSequences());
		}
		if (requestOptions.getMetadata() != null) {
			builder.metadata(requestOptions.getMetadata());
		}

		// Tool definitions will be added in Phase 3
		// Thinking config will be added in Phase 5

		return builder.build();
	}

	/**
	 * Builds generations from the Anthropic message response.
	 * @param message the Anthropic message response
	 * @return list of generations
	 */
	private List<Generation> buildGenerations(Message message) {
		List<Generation> generations = new ArrayList<>();

		String finishReason = message.stopReason().map(r -> r.toString()).orElse("");

		// Collect all text content from text blocks
		StringBuilder textContent = new StringBuilder();
		for (ContentBlock block : message.content()) {
			if (block.isText()) {
				TextBlock textBlock = block.asText();
				textContent.append(textBlock.text());
			}
			// TODO: Handle ToolUseBlock in Phase 3
			// TODO: Handle ThinkingBlock in Phase 5
		}

		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

		AssistantMessage assistantMessage = AssistantMessage.builder().content(textContent.toString()).build();

		generations.add(new Generation(assistantMessage, metadata));

		return generations;
	}

	/**
	 * Creates chat response metadata from the Anthropic message.
	 * @param message the Anthropic message
	 * @param usage the usage information
	 * @return the chat response metadata
	 */
	private ChatResponseMetadata from(Message message, Usage usage) {
		Assert.notNull(message, "Anthropic Message must not be null");
		return ChatResponseMetadata.builder().id(message.id()).usage(usage).model(message.model().asString()).build();
	}

	/**
	 * Converts Anthropic SDK usage to Spring AI usage.
	 * @param usage the Anthropic SDK usage
	 * @return the Spring AI usage
	 */
	private Usage getDefaultUsage(com.anthropic.models.messages.Usage usage) {
		if (usage == null) {
			return new EmptyUsage();
		}
		long inputTokens = usage.inputTokens();
		long outputTokens = usage.outputTokens();
		return new DefaultUsage(Math.toIntExact(inputTokens), Math.toIntExact(outputTokens),
				Math.toIntExact(inputTokens + outputTokens), usage);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.options.copy();
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
