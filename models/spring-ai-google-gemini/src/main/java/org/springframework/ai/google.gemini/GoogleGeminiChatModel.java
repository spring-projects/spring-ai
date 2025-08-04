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
package org.springframework.ai.google.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletion;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletionRequest;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.google.gemini.metadata.GoogleGeminiUsage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * @author Geng Rong
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoogleGeminiChatModel implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGeminiChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final GoogleGeminiChatOptions defaultOptions;

	/**
	 * The retry template used to retry the Google Gemini API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the Google Gemini API.
	 */
	private final GoogleGeminiApi api;

	/**
	 * Tool calling manager for function/tool call support.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * Predicate to determine if tool execution is required.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Creates an instance of the GoogleGeminiChatModel.
	 * @param api The GoogleGeminiApi instance to be used for interacting with the Google
	 * Gemini Chat API.
	 * @throws IllegalArgumentException if api is null
	 */
	public GoogleGeminiChatModel(GoogleGeminiApi api) {
		this(api, GoogleGeminiChatOptions.builder().withTemperature(1D).build());
	}

	public GoogleGeminiChatModel(GoogleGeminiApi api, GoogleGeminiChatOptions options) {
		this(api, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGeminiChatModel(GoogleGeminiApi api, GoogleGeminiChatOptions options, RetryTemplate retryTemplate) {
		this(api, options, ToolCallingManager.builder().build(), retryTemplate,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public GoogleGeminiChatModel(GoogleGeminiApi api, GoogleGeminiChatOptions options,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(api, "GoogleGeminiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(toolCallingManager, "ToolCallingManager must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "ToolExecutionEligibilityPredicate must not be null");
		this.api = api;
		this.defaultOptions = options;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	private final ObjectMapper jacksonObjectMapper = new ObjectMapper();

	// unfortunately, functions responses' response should be an object with fields.
	// in case we expect a primitive or a list, we cannot really name it, so let us choose
	// name automatically
	// https://ai.google.dev/api/caching#FunctionResponse
	private Object wrapInMapIfPrimitive(String value) {
		if (value == null) {
			return Map.of();
		}
		String trimmed = value.trim();
		try {
			// If it's a JSON object, return as-is
			if (trimmed.startsWith("{")) {
				return jacksonObjectMapper.readValue(trimmed, Object.class);
			}
			// If it's a JSON array, wrap in a map
			if (trimmed.startsWith("[")) {
				Object array = jacksonObjectMapper.readValue(trimmed, Object.class);
				return Map.of("value", array);
			}
			// Try to parse as a primitive (number, boolean, or null)
			Object primitive = jacksonObjectMapper.readValue(trimmed, Object.class);
			if (primitive instanceof String || primitive instanceof Number || primitive instanceof Boolean
					|| primitive == null) {
				return primitive != null ? Map.of("value", primitive) : Map.of();
			}
			return primitive;
		}
		catch (JsonProcessingException e) {
			// Fallback: treat as plain string
			return Map.of("value", value);
		}
	}

	private Object readJsonValue(String value) {
		try {
			return jacksonObjectMapper.readValue(value, Object.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private AssistantMessage createAssistantMessageFromCandidate(GoogleGeminiApi.Candidate choice) {
		String message = null;
		List<AssistantMessage.ToolCall> functionCalls = Collections.emptyList();
		if (choice != null && choice.content() != null && choice.content().parts() != null
				&& !choice.content().parts().isEmpty()) {
			message = choice.content().parts().get(0).text();

			functionCalls = choice.content()
				.parts()
				.stream()
				.map(GoogleGeminiApi.Part::functionCall)
				.filter(Objects::nonNull)
				.map(functionCall -> {
					try {
						return new AssistantMessage.ToolCall(functionCall.id(), "function_call", functionCall.name(),
								jacksonObjectMapper.writeValueAsString(functionCall.args()));
					}
					catch (JsonProcessingException e) {
						throw new RuntimeException(e);
					}
				})
				.toList();
		}

		return new AssistantMessage(message != null ? message : "", "", Collections.emptyMap(), functionCalls);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return internalCall(prompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatCompletionRequest request = createRequest(requestPrompt);

		ChatResponse response = this.retryTemplate.execute(ctx -> {
			ResponseEntity<ChatCompletion> completionEntity = this.doChatCompletion(request);
			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", requestPrompt);
				return new ChatResponse(List.of());
			}
			List<Generation> generations = chatCompletion.choices()
				.stream()
				.map(choice -> new Generation(createAssistantMessageFromCandidate(choice)))
				.toList();
			return new ChatResponse(generations, from(completionEntity.getBody()));
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
				return this.internalCall(
						new Prompt(toolExecutionResult.conversationHistory(), requestPrompt.getOptions()), response);
			}
		}
		return response;
	}

	private ChatResponseMetadata from(GoogleGeminiApi.ChatCompletion result) {
		Assert.notNull(result, "Google Gemini ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.usage(result.usage() == null ? new EmptyUsage() : GoogleGeminiUsage.from(result.usage()))
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return GoogleGeminiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return internalStream(prompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatCompletionRequest request = createRequest(requestPrompt);

		return Flux.deferContextual(contextView -> {
			var completionChunks = this.api.chatCompletionStream(request);
			return completionChunks.concatMap(chatCompletion -> {
				List<Generation> generations = chatCompletion.choices()
					.stream()
					.map(choice -> new Generation(createAssistantMessageFromCandidate(choice)))
					.toList();
				ChatResponse response = new ChatResponse(generations, from(chatCompletion));
				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(requestPrompt.getOptions(),
						response)) {
					return Flux.defer(() -> {
						var toolExecutionResult = this.toolCallingManager.executeToolCalls(requestPrompt, response);
						if (toolExecutionResult.returnDirect()) {
							return Flux.just(ChatResponse.builder()
								.from(response)
								.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
								.build());
						}
						else {
							return this.internalStream(
									new Prompt(toolExecutionResult.conversationHistory(), requestPrompt.getOptions()),
									response);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}
				else {
					return Flux.just(response);
				}
			});
		});
	}

	protected ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.api.chatCompletionEntity(request);
	}

	/**
	 * Accessible for testing.
	 */
	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		GoogleGeminiChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						GoogleGeminiChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						GoogleGeminiChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		GoogleGeminiChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				GoogleGeminiChatOptions.class);

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

	ChatCompletionRequest createRequest(Prompt prompt) {
		var requestOptions = (GoogleGeminiChatOptions) prompt.getOptions();
		// Add tool definitions if present
		List<ToolDefinition> toolDefinitions = this.toolCallingManager != null
				? this.toolCallingManager.resolveToolDefinitions(requestOptions) : List.of();

		ChatCompletionRequest request;
		if (!toolDefinitions.isEmpty()) {
			List<GoogleGeminiApi.FunctionDeclaration> functionDeclarations = buildFunctionDeclarations(toolDefinitions);
			List<GoogleGeminiApi.ChatCompletionMessage> chatCompletionMessages = buildChatCompletionMessages(prompt);
			request = new ChatCompletionRequest(chatCompletionMessages,
					GoogleGeminiApi.ChatCompletionMessage.getSystemInstruction(prompt),
					GoogleGeminiApi.GenerationConfig.of(requestOptions),
					List.of(new GoogleGeminiApi.Tool(functionDeclarations)));
		}
		else {
			request = new ChatCompletionRequest(prompt, requestOptions);
		}
		return request;
	}

	/**
	 * Build function declarations from tool definitions.
	 */
	private List<GoogleGeminiApi.FunctionDeclaration> buildFunctionDeclarations(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream()
			.map(td -> new GoogleGeminiApi.FunctionDeclaration(td.name(), td.description(),
					org.springframework.ai.model.ModelOptionsUtils.jsonToMap(td.inputSchema())))
			.toList();
	}

	/**
	 * Convert prompt instructions to ChatCompletionMessages, skipping SYSTEM messages.
	 */
	private List<GoogleGeminiApi.ChatCompletionMessage> buildChatCompletionMessages(Prompt prompt) {
		return prompt.getInstructions().stream().filter(i -> i.getMessageType() != MessageType.SYSTEM).map(msg -> {
			if (msg instanceof AssistantMessage assistantMessage) {
				return buildAssistantMessage(assistantMessage);
			}
			else if (msg instanceof UserMessage userMessage) {
				return buildUserMessage(userMessage);
			}
			else if (msg instanceof ToolResponseMessage toolResponseMessage) {
				return buildToolResponseMessage(toolResponseMessage);
			}
			else {
				throw new RuntimeException("Unknown type of message");
			}
		}).toList();
	}

	private GoogleGeminiApi.ChatCompletionMessage buildAssistantMessage(AssistantMessage assistantMessage) {
		Collection<GoogleGeminiApi.Part.FunctionCall> toolCalls = assistantMessage.hasToolCalls() ? assistantMessage
			.getToolCalls()
			.stream()
			.map(call -> new GoogleGeminiApi.Part.FunctionCall(call.id(), call.name(), readJsonValue(call.arguments())))
			.toList() : Collections.emptyList();

		List<GoogleGeminiApi.Part> parts = new ArrayList<>();
		for (GoogleGeminiApi.Part.FunctionCall call : toolCalls) {
			parts.add(new GoogleGeminiApi.Part(call));
		}

		return new GoogleGeminiApi.ChatCompletionMessage(GoogleGeminiApi.ChatCompletionMessage.Role.ASSISTANT, parts);
	}

	private GoogleGeminiApi.ChatCompletionMessage buildUserMessage(UserMessage userMessage) {
		return new GoogleGeminiApi.ChatCompletionMessage(GoogleGeminiApi.ChatCompletionMessage.Role.USER,
				userMessage.getText());
	}

	private GoogleGeminiApi.ChatCompletionMessage buildToolResponseMessage(ToolResponseMessage toolResponseMessage) {
		Collection<GoogleGeminiApi.Part.FunctionResponse> functionResponses = toolResponseMessage.getResponses()
			.stream()
			.map(functionResponse -> new GoogleGeminiApi.Part.FunctionResponse(functionResponse.id(),
					functionResponse.name(), wrapInMapIfPrimitive(functionResponse.responseData())))
			.toList();

		List<GoogleGeminiApi.Part> parts = new ArrayList<>();
		for (GoogleGeminiApi.Part.FunctionResponse functionResponse : functionResponses) {
			parts.add(new GoogleGeminiApi.Part(functionResponse));
		}

		return new GoogleGeminiApi.ChatCompletionMessage(GoogleGeminiApi.ChatCompletionMessage.Role.TOOL, parts);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String apiKey;

		private GoogleGeminiChatOptions options = GoogleGeminiChatOptions.builder().build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder options(GoogleGeminiChatOptions options) {
			this.options = options;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder toolExecutionEligibilityPredicate(ToolExecutionEligibilityPredicate predicate) {
			this.toolExecutionEligibilityPredicate = predicate;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public GoogleGeminiChatModel build() {
			Assert.hasText(apiKey, "API key must not be empty");
			return new GoogleGeminiChatModel(new GoogleGeminiApi(apiKey), options, toolCallingManager, retryTemplate,
					toolExecutionEligibilityPredicate);
		}

	}

}
