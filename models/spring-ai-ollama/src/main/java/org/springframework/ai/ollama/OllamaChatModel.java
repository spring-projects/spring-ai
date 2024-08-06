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
package org.springframework.ai.ollama;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCallFunction;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.metadata.OllamaUsage;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * {@link ChatModel} implementation for {@literal Ollama}. Ollama allows developers to run
 * large language models and generate embeddings locally. It supports open-source models
 * available on [Ollama AI Library](<a href="https://ollama.ai/library">...</a>). - Llama
 * 2 (7B parameters, 3.8GB size) - Mistral (7B parameters, 4.1GB size) Please refer to the
 * <a href="https://ollama.ai/">official Ollama website</a> for the most up-to-date
 * information on available models.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class OllamaChatModel extends AbstractToolCallSupport implements ChatModel {

	/**
	 * Low-level Ollama API library.
	 */
	private final OllamaApi chatApi;

	/**
	 * Default options to be used for all chat requests.
	 */
	private OllamaOptions defaultOptions;

	public OllamaChatModel(OllamaApi chatApi) {
		this(chatApi, OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL));
	}

	public OllamaChatModel(OllamaApi chatApi, OllamaOptions defaultOptions) {
		this(chatApi, defaultOptions, null);
	}

	public OllamaChatModel(OllamaApi chatApi, OllamaOptions defaultOptions,
			FunctionCallbackContext functionCallbackContext) {
		this(chatApi, defaultOptions, functionCallbackContext, List.of());
	}

	public OllamaChatModel(OllamaApi chatApi, OllamaOptions defaultOptions,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks) {
		super(functionCallbackContext, defaultOptions, toolFunctionCallbacks);
		Assert.notNull(chatApi, "OllamaApi must not be null");
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		this.chatApi = chatApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		OllamaApi.ChatResponse response = this.chatApi.chat(ollamaChatRequest(prompt, false));

		List<AssistantMessage.ToolCall> toolCalls = response.message().toolCalls() == null ? List.of()
				: response.message()
					.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
							ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
					.toList();

		var assistantMessage = new AssistantMessage(response.message().content(), Map.of(), toolCalls);

		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
		if (response.promptEvalCount() != null && response.evalCount() != null) {
			generationMetadata = ChatGenerationMetadata.from(response.doneReason(), null);
		}

		var generator = new Generation(assistantMessage, generationMetadata);
		var chatResponse = new ChatResponse(List.of(generator), from(response));

		if (isToolCall(chatResponse, Set.of("stop"))) {
			var toolCallConversation = handleToolCalls(prompt, chatResponse);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	public static ChatResponseMetadata from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		return ChatResponseMetadata.builder()
			.withUsage(OllamaUsage.from(response))
			.withModel(response.model())
			.withKeyValue("created-at", response.createdAt())
			.withKeyValue("eval-duration", response.evalDuration())
			.withKeyValue("eval-count", response.evalCount())
			.withKeyValue("load-duration", response.loadDuration())
			.withKeyValue("eval-duration", response.promptEvalDuration())
			.withKeyValue("eval-count", response.promptEvalCount())
			.withKeyValue("total-duration", response.totalDuration())
			.withKeyValue("done", response.done())
			.build();
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		Flux<OllamaApi.ChatResponse> ollamaResponse = this.chatApi.streamingChat(ollamaChatRequest(prompt, true));

		Flux<ChatResponse> chatResponse = ollamaResponse.map(chunk -> {
			String content = (chunk.message() != null) ? chunk.message().content() : "";
			List<AssistantMessage.ToolCall> toolCalls = chunk.message().toolCalls() == null ? List.of()
					: chunk.message()
						.toolCalls()
						.stream()
						.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
								ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
						.toList();

			var assistantMessage = new AssistantMessage(content, Map.of(), toolCalls);

			ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
			if (chunk.promptEvalCount() != null && chunk.evalCount() != null) {
				generationMetadata = ChatGenerationMetadata.from(chunk.doneReason(), null);
			}

			var generator = new Generation(assistantMessage, generationMetadata);
			return new ChatResponse(List.of(generator), from(chunk));
		});

		return chatResponse.flatMap(response -> {
			if (isToolCall(response, Set.of("stop"))) {
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

	/**
	 * Package access for testing.
	 */
	OllamaApi.ChatRequest ollamaChatRequest(Prompt prompt, boolean stream) {

		List<OllamaApi.Message> ollamaMessages = prompt.getInstructions().stream().map(message -> {
			if (message instanceof UserMessage userMessage) {
				var messageBuilder = OllamaApi.Message.builder(Role.USER).withContent(message.getContent());
				if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
					messageBuilder.withImages(
							userMessage.getMedia().stream().map(media -> this.fromMediaData(media.getData())).toList());
				}
				return List.of(messageBuilder.build());
			}
			else if (message instanceof SystemMessage systemMessage) {
				return List.of(OllamaApi.Message.builder(Role.SYSTEM).withContent(systemMessage.getContent()).build());
			}
			else if (message instanceof AssistantMessage assistantMessage) {
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ToolCallFunction(toolCall.name(),
								ModelOptionsUtils.jsonToMap(toolCall.arguments()));
						return new ToolCall(function);
					}).toList();
				}
				return List.of(OllamaApi.Message.builder(Role.ASSISTANT)
					.withContent(assistantMessage.getContent())
					.withToolCalls(toolCalls)
					.build());
			}
			else if (message instanceof ToolResponseMessage toolMessage) {

				List<OllamaApi.Message> responseMessages = toolMessage.getResponses()
					.stream()
					.map(tr -> OllamaApi.Message.builder(Role.TOOL).withContent(tr.responseData()).build())
					.toList();

				return responseMessages;
			}
			throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}).flatMap(List::stream).toList();

		Set<String> functionsForThisRequest = new HashSet<>();

		// runtime options
		OllamaOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					OllamaOptions.class);
			functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(runtimeOptions));
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
		}
		OllamaOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OllamaOptions.class);

		// Override the model.
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}

		String model = mergedOptions.getModel();
		OllamaApi.ChatRequest.Builder requestBuilder = OllamaApi.ChatRequest.builder(model)
			.withStream(stream)
			.withMessages(ollamaMessages)
			.withOptions(mergedOptions);

		if (mergedOptions.getFormat() != null) {
			requestBuilder.withFormat(mergedOptions.getFormat());
		}

		if (mergedOptions.getKeepAlive() != null) {
			requestBuilder.withKeepAlive(mergedOptions.getKeepAlive());
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			requestBuilder.withTools(this.getFunctionTools(functionsForThisRequest));
		}

		return requestBuilder.build();
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

	private List<ChatRequest.Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new ChatRequest.Tool.Function(functionCallback.getName(), functionCallback.getDescription(),
					functionCallback.getInputTypeSchema());
			return new ChatRequest.Tool(function);
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OllamaOptions.fromOptions(this.defaultOptions);
	}

}