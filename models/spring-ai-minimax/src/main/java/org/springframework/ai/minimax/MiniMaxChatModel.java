/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.minimax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
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
import org.springframework.ai.minimax.api.MiniMaxApi.FunctionTool;
import org.springframework.ai.minimax.metadata.MiniMaxUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.ai.minimax.api.MiniMaxApiConstants.TOOL_CALL_FUNCTION_TYPE;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal MiniMax}
 * backed by {@link MiniMaxApi}.
 *
 * @author Geng Rong
 * @see ChatModel
 * @see StreamingChatModel
 * @see MiniMaxApi
 * @since 1.0.0 M1
 */
public class MiniMaxChatModel extends AbstractToolCallSupport implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(MiniMaxChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MiniMaxChatOptions defaultOptions;

	/**
	 * The retry template used to retry the MiniMax API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the MiniMax API.
	 */
	private final MiniMaxApi miniMaxApi;

	/**
	 * Creates an instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @throws IllegalArgumentException if MiniMaxApi is null
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi) {
		this(miniMaxApi,
				MiniMaxChatOptions.builder().withModel(MiniMaxApi.DEFAULT_CHAT_MODEL).withTemperature(0.7).build());
	}

	/**
	 * Initializes an instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options) {
		this(miniMaxApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 * @param functionCallbackContext The function callback context.
	 * @param retryTemplate The retry template.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		this(miniMaxApi, options, functionCallbackContext, List.of(), retryTemplate);
	}

	/**
	 * Initializes a new instance of the MiniMaxChatModel.
	 * @param miniMaxApi The MiniMaxApi instance to be used for interacting with the
	 * MiniMax Chat API.
	 * @param options The MiniMaxChatOptions to configure the chat model.
	 * @param functionCallbackContext The function callback context.
	 * @param toolFunctionCallbacks The tool function callbacks.
	 * @param retryTemplate The retry template.
	 */
	public MiniMaxChatModel(MiniMaxApi miniMaxApi, MiniMaxChatOptions options,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		super(functionCallbackContext, options, toolFunctionCallbacks);
		Assert.notNull(miniMaxApi, "MiniMaxApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.isTrue(CollectionUtils.isEmpty(options.getFunctionCallbacks()),
				"The default function callbacks must be set via the toolFunctionCallbacks constructor parameter");
		this.miniMaxApi = miniMaxApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
			.execute(ctx -> this.miniMaxApi.chatCompletionEntity(request));

		var chatCompletion = completionEntity.getBody();

		if (chatCompletion == null) {
			logger.warn("No chat completion returned for prompt: {}", prompt);
			return new ChatResponse(List.of());
		}

		List<Choice> choices = chatCompletion.choices();
		if (choices == null) {
			logger.warn("No choices returned for prompt: {}, because: {}}", prompt,
					chatCompletion.baseResponse().message());
			return new ChatResponse(List.of());
		}

		List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
			// if the choice is a web search tool call, return last message of choice.messages
			ChatCompletionMessage message = null;
			if(choice.message() != null) {
				message = choice.message();
			} else if(!CollectionUtils.isEmpty(choice.messages())){
				// the MiniMax web search messages result is ['user message','assistant tool call', 'tool call', 'assistant message']
				// so the last message is the assistant message
				message = choice.messages().get(choice.messages().size() - 1);
			}
			Map<String, Object> metadata = Map.of(
					"id", chatCompletion.id(),
					"role", message != null && message.role() != null ? message.role().name() : "",
					"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		ChatResponse chatResponse = new ChatResponse(generations, from(completionEntity.getBody()));

		if (!isProxyToolCalls(prompt, this.defaultOptions) && isToolCall(chatResponse,
				Set.of(ChatCompletionFinishReason.TOOL_CALLS.name(), ChatCompletionFinishReason.STOP.name()))) {
			var toolCallConversation = handleToolCalls(prompt, chatResponse);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return MiniMaxChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, true);

		Flux<ChatCompletionChunk> completionChunks = this.retryTemplate
			.execute(ctx -> this.miniMaxApi.chatCompletionStream(request));

		// For chunked responses, only the first chunk contains the choice role.
		// The rest of the chunks with same ID share the same role.
		ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

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
						}).filter(Objects::nonNull).toList();
						// @formatter:on

					if (chatCompletion2.usage() != null) {
						return new ChatResponse(generations, from(chatCompletion2));
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

		return chatResponse.flatMap(response -> {

			if (!isProxyToolCalls(prompt, this.defaultOptions) && isToolCall(response,
					Set.of(ChatCompletionFinishReason.TOOL_CALLS.name(), ChatCompletionFinishReason.STOP.name()))) {
				var toolCallConversation = handleToolCalls(prompt, response);
				// Recursively call the stream method with the tool call message
				// conversation that contains the call responses.
				return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
			}
			return Flux.just(response);
		});
	}

	/**
	 * The MimiMax web search function tool type is 'web_search', so we need to filter out
	 * the tool calls whose type is not 'function'
	 * @param generation the generation to check
	 * @param toolCallFinishReasons the tool call finish reasons
	 * @return true if the generation is a tool call
	 */
	@Override
	protected boolean isToolCall(Generation generation, Set<String> toolCallFinishReasons) {
		if (!super.isToolCall(generation, toolCallFinishReasons)) {
			return false;
		}
		return generation.getOutput()
			.getToolCalls()
			.stream()
			.anyMatch(toolCall -> TOOL_CALL_FUNCTION_TYPE.equals(toolCall.type()));
	}

	private ChatResponseMetadata from(ChatCompletion result) {
		Assert.notNull(result, "MiniMax ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(MiniMaxUsage.from(result.usage()))
			.withModel(result.model())
			.withKeyValue("created", result.created())
			.build();
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
		var assistantMessage = new AssistantMessage(choice.message().content(), metadata, toolCalls);
		String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.from(finishReason, null);
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

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				Object content = message.getContent();
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
				return List.of(new ChatCompletionMessage(assistantMessage.getContent(),
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
				});

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

		Set<String> enabledToolsToUse = new HashSet<>();

		if (prompt.getOptions() != null) {
			MiniMaxChatOptions updatedRuntimeOptions;

			if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions,
						FunctionCallingOptions.class, MiniMaxChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						MiniMaxChatOptions.class);
			}

			enabledToolsToUse.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			enabledToolsToUse.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		if (!CollectionUtils.isEmpty(enabledToolsToUse)) {

			request = ModelOptionsUtils.merge(
					MiniMaxChatOptions.builder().withTools(this.getFunctionTools(enabledToolsToUse)).build(), request,
					ChatCompletionRequest.class);
		}

		return request;
	}

	private List<FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new FunctionTool.Function(functionCallback.getDescription(), functionCallback.getName(),
					functionCallback.getInputTypeSchema());
			return new FunctionTool(function);
		}).toList();
	}

}
