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
package org.springframework.ai.anthropic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.ContentBlockType;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.metadata.AnthropicUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link ChatModel} implementation for the Anthropic service.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Mariusz Bernacki
 * @since 1.0.0
 */
public class AnthropicChatModel extends AbstractToolCallSupport implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatModel.class);

	public static final String DEFAULT_MODEL_NAME = AnthropicApi.ChatModel.CLAUDE_3_5_SONNET.getValue();

	public static final Integer DEFAULT_MAX_TOKENS = 500;

	public static final Float DEFAULT_TEMPERATURE = 0.8f;

	/**
	 * The lower-level API for the Anthropic service.
	 */
	public final AnthropicApi anthropicApi;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final AnthropicChatOptions defaultOptions;

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi) {
		this(anthropicApi,
				AnthropicChatOptions.builder()
					.withModel(DEFAULT_MODEL_NAME)
					.withMaxTokens(DEFAULT_MAX_TOKENS)
					.withTemperature(DEFAULT_TEMPERATURE)
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
	 * @param functionCallbackContext the function callback context used to store the
	 * state of the function calls.
	 */
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackContext functionCallbackContext) {

		super(functionCallbackContext);

		Assert.notNull(anthropicApi, "AnthropicApi must not be null");
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, false);

		return this.retryTemplate.execute(ctx -> {
			ResponseEntity<ChatCompletionResponse> completionEntity = this.anthropicApi.chatCompletionEntity(request);

			if (this.isToolFunctionCall(completionEntity.getBody())) {
				List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
						completionEntity.getBody());
				return this.call(new Prompt(toolCallMessageConversation, prompt.getOptions()));
			}

			return toChatResponse(completionEntity.getBody());
		});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, true);

		return this.retryTemplate.execute(ctx -> {

			Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(request);

			return response.switchMap(chatCompletionResponse -> {

				if (this.isToolFunctionCall(chatCompletionResponse)) {
					List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
							chatCompletionResponse);
					return this.stream(new Prompt(toolCallMessageConversation, prompt.getOptions()));
				}

				return Mono.just(chatCompletionResponse).map(this::toChatResponse);
			});
		});
	}

	private List<Message> handleToolCallRequests(List<Message> previousMessages,
			ChatCompletionResponse chatCompletionResponse) {

		AnthropicMessage anthropicAssistantMessage = new AnthropicMessage(chatCompletionResponse.content(),
				Role.ASSISTANT);

		List<ContentBlock> toolToUseList = anthropicAssistantMessage.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.ContentBlockType.TOOL_USE)
			.toList();

		List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

		for (ContentBlock toolToUse : toolToUseList) {

			var functionCallId = toolToUse.id();
			var functionName = toolToUse.name();
			var functionArguments = ModelOptionsUtils.toJsonString(toolToUse.input());

			toolCalls.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
		}

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
		ToolResponseMessage toolResponseMessage = this.executeFunctions(assistantMessage);

		// History
		List<Message> toolCallMessageConversation = new ArrayList<>(previousMessages);
		toolCallMessageConversation.add(assistantMessage);
		toolCallMessageConversation.add(toolResponseMessage);

		return toolCallMessageConversation;
	}

	private ChatResponse toChatResponse(ChatCompletionResponse chatCompletion) {
		if (chatCompletion == null) {
			logger.warn("Null chat completion returned");
			return new ChatResponse(List.of());
		}

		List<Generation> generations = chatCompletion.content().stream().map(content -> {
			return new Generation(content.text(), Map.of())
				.withGenerationMetadata(ChatGenerationMetadata.from(chatCompletion.stopReason(), null));
		}).toList();

		return new ChatResponse(generations, from(chatCompletion));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result) {
		Assert.notNull(result, "Anthropic ChatCompletionResult must not be null");
		AnthropicUsage usage = AnthropicUsage.from(result.usage());
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withModel(result.model())
			.withUsage(usage)
			.withKeyValue("stop-reason", result.stopReason())
			.withKeyValue("stop-sequence", result.stopSequence())
			.withKeyValue("type", result.type())
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

	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<AnthropicMessage> userMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.map(message -> {
				if (message.getMessageType() == MessageType.USER) {
					List<ContentBlock> contents = new ArrayList<>(List.of(new ContentBlock(message.getContent())));
					if (message instanceof UserMessage userMessage) {
						if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
							List<ContentBlock> mediaContent = userMessage.getMedia()
								.stream()
								.map(media -> new ContentBlock(media.getMimeType().toString(),
										this.fromMediaData(media.getData())))
								.toList();
							contents.addAll(mediaContent);
						}
					}
					return new AnthropicMessage(contents, Role.valueOf(message.getMessageType().name()));
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					AssistantMessage assistantMessage = (AssistantMessage) message;
					List<ContentBlock> contentBlocks = new ArrayList<>();
					if (StringUtils.hasText(message.getContent())) {
						contentBlocks.add(new ContentBlock(message.getContent()));
					}
					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
							contentBlocks.add(new ContentBlock(ContentBlockType.TOOL_USE, toolCall.id(),
									toolCall.name(), ModelOptionsUtils.jsonToMap(toolCall.arguments())));
						}
					}
					return new AnthropicMessage(contentBlocks, Role.ASSISTANT);
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
						.stream()
						.map(toolResponse -> new ContentBlock(ContentBlockType.TOOL_RESULT, toolResponse.id(),
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
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getModel(), userMessages,
				systemPrompt, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(), stream);

		if (prompt.getOptions() != null) {
			AnthropicChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, AnthropicChatOptions.class);

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

	@SuppressWarnings("null")
	protected boolean isToolFunctionCall(ChatCompletionResponse response) {
		if (response == null || CollectionUtils.isEmpty(response.content())) {
			return false;
		}
		return response.content()
			.stream()
			.anyMatch(content -> content.type() == ContentBlock.ContentBlockType.TOOL_USE);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
	}

}
