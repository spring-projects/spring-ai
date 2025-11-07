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

package org.springframework.ai.zhipuai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
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
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletion;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletion.Choice;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionChunk;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.MediaContent;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;
import org.springframework.ai.zhipuai.api.ZhiPuApiConstants;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal ZhiPuAI}
 * backed by {@link ZhiPuAiApi}.
 *
 * @author Geng Rong
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @see ChatModel
 * @see StreamingChatModel
 * @see ZhiPuAiApi
 * @since 1.0.0 M1
 */
public class ZhiPuAiChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(ZhiPuAiChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The retry template used to retry the ZhiPuAI API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final ZhiPuAiChatOptions defaultOptions;

	/**
	 * Low-level access to the ZhiPuAI API.
	 */
	private final ZhiPuAiApi zhiPuAiApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * The Tool calling manager.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Creates an instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @throws IllegalArgumentException if zhiPuAiApi is null
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi) {
		this(zhiPuAiApi, ZhiPuAiChatOptions.builder().model(ZhiPuAiApi.DEFAULT_CHAT_MODEL).temperature(0.7).build());
	}

	/**
	 * Initializes an instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @param options The ZhiPuAiChatOptions to configure the chat model.
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi, ZhiPuAiChatOptions options) {
		this(zhiPuAiApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes an instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @param options The ZhiPuAiChatOptions to configure the chat model.
	 * @param retryTemplate The retry template.
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi, ZhiPuAiChatOptions options, RetryTemplate retryTemplate) {
		this(zhiPuAiApi, options, ToolCallingManager.builder().build(), retryTemplate, ObservationRegistry.NOOP,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Initializes an instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @param options The ZhiPuAiChatOptions to configure the chat model.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The Observation Registry.
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi, ZhiPuAiChatOptions options, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		this(zhiPuAiApi, options, ToolCallingManager.builder().build(), retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Initializes an instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @param toolCallingManager The tool calling manager
	 * @param options The ZhiPuAiChatOptions to configure the chat model.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The Observation Registry.
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi, ZhiPuAiChatOptions options, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		this(zhiPuAiApi, options, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Initializes a new instance of the ZhiPuAiChatModel.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to be used for interacting with the
	 * ZhiPuAI Chat API.
	 * @param options The ZhiPuAiChatOptions to configure the chat model.
	 * @param toolCallingManager The tool calling manager
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 * @param toolExecutionEligibilityPredicate The Tool execution eligibility predicate.
	 */
	public ZhiPuAiChatModel(ZhiPuAiApi zhiPuAiApi, ZhiPuAiChatOptions options, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(zhiPuAiApi, "ZhiPuAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.zhiPuAiApi = zhiPuAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	private static Generation buildGeneration(Choice choice, Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() == null ? List.of()
				: choice.message()
					.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function",
							toolCall.function().name(), toolCall.function().arguments()))
					.toList();

		String textContent = choice.message().content();
		String reasoningContent = choice.message().reasoningContent();

		ZhiPuAiAssistantMessage.Builder builder = new ZhiPuAiAssistantMessage.Builder();
		var assistantMessage = builder.content(textContent)
			.reasoningContent(reasoningContent)
			.properties(metadata)
			.toolCalls(toolCalls)
			.media(List.of())
			.build();

		String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(assistantMessage, generationMetadata);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatCompletionRequest request = createRequest(requestPrompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(ZhiPuApiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = null;
				try {
					completionEntity = this.retryTemplate.execute(() -> this.zhiPuAiApi.chatCompletionEntity(request));
				}
				catch (RetryException e) {
					if (e.getCause() instanceof RuntimeException r) {
						throw r;
					}
					else {
						throw new RuntimeException(e.getCause());
					}
				}

				var chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<Choice> choices = chatCompletion.choices();

				List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
					Map<String, Object> metadata = Map.of(
						"id", chatCompletion.id(),
						"role", choice.message().role() != null ? choice.message().role().name() : "",
						"finishReason", choice.finishReason() != null ? choice.finishReason().name() : ""
					);
					// @formatter:on
					return buildGeneration(choice, metadata);
				}).toList();

				ChatResponse chatResponse = new ChatResponse(generations, from(completionEntity.getBody()));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});
		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(requestPrompt.getOptions(), response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(requestPrompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.call(new Prompt(toolExecutionResult.conversationHistory(), requestPrompt.getOptions()));
			}
		}
		return response;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return ZhiPuAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			// Before moving any further, build the final request Prompt,
			// merging runtime and default options.
			Prompt requestPrompt = buildRequestPrompt(prompt);
			ChatCompletionRequest request = createRequest(requestPrompt, true);

			Flux<ChatCompletionChunk> completionChunks = null;
			try {
				completionChunks = this.retryTemplate.execute(() -> this.zhiPuAiApi.chatCompletionStream(request));
			}
			catch (RetryException e) {
				if (e.getCause() instanceof RuntimeException r) {
					throw r;
				}
				else {
					throw new RuntimeException(e.getCause());
				}
			}

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(requestPrompt)
				.provider(ZhiPuApiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion)
				.switchMap(chatCompletion -> Mono.just(chatCompletion).map(chatCompletion2 -> {
					try {
						String id = chatCompletion2.id();

				// @formatter:off
						List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
							if (choice.message().role() != null) {
								roleMap.putIfAbsent(id, choice.message().role().name());
							}
							Map<String, Object> metadata = Map.of(
								"id", chatCompletion2.id(),
								"role", roleMap.getOrDefault(id, ""),
								"finishReason", choice.finishReason() != null ? choice.finishReason().name() : ""
							);
							return buildGeneration(choice, metadata);
						}).toList();
						// @formatter:on

						return new ChatResponse(generations, from(chatCompletion2));
					}
					catch (Exception e) {
						logger.error("Error processing chat completion", e);
						return new ChatResponse(List.of());
					}

				}));

			// @formatter:off
			Flux<ChatResponse> flux = chatResponse.flatMap(response -> {
						if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(requestPrompt.getOptions(), response)) {
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
									return this.stream(new Prompt(toolExecutionResult.conversationHistory(), requestPrompt.getOptions()));
								}
							}).subscribeOn(Schedulers.boundedElastic());
						}
						return Flux.just(response);
			})
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	private ChatResponseMetadata from(ChatCompletion result) {
		Assert.notNull(result, "ZhiPuAI ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id() != null ? result.id() : "")
			.usage(result.usage() != null ? getDefaultUsage(result.usage()) : new EmptyUsage())
			.model(result.model() != null ? result.model() : "")
			.keyValue("created", result.created() != null ? result.created() : 0L)
			.keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "")
			.build();
	}

	private DefaultUsage getDefaultUsage(ZhiPuAiApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
		List<ChatCompletion.Choice> choices = chunk.choices().stream().map(cc -> {
			ChatCompletionMessage delta = cc.delta();
			if (delta == null) {
				delta = new ChatCompletionMessage("", Role.ASSISTANT);
			}
			return new ChatCompletion.Choice(cc.finishReason(), cc.index(), delta, cc.logprobs());
		}).toList();

		return new ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.systemFingerprint(),
				"chat.completion", null);
	}

	private List<ZhiPuAiApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new ZhiPuAiApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new ZhiPuAiApi.FunctionTool(function);
		}).toList();
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		ZhiPuAiChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						ZhiPuAiChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						ZhiPuAiChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		ZhiPuAiChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				ZhiPuAiChatOptions.class);

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

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				Object content = message.getText();
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<MediaContent> contentList = new ArrayList<>(List.of(new MediaContent(message.getText())));

						contentList.addAll(userMessage.getMedia()
							.stream()
							.map(media -> new MediaContent(new MediaContent.ImageUrl(
									this.fromMediaData(media.getMimeType(), media.getData()))))
							.toList());

						content = contentList;
					}
				}

				return List.of(new ChatCompletionMessage(content,
						ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}
				return List.of(new ChatCompletionMessage(assistantMessage.getText(),
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls, null));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses()
					.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatCompletionMessage(tr.responseData(), ChatCompletionMessage.Role.TOOL, tr.name(),
							tr.id(), null, null))
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		if (prompt.getOptions() != null) {
			ZhiPuAiChatOptions updatedRuntimeOptions;
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions,
						ToolCallingChatOptions.class, ZhiPuAiChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						ZhiPuAiChatOptions.class);
			}

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		ZhiPuAiChatOptions requestOptions = (ZhiPuAiChatOptions) prompt.getOptions();

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(
					ZhiPuAiChatOptions.builder().tools(this.getFunctionTools(toolDefinitions)).build(), request,
					ChatCompletionRequest.class);
		}
		return request;
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

	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
