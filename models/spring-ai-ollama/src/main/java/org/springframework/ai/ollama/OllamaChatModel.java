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

package org.springframework.ai.ollama;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
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
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCallFunction;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} implementation for {@literal Ollama}. Ollama allows developers to run
 * large language models and generate embeddings locally. It supports open-source models
 * available on [Ollama AI Library](<a href="https://ollama.ai/library">...</a>) and on
 * Hugging Face. Please refer to the <a href="https://ollama.ai/">official Ollama
 * website</a> for the most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Thomas Vitale
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @author Sun Yuhan
 * @since 1.0.0
 */
public class OllamaChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(OllamaChatModel.class);

	private static final String DONE = "done";

	private static final String METADATA_PROMPT_EVAL_COUNT = "prompt-eval-count";

	private static final String METADATA_EVAL_COUNT = "eval-count";

	private static final String METADATA_CREATED_AT = "created-at";

	private static final String METADATA_TOTAL_DURATION = "total-duration";

	private static final String METADATA_LOAD_DURATION = "load-duration";

	private static final String METADATA_PROMPT_EVAL_DURATION = "prompt-eval-duration";

	private static final String METADATA_EVAL_DURATION = "eval-duration";

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final OllamaApi chatApi;

	private final OllamaChatOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private final OllamaModelManager modelManager;

	private final ToolCallingManager toolCallingManager;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private final RetryTemplate retryTemplate;

	public OllamaChatModel(OllamaApi ollamaApi, OllamaChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry, ModelManagementOptions modelManagementOptions) {
		this(ollamaApi, defaultOptions, toolCallingManager, observationRegistry, modelManagementOptions,
				new DefaultToolExecutionEligibilityPredicate(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public OllamaChatModel(OllamaApi ollamaApi, OllamaChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry, ModelManagementOptions modelManagementOptions,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate, RetryTemplate retryTemplate) {

		Assert.notNull(ollamaApi, "ollamaApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		Assert.notNull(modelManagementOptions, "modelManagementOptions must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		this.chatApi = ollamaApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.observationRegistry = observationRegistry;
		this.modelManager = new OllamaModelManager(this.chatApi, modelManagementOptions);
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
		this.retryTemplate = retryTemplate;
		String model = defaultOptions.getModel();
		Assert.state(model != null, "model must not be null");
		initializeModel(model, modelManagementOptions.pullModelStrategy());
	}

	public static Builder builder() {
		return new Builder();
	}

	static ChatResponseMetadata from(OllamaApi.ChatResponse response, @Nullable ChatResponse previousChatResponse) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");

		DefaultUsage newUsage = getDefaultUsage(response);
		Integer promptTokens = newUsage.getPromptTokens();
		Integer generationTokens = newUsage.getCompletionTokens();
		int totalTokens = newUsage.getTotalTokens();

		Duration evalDuration = response.getEvalDuration();
		Duration promptEvalDuration = response.getPromptEvalDuration();
		Duration loadDuration = response.getLoadDuration();
		Duration totalDuration = response.getTotalDuration();

		if (previousChatResponse != null && previousChatResponse.getMetadata() != null) {
			Object metadataEvalDuration = previousChatResponse.getMetadata().get(METADATA_EVAL_DURATION);
			if (metadataEvalDuration != null && evalDuration != null) {
				evalDuration = evalDuration.plus((Duration) metadataEvalDuration);
			}
			Object metadataPromptEvalDuration = previousChatResponse.getMetadata().get(METADATA_PROMPT_EVAL_DURATION);
			if (metadataPromptEvalDuration != null && promptEvalDuration != null) {
				promptEvalDuration = promptEvalDuration.plus((Duration) metadataPromptEvalDuration);
			}
			Object metadataLoadDuration = previousChatResponse.getMetadata().get(METADATA_LOAD_DURATION);
			if (metadataLoadDuration != null && loadDuration != null) {
				loadDuration = loadDuration.plus((Duration) metadataLoadDuration);
			}
			Object metadataTotalDuration = previousChatResponse.getMetadata().get(METADATA_TOTAL_DURATION);
			if (metadataTotalDuration != null && totalDuration != null) {
				totalDuration = totalDuration.plus((Duration) metadataTotalDuration);
			}
			if (previousChatResponse.getMetadata().getUsage() != null) {
				promptTokens += previousChatResponse.getMetadata().getUsage().getPromptTokens();
				generationTokens += previousChatResponse.getMetadata().getUsage().getCompletionTokens();
				totalTokens += previousChatResponse.getMetadata().getUsage().getTotalTokens();
			}
		}

		DefaultUsage aggregatedUsage = new DefaultUsage(promptTokens, generationTokens, totalTokens);

		return ChatResponseMetadata.builder()
			.usage(aggregatedUsage)
			.model(response.model())
			.keyValue(METADATA_CREATED_AT, response.createdAt())
			.keyValue(METADATA_EVAL_DURATION, evalDuration)
			.keyValue(METADATA_EVAL_COUNT, aggregatedUsage.getCompletionTokens())
			.keyValue(METADATA_LOAD_DURATION, loadDuration)
			.keyValue(METADATA_PROMPT_EVAL_DURATION, promptEvalDuration)
			.keyValue(METADATA_PROMPT_EVAL_COUNT, aggregatedUsage.getPromptTokens())
			.keyValue(METADATA_TOTAL_DURATION, totalDuration)
			.keyValue(DONE, response.done())
			.build();
	}

	private static DefaultUsage getDefaultUsage(OllamaApi.ChatResponse response) {
		return new DefaultUsage(Optional.ofNullable(response.promptEvalCount()).orElse(0),
				Optional.ofNullable(response.evalCount()).orElse(0));
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		OllamaApi.ChatRequest request = ollamaChatRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(OllamaApiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				OllamaApi.ChatResponse ollamaResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.chatApi.chat(request));

				List<AssistantMessage.ToolCall> toolCalls = ollamaResponse.message().toolCalls() == null ? List.of()
						: ollamaResponse.message()
							.toolCalls()
							.stream()
							.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
									ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
							.toList();

				var assistantMessage = AssistantMessage.builder()
					.content(ollamaResponse.message().content())
					.properties(Map.of())
					.toolCalls(toolCalls)
					.build();

				ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
				if (ollamaResponse.promptEvalCount() != null && ollamaResponse.evalCount() != null) {
					ChatGenerationMetadata.Builder builder = ChatGenerationMetadata.builder()
						.finishReason(ollamaResponse.doneReason());
					String thinking = ollamaResponse.message().thinking();
					if (thinking != null) {
						builder.metadata("thinking", thinking);
					}
					generationMetadata = builder.build();
				}

				var generator = new Generation(assistantMessage, generationMetadata);
				ChatResponse chatResponse = new ChatResponse(List.of(generator),
						from(ollamaResponse, previousChatResponse));

				observationContext.setResponse(chatResponse);

				return chatResponse;

			});

		ChatOptions options = prompt.getOptions();
		Assert.state(options != null, "ChatOptions must not be null");
		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(options, response)) {
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
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), options), response);
			}
		}

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			OllamaApi.ChatRequest request = ollamaChatRequest(prompt, true);

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(OllamaApiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<OllamaApi.ChatResponse> ollamaResponse = this.chatApi.streamingChat(request);

			Flux<ChatResponse> chatResponse = ollamaResponse.map(chunk -> {
				String content = (chunk.message() != null) ? chunk.message().content() : "";

				List<AssistantMessage.ToolCall> toolCalls = List.of();

				// Added null checks to prevent NPE when accessing tool calls
				if (chunk.message() != null && chunk.message().toolCalls() != null) {
					toolCalls = chunk.message()
						.toolCalls()
						.stream()
						.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
								ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
						.toList();
				}

				var assistantMessage = AssistantMessage.builder()
					.content(content)
					.properties(Map.of())
					.toolCalls(toolCalls)
					.build();

				ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
				if (chunk.promptEvalCount() != null && chunk.evalCount() != null) {
					String doneReason = chunk.doneReason();
					Assert.state(doneReason != null, "doneReason must not be null");
					generationMetadata = ChatGenerationMetadata.builder().finishReason(doneReason).build();
				}

				var generator = new Generation(assistantMessage, generationMetadata);
				return new ChatResponse(List.of(generator), from(chunk, previousChatResponse));
			});

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = chatResponse.flatMap(response -> {
				ChatOptions options = prompt.getOptions();
				Assert.state(options != null, "ChatOptions must not be null");
				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(options, response)) {
					// FIXME: bounded elastic needs to be used since tool calling
					//  is currently only synchronous
					return Flux.deferContextual(ctx -> {
						ToolExecutionResult toolExecutionResult;
						try {
							ToolCallReactiveContextHolder.setContext(ctx);
							toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
						}
						finally {
							ToolCallReactiveContextHolder.clearContext();
						}
						if (toolExecutionResult.returnDirect()) {
							// Return tool execution result directly to the client.
							return Flux.just(ChatResponse.builder().from(response)
									.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
									.build());
						}
						else {
							// Send the tool execution result back to the model.
							return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), options),
									response);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}
				else {
					return Flux.just(response);
				}
			})
			.doOnError(observation::error)
			.doFinally(s ->
				observation.stop()
			)
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		OllamaChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof OllamaChatOptions ollamaChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(OllamaChatOptions.fromOptions(ollamaChatOptions),
						OllamaChatOptions.class, OllamaChatOptions.class);
			}
			else if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						OllamaChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						OllamaChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		OllamaChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				OllamaChatOptions.class);
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
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
		}

		// Validate request options
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	/**
	 * Package access for testing.
	 */
	OllamaApi.ChatRequest ollamaChatRequest(Prompt prompt, boolean stream) {

		List<OllamaApi.Message> ollamaMessages = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.SYSTEM) {
				return List.of(OllamaApi.Message.builder(Role.SYSTEM).content(message.getText()).build());
			}
			else if (message.getMessageType() == MessageType.USER) {
				var messageBuilder = OllamaApi.Message.builder(Role.USER).content(message.getText());
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						messageBuilder.images(userMessage.getMedia()
							.stream()
							.map(media -> this.fromMediaData(media.getData()))
							.toList());
					}
				}

				return List.of(messageBuilder.build());
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ToolCallFunction(toolCall.name(),
								JsonParser.fromJson(toolCall.arguments(), new TypeReference<>() {
								}));
						return new ToolCall(function);
					}).toList();
				}
				return List.of(OllamaApi.Message.builder(Role.ASSISTANT)
					.content(assistantMessage.getText())
					.toolCalls(toolCalls)
					.build());
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;
				return toolMessage.getResponses()
					.stream()
					.map(tr -> OllamaApi.Message.builder(Role.TOOL).content(tr.responseData()).build())
					.toList();
			}
			throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}).flatMap(List::stream).toList();

		OllamaChatOptions requestOptions = null;
		if (prompt.getOptions() instanceof OllamaChatOptions) {
			requestOptions = (OllamaChatOptions) prompt.getOptions();
		}
		else {
			requestOptions = OllamaChatOptions
				.fromOptions((OllamaChatOptions) Objects.requireNonNull(prompt.getOptions()));
		}

		String model = requestOptions.getModel();
		Assert.state(model != null, "model must not be null");
		OllamaApi.ChatRequest.Builder requestBuilder = OllamaApi.ChatRequest.builder(model)
			.stream(stream)
			.messages(ollamaMessages)
			.options(requestOptions)
			.think(requestOptions.getThinkOption());

		if (requestOptions.getFormat() != null) {
			requestBuilder.format(requestOptions.getFormat());
		}

		if (requestOptions.getKeepAlive() != null) {
			requestBuilder.keepAlive(requestOptions.getKeepAlive());
		}

		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			requestBuilder.tools(this.getTools(toolDefinitions));
		}

		return requestBuilder.build();
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}

	}

	private List<ChatRequest.Tool> getTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var tool = new ChatRequest.Tool.Function(toolDefinition.name(), toolDefinition.description(),
					toolDefinition.inputSchema());
			return new ChatRequest.Tool(tool);
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OllamaChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Pull the given model into Ollama based on the specified strategy.
	 */
	private void initializeModel(String model, @Nullable PullModelStrategy pullModelStrategy) {
		if (pullModelStrategy != null && !PullModelStrategy.NEVER.equals(pullModelStrategy)) {
			this.modelManager.pullModel(model, pullModelStrategy);
		}
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static final class Builder {

		private @Nullable OllamaApi ollamaApi;

		private OllamaChatOptions defaultOptions = OllamaChatOptions.builder().model(OllamaModel.MISTRAL.id()).build();

		private @Nullable ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ModelManagementOptions modelManagementOptions = ModelManagementOptions.defaults();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private Builder() {
		}

		public Builder ollamaApi(OllamaApi ollamaApi) {
			this.ollamaApi = ollamaApi;
			return this;
		}

		public Builder defaultOptions(OllamaChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder modelManagementOptions(ModelManagementOptions modelManagementOptions) {
			this.modelManagementOptions = modelManagementOptions;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public OllamaChatModel build() {
			Assert.state(this.ollamaApi != null, "OllamaApi must not be null");
			return new OllamaChatModel(this.ollamaApi, this.defaultOptions,
					Objects.requireNonNullElse(this.toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER),
					this.observationRegistry, this.modelManagementOptions, this.toolExecutionEligibilityPredicate,
					this.retryTemplate);
		}

	}

}
