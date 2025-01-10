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

package org.springframework.ai.mistralai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
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
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion.Choice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.metadata.MistralAiUsage;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * Represents a Mistral AI Chat Model.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Thomas Vitale
 * @author luocongqiu
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class MistralAiChatModel extends AbstractToolCallSupport implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MistralAiChatOptions defaultOptions;

	/**
	 * Low-level access to the OpenAI API.
	 */
	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public MistralAiChatModel(MistralAiApi mistralAiApi) {
		this(mistralAiApi,
				MistralAiChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.safePrompt(false)
					.model(MistralAiApi.ChatModel.OPEN_MISTRAL_7B.getValue())
					.build());
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options) {
		this(mistralAiApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, RetryTemplate retryTemplate) {
		this(mistralAiApi, options, functionCallbackResolver, List.of(), retryTemplate);
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		this(mistralAiApi, options, functionCallbackResolver, toolFunctionCallbacks, retryTemplate,
				ObservationRegistry.NOOP);
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		super(functionCallbackResolver, options, toolFunctionCallbacks);
		Assert.notNull(mistralAiApi, "mistralAiApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.mistralAiApi = mistralAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	public static ChatResponseMetadata from(MistralAiApi.ChatCompletion result) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		MistralAiUsage usage = MistralAiUsage.from(result.usage());
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("created", result.created())
			.build();
	}

	public static ChatResponseMetadata from(MistralAiApi.ChatCompletion result, Usage usage) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("created", result.created())
			.build();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return this.internalCall(prompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		MistralAiApi.ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(MistralAiApi.PROVIDER_NAME)
			.requestOptions(buildRequestOptions(request))
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
					.execute(ctx -> this.mistralAiApi.chatCompletionEntity(request));

				ChatCompletion chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
			// @formatter:off
					Map<String, Object> metadata = Map.of(
							"id", chatCompletion.id() != null ? chatCompletion.id() : "",
							"index", choice.index(),
							"role", choice.message().role() != null ? choice.message().role().name() : "",
							"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
					// @formatter:on
					return buildGeneration(choice, metadata);
				}).toList();

				MistralAiUsage usage = MistralAiUsage.from(completionEntity.getBody().usage());
				Usage cumulativeUsage = UsageUtils.getCumulativeUsage(usage, previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations,
						from(completionEntity.getBody(), cumulativeUsage));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		if (!isProxyToolCalls(prompt, this.defaultOptions) && response != null
				&& isToolCall(response, Set.of(MistralAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
						MistralAiApi.ChatCompletionFinishReason.STOP.name()))) {
			var toolCallConversation = handleToolCalls(prompt, response);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.internalCall(new Prompt(toolCallConversation, prompt.getOptions()), response);
		}

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.internalStream(prompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			var request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(MistralAiApi.PROVIDER_NAME)
				.requestOptions(buildRequestOptions(request))
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatCompletionChunk> completionChunks = this.retryTemplate
				.execute(ctx -> this.mistralAiApi.chatCompletionStream(request));

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::toChatCompletion)
				.switchMap(chatCompletion -> Mono.just(chatCompletion).map(chatCompletion2 -> {
					try {
						@SuppressWarnings("null")
						String id = chatCompletion2.id();

				// @formatter:off
							List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
								if (choice.message().role() != null) {
									roleMap.putIfAbsent(id, choice.message().role().name());
								}
								Map<String, Object> metadata = Map.of(
										"id", chatCompletion2.id(),
										"role", roleMap.getOrDefault(id, ""),
										"index", choice.index(),
										"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
								return buildGeneration(choice, metadata);
							}).toList();
							// @formatter:on

						if (chatCompletion2.usage() != null) {
							MistralAiUsage usage = MistralAiUsage.from(chatCompletion2.usage());
							Usage cumulativeUsage = UsageUtils.getCumulativeUsage(usage, previousChatResponse);
							return new ChatResponse(generations, from(chatCompletion2, cumulativeUsage));
						}
						else {
							return new ChatResponse(generations);
						}
					}
					catch (Exception e) {
						logger.error("Error processing chat completion", e);
						return new ChatResponse(List.of());
					}
				}));

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = chatResponse.flatMap(response -> {
				if (!isProxyToolCalls(prompt, this.defaultOptions) && isToolCall(response, Set.of(MistralAiApi.ChatCompletionFinishReason.TOOL_CALLS.name()))) {
					var toolCallConversation = handleToolCalls(prompt, response);
					// Recursively call the stream method with the tool call message
					// conversation that contains the call responses.
					return this.internalStream(new Prompt(toolCallConversation, prompt.getOptions()), response);
				}
				else {
					return Flux.just(response);
				}
			})
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on;

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});

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
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(assistantMessage, generationMetadata);
	}

	private ChatCompletion toChatCompletion(ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(cc -> new Choice(cc.index(), cc.delta(), cc.finishReason(), cc.logprobs()))
			.toList();

		return new ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.model(), choices,
				chunk.usage());
	}

	/**
	 * Accessible for testing.
	 */
	MistralAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message instanceof UserMessage userMessage) {
				Object content = message.getText();

				if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
					List<ChatCompletionMessage.MediaContent> contentList = new ArrayList<>(
							List.of(new ChatCompletionMessage.MediaContent(message.getText())));

					contentList.addAll(userMessage.getMedia().stream().map(this::mapToMediaContent).toList());

					content = contentList;
				}

				return List
					.of(new MistralAiApi.ChatCompletionMessage(content, MistralAiApi.ChatCompletionMessage.Role.USER));
			}
			else if (message instanceof SystemMessage systemMessage) {
				return List.of(new MistralAiApi.ChatCompletionMessage(systemMessage.getText(),
						MistralAiApi.ChatCompletionMessage.Role.SYSTEM));
			}
			else if (message instanceof AssistantMessage assistantMessage) {
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}

				return List.of(new MistralAiApi.ChatCompletionMessage(assistantMessage.getText(),
						MistralAiApi.ChatCompletionMessage.Role.ASSISTANT, null, toolCalls, null));
			}
			else if (message instanceof ToolResponseMessage toolResponseMessage) {

				toolResponseMessage.getResponses()
					.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

				return toolResponseMessage.getResponses()
					.stream()
					.map(toolResponse -> new MistralAiApi.ChatCompletionMessage(toolResponse.responseData(),
							MistralAiApi.ChatCompletionMessage.Role.TOOL, toolResponse.name(), null, toolResponse.id()))
					.toList();
			}
			else {
				throw new IllegalStateException("Unexpected message type: " + message);
			}
		}).flatMap(List::stream).toList();

		var request = new MistralAiApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, MistralAiApi.ChatCompletionRequest.class);

		if (prompt.getOptions() != null) {
			MistralAiChatOptions updatedRuntimeOptions;

			if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions,
						FunctionCallingOptions.class, MistralAiChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						MistralAiChatOptions.class);
			}

			functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, MistralAiApi.ChatCompletionRequest.class);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			request = ModelOptionsUtils.merge(
					MistralAiChatOptions.builder().tools(this.getFunctionTools(functionsForThisRequest)).build(),
					request, ChatCompletionRequest.class);
		}

		return request;
	}

	private ChatCompletionMessage.MediaContent mapToMediaContent(Media media) {
		return new ChatCompletionMessage.MediaContent(new ChatCompletionMessage.MediaContent.ImageUrl(
				this.fromMediaData(media.getMimeType(), media.getData())));
	}

	private String fromMediaData(MimeType mimeType, Object mediaContentData) {
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

	private List<MistralAiApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new MistralAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			return new MistralAiApi.FunctionTool(function);
		}).toList();
	}

	private ChatOptions buildRequestOptions(MistralAiApi.ChatCompletionRequest request) {
		return ChatOptions.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			.stopSequences(request.stop())
			.temperature(request.temperature())
			.topP(request.topP())
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return MistralAiChatOptions.fromOptions(this.defaultOptions);
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
