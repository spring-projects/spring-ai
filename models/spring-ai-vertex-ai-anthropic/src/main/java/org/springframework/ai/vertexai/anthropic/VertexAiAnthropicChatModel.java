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
package org.springframework.ai.vertexai.anthropic;

import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.ai.vertexai.anthropic.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the VertexAI Anthropic chat
 * generative.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public class VertexAiAnthropicChatModel extends AbstractToolCallSupport implements ChatModel, StreamingChatModel {

	private final VertexAiAnthropicApi anthropicApi;

	private final VertexAiAnthropicChatOptions defaultOptions;

	public final RetryTemplate retryTemplate;

	public static final String DEFAULT_ANTHROPIC_VERSION = "vertex-2023-10-16";

	public enum ChatModel implements ChatModelDescription {

		CLAUDE_3_5_SONNET("claude-3-5-sonnet@20240620"),

		CLAUDE_3_OPUS("claude-3-opus@20240229"), CLAUDE_3_SONNET("claude-3-sonnet@20240229"),
		CLAUDE_3_HAIKU("claude-3-haiku@20240307");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	public VertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi) {
		this(anthropicApi,
				VertexAiAnthropicChatOptions.builder()
					.withTemperature(0.8f)
					.withMaxTokens(500)
					.withTopK(10)
					.withAnthropicVersion(DEFAULT_ANTHROPIC_VERSION)
					.withModel(ChatModel.CLAUDE_3_5_SONNET.getValue())
					.build());
	}

	public VertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi, VertexAiAnthropicChatOptions options) {
		this(anthropicApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public VertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi, VertexAiAnthropicChatOptions options,
			RetryTemplate retryTemplate) {
		this(anthropicApi, options, retryTemplate, null);
	}

	public VertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi, VertexAiAnthropicChatOptions options,
			RetryTemplate retryTemplate, FunctionCallbackContext functionCallbackContext) {
		this(anthropicApi, options, retryTemplate, functionCallbackContext, List.of());
	}

	public VertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi, VertexAiAnthropicChatOptions options,
			RetryTemplate retryTemplate, FunctionCallbackContext functionCallbackContext,
			List<FunctionCallback> toolFunctionCallbacks) {

		super(functionCallbackContext, options, toolFunctionCallbacks);

		Assert.notNull(anthropicApi, "VertexAiAnthropicApi must not be null");
		Assert.notNull(options, "VertexAiAnthropicChatOptions must not be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	/**
	 * Calls the VertexAI Anthropic chat model with the given prompt.
	 * @param prompt the Prompt object containing the instructions and options for the
	 * chat model
	 * @return the ChatResponse from the chat model
	 * @throws IllegalArgumentException if the prompt is null or the prompt instructions
	 * are empty
	 */
	@Override
	public ChatResponse call(Prompt prompt) {
		Assert.notNull(prompt, "Prompt cannot be null");
		Assert.notEmpty(prompt.getInstructions(), "Prompt instructions cannot be empty");

		ChatCompletionRequest request = createRequest(prompt, false);

		ResponseEntity<ChatCompletionResponse> completionEntity = this.retryTemplate
			.execute(ctx -> this.anthropicApi.chatCompletion(request, defaultOptions.getModel()));

		ChatResponse chatResponse = toChatResponse(completionEntity.getBody());

		if (this.isToolCall(chatResponse, Set.of("tool_use"))) {
			List<Message> toolCallConversation = handleToolCalls(prompt, chatResponse);
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	/**
	 * Streams the chat responses from the VertexAI Anthropic chat model with the given
	 * prompt.
	 * @param prompt the Prompt object containing the instructions and options for the
	 * chat model
	 * @return a Flux of ChatResponse from the chat model
	 * @throws IllegalArgumentException if the prompt is null or the prompt instructions
	 * are empty
	 */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Assert.notNull(prompt, "Prompt cannot be null");
		Assert.notEmpty(prompt.getInstructions(), "Prompt instructions cannot be empty");

		ChatCompletionRequest request = createRequest(prompt, true);

		Flux<ChatCompletionResponse> response = this.retryTemplate
			.execute(ctx -> this.anthropicApi.chatCompletionStream(request, defaultOptions.getModel()));

		return response.switchMap(chatCompletionResponse -> {

			ChatResponse chatResponse = toChatResponse(chatCompletionResponse);

			if (this.isToolCall(chatResponse, Set.of("tool_use"))) {
				List<Message> toolCallConversation = handleToolCalls(prompt, chatResponse);
				return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
			}

			return Mono.just(chatResponse);
		});
	}

	/**
	 * Creates a ChatCompletionRequest based on the given prompt and stream flag.
	 * @param prompt the Prompt object containing the instructions and options for the
	 * chat model
	 * @param stream a boolean flag indicating whether the request is for streaming
	 * responses
	 * @return the constructed ChatCompletionRequest
	 * @throws IllegalArgumentException if an unsupported message type is encountered
	 */
	private ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

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
							contentBlocks.add(new ContentBlock(ContentBlock.Type.TOOL_USE, toolCall.id(),
									toolCall.name(), ModelOptionsUtils.jsonToMap(toolCall.arguments())));
						}
					}
					return new AnthropicMessage(contentBlocks, Role.ASSISTANT);
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
						.stream()
						.map(toolResponse -> new ContentBlock(ContentBlock.Type.TOOL_RESULT, toolResponse.id(),
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

		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getAnthropicVersion(),
				userMessages, systemPrompt, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(),
				stream);

		if (prompt.getOptions() != null) {
			VertexAiAnthropicChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, VertexAiAnthropicChatOptions.class);

			functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			List<Tool> tools = getFunctionTools(functionsForThisRequest);

			request = ChatCompletionRequest.from(request).withTools(tools).build();
		}

		return request;
	}

	/**
	 * Converts media data to a string representation.
	 * @param mediaData the media data to convert, which can be either a byte array or a
	 * string
	 * @return the string representation of the media data
	 * @throws IllegalArgumentException if the media data type is unsupported
	 */
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

	/**
	 * Retrieves a list of tools based on the provided function names.
	 * @param functionNames a set of function names to resolve into tools
	 * @return a list of Tool objects corresponding to the provided function names
	 */
	private List<Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			String description = functionCallback.getDescription();
			String name = functionCallback.getName();
			String inputSchema = functionCallback.getInputTypeSchema();
			return new Tool(name, description, ModelOptionsUtils.jsonToMap(inputSchema));
		}).toList();
	}

	/**
	 * Converts a ChatCompletionResponse into a ChatResponse.
	 * @param chatCompletion the ChatCompletionResponse object containing the response
	 * data from the chat model
	 * @return the ChatResponse object containing the processed response data
	 */
	private ChatResponse toChatResponse(ChatCompletionResponse chatCompletion) {

		if (chatCompletion == null) {
			return new ChatResponse(List.of());
		}

		List<Generation> generations = chatCompletion.content()
			.stream()
			.filter(content -> content.type() != ContentBlock.Type.TOOL_USE)
			.map(content -> {
				return new Generation(new AssistantMessage(content.text(), Map.of()),
						ChatGenerationMetadata.from(chatCompletion.stopReason(), null));
			})
			.toList();

		List<Generation> allGenerations = new ArrayList<>(generations);

		List<ContentBlock> toolToUseList = chatCompletion.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.Type.TOOL_USE)
			.toList();

		if (!CollectionUtils.isEmpty(toolToUseList)) {
			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

			for (ContentBlock toolToUse : toolToUseList) {

				String functionCallId = toolToUse.id();
				String functionName = toolToUse.name();
				String functionArguments = ModelOptionsUtils.toJsonString(toolToUse.input());

				toolCalls
					.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
			}

			AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.from(chatCompletion.stopReason(), null));
			allGenerations.add(toolCallGeneration);
		}

		return new ChatResponse(allGenerations, this.from(chatCompletion));
	}

	/**
	 * Converts a ChatCompletionResponse into a ChatResponseMetadata.
	 * @param result the ChatCompletionResponse object containing the response data from
	 * the chat model
	 * @return the ChatResponseMetadata object containing the processed metadata
	 * @throws IllegalArgumentException if the result is null
	 */
	private ChatResponseMetadata from(ChatCompletionResponse result) {
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

	@Override
	public ChatOptions getDefaultOptions() {
		return VertexAiAnthropicChatOptions.fromOptions(this.defaultOptions);
	}

}
