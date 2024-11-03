/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.support.ChatModelObservationSupport;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.MediaContent;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.openai.metadata.OpenAiUsage;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.util.ValueUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal OpenAI}
 * backed by {@link OpenAiApi}.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Ueibin Kim
 * @author John Blum
 * @author Josh Long
 * @author Jemin Huh
 * @author Grogdunn
 * @author Hyunjoon Choi
 * @author Mariusz Bernacki
 * @author luocongqiu
 * @author Thomas Vitale
 * @see ChatModel
 * @see StreamingChatModel
 * @see OpenAiApi
 */
public class OpenAiChatModel extends AbstractToolCallSupport implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final EnumSet<MessageType> USER_SYSTEM_MESSAGE_TYPE_SET = EnumSet.of(MessageType.USER,
			MessageType.SYSTEM);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final OpenAiChatOptions defaultOptions;

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the OpenAI API.
	 */
	private final OpenAiApi openAiApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates an instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @throws IllegalArgumentException if openAiApi is null
	 */
	public OpenAiChatModel(OpenAiApi openAiApi) {
		this(openAiApi,
				OpenAiChatOptions.builder().withModel(OpenAiApi.DEFAULT_CHAT_MODEL).withTemperature(0.7).build());
	}

	/**
	 * Initializes an instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @param options The OpenAiChatOptions to configure the chat model.
	 */
	public OpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions options) {
		this(openAiApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @param options The OpenAiChatOptions to configure the chat model.
	 * @param functionCallbackContext The function callback context.
	 * @param retryTemplate The retry template.
	 */
	public OpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		this(openAiApi, options, functionCallbackContext, List.of(), retryTemplate);
	}

	/**
	 * Initializes a new instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @param options The OpenAiChatOptions to configure the chat model.
	 * @param functionCallbackContext The function callback context.
	 * @param toolFunctionCallbacks The tool function callbacks.
	 * @param retryTemplate The retry template.
	 */
	public OpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		this(openAiApi, options, functionCallbackContext, toolFunctionCallbacks, retryTemplate,
				ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @param options The OpenAiChatOptions to configure the chat model.
	 * @param functionCallbackContext The function callback context.
	 * @param toolFunctionCallbacks The tool function callbacks.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 */
	public OpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {

		super(functionCallbackContext, options, toolFunctionCallbacks);

		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.isTrue(CollectionUtils.isEmpty(options.getFunctionCallbacks()),
				"The default function callbacks must be set via the toolFunctionCallbacks constructor parameter");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");

		this.openAiApi = openAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, false);

		Supplier<ChatModelObservationContext> observationContext = () -> ChatModelObservationContext.builder()
			.requestOptions(buildRequestOptions(request))
			.provider(OpenAiApiConstants.PROVIDER_NAME)
			.prompt(prompt)
			.build();

		Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
				this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, observationContext,
				this.observationRegistry);

		ChatResponse response = observation.observe(() -> {

			ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
				.execute(context -> this.openAiApi.chatCompletionEntity(request, getAdditionalHttpHeaders(prompt)));

			var chatCompletion = completionEntity.getBody();

			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(Collections.emptyList());
			}

			List<Choice> choices = chatCompletion.choices();

			if (choices == null) {
				logger.warn("No choices returned for prompt: {}", prompt);
				return new ChatResponse(Collections.emptyList());
			}

			// @formatter:off
			List<Generation> generations = choices.stream().map(choice -> {
				Map<String, Object> metadata = choiceMetadata(choice, chatCompletion.id(),
					ValueUtils.defaultToEmptyString(choice.message().role(), OpenAiApi.ChatCompletionMessage.Role::name));

				return buildGeneration(choice, metadata);
			}).toList();
			// @formatter:on

			// Non function calling.
			RateLimit rateLimit = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			ChatResponse chatResponse = new ChatResponse(generations, from(completionEntity.getBody(), rateLimit));

			ChatModelObservationSupport.getObservationContext(observation)
				.ifPresent(context -> context.setResponse(chatResponse));

			return chatResponse;

		});

		if (!isProxyToolCalls(prompt, this.defaultOptions)
				&& isToolCall(response, Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
						OpenAiApi.ChatCompletionFinishReason.STOP.name()))) {
			var toolCallConversation = handleToolCalls(prompt, response);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		return Flux.deferContextual(contextView -> {

			ChatCompletionRequest request = createRequest(prompt, true);

			Flux<OpenAiApi.ChatCompletionChunk> completionChunks = this.openAiApi.chatCompletionStream(request,
					getAdditionalHttpHeaders(prompt));

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentMap<String, String> roleMap = new ConcurrentHashMap<>();

			Supplier<ChatModelObservationContext> observationContext = () -> ChatModelObservationContext.builder()
				.requestOptions(buildRequestOptions(request))
				.provider(OpenAiApiConstants.PROVIDER_NAME)
				.prompt(prompt)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion)
				.switchMap(chatCompletion -> Mono.just(chatCompletion).map(resolvedChatCompletion -> {
					try {
						@SuppressWarnings("null")
						String id = resolvedChatCompletion.id();

						List<Generation> generations = resolvedChatCompletion.choices().stream().map(choice -> {
							if (choice.message().role() != null) {
								roleMap.putIfAbsent(id, choice.message().role().name());
							}

							Map<String, Object> metadata = choiceMetadata(choice, id, roleMap.getOrDefault(id, ""));

							return buildGeneration(choice, metadata);
						}).toList();

						return new ChatResponse(generations, from(resolvedChatCompletion, null));
					}
					catch (Exception e) {
						logger.error("Error processing chat completion", e);
						return new ChatResponse(List.of());
					}

				}));

			// @formatter:off
			Flux<ChatResponse> flux = chatResponse
				.flatMap(response -> {
					if (!isProxyToolCalls(prompt, this.defaultOptions)
							&& isToolCall(response, Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
									OpenAiApi.ChatCompletionFinishReason.STOP.name()))) {
						var toolCallConversation = handleToolCalls(prompt, response);
						// Recursively call the stream method with the tool call message
						// conversation that contains the call responses.
						return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
					}
					else {
						return Flux.just(response);
					}
				})
				.doOnError(observation::error)
				.doFinally(signalType -> observation.stop())
				.contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(flux,
					ChatModelObservationSupport.setChatResponseInObservationContext(observation));
		});
	}

	private Map<String, Object> choiceMetadata(Choice choice, String id, String roleName) {

		// @formatter:off
		return Map.of(
			"id", ValueUtils.defaultToEmptyString(id),
			"role", roleName,
			"index", choice.index(),
			"finishReason", ValueUtils.defaultToEmptyString(choice.finishReason(),
				OpenAiApi.ChatCompletionFinishReason::name),
			"refusal", ValueUtils.defaultToEmptyString(choice.message().refusal())
		);
		// @formatter:on
	}

	private MultiValueMap<String, String> getAdditionalHttpHeaders(Prompt prompt) {

		Map<String, String> headers = new HashMap<>(this.defaultOptions.getHttpHeaders());
		if (prompt.getOptions() != null && prompt.getOptions() instanceof OpenAiChatOptions chatOptions) {
			headers.putAll(chatOptions.getHttpHeaders());
		}
		return CollectionUtils.toMultiValueMap(
				headers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))));
	}

	private Generation buildGeneration(Choice choice, Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() == null ? List.of()
				: choice.message()
					.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function",
							toolCall.function().name(), toolCall.function().arguments()))
					.toList();

		var assistantMessage = new AssistantMessage(choice.message().content(), metadata, toolCalls);
		String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.from(finishReason, null);
		return new Generation(assistantMessage, generationMetadata);
	}

	private ChatResponseMetadata from(OpenAiApi.ChatCompletion result, RateLimit rateLimit) {
		Assert.notNull(result, "OpenAI ChatCompletionResult must not be null");
		var builder = ChatResponseMetadata.builder()
			.withId(result.id() != null ? result.id() : "")
			.withUsage(result.usage() != null ? OpenAiUsage.from(result.usage()) : new EmptyUsage())
			.withModel(result.model() != null ? result.model() : "")
			.withKeyValue("created", result.created() != null ? result.created() : 0L)
			.withKeyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "");
		if (rateLimit != null) {
			builder.withRateLimit(rateLimit);
		}
		return builder.build();
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private OpenAiApi.ChatCompletion chunkToChatCompletion(OpenAiApi.ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(chunkChoice -> new Choice(chunkChoice.finishReason(), chunkChoice.index(), chunkChoice.delta(),
					chunkChoice.logprobs()))
			.toList();

		return new OpenAiApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(),
				chunk.systemFingerprint(), "chat.completion", chunk.usage());
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			MessageType messageType = message.getMessageType();
			if (USER_SYSTEM_MESSAGE_TYPE_SET.contains(messageType)) {
				Object content = message.getContent();
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<MediaContent> contentList = new ArrayList<>(
								List.of(new MediaContent(message.getContent())));

						contentList.addAll(userMessage.getMedia()
							.stream()
							.map(OpenAiApi.MediaConverter.INSTANCE::convert)
							.toList());

						content = contentList;
					}
				}

				return List.of(new ChatCompletionMessage(content,
						ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
			}
			else if (MessageType.ASSISTANT.equals(messageType)) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}
				return List.of(ChatCompletionMessage.builder()
					.rawContent(assistantMessage.getContent())
					.role(ChatCompletionMessage.Role.ASSISTANT)
					.toolCalls(toolCalls)
					.build());
			}
			else if (MessageType.TOOL.equals(messageType)) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses()
					.forEach(response -> Assert.notNull(response.id(), "ToolResponseMessage must have an id"));

				return toolMessage.getResponses()
					.stream()
					.map(toolResponse -> ChatCompletionMessage.builder()
						.rawContent(toolResponse.responseData())
						.role(ChatCompletionMessage.Role.TOOL)
						.name(toolResponse.name())
						.toolCallId(toolResponse.id())
						.build())
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		Set<String> enabledToolsToUse = new HashSet<>();

		if (prompt.getOptions() != null) {
			OpenAiChatOptions updatedRuntimeOptions = null;

			if (prompt.getOptions() instanceof FunctionCallingOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(((FunctionCallingOptions) prompt.getOptions()),
						FunctionCallingOptions.class, OpenAiChatOptions.class);
			}
			else if (prompt.getOptions() instanceof OpenAiChatOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						OpenAiChatOptions.class);
			}

			enabledToolsToUse.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			enabledToolsToUse.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(enabledToolsToUse)) {
			request = ModelOptionsUtils.merge(
					OpenAiChatOptions.builder().withTools(this.getFunctionTools(enabledToolsToUse)).build(), request,
					ChatCompletionRequest.class);
		}

		// Remove `streamOptions` from the request if it is not a streaming request
		if (request.streamOptions() != null && !stream) {
			logger.warn("Removing streamOptions from the request as it is not a streaming request!");
			request = request.withStreamOptions(null);
		}

		return request;
	}

	private List<OpenAiApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			return new OpenAiApi.FunctionTool(function);
		}).toList();
	}

	private ChatOptions buildRequestOptions(OpenAiApi.ChatCompletionRequest request) {
		return ChatOptionsBuilder.builder()
			.withModel(request.model())
			.withFrequencyPenalty(request.frequencyPenalty())
			.withMaxTokens(request.maxTokens())
			.withPresencePenalty(request.presencePenalty())
			.withStopSequences(request.stop())
			.withTemperature(request.temperature())
			.withTopP(request.topP())
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OpenAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public String toString() {
		return "OpenAiChatModel [defaultOptions=" + this.defaultOptions + "]";
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
