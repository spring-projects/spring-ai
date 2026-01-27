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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropicsdk.setup.AnthropicSdkSetup;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
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
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Chat Model implementation using the official Anthropic Java SDK.
 *
 * @author Soby Chacko
 * @since 1.0.0
 */
public class AnthropicSdkChatModel implements ChatModel, StreamingChatModel {

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

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt, null);
	}

	/**
	 * Internal method to handle streaming chat completion calls.
	 * @param prompt the prompt for the chat completion
	 * @param previousChatResponse the previous chat response for accumulating usage
	 * @return a Flux of chat responses
	 */
	public Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		return Flux.deferContextual(contextView -> {
			MessageCreateParams request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.ANTHROPIC.value())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// Track streaming state for usage accumulation
			StreamingState streamingState = new StreamingState();

			Flux<ChatResponse> chatResponseFlux = Flux.create(sink -> {
				this.anthropicClientAsync.messages().createStreaming(request).subscribe(event -> {
					try {
						ChatResponse chatResponse = convertStreamEventToChatResponse(event, previousChatResponse,
								streamingState);
						if (chatResponse != null) {
							sink.next(chatResponse);
						}
					}
					catch (Exception e) {
						logger.error("Error processing streaming event", e);
						sink.error(e);
					}
				}).onCompleteFuture().whenComplete((result, throwable) -> {
					if (throwable != null) {
						sink.error(throwable);
					}
					else {
						sink.complete();
					}
				});
			});

			// @formatter:off
			Flux<ChatResponse> flux = chatResponseFlux
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	/**
	 * Converts a streaming event to a ChatResponse.
	 * @param event the raw message stream event
	 * @param previousChatResponse the previous chat response for usage accumulation
	 * @param streamingState the state accumulated during streaming
	 * @return the chat response, or null if the event doesn't produce a response
	 */
	@Nullable private ChatResponse convertStreamEventToChatResponse(RawMessageStreamEvent event,
			@Nullable ChatResponse previousChatResponse, StreamingState streamingState) {

		// Handle message start events (contains message ID, model, input tokens)
		if (event.messageStart().isPresent()) {
			var startEvent = event.messageStart().get();
			var message = startEvent.message();
			streamingState.setMessageInfo(message.id(), message.model().asString(), message.usage().inputTokens());
			// Don't emit a ChatResponse for message_start, just capture state
			return null;
		}

		// Handle text delta events
		Optional<String> textDelta = event.contentBlockDelta()
			.flatMap(delta -> delta.delta().text())
			.map(textD -> textD.text());

		if (textDelta.isPresent()) {
			AssistantMessage assistantMessage = AssistantMessage.builder().content(textDelta.get()).build();
			Generation generation = new Generation(assistantMessage);
			return new ChatResponse(List.of(generation));
		}

		// Handle message delta events (contains stop reason and output token usage)
		Optional<ChatResponse> messageDeltaResponse = event.messageDelta().map(deltaEvent -> {
			String stopReason = deltaEvent.delta().stopReason().map(r -> r.toString()).orElse("");
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(stopReason).build();
			AssistantMessage assistantMessage = AssistantMessage.builder().content("").build();
			Generation generation = new Generation(assistantMessage, metadata);

			// Combine input tokens from message_start with output tokens from
			// message_delta
			long inputTokens = streamingState.getInputTokens();
			long outputTokens = deltaEvent.usage().outputTokens();
			Usage usage = new DefaultUsage(Math.toIntExact(inputTokens), Math.toIntExact(outputTokens),
					Math.toIntExact(inputTokens + outputTokens), deltaEvent.usage());

			Usage accumulatedUsage = previousChatResponse != null
					? UsageCalculator.getCumulativeUsage(usage, previousChatResponse) : usage;

			ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
				.id(streamingState.getMessageId())
				.model(streamingState.getModel())
				.usage(accumulatedUsage)
				.build();

			return new ChatResponse(List.of(generation), responseMetadata);
		});

		return messageDeltaResponse.orElse(null);
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
			if (message.getMessageType() == MessageType.SYSTEM) {
				String text = message.getText();
				if (text != null) {
					builder.system(text);
				}
			}
			else if (message.getMessageType() == MessageType.USER) {
				String text = message.getText();
				if (text != null) {
					builder.addUserMessage(text);
				}
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					// TOOL CALLING: When rebuilding conversation history, we must include
					// the assistant's tool use requests. The Anthropic API requires the
					// conversation to show: assistant requested tool → user provided
					// result.
					// Each tool call becomes a ToolUseBlockParam with the original ID and
					// name.
					// Note: The input is empty here because we only need to reference
					// the tool call, not replay its arguments.
					List<ContentBlockParam> toolUseBlocks = assistantMessage.getToolCalls()
						.stream()
						.map(toolCall -> ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
							.id(toolCall.id())
							.name(toolCall.name())
							.input(ToolUseBlockParam.Input.builder().build())
							.build()))
						.toList();
					builder.addAssistantMessageOfBlockParams(toolUseBlocks);
				}
				else {
					String text = message.getText();
					if (text != null) {
						builder.addAssistantMessage(text);
					}
				}
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				// TOOL CALLING: Tool execution results are sent back to the model.
				// Anthropic's API expects tool results as "user" messages containing
				// ToolResultBlockParam. This is different from some other providers
				// that have a separate "tool" role.
				//
				// The toolUseId must match the id from the original ToolUseBlock
				// so the model knows which tool call this result corresponds to.
				ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
				List<ContentBlockParam> toolResultBlocks = toolResponseMessage.getResponses()
					.stream()
					.map(response -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
						.toolUseId(response.id())
						.content(response.responseData())
						.build()))
					.toList();
				builder.addUserMessageOfBlockParams(toolResultBlocks);
			}
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

		// TOOL CALLING: Add tool definitions to enable the model to request tool use.
		// The ToolCallingManager resolves tools from both:
		// - toolCallbacks (inline function definitions with implementations)
		// - toolNames (references to tools registered in a ToolCallbackProvider)
		// Each ToolDefinition is converted to SDK's Tool format via toAnthropicTool().
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			List<ToolUnion> tools = toolDefinitions.stream().map(td -> ToolUnion.ofTool(toAnthropicTool(td))).toList();
			builder.tools(tools);

			// Set tool choice if specified
			if (requestOptions.getToolChoice() != null) {
				builder.toolChoice(requestOptions.getToolChoice());
			}
		}

		// Thinking config will be added in Phase 5

		return builder.build();
	}

	/**
	 * Builds generations from the Anthropic message response.
	 * <p>
	 * This method processes the content blocks returned by the Anthropic API and
	 * extracts:
	 * <ul>
	 * <li><b>Text content</b> - from {@code TextBlock} instances</li>
	 * <li><b>Tool calls</b> - from {@code ToolUseBlock} instances when the model requests
	 * tool execution</li>
	 * </ul>
	 * <p>
	 * <b>Important:</b> When extracting tool calls, the {@code ToolUseBlock._input()}
	 * method returns a {@link JsonValue}, which is the SDK's internal JSON
	 * representation. This must be converted to a proper JSON string using
	 * {@link #convertJsonValueToString(JsonValue)} because Spring AI's {@code ToolCall}
	 * expects the arguments as a JSON string that can be deserialized by Jackson.
	 * @param message the Anthropic message response containing content blocks
	 * @return list of generations with text content and/or tool calls
	 * @see #convertJsonValueToString(JsonValue)
	 */
	private List<Generation> buildGenerations(Message message) {
		List<Generation> generations = new ArrayList<>();

		String finishReason = message.stopReason().map(r -> r.toString()).orElse("");

		// Collect all text content from text blocks and tool use blocks
		StringBuilder textContent = new StringBuilder();
		List<ToolCall> toolCalls = new ArrayList<>();

		for (ContentBlock block : message.content()) {
			if (block.isText()) {
				TextBlock textBlock = block.asText();
				textContent.append(textBlock.text());
			}
			else if (block.isToolUse()) {
				ToolUseBlock toolUseBlock = block.asToolUse();
				// CRITICAL: ToolUseBlock._input() returns JsonValue, not a JSON string.
				// JsonValue.toString() produces Java Map format like "{key=value}",
				// NOT valid JSON like "{\"key\":\"value\"}". We must use the visitor
				// pattern to convert it to native Java objects, then serialize with
				// Jackson.
				String arguments = convertJsonValueToString(toolUseBlock._input());
				toolCalls.add(new ToolCall(toolUseBlock.id(), "function", toolUseBlock.name(), arguments));
			}
			// TODO: Handle ThinkingBlock in Phase 5
		}

		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

		AssistantMessage.Builder assistantMessageBuilder = AssistantMessage.builder().content(textContent.toString());

		if (!toolCalls.isEmpty()) {
			assistantMessageBuilder.toolCalls(toolCalls);
		}

		generations.add(new Generation(assistantMessageBuilder.build(), metadata));

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

	/**
	 * Converts a {@link JsonValue} to a valid JSON string representation.
	 * <p>
	 * <b>Why this is needed:</b> The Anthropic SDK's {@code JsonValue} is a sealed type
	 * that represents JSON data internally. However, calling {@code JsonValue.toString()}
	 * produces Java's default Map/List string format (e.g., {@code {key=value}}), NOT
	 * valid JSON (e.g., {@code {"key":"value"}}).
	 * <p>
	 * This method uses a two-step conversion:
	 * <ol>
	 * <li>Convert {@code JsonValue} to native Java objects (Map, List, String, etc.)
	 * using the visitor pattern via {@link #convertJsonValueToNative(JsonValue)}</li>
	 * <li>Serialize the native objects to JSON using Jackson's ObjectMapper</li>
	 * </ol>
	 * <p>
	 * <b>Example:</b>
	 *
	 * <pre>
	 * // SDK returns: JsonValue representing {"location": "Paris", "unit": "C"}
	 * // JsonValue.toString() would produce: {location=Paris, unit=C}  (INVALID JSON!)
	 * // This method produces: {"location":"Paris","unit":"C"}  (VALID JSON)
	 * </pre>
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return a valid JSON string that can be parsed by Jackson
	 * @throws RuntimeException if JSON serialization fails
	 * @see #convertJsonValueToNative(JsonValue)
	 */
	private String convertJsonValueToString(JsonValue jsonValue) {
		try {
			var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			// Convert JsonValue to a native Java object that Jackson can serialize
			Object nativeValue = convertJsonValueToNative(jsonValue);
			return objectMapper.writeValueAsString(nativeValue);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert JsonValue to string", e);
		}
	}

	/**
	 * Converts a {@link JsonValue} to a native Java object using the visitor pattern.
	 * <p>
	 * The Anthropic SDK's {@code JsonValue} is a sealed type that requires the visitor
	 * pattern to access its contents. This method traverses the JSON structure and
	 * converts it to standard Java types:
	 * <ul>
	 * <li>{@code visitNull()} → {@code null}</li>
	 * <li>{@code visitBoolean()} → {@code Boolean}</li>
	 * <li>{@code visitNumber()} → {@code Number}</li>
	 * <li>{@code visitString()} → {@code String}</li>
	 * <li>{@code visitArray()} → {@code List<Object>} (recursive)</li>
	 * <li>{@code visitObject()} → {@code Map<String, Object>} (recursive)</li>
	 * </ul>
	 * <p>
	 * <b>Note:</b> The visitor interface uses wildcard generics
	 * ({@code List<? extends JsonValue>}) which must be matched exactly in the
	 * implementation.
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return the equivalent native Java object, or null for JSON null
	 */
	@Nullable private Object convertJsonValueToNative(JsonValue jsonValue) {
		return jsonValue.accept(new JsonValue.Visitor<@Nullable Object>() {
			@Override
			@Nullable public Object visitNull() {
				return null;
			}

			@Override
			public Object visitBoolean(boolean value) {
				return value;
			}

			@Override
			public Object visitNumber(Number value) {
				return value;
			}

			@Override
			public Object visitString(String value) {
				return value;
			}

			@Override
			public Object visitArray(List<? extends JsonValue> values) {
				return values.stream().map(v -> convertJsonValueToNative(v)).toList();
			}

			@Override
			public Object visitObject(java.util.Map<String, ? extends JsonValue> values) {
				java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
				for (java.util.Map.Entry<String, ? extends JsonValue> entry : values.entrySet()) {
					result.put(entry.getKey(), convertJsonValueToNative(entry.getValue()));
				}
				return result;
			}
		});
	}

	/**
	 * Converts a Spring AI {@link ToolDefinition} to an Anthropic SDK {@link Tool}.
	 * <p>
	 * <b>The Challenge:</b> Spring AI provides the tool's input schema as a JSON string
	 * (from {@code ToolDefinition.inputSchema()}), but the Anthropic SDK expects a
	 * structured {@code Tool.InputSchema} object built using the builder pattern.
	 * <p>
	 * <b>Spring AI input schema format (JSON string):</b>
	 *
	 * <pre>
	 * {
	 *   "type": "object",
	 *   "properties": {
	 *     "location": {
	 *       "type": "string",
	 *       "description": "The city and state"
	 *     },
	 *     "unit": {
	 *       "type": "string",
	 *       "enum": ["C", "F"]
	 *     }
	 *   },
	 *   "required": ["location", "unit"]
	 * }
	 * </pre>
	 * <p>
	 * <b>SDK expects (builder pattern):</b>
	 *
	 * <pre>
	 * Tool.InputSchema.builder()
	 *     .properties(Properties.builder()
	 *         .putAdditionalProperty("location", JsonValue.from(...))
	 *         .putAdditionalProperty("unit", JsonValue.from(...))
	 *         .build())
	 *     .addRequired("location")
	 *     .addRequired("unit")
	 *     .build()
	 * </pre>
	 * <p>
	 * <b>Conversion steps:</b>
	 * <ol>
	 * <li>Parse the JSON schema string to a {@code Map<String, Object>}</li>
	 * <li>Extract the "properties" map and add each property using
	 * {@code putAdditionalProperty()}</li>
	 * <li>Extract the "required" list and add each field using {@code addRequired()}</li>
	 * <li>Build the final {@code Tool} with name, description, and input schema</li>
	 * </ol>
	 * @param toolDefinition the Spring AI tool definition containing name, description,
	 * and JSON schema
	 * @return the Anthropic SDK Tool with properly structured InputSchema
	 * @throws RuntimeException if the JSON schema cannot be parsed
	 */
	@SuppressWarnings("unchecked")
	private Tool toAnthropicTool(ToolDefinition toolDefinition) {
		try {
			// Step 1: Parse the JSON schema string to a Map
			var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			java.util.Map<String, Object> schemaMap = objectMapper.readValue(toolDefinition.inputSchema(),
					new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
					});

			// Step 2: Build the properties object by iterating over each property
			// and adding it via putAdditionalProperty (SDK doesn't accept raw JSON)
			Tool.InputSchema.Properties.Builder propertiesBuilder = Tool.InputSchema.Properties.builder();
			Object propertiesObj = schemaMap.get("properties");
			if (propertiesObj instanceof java.util.Map) {
				java.util.Map<String, Object> properties = (java.util.Map<String, Object>) propertiesObj;
				for (java.util.Map.Entry<String, Object> entry : properties.entrySet()) {
					// JsonValue.from() can convert Map/List/primitives to JsonValue
					propertiesBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
				}
			}

			// Step 3: Build the input schema with properties
			Tool.InputSchema.Builder inputSchemaBuilder = Tool.InputSchema.builder()
				.properties(propertiesBuilder.build());

			// Step 4: Add required fields if present in the schema
			Object requiredObj = schemaMap.get("required");
			if (requiredObj instanceof java.util.List) {
				java.util.List<String> required = (java.util.List<String>) requiredObj;
				for (String req : required) {
					inputSchemaBuilder.addRequired(req);
				}
			}

			return Tool.builder()
				.name(toolDefinition.name())
				.description(toolDefinition.description())
				.inputSchema(inputSchemaBuilder.build())
				.build();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse tool input schema: " + toolDefinition.inputSchema(), e);
		}
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

	/**
	 * Holds state accumulated during streaming for building complete responses.
	 */
	private static class StreamingState {

		private final AtomicReference<String> messageId = new AtomicReference<>();

		private final AtomicReference<String> model = new AtomicReference<>();

		private final AtomicReference<Long> inputTokens = new AtomicReference<>(0L);

		void setMessageInfo(String id, String modelName, long tokens) {
			this.messageId.set(id);
			this.model.set(modelName);
			this.inputTokens.set(tokens);
		}

		String getMessageId() {
			return this.messageId.get();
		}

		String getModel() {
			return this.model.get();
		}

		long getInputTokens() {
			return this.inputTokens.get();
		}

	}

}
