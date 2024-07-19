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
package org.springframework.ai.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.MediaContent;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.metadata.OpenAiUsage;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
	 * Creates an instance of the OpenAiChatModel.
	 * @param openAiApi The OpenAiApi instance to be used for interacting with the OpenAI
	 * Chat API.
	 * @throws IllegalArgumentException if openAiApi is null
	 */
	public OpenAiChatModel(OpenAiApi openAiApi) {
		this(openAiApi,
				OpenAiChatOptions.builder().withModel(OpenAiApi.DEFAULT_CHAT_MODEL).withTemperature(0.7f).build());
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
		super(functionCallbackContext);
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.openAiApi = openAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, false);

		ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
			.execute(ctx -> this.openAiApi.chatCompletionEntity(request));

		var chatCompletion = completionEntity.getBody();

		if (chatCompletion == null) {
			logger.warn("No chat completion returned for prompt: {}", prompt);
			return new ChatResponse(List.of());
		}

		List<Choice> choices = chatCompletion.choices();
		if (choices == null) {
			logger.warn("No choices returned for prompt: {}", prompt);
			return new ChatResponse(List.of());
		}

		List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
			Map<String, Object> metadata = Map.of(
					"id", chatCompletion.id(),
					"role", choice.message().role() != null ? choice.message().role().name() : "",
					"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		// Non function calling.
		RateLimit rateLimit = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

		ChatResponse chatResponse = new ChatResponse(generations, from(completionEntity.getBody(), rateLimit));

		if (isToolCall(chatResponse, OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name())) {
			var toolCallConversation = handleToolCalls(prompt, chatResponse);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	public static ChatResponseMetadata from(OpenAiApi.ChatCompletion result, RateLimit rateLimit) {
		Assert.notNull(result, "OpenAI ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(OpenAiUsage.from(result.usage()))
			.withModel(result.model())
			.withRateLimit(rateLimit)
			.withKeyValue("created", result.created())
			.withKeyValue("system-fingerprint", result.systemFingerprint())
			.build();
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, true);

		Flux<OpenAiApi.ChatCompletionChunk> completionChunks = this.retryTemplate
			.execute(ctx -> this.openAiApi.chatCompletionStream(request));

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
						}).toList();
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
			if (isToolCall(response, OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name())) {
				var toolCallConversation = handleToolCalls(prompt, response);
				// Recursively call the stream method with the tool call message
				// conversation that contains the call responses.
				return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
			}
			else {
				return Flux.just(response);
			}
		});
	}

	private ChatResponseMetadata from(OpenAiApi.ChatCompletion result) {
		Assert.notNull(result, "OpenAI ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(OpenAiUsage.from(result.usage()))
			.withModel(result.model())
			.withKeyValue("created", result.created())
			.withKeyValue("system-fingerprint", result.systemFingerprint())
			.build();
	}

	private static Generation buildGeneration(Choice choice, Map<String, Object> metadata) {
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
		var generation = new Generation(assistantMessage, generationMetadata);

		return generation;
	}

	private List<Message> handleToolCalls(Prompt prompt, ChatResponse response) {
		AssistantMessage assistantMessage = response.getResult().getOutput();
		ToolResponseMessage toolMessageResponse = this.executeFuncitons(assistantMessage);
		return this.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);
	}

	private List<Message> buildToolCallConversation(List<Message> previousMessages, AssistantMessage assistantMessage,
			ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
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

		Set<String> functionsForThisRequest = new HashSet<>();

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				Object content = message.getContent();
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<MediaContent> contentList = new ArrayList<>(
								List.of(new MediaContent(message.getContent())));

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
				return List.of(new ChatCompletionMessage(assistantMessage.getContent(),
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
					Assert.isTrue(response.name() != null, "ToolResponseMessage must have a name");
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

		if (prompt.getOptions() != null) {
			OpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, OpenAiChatOptions.class);

			Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
					IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(promptEnabledFunctions);

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (this.defaultOptions != null) {

			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);

			functionsForThisRequest.addAll(defaultEnabledFunctions);

			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			request = ModelOptionsUtils.merge(
					OpenAiChatOptions.builder().withTools(this.getFunctionTools(functionsForThisRequest)).build(),
					request, ChatCompletionRequest.class);
		}

		// Remove `streamOptions` from the request if it is not a streaming request
		if (request.streamOptions() != null && !stream) {
			logger.warn("Removing streamOptions from the request as it is not a streaming request!");
			request = request.withStreamOptions(null);
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

	private List<OpenAiApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			return new OpenAiApi.FunctionTool(function);
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OpenAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public String toString() {
		return "OpenAiChatModel [defaultOptions=" + defaultOptions + "]";
	}

}
