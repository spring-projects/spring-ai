/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.openaisdk;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.ResponseFormatText;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionNamedToolChoice;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openaisdk.setup.OpenAiSdkSetup;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Soby Chacko
 */
public class OpenAiSdkChatModel implements ChatModel {

	private static final String DEFAULT_MODEL_NAME = OpenAiSdkChatOptions.DEFAULT_CHAT_MODEL;

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(OpenAiSdkChatModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final OpenAiSdkChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new builder for {@link OpenAiSdkChatModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new OpenAiSdkChatModel with default options.
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel() {
		this(null, null, null, null, null, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given options.
	 * @param options the chat options
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAiSdkChatOptions options) {
		this(null, null, options, null, null, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given options and observation registry.
	 * @param options the chat options
	 * @param observationRegistry the observation registry
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAiSdkChatOptions options,
			@Nullable ObservationRegistry observationRegistry) {
		this(null, null, options, null, observationRegistry, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given options, tool calling manager, and
	 * observation registry.
	 * @param options the chat options
	 * @param toolCallingManager the tool calling manager
	 * @param observationRegistry the observation registry
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAiSdkChatOptions options, @Nullable ToolCallingManager toolCallingManager,
			@Nullable ObservationRegistry observationRegistry) {
		this(null, null, options, toolCallingManager, observationRegistry, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given OpenAI clients.
	 * @param openAIClient the synchronous OpenAI client
	 * @param openAiClientAsync the asynchronous OpenAI client
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAIClient openAIClient, @Nullable OpenAIClientAsync openAiClientAsync) {
		this(openAIClient, openAiClientAsync, null, null, null, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given OpenAI clients and options.
	 * @param openAIClient the synchronous OpenAI client
	 * @param openAiClientAsync the asynchronous OpenAI client
	 * @param options the chat options
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAIClient openAIClient, @Nullable OpenAIClientAsync openAiClientAsync,
			@Nullable OpenAiSdkChatOptions options) {
		this(openAIClient, openAiClientAsync, options, null, null, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with the given OpenAI clients, options, and
	 * observation registry.
	 * @param openAIClient the synchronous OpenAI client
	 * @param openAiClientAsync the asynchronous OpenAI client
	 * @param options the chat options
	 * @param observationRegistry the observation registry
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAIClient openAIClient, @Nullable OpenAIClientAsync openAiClientAsync,
			@Nullable OpenAiSdkChatOptions options, @Nullable ObservationRegistry observationRegistry) {
		this(openAIClient, openAiClientAsync, options, null, observationRegistry, null);
	}

	/**
	 * Creates a new OpenAiSdkChatModel with all configuration options.
	 * @param openAiClient the synchronous OpenAI client
	 * @param openAiClientAsync the asynchronous OpenAI client
	 * @param options the chat options
	 * @param toolCallingManager the tool calling manager
	 * @param observationRegistry the observation registry
	 * @param toolExecutionEligibilityPredicate the predicate to determine tool execution
	 * eligibility
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "2.0.0-M3")
	public OpenAiSdkChatModel(@Nullable OpenAIClient openAiClient, @Nullable OpenAIClientAsync openAiClientAsync,
			@Nullable OpenAiSdkChatOptions options, @Nullable ToolCallingManager toolCallingManager,
			@Nullable ObservationRegistry observationRegistry,
			@Nullable ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		if (options == null) {
			this.options = OpenAiSdkChatOptions.builder().model(DEFAULT_MODEL_NAME).build();
		}
		else {
			this.options = options;
		}
		this.openAiClient = Objects.requireNonNullElseGet(openAiClient,
				() -> OpenAiSdkSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.openAiClientAsync = Objects.requireNonNullElseGet(openAiClientAsync,
				() -> OpenAiSdkSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
		this.toolCallingManager = Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
		this.toolExecutionEligibilityPredicate = Objects.requireNonNullElse(toolExecutionEligibilityPredicate,
				new DefaultToolExecutionEligibilityPredicate());
	}

	private OpenAiSdkChatModel(Builder builder) {
		if (builder.options == null) {
			this.options = OpenAiSdkChatOptions.builder().model(DEFAULT_MODEL_NAME).build();
		}
		else {
			this.options = builder.options;
		}
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSdkSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.openAiClientAsync = Objects.requireNonNullElseGet(builder.openAiClientAsync,
				() -> OpenAiSdkSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
		this.toolCallingManager = Objects.requireNonNullElse(builder.toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
		this.toolExecutionEligibilityPredicate = Objects.requireNonNullElse(builder.toolExecutionEligibilityPredicate,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Gets the chat options for this model.
	 * @return the chat options
	 */
	public OpenAiSdkChatOptions getOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	/**
	 * Internal method to handle chat completion calls with tool execution support.
	 * @param prompt the prompt for the chat completion
	 * @param previousChatResponse the previous chat response for accumulating usage
	 * @return the chat response
	 */
	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		ChatCompletionCreateParams request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.OPENAI_SDK.value())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ChatCompletion chatCompletion = this.openAiClient.chat().completions().create(request);

				List<ChatCompletion.Choice> choices = chatCompletion.choices();
				if (choices.isEmpty()) {
					logger.warn("No choices returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<Generation> generations = choices.stream().map(choice -> {
					chatCompletion.id();
					choice.finishReason();
					Map<String, Object> metadata = Map.of("id", chatCompletion.id(), "role",
							choice.message()._role().asString().isPresent() ? choice.message()._role().asStringOrThrow()
									: "",
							"index", choice.index(), "finishReason", choice.finishReason().value().toString(),
							"refusal", choice.message().refusal().isPresent() ? choice.message().refusal() : "",
							"annotations", choice.message().annotations().isPresent() ? choice.message().annotations()
									: List.of(Map.of()));
					return buildGeneration(choice, metadata, request);
				}).toList();

				// Current usage
				CompletionUsage usage = chatCompletion.usage().orElse(null);
				Usage currentChatResponseUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
						previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations, from(chatCompletion, accumulatedUsage));

				observationContext.setResponse(chatResponse);

				return chatResponse;

			});

		Assert.state(prompt.getOptions() != null, "Prompt options must not be null");
		Assert.state(response != null, "Chat response must not be null");
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

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt, null);
	}

	/**
	 * Safely extracts the assistant message from a chat response.
	 * @param response the chat response
	 * @return the assistant message, or null if not available
	 */
	public @Nullable AssistantMessage safeAssistantMessage(@Nullable ChatResponse response) {
		if (response == null) {
			return null;
		}
		Generation gen = response.getResult();
		if (gen == null) {
			return null;
		}
		return gen.getOutput();
	}

	/**
	 * Internal method to handle streaming chat completion calls with tool execution
	 * support.
	 * @param prompt the prompt for the chat completion
	 * @param previousChatResponse the previous chat response for accumulating usage
	 * @return a Flux of chat responses
	 */
	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionCreateParams request = createRequest(prompt, true);
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.OPENAI_SDK.value())
				.build();
			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);
			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatResponse> chatResponses = Flux.<ChatResponse>create(sink -> {
				this.openAiClientAsync.chat().completions().createStreaming(request).subscribe(chunk -> {
					try {
						ChatCompletion chatCompletion = chunkToChatCompletion(chunk);
						String id = chatCompletion.id();
						List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
							roleMap.putIfAbsent(id, choice.message()._role().asString().isPresent()
									? choice.message()._role().asStringOrThrow() : "");

							Map<String, Object> metadata = Map.of("id", id, "role", roleMap.getOrDefault(id, ""),
									"index", choice.index(), "finishReason", choice.finishReason().value(), "refusal",
									choice.message().refusal().isPresent() ? choice.message().refusal() : "",
									"annotations", choice.message().annotations().isPresent()
											? choice.message().annotations() : List.of(),
									"chunkChoice", chunk.choices().get((int) choice.index()));

							return buildGeneration(choice, metadata, request);
						}).toList();
						Optional<CompletionUsage> usage = chatCompletion.usage();
						CompletionUsage usageVal = usage.orElse(null);
						Usage currentUsage = usageVal != null ? getDefaultUsage(usageVal) : new EmptyUsage();
						Usage accumulated = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
						sink.next(new ChatResponse(generations, from(chatCompletion, accumulated)));
					}
					catch (Exception e) {
						logger.error("Error processing chat completion", e);
						sink.error(e);
					}
				}).onCompleteFuture().whenComplete((unused, throwable) -> {
					if (throwable != null) {
						sink.error(throwable);
					}
					else {
						sink.complete();
					}
				});
			}).buffer(2, 1).map(buffer -> {
				ChatResponse first = buffer.get(0);
				if (request.streamOptions().isPresent() && buffer.size() == 2) {
					ChatResponse second = buffer.get(1);
					if (second != null) {
						Usage usage = second.getMetadata().getUsage();
						if (!UsageCalculator.isEmpty(usage)) {
							return new ChatResponse(first.getResults(), from(first.getMetadata(), usage));
						}
					}
				}
				return first;
			});

			Flux<ChatResponse> flux = chatResponses
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return flux.collectList().flatMapMany(list -> {
				if (list.isEmpty()) {
					return Flux.empty();
				}
				boolean hasToolCalls = list.stream()
					.map(this::safeAssistantMessage)
					.filter(Objects::nonNull)
					.anyMatch(am -> !CollectionUtils.isEmpty(am.getToolCalls()));
				if (!hasToolCalls) {
					if (list.size() > 2) {
						ChatResponse penultimateResponse = list.get(list.size() - 2); // Get
																						// the
																						// finish
																						// reason
						ChatResponse lastResponse = list.get(list.size() - 1); // Get the
																				// usage
						Usage usage = lastResponse.getMetadata().getUsage();
						observationContext.setResponse(new ChatResponse(penultimateResponse.getResults(),
								from(penultimateResponse.getMetadata(), usage)));
					}
					return Flux.fromIterable(list);
				}
				Map<String, ToolCallBuilder> builders = new HashMap<>();
				StringBuilder text = new StringBuilder();
				ChatResponseMetadata finalMetadata = null;
				ChatGenerationMetadata finalGenMetadata = null;
				Map<String, Object> props = new HashMap<>();
				for (ChatResponse chatResponse : list) {
					AssistantMessage am = safeAssistantMessage(chatResponse);
					if (am == null) {
						continue;
					}
					if (am.getText() != null) {
						text.append(am.getText());
					}
					props.putAll(am.getMetadata());
					if (!CollectionUtils.isEmpty(am.getToolCalls())) {
						Object ccObj = am.getMetadata().get("chunkChoice");
						if (ccObj instanceof ChatCompletionChunk.Choice chunkChoice
								&& chunkChoice.delta().toolCalls().isPresent()) {
							List<ChatCompletionChunk.Choice.Delta.ToolCall> deltaCalls = chunkChoice.delta()
								.toolCalls()
								.get();
							for (int i = 0; i < am.getToolCalls().size() && i < deltaCalls.size(); i++) {
								AssistantMessage.ToolCall tc = am.getToolCalls().get(i);
								ChatCompletionChunk.Choice.Delta.ToolCall dtc = deltaCalls.get(i);
								String key = chunkChoice.index() + "-" + dtc.index();
								ToolCallBuilder toolCallBuilder = builders.computeIfAbsent(key,
										k -> new ToolCallBuilder());
								toolCallBuilder.merge(tc);
							}
						}
						else {
							for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
								ToolCallBuilder toolCallBuilder = builders.computeIfAbsent(tc.id(),
										k -> new ToolCallBuilder());
								toolCallBuilder.merge(tc);
							}
						}
					}
					Generation generation = chatResponse.getResult();
					if (generation != null && generation.getMetadata() != ChatGenerationMetadata.NULL) {
						finalGenMetadata = generation.getMetadata();
					}
					finalMetadata = chatResponse.getMetadata();
				}
				List<AssistantMessage.ToolCall> merged = builders.values()
					.stream()
					.map(ToolCallBuilder::build)
					.filter(tc -> StringUtils.hasText(tc.name()))
					.toList();
				AssistantMessage.Builder assistantMessageBuilder = AssistantMessage.builder()
					.content(text.toString())
					.properties(props);
				if (!merged.isEmpty()) {
					assistantMessageBuilder.toolCalls(merged);
				}
				AssistantMessage assistantMessage = assistantMessageBuilder.build();
				Generation finalGen = new Generation(assistantMessage,
						finalGenMetadata != null ? finalGenMetadata : ChatGenerationMetadata.NULL);
				ChatResponse aggregated = new ChatResponse(List.of(finalGen),
						finalMetadata != null ? finalMetadata : ChatResponseMetadata.builder().build());
				observationContext.setResponse(aggregated);
				Assert.state(prompt.getOptions() != null, "ChatOptions must not be null");
				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), aggregated)) {
					return Flux.deferContextual(ctx -> {
						ToolExecutionResult tetoolExecutionResult;
						try {
							ToolCallReactiveContextHolder.setContext(ctx);
							tetoolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, aggregated);
						}
						finally {
							ToolCallReactiveContextHolder.clearContext();
						}
						if (tetoolExecutionResult.returnDirect()) {
							return Flux.just(ChatResponse.builder()
								.from(aggregated)
								.generations(ToolExecutionResult.buildGenerations(tetoolExecutionResult))
								.build());
						}
						return this.internalStream(
								new Prompt(tetoolExecutionResult.conversationHistory(), prompt.getOptions()),
								aggregated);
					}).subscribeOn(Schedulers.boundedElastic());
				}
				return Flux.just(aggregated);
			}).doOnError(observation::error).doFinally(s -> observation.stop());
		});
	}

	private Generation buildGeneration(ChatCompletion.Choice choice, Map<String, Object> metadata,
			ChatCompletionCreateParams request) {
		ChatCompletionMessage message = choice.message();
		List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

		if (metadata.containsKey("chunkChoice")) {
			Object chunkChoiceObj = metadata.get("chunkChoice");
			if (chunkChoiceObj instanceof ChatCompletionChunk.Choice chunkChoice) {
				if (chunkChoice.delta().toolCalls().isPresent()) {
					toolCalls = chunkChoice.delta()
						.toolCalls()
						.get()
						.stream()
						.filter(tc -> tc.function().isPresent())
						.map(tc -> {
							var funcOpt = tc.function();
							if (funcOpt.isEmpty()) {
								return null;
							}
							var func = funcOpt.get();
							String id = tc.id().orElse("");
							String name = func.name().orElse("");
							String arguments = func.arguments().orElse("");
							return new AssistantMessage.ToolCall(id, "function", name, arguments);
						})
						.filter(Objects::nonNull)
						.toList();
				}
			}
		}
		else {
			toolCalls = message.toolCalls()
				.map(list -> list.stream().filter(tc -> tc.function().isPresent()).map(tc -> {
					var opt = tc.function();
					if (opt.isEmpty()) {
						return null;
					}
					var funcCall = opt.get();
					var functionDef = funcCall.function();
					String id = funcCall.id();
					String name = functionDef.name();
					String arguments = functionDef.arguments();
					return new AssistantMessage.ToolCall(id, "function", name, arguments);
				}).filter(Objects::nonNull).toList())
				.orElse(List.of());
		}

		var generationMetadataBuilder = ChatGenerationMetadata.builder()
			.finishReason(choice.finishReason().value().name());

		String textContent = message.content().orElse("");

		List<Media> media = new ArrayList<>();

		if (message.audio().isPresent() && StringUtils.hasText(message.audio().get().data())
				&& request.audio().isPresent()) {
			var audioOutput = message.audio().get();
			String mimeType = String.format("audio/%s", request.audio().get().format().value().name().toLowerCase());
			byte[] audioData = Base64.getDecoder().decode(audioOutput.data());
			Resource resource = new ByteArrayResource(audioData);
			Media.builder().mimeType(MimeTypeUtils.parseMimeType(mimeType)).data(resource).id(audioOutput.id()).build();
			media.add(Media.builder()
				.mimeType(MimeTypeUtils.parseMimeType(mimeType))
				.data(resource)
				.id(audioOutput.id())
				.build());
			if (!StringUtils.hasText(textContent)) {
				textContent = audioOutput.transcript();
			}
			generationMetadataBuilder.metadata("audioId", audioOutput.id());
			generationMetadataBuilder.metadata("audioExpiresAt", audioOutput.expiresAt());
		}

		var assistantMessage = AssistantMessage.builder()
			.content(textContent)
			.properties(metadata)
			.toolCalls(toolCalls)
			.media(media)
			.build();
		return new Generation(assistantMessage, generationMetadataBuilder.build());
	}

	private ChatResponseMetadata from(ChatCompletion result, Usage usage) {
		Assert.notNull(result, "OpenAI ChatCompletion must not be null");
		result.model();
		result.id();
		return ChatResponseMetadata.builder()
			.id(result.id())
			.usage(usage)
			.model(result.model())
			.keyValue("created", result.created())
			.build();
	}

	private ChatResponseMetadata from(ChatResponseMetadata chatResponseMetadata, Usage usage) {
		Assert.notNull(chatResponseMetadata, "OpenAI ChatResponseMetadata must not be null");
		return ChatResponseMetadata.builder()
			.id(chatResponseMetadata.getId())
			.usage(usage)
			.model(chatResponseMetadata.getModel())
			.build();
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {

		List<ChatCompletion.Choice> choices = (chunk._choices().isMissing()) ? List.of()
				: chunk.choices().stream().map(chunkChoice -> {
					ChatCompletion.Choice.FinishReason finishReason = ChatCompletion.Choice.FinishReason.of("");
					if (chunkChoice.finishReason().isPresent()) {
						finishReason = ChatCompletion.Choice.FinishReason
							.of(chunkChoice.finishReason().get().value().name().toLowerCase());
					}

					ChatCompletion.Choice.Builder choiceBuilder = ChatCompletion.Choice.builder()
						.finishReason(finishReason)
						.index(chunkChoice.index())
						.message(ChatCompletionMessage.builder()
							.content(chunkChoice.delta().content())
							.refusal(chunkChoice.delta().refusal())
							.build());

					// Handle optional logprobs
					if (chunkChoice.logprobs().isPresent()) {
						var logprobs = chunkChoice.logprobs().get();
						choiceBuilder.logprobs(ChatCompletion.Choice.Logprobs.builder()
							.content(logprobs.content())
							.refusal(logprobs.refusal())
							.build());
					}
					else {
						// Provide empty logprobs when not present
						choiceBuilder.logprobs(
								ChatCompletion.Choice.Logprobs.builder().content(List.of()).refusal(List.of()).build());
					}

					chunkChoice.delta();

					return choiceBuilder.build();
				}).toList();

		return ChatCompletion.builder()
			.id(chunk.id())
			.choices(choices)
			.created(chunk.created())
			.model(chunk.model())
			.usage(chunk.usage()
				.orElse(CompletionUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build()))
			.build();
	}

	private DefaultUsage getDefaultUsage(CompletionUsage usage) {
		return new DefaultUsage(Math.toIntExact(usage.promptTokens()), Math.toIntExact(usage.completionTokens()),
				Math.toIntExact(usage.totalTokens()), usage);
	}

	/**
	 * Builds the request prompt by merging runtime options with default options.
	 * @param prompt the original prompt
	 * @return the prompt with merged options
	 */
	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		OpenAiSdkChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						OpenAiSdkChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						OpenAiSdkChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		OpenAiSdkChatOptions requestOptions = OpenAiSdkChatOptions.builder()
			.from(this.options)
			.merge(runtimeOptions != null ? runtimeOptions : OpenAiSdkChatOptions.builder().build())
			.build();

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			if (runtimeOptions.getTopK() != null) {
				logger.warn("The topK option is not supported by OpenAI chat models. Ignoring.");
			}

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
	 * Creates a chat completion request from the given prompt.
	 * @param prompt the prompt containing messages and options
	 * @param stream whether this is a streaming request
	 * @return the chat completion create parameters
	 */
	ChatCompletionCreateParams createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessageParam> chatCompletionMessageParams = prompt.getInstructions()
			.stream()
			.map(message -> {
				if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
					// Handle simple text content for user and system messages
					ChatCompletionUserMessageParam.Builder builder = ChatCompletionUserMessageParam.builder();

					if (message instanceof UserMessage userMessage
							&& !CollectionUtils.isEmpty(userMessage.getMedia())) {
						// Handle media content (images, audio, files)
						List<ChatCompletionContentPart> parts = new ArrayList<>();

						String messageText = message.getText();
						if (messageText != null && !messageText.isEmpty()) {
							parts.add(ChatCompletionContentPart
								.ofText(ChatCompletionContentPartText.builder().text(messageText).build()));
						}

						// Add media content parts
						userMessage.getMedia().forEach(media -> {
							String mimeType = media.getMimeType().toString();
							if (mimeType.startsWith("image/")) {
								if (media.getData() instanceof java.net.URI uri) {
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
												.url(uri.toString())
												.build())
											.build()));
								}
								else if (media.getData() instanceof String text) {
									// The org.springframework.ai.content.Media object
									// should store the URL as a java.net.URI but it
									// transforms it to String somewhere along the way,
									// for example in its Builder class. So, we accept
									// String as well here for image URLs.
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(
													ChatCompletionContentPartImage.ImageUrl.builder().url(text).build())
											.build()));
								}
								else if (media.getData() instanceof byte[] bytes) {
									// Assume the bytes are an image. So, convert the
									// bytes to a base64 encoded
									ChatCompletionContentPartImage.ImageUrl.Builder imageUrlBuilder = ChatCompletionContentPartImage.ImageUrl
										.builder();

									imageUrlBuilder.url("data:" + mimeType + ";base64,"
											+ Base64.getEncoder().encodeToString(bytes));
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(imageUrlBuilder.build())
											.build()));
								}
								else {
									logger.info(
											"Could not process image media with data of type: {}. Only java.net.URI is supported for image URLs.",
											media.getData().getClass().getSimpleName());
								}
							}
							else if (mimeType.startsWith("audio/")) {
								parts.add(ChatCompletionContentPart
									.ofInputAudio(ChatCompletionContentPartInputAudio.builder()
										.inputAudio(ChatCompletionContentPartInputAudio.builder()
											.inputAudio(ChatCompletionContentPartInputAudio.InputAudio.builder()
												.data(fromAudioData(media.getData()))
												.format(mimeType.contains("mp3")
														? ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
														: ChatCompletionContentPartInputAudio.InputAudio.Format.WAV)
												.build())
											.build()
											.inputAudio())
										.build()));
							}
							else {
								// Assume it's a file or other media type represented as a
								// data URL
								parts.add(ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
									.text(fromMediaData(media.getMimeType(), media.getData()))
									.build()));
							}
						});
						builder.contentOfArrayOfContentParts(parts);
					}
					else {
						// Simple text message
						String messageText = message.getText();
						if (messageText != null) {
							builder.content(ChatCompletionContentPartText.builder().text(messageText).build().text());
						}
					}

					if (message.getMessageType() == MessageType.USER) {
						builder.role(JsonValue.from(MessageType.USER.getValue()));
					}
					else {
						builder.role(JsonValue.from(MessageType.SYSTEM.getValue()));
					}

					return List.of(ChatCompletionMessageParam.ofUser(builder.build()));
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					var assistantMessage = (AssistantMessage) message;
					ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder()
						.role(JsonValue.from(MessageType.ASSISTANT.getValue()));

					if (assistantMessage.getText() != null) {
						builder.content(ChatCompletionAssistantMessageParam.builder()
							.content(assistantMessage.getText())
							.build()
							.content());
					}

					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						List<ChatCompletionMessageToolCall> toolCalls = assistantMessage.getToolCalls()
							.stream()
							.map(toolCall -> ChatCompletionMessageToolCall
								.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
									.id(toolCall.id())
									.function(ChatCompletionMessageFunctionToolCall.Function.builder()
										.name(toolCall.name())
										.arguments(toolCall.arguments())
										.build())
									.build()))
							.toList();

						builder.toolCalls(toolCalls);
					}

					return List.of(ChatCompletionMessageParam.ofAssistant(builder.build()));
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					ToolResponseMessage toolMessage = (ToolResponseMessage) message;

					ChatCompletionToolMessageParam.Builder builder = ChatCompletionToolMessageParam.builder();
					builder.content(toolMessage.getText() != null ? toolMessage.getText() : "");
					builder.role(JsonValue.from(MessageType.TOOL.getValue()));

					if (toolMessage.getResponses().isEmpty()) {
						return List.of(ChatCompletionMessageParam.ofTool(builder.build()));
					}
					return toolMessage.getResponses().stream().map(response -> {
						String callId = response.id();
						String callResponse = response.responseData();

						return ChatCompletionMessageParam
							.ofTool(builder.toolCallId(callId).content(callResponse).build());
					}).toList();
				}
				else {
					throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
				}
			})
			.flatMap(List::stream)
			.toList();

		ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();

		chatCompletionMessageParams.forEach(builder::addMessage);

		OpenAiSdkChatOptions requestOptions = (OpenAiSdkChatOptions) prompt.getOptions();
		Assert.state(requestOptions != null, "ChatOptions must not be null");

		// Use deployment name if available (for Microsoft Foundry), otherwise use model
		// name
		if (requestOptions.getDeploymentName() != null) {
			builder.model(requestOptions.getDeploymentName());
		}
		else if (requestOptions.getModel() != null) {
			builder.model(requestOptions.getModel());
		}

		if (requestOptions.getFrequencyPenalty() != null) {
			builder.frequencyPenalty(requestOptions.getFrequencyPenalty());
		}
		if (requestOptions.getLogitBias() != null) {
			builder.logitBias(ChatCompletionCreateParams.LogitBias.builder()
				.putAllAdditionalProperties(requestOptions.getLogitBias()
					.entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
				.build());
		}
		if (requestOptions.getLogprobs() != null) {
			builder.logprobs(requestOptions.getLogprobs());
		}
		if (requestOptions.getTopLogprobs() != null) {
			builder.topLogprobs(requestOptions.getTopLogprobs());
		}
		if (requestOptions.getMaxTokens() != null) {
			builder.maxTokens(requestOptions.getMaxTokens());
		}
		if (requestOptions.getMaxCompletionTokens() != null) {
			builder.maxCompletionTokens(requestOptions.getMaxCompletionTokens());
		}
		if (requestOptions.getN() != null) {
			builder.n(requestOptions.getN());
		}
		if (requestOptions.getOutputModalities() != null) {
			builder.modalities(requestOptions.getOutputModalities()
				.stream()
				.map(modality -> ChatCompletionCreateParams.Modality.of(modality.toLowerCase()))
				.toList());
		}
		if (requestOptions.getOutputAudio() != null) {
			builder.audio(requestOptions.getOutputAudio().toChatCompletionAudioParam());
		}
		if (requestOptions.getPresencePenalty() != null) {
			builder.presencePenalty(requestOptions.getPresencePenalty());
		}
		if (requestOptions.getResponseFormat() != null) {
			ResponseFormat responseFormat = requestOptions.getResponseFormat();
			if (responseFormat.getType().equals(ResponseFormat.Type.TEXT)) {
				builder.responseFormat(ResponseFormatText.builder().build());
			}
			else if (responseFormat.getType().equals(ResponseFormat.Type.JSON_OBJECT)) {
				builder.responseFormat(ResponseFormatJsonObject.builder().build());
			}
			else if (responseFormat.getType().equals(ResponseFormat.Type.JSON_SCHEMA)) {
				String jsonSchemaString = responseFormat.getJsonSchema() != null ? responseFormat.getJsonSchema() : "";
				try {
					com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
					ResponseFormatJsonSchema.JsonSchema.Builder jsonSchemaBuilder = ResponseFormatJsonSchema.JsonSchema
						.builder();
					jsonSchemaBuilder.name("json_schema");
					jsonSchemaBuilder.strict(true);

					ResponseFormatJsonSchema.JsonSchema.Schema schema = mapper.readValue(jsonSchemaString,
							ResponseFormatJsonSchema.JsonSchema.Schema.class);

					jsonSchemaBuilder.schema(schema);

					builder.responseFormat(
							ResponseFormatJsonSchema.builder().jsonSchema(jsonSchemaBuilder.build()).build());
				}
				catch (Exception e) {
					throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchemaString, e);
				}
			}
			else {
				throw new IllegalArgumentException("Unsupported response format type: " + responseFormat.getType());
			}
		}
		if (requestOptions.getSeed() != null) {
			builder.seed(requestOptions.getSeed());
		}
		if (requestOptions.getStop() != null && !requestOptions.getStop().isEmpty()) {
			if (requestOptions.getStop().size() == 1) {
				builder.stop(ChatCompletionCreateParams.Stop.ofString(requestOptions.getStop().get(0)));
			}
			else {
				builder.stop(ChatCompletionCreateParams.Stop.ofStrings(requestOptions.getStop()));
			}
		}
		if (requestOptions.getTemperature() != null) {
			builder.temperature(requestOptions.getTemperature());
		}
		if (requestOptions.getTopP() != null) {
			builder.topP(requestOptions.getTopP());
		}
		if (requestOptions.getUser() != null) {
			builder.user(requestOptions.getUser());
		}
		if (requestOptions.getParallelToolCalls() != null) {
			builder.parallelToolCalls(requestOptions.getParallelToolCalls());
		}
		if (requestOptions.getReasoningEffort() != null) {
			builder.reasoningEffort(ReasoningEffort.of(requestOptions.getReasoningEffort().toLowerCase()));
		}
		if (requestOptions.getVerbosity() != null) {
			builder.verbosity(ChatCompletionCreateParams.Verbosity.of(requestOptions.getVerbosity()));
		}

		if (requestOptions.getStore() != null) {
			builder.store(requestOptions.getStore());
		}
		if (requestOptions.getMetadata() != null && !requestOptions.getMetadata().isEmpty()) {
			builder.metadata(ChatCompletionCreateParams.Metadata.builder()
				.putAllAdditionalProperties(requestOptions.getMetadata()
					.entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
				.build());
		}
		if (requestOptions.getServiceTier() != null) {
			builder.serviceTier(ChatCompletionCreateParams.ServiceTier.of(requestOptions.getServiceTier()));
		}

		if (stream) {
			if (requestOptions.getStreamOptions() != null) {
				ChatCompletionStreamOptions.Builder streamOptionsBuilder = ChatCompletionStreamOptions.builder();

				var ops = requestOptions.getStreamOptions();

				streamOptionsBuilder.includeObfuscation(ops.includeObfuscation() != null && ops.includeObfuscation());
				streamOptionsBuilder.includeUsage(ops.includeUsage() != null && ops.includeUsage());

				if (!CollectionUtils.isEmpty(ops.additionalProperties())) {
					Map<String, com.openai.core.JsonValue> nativeParams = ops.additionalProperties()
						.entrySet()
						.stream()
						.map(e -> Map.entry(e.getKey(), com.openai.core.JsonValue.from(e.getValue())))
						.collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);

					streamOptionsBuilder.putAllAdditionalProperties(nativeParams);
				}
				builder.streamOptions(streamOptionsBuilder.build());
			}
			else {
				builder.streamOptions(ChatCompletionStreamOptions.builder()
					.includeUsage(true) // Include usage by default for streaming
					.build());
			}
		}

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			builder.tools(getChatCompletionTools(toolDefinitions));
		}

		if (requestOptions.getToolChoice() != null) {
			if (requestOptions.getToolChoice() instanceof ChatCompletionToolChoiceOption toolChoiceOption) {
				builder.toolChoice(toolChoiceOption);
			}
			else if (requestOptions.getToolChoice() instanceof String json) {
				try {
					var node = ModelOptionsUtils.JSON_MAPPER.readTree(json);
					builder.toolChoice(parseToolChoice(node));
				}
				catch (Exception e) {
					throw new IllegalArgumentException("Failed to parse toolChoice JSON: " + json, e);
				}
			}
		}

		return builder.build();
	}

	public static ChatCompletionToolChoiceOption parseToolChoice(JsonNode node) {
		String type = node.get("type").asText();
		switch (type) {
			case "function":
				String functionName = node.get("function").get("name").asText();
				ChatCompletionNamedToolChoice.Function func = ChatCompletionNamedToolChoice.Function.builder()
					.name(functionName)
					.build();
				ChatCompletionNamedToolChoice named = ChatCompletionNamedToolChoice.builder().function(func).build();
				return ChatCompletionToolChoiceOption.ofNamedToolChoice(named);
			case "auto":
				// There is a built-in “auto” option — but how to get it depends on SDK
				// version
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO);
			case "required":
				// There may or may not be a 'required' option; if SDK supports, you need
				// a way to construct it
				// If it's not supported, you must use JSON fallback
				throw new UnsupportedOperationException("SDK version does not support typed 'required' toolChoice");
			case "none":
				// Similarly for none
				throw new UnsupportedOperationException("SDK version does not support typed 'none' toolChoice");
			default:
				throw new IllegalArgumentException("Unknown tool_choice type: " + type);
		}
	}

	private String fromAudioData(Object audioData) {
		if (audioData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		throw new IllegalArgumentException("Unsupported audio data type: " + audioData.getClass().getSimpleName());
	}

	private String fromMediaData(org.springframework.util.MimeType mimeType, Object mediaContentData) {
		if (mediaContentData instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64 encoded
			// following the prefix pattern.
			return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
		}
		else if (mediaContentData instanceof String text) {
			// Assume the text is a URLs or a base64 encoded image prefixed by the user.
			return text;
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
		}
	}

	private List<ChatCompletionTool> getChatCompletionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			FunctionParameters.Builder parametersBuilder = FunctionParameters.builder();

			if (!toolDefinition.inputSchema().isEmpty()) {
				// Parse the schema and add its properties directly
				try {
					com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
					@SuppressWarnings("unchecked")
					Map<String, Object> schemaMap = mapper.readValue(toolDefinition.inputSchema(), Map.class);

					// Add each property from the schema to the parameters
					schemaMap
						.forEach((key, value) -> parametersBuilder.putAdditionalProperty(key, JsonValue.from(value)));

					// Add strict mode
					parametersBuilder.putAdditionalProperty("strict", JsonValue.from(true)); // TODO
																								// allow
																								// non-strict
																								// mode
				}
				catch (Exception e) {
					logger.error("Failed to parse tool schema", e);
				}
			}

			FunctionDefinition functionDefinition = FunctionDefinition.builder()
				.name(toolDefinition.name())
				.description(toolDefinition.description())
				.parameters(parametersBuilder.build())
				.build();

			return ChatCompletionTool
				.ofFunction(ChatCompletionFunctionTool.builder().function(functionDefinition).build());
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.options.copy();
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Response format (text, json_object, json_schema) for OpenAiSdkChatModel responses.
	 *
	 * @author Julien Dubois
	 */
	public static class ResponseFormat {

		private Type type = Type.TEXT;

		private @Nullable String jsonSchema;

		public Type getType() {
			return this.type;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public @Nullable String getJsonSchema() {
			return this.jsonSchema;
		}

		public void setJsonSchema(@Nullable String jsonSchema) {
			this.jsonSchema = jsonSchema;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private final ResponseFormat responseFormat = new ResponseFormat();

			private Builder() {
			}

			public Builder type(Type type) {
				this.responseFormat.setType(type);
				return this;
			}

			public Builder jsonSchema(String jsonSchema) {
				this.responseFormat.setType(Type.JSON_SCHEMA);
				this.responseFormat.setJsonSchema(jsonSchema);
				return this;
			}

			public ResponseFormat build() {
				return this.responseFormat;
			}

		}

		public enum Type {

			/**
			 * Generates a text response. (default)
			 */
			TEXT,

			/**
			 * Enables JSON mode, which guarantees the message the model generates is
			 * valid JSON.
			 */
			JSON_OBJECT,

			/**
			 * Enables Structured Outputs which guarantees the model will match your
			 * supplied JSON schema.
			 */
			JSON_SCHEMA

		}

	}

	/**
	 * Helper class to merge streaming tool calls that arrive in pieces across multiple
	 * chunks. In OpenAI streaming, a tool call's ID, name, and arguments can arrive in
	 * separate chunks.
	 */
	private static class ToolCallBuilder {

		private String id = "";

		private String type = "function";

		private String name = "";

		private StringBuilder arguments = new StringBuilder();

		void merge(AssistantMessage.ToolCall toolCall) {
			if (!toolCall.id().isEmpty()) {
				this.id = toolCall.id();
			}
			if (!toolCall.type().isEmpty()) {
				this.type = toolCall.type();
			}
			if (!toolCall.name().isEmpty()) {
				this.name = toolCall.name();
			}
			if (!toolCall.arguments().isEmpty()) {
				this.arguments.append(toolCall.arguments());
			}
		}

		AssistantMessage.ToolCall build() {
			return new AssistantMessage.ToolCall(this.id, this.type, this.name, this.arguments.toString());
		}

	}

	/**
	 * Builder for creating {@link OpenAiSdkChatModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable OpenAiSdkChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

		private Builder() {
		}

		/**
		 * Sets the synchronous OpenAI client.
		 * @param openAiClient the synchronous client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the asynchronous OpenAI client.
		 * @param openAiClientAsync the asynchronous client
		 * @return this builder
		 */
		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
			return this;
		}

		/**
		 * Sets the chat options.
		 * @param options the chat options
		 * @return this builder
		 */
		public Builder options(OpenAiSdkChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Sets the observation registry for metrics and tracing.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Sets the predicate to determine tool execution eligibility.
		 * @param toolExecutionEligibilityPredicate the predicate
		 * @return this builder
		 */
		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
			return this;
		}

		/**
		 * Builds a new {@link OpenAiSdkChatModel} instance.
		 * @return the configured chat model
		 */
		public OpenAiSdkChatModel build() {
			return new OpenAiSdkChatModel(this);
		}

	}

}
