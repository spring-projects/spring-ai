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
import java.util.List;
import java.util.Map;
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
import org.springframework.ai.model.ToolFunctionCallback;
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

	private OpenAiChatOptions defaultOptions;

	private Map<String, ToolFunctionCallback> toolCallbacks = new ConcurrentHashMap<>();

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			};
		})
		.build();

	private final OpenAiApi openAiApi;

	public OpenAiChatClient(OpenAiApi openAiApi) {
		this(openAiApi, OpenAiChatOptions.builder().withModel("gpt-3.5-turbo").withTemperature(0.7f).build());
	}

	public OpenAiChatClient(OpenAiApi openAiApi, OpenAiChatOptions options) {
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		this.openAiApi = openAiApi;
		this.defaultOptions = options;
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
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);

				// Add the prompt tool callbacks to the tool callbacks catalog
				if (!CollectionUtils.isEmpty(updatedRuntimeOptions.getToolCallbacks())) {
					updatedRuntimeOptions.getToolCallbacks()
						.stream()
						.forEach(toolCallback -> this.withFunctionCallback(toolCallback));
				}
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}
		// Note: the default options merge must be done after the prompt options merge or
		// the tools will be skipped.
		if (this.defaultOptions != null) {
			if (!CollectionUtils.isEmpty(this.defaultOptions.getToolCallbacks())) {
				this.defaultOptions.getToolCallbacks()
					.stream()
					.forEach(toolCallback -> this.withFunctionCallback(toolCallback));
			}
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}

		return request;
	}

	/**
	 * Register a function callback to be called when the model calls a function.
	 * @param functionCallback the function callback to register.
	 * @return the OpenAiChatClient instance.
	 */
	public OpenAiChatClient withFunctionCallback(ToolFunctionCallback functionCallback) {

		if (!this.toolCallbacks.containsKey(functionCallback.getName())) {

			var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());

			var updatedDefaults = OpenAiChatOptions.builder()
				.withTools(List.of(new OpenAiApi.FunctionTool(function)))
				.build();

			this.defaultOptions = ModelOptionsUtils.merge(updatedDefaults, this.defaultOptions,
					OpenAiChatOptions.class);

			this.toolCallbacks.put(functionCallback.getName(), functionCallback);
		}

		return this;
	}

	/**
	 * @return returns the registered tool callbacks.
	 */
	public Map<String, ToolFunctionCallback> getToolCallbacks() {
		return toolCallbacks;
	}

	/**
	 * Function Call handling. If the model calls a function, the function is called and
	 * the response is added to the conversation history. The conversation history is then
	 * sent back to the model.
	 * @param request the chat completion request
	 * @return the chat completion response.
	 */
	private ResponseEntity<ChatCompletion> chatCompletionWithTools(OpenAiApi.ChatCompletionRequest request) {

		ResponseEntity<ChatCompletion> chatCompletion = this.openAiApi.chatCompletionEntity(request);

		// Return if the model doesn't call a function.
		if (!isToolCall(chatCompletion)) {
			return chatCompletion;
		}

		List<ChatCompletionMessage> conversationMessages = new ArrayList<>(request.messages());

		// TODO: handle multiple choices response
		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		// Add the assistant response to the message conversation history.
		conversationMessages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();

			String functionArguments = toolCall.function().arguments();
			ToolFunctionCallback functionCallback = this.toolCallbacks.get(functionName);
			String functionResponse = functionCallback.call(functionArguments);

			// extend conversation with function response.
			conversationMessages.add(new ChatCompletionMessage(functionResponse, Role.TOOL, null, toolCall.id(), null));
		}

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		ChatCompletionRequest newRequest = new ChatCompletionRequest(conversationMessages, request.stream());
		newRequest = ModelOptionsUtils.merge(newRequest, request, ChatCompletionRequest.class);
		return chatCompletionWithTools(newRequest);
	}

	private Map<String, Object> toMap(ChatCompletionMessage message) {
		Map<String, Object> map = new HashMap<>();

		// The tool_calls and tool_call_id are not used by the OpenAiChatClient functions
		// call support!
		// Useful only for users that want to use the tool_calls and tool_call_id in their
		// applications.
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
