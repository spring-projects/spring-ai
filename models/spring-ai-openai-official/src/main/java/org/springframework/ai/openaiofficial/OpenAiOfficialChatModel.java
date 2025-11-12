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

package org.springframework.ai.openaiofficial;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonArray;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import com.openai.models.completions.CompletionUsage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.springframework.ai.openaiofficial.setup.OpenAiOfficialSetup.setupAsyncClient;
import static org.springframework.ai.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;

/**
 * Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialChatModel implements ChatModel {

	private static final String DEFAULT_MODEL_NAME = OpenAiOfficialChatOptions.DEFAULT_CHAT_MODEL;

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(OpenAiOfficialChatModel.class);

	private OpenAIClient openAiClient;

	private OpenAIClientAsync openAiClientAsync;

	private final OpenAiOfficialChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OpenAiOfficialChatModel() {
		this(null, null, null, null, null, null);
	}

	public OpenAiOfficialChatModel(OpenAiOfficialChatOptions options) {
		this(null, null, options, null, null, null);
	}

	public OpenAiOfficialChatModel(OpenAiOfficialChatOptions options, ObservationRegistry observationRegistry) {
		this(null, null, options, null, observationRegistry, null);
	}

	public OpenAiOfficialChatModel(OpenAiOfficialChatOptions options, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry) {
		this(null, null, options, toolCallingManager, observationRegistry, null);
	}

	public OpenAiOfficialChatModel(OpenAIClient openAIClient, OpenAIClientAsync openAiClientAsync) {
		this(openAIClient, openAiClientAsync, null, null, null, null);
	}

	public OpenAiOfficialChatModel(OpenAIClient openAIClient, OpenAIClientAsync openAiClientAsync,
			OpenAiOfficialChatOptions options) {
		this(openAIClient, openAiClientAsync, options, null, null, null);
	}

	public OpenAiOfficialChatModel(OpenAIClient openAIClient, OpenAIClientAsync openAiClientAsync,
			OpenAiOfficialChatOptions options, ObservationRegistry observationRegistry) {
		this(openAIClient, openAiClientAsync, options, null, observationRegistry, null);
	}

	public OpenAiOfficialChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync,
			OpenAiOfficialChatOptions options, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		if (options == null) {
			this.options = OpenAiOfficialChatOptions.builder().model(DEFAULT_MODEL_NAME).build();
		}
		else {
			this.options = options;
		}
		this.openAiClient = Objects.requireNonNullElseGet(openAiClient,
				() -> setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(), this.options.getCredential(),
						this.options.getAzureDeploymentName(), this.options.getAzureOpenAIServiceVersion(),
						this.options.getOrganizationId(), this.options.isAzure(), this.options.isGitHubModels(),
						this.options.getModel(), this.options.getTimeout(), this.options.getMaxRetries(),
						this.options.getProxy(), this.options.getCustomHeaders()));

		this.openAiClientAsync = Objects.requireNonNullElseGet(openAiClientAsync,
				() -> setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getAzureDeploymentName(),
						this.options.getAzureOpenAIServiceVersion(), this.options.getOrganizationId(),
						this.options.isAzure(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders()));

		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
		this.toolCallingManager = Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
		this.toolExecutionEligibilityPredicate = Objects.requireNonNullElse(toolExecutionEligibilityPredicate,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public OpenAiOfficialChatOptions getOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		if (this.openAiClient == null) {
			throw new IllegalStateException(
					"OpenAI sync client is not configured. Have you set the 'streamUsage' option to false?");
		}
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		ChatCompletionCreateParams request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.OPENAI_OFFICIAL.value())
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
					return buildGeneration(choice, metadata);
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
		if (this.openAiClientAsync == null) {
			throw new IllegalStateException(
					"OpenAI async client is not configured. Streaming is not supported with the current configuration. Have you set the 'streamUsage' option to true?");
		}
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionCreateParams request = createRequest(prompt, true);

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.OPENAI_OFFICIAL.value())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatResponse> chatResponses = Flux.create(sink -> {
				this.openAiClientAsync.chat().completions().createStreaming(request).subscribe(chunk -> {
					try {
						ChatCompletion chatCompletion = chunkToChatCompletion(chunk);
						// If an id is not provided, set to "NO_ID" (for compatible APIs).
						chatCompletion.id();
						String id = chatCompletion.id();

						List<Generation> generations = chatCompletion.choices().stream().map(choice -> { // @formatter:off
								roleMap.putIfAbsent(id, choice.message()._role().asString().isPresent() ? choice.message()._role().asStringOrThrow() : "");
								Map<String, Object> metadata = Map.of(
										"id", id,
										"role", roleMap.getOrDefault(id, ""),
										"index", choice.index(),
										"finishReason", choice.finishReason().asString(),
										"refusal", choice.message().refusal().isPresent() ? choice.message().refusal() : "",
										"annotations", choice.message().annotations().isPresent() ? choice.message().annotations() : List.of());
								return buildGeneration(choice, metadata);
							}).toList();

							Optional<CompletionUsage> usage = chatCompletion.usage();
							Usage currentChatResponseUsage = usage.isPresent()? getDefaultUsage(usage.get()) : new EmptyUsage();
							Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
									previousChatResponse);
							ChatResponse response = new ChatResponse(generations, from(chatCompletion, accumulatedUsage));
							sink.next(response);
						}
						catch (Exception e) {
							logger.error("Error processing chat completion", e);
							sink.error(e);
						}
					}).onCompleteFuture().whenComplete((unused, throwable) -> {
						if (throwable != null) {
							sink.error(throwable);
						} else {
							sink.complete();
						}
					});
		})
					.buffer(2, 1)
					.map(bufferList -> {
								ChatResponse firstResponse = (ChatResponse) bufferList.get(0);
								if (request.streamOptions().isPresent()) {
									if (bufferList.size() == 2) {
										ChatResponse secondResponse = (ChatResponse) bufferList.get(1);
										if (secondResponse!=null) {
											// This is the usage from the final Chat response for a
											// given Chat request.
											Usage usage = secondResponse.getMetadata().getUsage();
											if (!UsageCalculator.isEmpty(usage)) {
												// Store the usage from the final response to the
												// penultimate response for accumulation.
												return new ChatResponse(firstResponse.getResults(),
														from(firstResponse.getMetadata(), usage));
											}
										}
									}
								}
								return firstResponse;
					});

		Flux<ChatResponse> flux = chatResponses.flatMap(response -> {
						assert prompt.getOptions() != null;
						if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
					// FIXME: bounded elastic needs to be used since tool calling
					// is currently only synchronous
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
							return Flux.just(ChatResponse.builder()
								.from(response)
								.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
								.build());
						}
						else {
							// Send the tool execution result back to the model.
							return this.internalStream(
									new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
									response);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}
				else {
					return Flux.just(response);
				}
			})
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);

		});
	}

	private Generation buildGeneration(ChatCompletion.Choice choice, Map<String, Object> metadata) {
		ChatCompletionMessage message = choice.message();
		List<AssistantMessage.ToolCall> toolCalls = message.toolCalls()
			.map(toolCallsList -> toolCallsList.stream()
					.filter(toolCall -> toolCall.function().isPresent())
					.map(toolCall -> {
					return new AssistantMessage.ToolCall(toolCall.function().get().id(), "function", toolCall.function().get().function().name(),
							toolCall.function().get().function().arguments());
					}).toList()).orElse(List.of());

		var generationMetadataBuilder = ChatGenerationMetadata.builder()
			.finishReason(choice.finishReason().value().name());

		String textContent = message.content().isPresent() ? message.content().get() : "";

		var assistantMessage = AssistantMessage.builder()
			.content(textContent)
			.properties(metadata)
			.toolCalls(toolCalls)
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
			.id(chatResponseMetadata.getId() != null ? chatResponseMetadata.getId() : "")
			.usage(usage)
			.model(chatResponseMetadata.getModel() != null ? chatResponseMetadata.getModel() : "")
			.build();
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
		List<ChatCompletion.Choice> choices = chunk.choices()
			.stream()
			.map(chunkChoice -> {
				ChatCompletion.Choice.Builder choiceBuilder = ChatCompletion.Choice.builder()
					.finishReason(ChatCompletion.Choice.FinishReason.of(chunkChoice.finishReason().toString()))
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
				} else {
					// Provide empty logprobs when not present
					choiceBuilder.logprobs(ChatCompletion.Choice.Logprobs.builder()
						.content(List.of())
						.refusal(List.of())
						.build());
				}

				return choiceBuilder.build();
			})
			.toList();

		return ChatCompletion.builder()
			.id(chunk.id())
			.choices(choices)
			.created(chunk.created())
			.model(chunk.model())
			.usage(chunk.usage().orElse(CompletionUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build()))
			.build();
	}

	private DefaultUsage getDefaultUsage(CompletionUsage usage) {
		return new DefaultUsage(Math.toIntExact(usage.promptTokens()), Math.toIntExact(usage.completionTokens()),
				Math.toIntExact(usage.totalTokens()), usage);
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		OpenAiOfficialChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						OpenAiOfficialChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						OpenAiOfficialChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		OpenAiOfficialChatOptions requestOptions = OpenAiOfficialChatOptions.builder()
			.from(this.options)
			.merge(runtimeOptions != null ? runtimeOptions : OpenAiOfficialChatOptions.builder().build())
			.build();

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			if (runtimeOptions.getTopK() != null) {
				logger.warn("The topK option is not supported by OpenAI chat models. Ignoring.");
			}

			Map<String, String> mergedHttpHeaders = new HashMap<>(this.options.getHttpHeaders());
			mergedHttpHeaders.putAll(runtimeOptions.getHttpHeaders());
			requestOptions.setHttpHeaders(mergedHttpHeaders);

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
			requestOptions.setHttpHeaders(this.options.getHttpHeaders());
			requestOptions.setInternalToolExecutionEnabled(this.options.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.options.getToolNames());
			requestOptions.setToolCallbacks(this.options.getToolCallbacks());
			requestOptions.setToolContext(this.options.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	ChatCompletionCreateParams createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessageParam> chatCompletionMessageParams = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				// Handle simple text content for user and system messages
				ChatCompletionUserMessageParam.Builder builder = ChatCompletionUserMessageParam.builder();

				if (message instanceof UserMessage userMessage && !CollectionUtils.isEmpty(userMessage.getMedia())) {
					// Handle media content (images, audio, files)
					List<ChatCompletionContentPart> parts = new ArrayList<>();

					if (!message.getText().isEmpty()) {
						parts.add(ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(message.getText()).build()));
					}

					// Add media content parts
					userMessage.getMedia().forEach(media -> {
						String mimeType = media.getMimeType().toString();
						if (mimeType.startsWith("image/")) {
							if (media.getData() instanceof java.net.URI uri) {
								parts.add(ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage.builder()
										.imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(uri.toString()).build())
										.build()));
								} else if (media.getData() instanceof String text) {
								// The org.springframework.ai.content.Media object should store the URL as a java.net.URI but it transforms it to String somewhere along the way,
								// for example in its Builder class. So, we accept String as well here for image URLs.
								parts.add(ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage.builder()
										.imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(text).build())
										.build()));
							} else {
								logger.info("Could not process image media with data of type: {}. Only java.net.URI is supported for image URLs.",
										media.getData().getClass().getSimpleName());
							}
						}
						else if (mimeType.startsWith("audio/")) {
							parts.add(ChatCompletionContentPart.ofInputAudio(ChatCompletionContentPartInputAudio.builder()
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
							// Assume it's a file or other media type represented as a data URL
							parts.add(ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(fromMediaData(media.getMimeType(), media.getData())).build()));
						}
					});
					builder.contentOfArrayOfContentParts(parts);
				}
				else {
					// Simple text message
					builder.content(ChatCompletionContentPartText.builder().text(message.getText()).build().text());
				}

				if (message.getMessageType() == MessageType.USER) {
					builder.role(JsonValue.from(MessageType.USER.getValue()));
				}
				else {
					builder.role(JsonValue.from(MessageType.SYSTEM.getValue()));
				}

				return ChatCompletionMessageParam.ofUser(builder.build());
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder()
					.role(JsonValue.from(MessageType.ASSISTANT.getValue()));

				if (assistantMessage.getText() != null) {
					builder.content(ChatCompletionAssistantMessageParam.builder()
							.content(assistantMessage.getText())
							.build().content());
				}

				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					List<ChatCompletionMessageToolCall> toolCalls = assistantMessage.getToolCalls()
						.stream()
							.map(toolCall -> ChatCompletionMessageToolCall.ofFunction(
									ChatCompletionMessageFunctionToolCall.builder()
											.id(toolCall.id())
											.function(ChatCompletionMessageFunctionToolCall.Function.builder()
													.name(toolCall.name())
													.arguments(toolCall.arguments()).build())
													.build())
									)
						.toList();

					builder.toolCalls(toolCalls);
				}

				return ChatCompletionMessageParam.ofAssistant(builder.build());
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				ChatCompletionToolMessageParam.Builder builder = ChatCompletionToolMessageParam.builder();
				builder.content(toolMessage.getText() != null ? toolMessage.getText() : "");
				builder.role(JsonValue.from(MessageType.TOOL.getValue()));

				if (toolMessage.getResponses().isEmpty()) {
					return ChatCompletionMessageParam.ofTool(builder.build());
				}
				String callId = toolMessage.getResponses().get(0).id();
				String callResponse = toolMessage.getResponses().get(0).responseData();

				return ChatCompletionMessageParam.ofTool(builder
								.toolCallId(callId)
								.content(callResponse)
								.role(JsonValue.from(MessageType.TOOL.getValue()))
						.build());
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).toList();

		ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();

		chatCompletionMessageParams.forEach(builder::addMessage);

		OpenAiOfficialChatOptions requestOptions = (OpenAiOfficialChatOptions) prompt.getOptions();

		// Use deployment name if available (for Azure AI Foundry), otherwise use model
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
					.putAllAdditionalProperties(requestOptions.getLogitBias().entrySet().stream()
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
		if (requestOptions.getOutputAudio() != null) {
			builder.audio(requestOptions.getOutputAudio());
		}
		if (requestOptions.getPresencePenalty() != null) {
			builder.presencePenalty(requestOptions.getPresencePenalty());
		}
		if (requestOptions.getResponseFormat() != null) {
			builder.responseFormat(requestOptions.getResponseFormat());
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
		if (requestOptions.getStore() != null) {
			builder.store(requestOptions.getStore());
		}
		if (requestOptions.getMetadata() != null &&  !requestOptions.getMetadata().isEmpty()) {
			builder.metadata(ChatCompletionCreateParams.Metadata.builder()
					.putAllAdditionalProperties(requestOptions.getMetadata() .entrySet().stream()
							.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
					.build());
		}
		if (requestOptions.getServiceTier() != null) {
			builder.serviceTier(ChatCompletionCreateParams.ServiceTier.of(requestOptions.getServiceTier()));
		}

		if (stream) {
			if (requestOptions.getStreamOptions() != null) {
				ChatCompletionStreamOptions.Builder streamOptionsBuilder = ChatCompletionStreamOptions.builder();

				if (requestOptions.getStreamOptions().includeObfuscation().isPresent()) {
					streamOptionsBuilder.includeObfuscation(requestOptions.getStreamOptions().includeObfuscation().get());
				}
				streamOptionsBuilder.additionalProperties(requestOptions.getStreamOptions()._additionalProperties());
				streamOptionsBuilder.includeUsage(requestOptions.getStreamUsage());
				builder.streamOptions(streamOptionsBuilder.build());
			} else {
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
			builder.toolChoice(requestOptions.getToolChoice());
		}

		return builder.build();
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
		return toolDefinitions.stream()
				.map(toolDefinition -> {
					FunctionParameters.Builder parametersBuilder = FunctionParameters.builder();

					if (!toolDefinition.inputSchema().isEmpty()) {
						// Parse the schema and add its properties directly
						try {
							com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
							@SuppressWarnings("unchecked")
							Map<String, Object> schemaMap = mapper.readValue(toolDefinition.inputSchema(), Map.class);

							// Add each property from the schema to the parameters
							schemaMap.forEach((key, value) ->
									parametersBuilder.putAdditionalProperty(key, JsonValue.from(value))
							);

							// Add strict mode
							parametersBuilder.putAdditionalProperty("strict", JsonValue.from(true)); // TODO allow non-strict mode
						} catch (Exception e) {
							logger.error("Failed to parse tool schema", e);
						}
					}

					FunctionDefinition functionDefinition = FunctionDefinition.builder()
							.name(toolDefinition.name())
							.description(toolDefinition.description())
							.parameters(parametersBuilder.build())
							.build();

					return ChatCompletionTool.ofFunction(
							ChatCompletionFunctionTool.builder().function(functionDefinition).build()
					);
				})
				.toList();
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

}
