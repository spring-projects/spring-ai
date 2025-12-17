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

package org.springframework.ai.minimax;

import java.util.ArrayList;
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
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletion;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletion.Choice;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionChunk;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionFinishReason;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.Role;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionRequest;
import org.springframework.ai.minimax.api.MiniMaxApiConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal MiniMax}
 * backed by {@link MiniMaxApi}.
 *
 * @author Geng Rong
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @see ChatModel
 * @see StreamingChatModel
 * @see MiniMaxApi
 * @since 1.0.0 M1
 */
public class MiniMaxChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(MiniMaxChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The retry template used to retry the MiniMax API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MiniMaxChatOptions defaultOptions;

	/**
	 * Low-level access to the MiniMax API.
	 */
	private final MiniMaxApi miniMaxApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * The tool calling manager.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates an instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @throws IllegalArgumentException if MiniMaxApi is null
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi) {
		this(miniMaxApi, MiniMaxChatOptions.builder().model(MiniMaxApi.DEFAULT_CHAT_MODEL).temperature(0.7).build());
	}

	/**
	 * Initializes an instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options) {
		this(miniMaxApi, options, ToolCallingManager.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 * @param toolCallingManager The tool calling manager.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options, ToolCallingManager toolCallingManager) {
		this(miniMaxApi, options, toolCallingManager, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 * @param toolCallingManager The tool calling manager.
	 * @param retryTemplate The retry template.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate) {
		this(miniMaxApi, options, toolCallingManager, retryTemplate, ObservationRegistry.NOOP,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Initializes a new instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 * @param toolExecutionEligibilityPredicate The Tool
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(miniMaxApi, "MiniMaxApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.miniMaxApi = miniMaxApi;
		this.defaultOptions = options;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	private static Generation buildGeneration(Choice choice, Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() == null ? List.of()
				: choice.message()
					.toolCalls()
					.stream()
					// the MiniMax's stream function calls response are really odd
					// occasionally, tool call might get split.
					// for example, id empty means the previous tool call is not finished,
					// the toolCalls:
					// [{id:'1',function:{name:'a'}},{id:'',function:{arguments:'[1]'}}]
					// these need to be merged into [{id:'1', name:'a', arguments:'[1]'}]
					// it worked before, maybe the model provider made some adjustments
					.reduce(new ArrayList<>(), (acc, current) -> {
						if (!acc.isEmpty() && current.id().isEmpty()) {
							AssistantMessage.ToolCall prev = acc.get(acc.size() - 1);
							acc.set(acc.size() - 1, new AssistantMessage.ToolCall(prev.id(), prev.type(), prev.name(),
									current.function().arguments()));
						}
						else {
							AssistantMessage.ToolCall currentToolCall = new AssistantMessage.ToolCall(current.id(),
									current.type(), current.function().name(), current.function().arguments());
							acc.add(currentToolCall);
						}
						return acc;
					}, (acc1, acc2) -> {
						acc1.addAll(acc2);
						return acc1;
					});
		var assistantMessage = AssistantMessage.builder()
			.content(choice.message().content())
			.properties(metadata)
			.toolCalls(toolCalls)
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
			.provider(MiniMaxApiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
						() -> this.miniMaxApi.chatCompletionEntity(request));

				var chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", requestPrompt);
					return new ChatResponse(List.of());
				}

				List<Choice> choices = chatCompletion.choices();
				if (choices == null) {
					logger.warn("No choices returned for prompt: {}, because: {}}", requestPrompt,
							chatCompletion.baseResponse().message());
					return new ChatResponse(List.of());
				}

				List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
						// if the choice is a web search tool call, return last message of choice.messages
						ChatCompletionMessage message = null;
						if (choice.message() != null) {
							message = choice.message();
						}
						else if (!CollectionUtils.isEmpty(choice.messages())) {
							// the MiniMax web search messages result is ['user message','assistant tool call', 'tool call', 'assistant message']
							// so the last message is the assistant message
							message = choice.messages().get(choice.messages().size() - 1);
						}
						Map<String, Object> metadata = Map.of(
								"id", chatCompletion.id(),
								"role", message != null && message.role() != null ? message.role().name() : "",
								"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
						// @formatter:on
					return buildGeneration(message, choice.finishReason(), metadata);
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
	public MiniMaxChatOptions getDefaultOptions() {
		return MiniMaxChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return Flux.deferContextual(contextView -> {
			ChatCompletionRequest request = createRequest(requestPrompt, true);

			Flux<ChatCompletionChunk> completionChunks = RetryUtils.execute(this.retryTemplate,
					() -> this.miniMaxApi.chatCompletionStream(request));

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(requestPrompt)
				.provider(MiniMaxApiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion)
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
										"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
								return buildGeneration(choice, metadata);
							}).toList();
							return new ChatResponse(generations, from(chatCompletion2));
					}
					catch (Exception e) {
							logger.error("Error processing chat completion", e);
							return new ChatResponse(List.of());
						}
					}));

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
					.doFinally(signalType -> observation.stop())
					.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	private ChatResponseMetadata from(ChatCompletion result) {
		Assert.notNull(result, "MiniMax ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id() != null ? result.id() : "")
			.usage(result.usage() != null ? getDefaultUsage(result.usage()) : new EmptyUsage())
			.model(result.model() != null ? result.model() : "")
			.keyValue("created", result.created() != null ? result.created() : 0L)
			.keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "")
			.build();
	}

	private DefaultUsage getDefaultUsage(MiniMaxApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	private Generation buildGeneration(ChatCompletionMessage message, ChatCompletionFinishReason completionFinishReason,
			Map<String, Object> metadata) {
		if (message == null || message.role() == Role.TOOL) {
			return null;
		}
		List<AssistantMessage.ToolCall> toolCalls = message.toolCalls() == null ? List.of()
				: message.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), toolCall.type(),
							toolCall.function().name(), toolCall.function().arguments()))
					.toList();

		var assistantMessage = AssistantMessage.builder()
			.content(message.content())
			.properties(metadata)
			.toolCalls(toolCalls)
			.build();
		String finishReason = (completionFinishReason != null ? completionFinishReason.name() : "");
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(assistantMessage, generationMetadata);
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
			return new ChatCompletion.Choice(cc.finishReason(), cc.index(), delta, null, cc.logprobs());
		}).toList();

		return new ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.systemFingerprint(),
				"chat.completion", null, null);
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		MiniMaxChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						MiniMaxChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						MiniMaxChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		MiniMaxChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				MiniMaxChatOptions.class);

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
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses()
					.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatCompletionMessage(tr.responseData(), ChatCompletionMessage.Role.TOOL, tr.name(),
							tr.id(), null))
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);
		MiniMaxChatOptions requestOptions = (MiniMaxChatOptions) prompt.getOptions();
		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(
					MiniMaxChatOptions.builder().tools(this.getFunctionTools(toolDefinitions)).build(), request,
					ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			MiniMaxChatOptions updatedRuntimeOptions;

			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions,
						ToolCallingChatOptions.class, MiniMaxChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						MiniMaxChatOptions.class);
			}

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		return request;
	}

	private List<MiniMaxApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new MiniMaxApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new MiniMaxApi.FunctionTool(function);
		}).toList();
	}

	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
