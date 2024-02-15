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
package org.springframework.ai.openai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.metadata.OpenAiChatResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ChatClient} implementation for {@literal OpenAI} backed by {@link OpenAiApi}.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Ueibin Kim
 * @author John Blum
 * @author Josh Long
 * @author Jemin Huh
 * @see ChatClient
 * @see StreamingChatClient
 * @see OpenAiApi
 */
public class OpenAiChatClient implements ChatClient, StreamingChatClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private OpenAiChatOptions defaultOptions;

	/**
	 * The function callback register is used to resolve the function callbacks by name.
	 */
	private Map<String, FunctionCallback> functionCallbackRegister = new ConcurrentHashMap<>();

	/**
	 * The function callback context is used to resolve the function callbacks by name
	 * from the Spring context. It is optional and usually used with Spring
	 * auto-configuration.
	 */
	private FunctionCallbackContext functionCallbackContext;

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {
			@Override
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			};
		})
		.build();

	/**
	 * Low-level access to the OpenAI API.
	 */
	private final OpenAiApi openAiApi;

	public OpenAiChatClient(OpenAiApi openAiApi) {
		this(openAiApi,
				OpenAiChatOptions.builder().withModel(OpenAiApi.DEFAULT_CHAT_MODEL).withTemperature(0.7f).build());
	}

	public OpenAiChatClient(OpenAiApi openAiApi, OpenAiChatOptions options) {
		this(openAiApi, options, null);
	}

	public OpenAiChatClient(OpenAiApi openAiApi, OpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext) {
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		this.openAiApi = openAiApi;
		this.defaultOptions = options;
		this.functionCallbackContext = functionCallbackContext;

		// Register the default function callbacks.
		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctionCallbacks())) {
			this.defaultOptions.getFunctionCallbacks()
				.stream()
				.forEach(functionCallback -> this.functionCallbackRegister.put(functionCallback.getName(),
						functionCallback));
		}
	}

	/**
	 * @deprecated since 0.8.0, use the
	 * {@link #OpenAiChatClient(OpenAiApi, OpenAiChatOptions)} constructor instead.
	 */
	@Deprecated(since = "0.8.0", forRemoval = true)
	public OpenAiChatClient withDefaultOptions(OpenAiChatOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {

			ChatCompletionRequest request = createRequest(prompt, false);

			ResponseEntity<ChatCompletion> completionEntity = this.chatCompletionWithTools(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(List.of());
			}

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
				return new Generation(choice.message().content(), toMap(choice.message()))
					.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null));
			}).toList();

			return new ChatResponse(generations,
					OpenAiChatResponseMetadata.from(completionEntity.getBody()).withRateLimit(rateLimits));
		});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.retryTemplate.execute(ctx -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			Flux<OpenAiApi.ChatCompletionChunk> completionChunks = this.openAiApi.chatCompletionStream(request);

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			return completionChunks.map(chunk -> {
				String chunkId = chunk.id();
				List<Generation> generations = chunk.choices().stream().map(choice -> {
					if (choice.delta().role() != null) {
						roleMap.putIfAbsent(chunkId, choice.delta().role().name());
					}
					var generation = new Generation(choice.delta().content(), Map.of("role", roleMap.get(chunkId)));
					if (choice.finishReason() != null) {
						generation = generation
							.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null));
					}
					return generation;
				}).toList();
				return new ChatResponse(generations);
			});
		});
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> enabledFunctionsForRequest = new HashSet<>();

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new ChatCompletionMessage(m.getContent(),
					ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				OpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, OpenAiChatOptions.class);

				Set<String> promptEnabledFunctions = handleFunctionCallbackConfigurations(updatedRuntimeOptions, true,
						true);
				enabledFunctionsForRequest.addAll(promptEnabledFunctions);

				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		if (this.defaultOptions != null) {

			Set<String> defaultEnabledFunctions = handleFunctionCallbackConfigurations(this.defaultOptions, false,
					false);

			enabledFunctionsForRequest.addAll(defaultEnabledFunctions);

			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(enabledFunctionsForRequest)) {

			if (stream) {
				throw new IllegalArgumentException("Currently tool functions are not supported in streaming mode");
			}

			request = ModelOptionsUtils.merge(
					OpenAiChatOptions.builder().withTools(this.getFunctionTools(enabledFunctionsForRequest)).build(),
					request, ChatCompletionRequest.class);
		}

		return request;
	}

	private Set<String> handleFunctionCallbackConfigurations(OpenAiChatOptions options,
			boolean autoEnableCallbackFunctions, boolean overrideCallbackFunctionsRegister) {

		Set<String> enabledFunctions = new HashSet<>();

		if (options != null) {
			if (!CollectionUtils.isEmpty(options.getFunctionCallbacks())) {
				options.getFunctionCallbacks().stream().forEach(functionCallback -> {

					// Register the tool callback.
					if (overrideCallbackFunctionsRegister) {
						this.functionCallbackRegister.put(functionCallback.getName(), functionCallback);
					}
					else {
						this.functionCallbackRegister.putIfAbsent(functionCallback.getName(), functionCallback);
					}

					// Automatically enable the function, usually from prompt callback.
					if (autoEnableCallbackFunctions) {
						enabledFunctions.add(functionCallback.getName());
					}
				});
			}

			// Add the explicitly enabled functions.
			if (!CollectionUtils.isEmpty(options.getEnabledFunctions())) {
				enabledFunctions.addAll(options.getEnabledFunctions());
			}
		}

		return enabledFunctions;
	}

	/**
	 * @return returns the registered tool callbacks.
	 */
	Map<String, FunctionCallback> getFunctionCallbackRegister() {
		return functionCallbackRegister;
	}

	private List<OpenAiApi.FunctionTool> getFunctionTools(Set<String> functionNames) {

		List<OpenAiApi.FunctionTool> functionTools = new ArrayList<>();
		for (String functionName : functionNames) {
			if (!this.functionCallbackRegister.containsKey(functionName)) {

				if (this.functionCallbackContext != null) {
					FunctionCallback functionCallback = this.functionCallbackContext.getFunctionCallback(functionName,
							null);
					if (functionCallback != null) {
						this.functionCallbackRegister.put(functionName, functionCallback);
					}
					else {
						throw new IllegalStateException(
								"No function callback [" + functionName + "] fund in tht FunctionCallbackContext");
					}
				}
				else {
					throw new IllegalStateException("No function callback found for name: " + functionName);
				}
			}
			FunctionCallback functionCallback = this.functionCallbackRegister.get(functionName);

			var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			functionTools.add(new OpenAiApi.FunctionTool(function));
		}

		return functionTools;
	}

	/**
	 * Function Call handling. If the model calls a function, the function is called and
	 * the response is added to the conversation history. The conversation history is then
	 * sent back to the model.
	 * @param request the chat completion request
	 * @return the chat completion response.
	 */
	@SuppressWarnings("null")
	private ResponseEntity<ChatCompletion> chatCompletionWithTools(OpenAiApi.ChatCompletionRequest request) {

		ResponseEntity<ChatCompletion> chatCompletion = this.openAiApi.chatCompletionEntity(request);

		// Return the result if the model is not calling a function.
		if (Boolean.FALSE.equals(this.isToolCall(chatCompletion))) {
			return chatCompletion;
		}

		// The OpenAI chat completion tool call API requires the complete conversation
		// history. Including the initial user message.
		List<ChatCompletionMessage> conversationMessages = new ArrayList<>(request.messages());

		// We assume that the tool calling information is inside the response's first
		// choice.
		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().iterator().next().message();

		if (chatCompletion.getBody().choices().size() > 1) {
			logger.warn("More than one choice returned. Only the first choice is processed.");
		}

		// Add the assistant response to the message conversation history.
		conversationMessages.add(responseMessage);

		// Every tool-call item requires a separate function call and a response (TOOL)
		// message.
		for (ToolCall toolCall : responseMessage.toolCalls()) {

			var functionName = toolCall.function().name();
			String functionArguments = toolCall.function().arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			// Add the function response to the conversation.
			conversationMessages.add(new ChatCompletionMessage(functionResponse, Role.TOOL, null, toolCall.id(), null));
		}

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		ChatCompletionRequest newRequest = new ChatCompletionRequest(conversationMessages, request.stream());
		newRequest = ModelOptionsUtils.merge(newRequest, request, ChatCompletionRequest.class);

		return this.chatCompletionWithTools(newRequest);
	}

	private Map<String, Object> toMap(ChatCompletionMessage message) {
		Map<String, Object> map = new HashMap<>();

		// The tool_calls and tool_call_id are not used by the OpenAiChatClient functions
		// call support! Useful only for users that want to use the tool_calls and
		// tool_call_id in their applications.
		if (message.toolCalls() != null) {
			map.put("tool_calls", message.toolCalls());
		}
		if (message.toolCallId() != null) {
			map.put("tool_call_id", message.toolCallId());
		}

		if (message.role() != null) {
			map.put("role", message.role().name());
		}
		return map;
	}

	/**
	 * Check if it is a model calls function response.
	 * @param chatCompletion the chat completion response.
	 * @return true if the model expects a function call.
	 */
	private Boolean isToolCall(ResponseEntity<ChatCompletion> chatCompletion) {
		var body = chatCompletion.getBody();
		if (body == null) {
			return false;
		}

		var choices = body.choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		return choices.get(0).message().toolCalls() != null;
	}

}
