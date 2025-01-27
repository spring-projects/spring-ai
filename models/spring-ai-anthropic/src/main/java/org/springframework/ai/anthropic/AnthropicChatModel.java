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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Source;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.UsageUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
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
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * The {@link ChatModel} implementation for the Anthropic service.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 * @author Claudio Silva Junior
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class AnthropicChatModel extends AbstractToolCallSupport implements ChatModel {

	public static final String DEFAULT_MODEL_NAME = AnthropicApi.ChatModel.CLAUDE_3_5_SONNET.getValue();

	public static final Integer DEFAULT_MAX_TOKENS = 500;

	public static final Double DEFAULT_TEMPERATURE = 0.8;

	private static final LogAccessor logger = new LogAccessor(AnthropicChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The lower-level API for the Anthropic service.
	 */
	private final AnthropicApi anthropicApi;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final AnthropicChatOptions defaultOptions;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi) {
		this(anthropicApi,
				AnthropicChatOptions.builder()
					.model(DEFAULT_MODEL_NAME)
					.maxTokens(DEFAULT_MAX_TOKENS)
					.temperature(DEFAULT_TEMPERATURE)
					.build());
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions) {
		this(anthropicApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate) {
		this(anthropicApi, defaultOptions, retryTemplate, null);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackResolver functionCallbackResolver) {

		this(anthropicApi, defaultOptions, retryTemplate, functionCallbackResolver, List.of());
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 * @param toolFunctionCallbacks the tool function callbacks used to handle the tool
	 * calls.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackResolver functionCallbackResolver,
			List<FunctionCallback> toolFunctionCallbacks) {
		this(anthropicApi, defaultOptions, retryTemplate, functionCallbackResolver, toolFunctionCallbacks,
				ObservationRegistry.NOOP);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 * @param toolFunctionCallbacks the tool function callbacks used to handle the tool
	 * calls.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackResolver functionCallbackResolver,
			List<FunctionCallback> toolFunctionCallbacks, ObservationRegistry observationRegistry) {

		super(functionCallbackResolver, defaultOptions, toolFunctionCallbacks);

		Assert.notNull(anthropicApi, "AnthropicApi must not be null");
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return this.internalCall(prompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AnthropicApi.PROVIDER_NAME)
			.requestOptions(buildRequestOptions(request))
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletionResponse> completionEntity = this.retryTemplate
					.execute(ctx -> this.anthropicApi.chatCompletionEntity(request));

				AnthropicApi.ChatCompletionResponse completionResponse = completionEntity.getBody();
				AnthropicApi.Usage usage = completionResponse.usage();

				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(completionResponse.usage())
						: new EmptyUsage();
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);

				ChatResponse chatResponse = toChatResponse(completionEntity.getBody(), accumulatedUsage);
				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		if (!isProxyToolCalls(prompt, this.defaultOptions) && response != null
				&& this.isToolCall(response, Set.of("tool_use"))) {
			var toolCallConversation = handleToolCalls(prompt, response);
			return this.internalCall(new Prompt(toolCallConversation, prompt.getOptions()), response);
		}

		return response;
	}

	private DefaultUsage getDefaultUsage(AnthropicApi.Usage usage) {
		return new DefaultUsage(usage.inputTokens(), usage.outputTokens(), usage.inputTokens() + usage.outputTokens(),
				usage);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.internalStream(prompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AnthropicApi.PROVIDER_NAME)
				.requestOptions(buildRequestOptions(request))
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(request);

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = response.switchMap(chatCompletionResponse -> {
				AnthropicApi.Usage usage = chatCompletionResponse.usage();
				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(chatCompletionResponse.usage()) : new EmptyUsage();
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);
				ChatResponse chatResponse = toChatResponse(chatCompletionResponse, accumulatedUsage);

				if (!isProxyToolCalls(prompt, this.defaultOptions) && this.isToolCall(chatResponse, Set.of("tool_use"))) {
					var toolCallConversation = handleToolCalls(prompt, chatResponse);
					return this.internalStream(new Prompt(toolCallConversation, prompt.getOptions()), chatResponse);
				}

				return Mono.just(chatResponse);
			})
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	private ChatResponse toChatResponse(ChatCompletionResponse chatCompletion, Usage usage) {

		if (chatCompletion == null) {
			logger.warn("Null chat completion returned");
			return new ChatResponse(List.of());
		}

		List<Generation> generations = chatCompletion.content()
			.stream()
			.filter(content -> content.type() != ContentBlock.Type.TOOL_USE)
			.map(content -> new Generation(new AssistantMessage(content.text(), Map.of()),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()))
			.toList();

		List<Generation> allGenerations = new ArrayList<>(generations);

		if (chatCompletion.stopReason() != null && generations.isEmpty()) {
			Generation generation = new Generation(new AssistantMessage(null, Map.of()),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			allGenerations.add(generation);
		}

		List<ContentBlock> toolToUseList = chatCompletion.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.Type.TOOL_USE)
			.toList();

		if (!CollectionUtils.isEmpty(toolToUseList)) {
			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

			for (ContentBlock toolToUse : toolToUseList) {

				var functionCallId = toolToUse.id();
				var functionName = toolToUse.name();
				var functionArguments = ModelOptionsUtils.toJsonString(toolToUse.input());

				toolCalls
					.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
			}

			AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			allGenerations.add(toolCallGeneration);
		}

		return new ChatResponse(allGenerations, this.from(chatCompletion, usage));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result) {
		return from(result, this.getDefaultUsage(result.usage()));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result, Usage usage) {
		Assert.notNull(result, "Anthropic ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("stop-reason", result.stopReason())
			.keyValue("stop-sequence", result.stopSequence())
			.keyValue("type", result.type())
			.build();
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

	private Type getContentBlockTypeByMedia(Media media) {
		String mimeType = media.getMimeType().toString();
		if (mimeType.startsWith("image")) {
			return Type.IMAGE;
		}
		else if (mimeType.contains("pdf")) {
			return Type.DOCUMENT;
		}
		throw new IllegalArgumentException("Unsupported media type: " + mimeType
				+ ". Supported types are: images (image/*) and PDF documents (application/pdf)");
	}

	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<AnthropicMessage> userMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.map(message -> {
				if (message.getMessageType() == MessageType.USER) {
					List<ContentBlock> contents = new ArrayList<>(List.of(new ContentBlock(message.getText())));
					if (message instanceof UserMessage userMessage) {
						if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
							List<ContentBlock> mediaContent = userMessage.getMedia().stream().map(media -> {
								Type contentBlockType = getContentBlockTypeByMedia(media);
								var source = new Source(media.getMimeType().toString(),
										this.fromMediaData(media.getData()));
								return new ContentBlock(contentBlockType, source);
							}).toList();
							contents.addAll(mediaContent);
						}
					}
					return new AnthropicMessage(contents, Role.valueOf(message.getMessageType().name()));
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					AssistantMessage assistantMessage = (AssistantMessage) message;
					List<ContentBlock> contentBlocks = new ArrayList<>();
					if (StringUtils.hasText(message.getText())) {
						contentBlocks.add(new ContentBlock(message.getText()));
					}
					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
							contentBlocks.add(new ContentBlock(Type.TOOL_USE, toolCall.id(), toolCall.name(),
									ModelOptionsUtils.jsonToMap(toolCall.arguments())));
						}
					}
					return new AnthropicMessage(contentBlocks, Role.ASSISTANT);
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
						.stream()
						.map(toolResponse -> new ContentBlock(Type.TOOL_RESULT, toolResponse.id(),
								toolResponse.responseData()))
						.toList();
					return new AnthropicMessage(toolResponses, Role.USER);
				}
				else {
					throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
				}
			})
			.toList();

		String systemPrompt = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(m -> m.getText())
			.collect(Collectors.joining(System.lineSeparator()));

		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getModel(), userMessages,
				systemPrompt, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(), stream);

		if (prompt.getOptions() != null) {
			AnthropicChatOptions updatedRuntimeOptions;
			if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions,
						FunctionCallingOptions.class, AnthropicChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						AnthropicChatOptions.class);
			}

			functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			List<AnthropicApi.Tool> tools = getFunctionTools(functionsForThisRequest);

			request = ChatCompletionRequest.from(request).withTools(tools).build();
		}

		return request;
	}

	private List<AnthropicApi.Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var description = functionCallback.getDescription();
			var name = functionCallback.getName();
			String inputSchema = functionCallback.getInputTypeSchema();
			return new AnthropicApi.Tool(name, description, ModelOptionsUtils.jsonToMap(inputSchema));
		}).toList();
	}

	private ChatOptions buildRequestOptions(AnthropicApi.ChatCompletionRequest request) {
		return ChatOptions.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			.stopSequences(request.stopSequences())
			.temperature(request.temperature())
			.topK(request.topK())
			.topP(request.topP())
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
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
