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

	public ChatResponse call2(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {

			List<ChatCompletionMessage> promptChatCompletionMessages = prompt.getInstructions().stream().map(m -> {
				String content = m.getContent();
				var role = ChatCompletionMessage.Role.valueOf(m.getMessageType().name());
				String toolCallId = null;
				List<ToolCall> toolCalls = null;
				return new ChatCompletionMessage(content, role, null, toolCallId, toolCalls);
			}).toList();

			OpenAiChatOptions promptOptions = (prompt.getOptions() != null
					&& prompt.getOptions() instanceof OpenAiChatOptions options) ? options : null;

			ResponseEntity<ChatCompletion> completionEntity = this.chatCompletionWithTools(promptChatCompletionMessages,
					promptOptions);

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
	public ChatResponse call(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {

			ChatCompletionRequest request = createRequest(prompt, false);

			ResponseEntity<ChatCompletion> completionEntity = this.openAiApi.chatCompletionEntity(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for request: {}", prompt);
				return new ChatResponse(List.of());
			}

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
				return new Generation(choice.message().content(), Map.of("role", choice.message().role().name()))
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

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				OpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, OpenAiChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

	private Map<String, Object> toMap(ChatCompletionMessage message) {
		Map<String, Object> map = new HashMap<>();
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

	public OpenAiChatClient withFunctionCallback(ToolFunctionCallback functionCallback) {

		var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
				functionCallback.getName(), functionCallback.getInputTypeSchema());

		var updatedDefaults = OpenAiChatOptions.builder()
			.withTools(List.of(new OpenAiApi.FunctionTool(function)))
			.build();

		this.defaultOptions = ModelOptionsUtils.merge(updatedDefaults, this.defaultOptions, OpenAiChatOptions.class);

		this.toolCallbacks.put(functionCallback.getName(), functionCallback);

		return this;
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

	private ResponseEntity<ChatCompletion> chatCompletionWithTools(
			List<ChatCompletionMessage> promptChatCompletionMessages, OpenAiChatOptions promptOptions) {

		List<ToolFunctionCallback> promptToolCallbacks = (promptOptions != null) ? promptOptions.getToolCallbacks()
				: null;
		// Add the prompt tool callbacks to the tool callbacks catalog
		if (!CollectionUtils.isEmpty(promptToolCallbacks)) {
			promptToolCallbacks.stream().forEach(toolCallback -> this.withFunctionCallback(toolCallback));
		}

		OpenAiApi.ChatCompletionRequest request = this.createChatCompletionRequest(promptChatCompletionMessages,
				promptOptions, false);

		List<ChatCompletionMessage> conversationMessages = new ArrayList<>(request.messages());

		ResponseEntity<ChatCompletion> chatCompletion = this.openAiApi.chatCompletionEntity(request);

		// Return if the model doesn't call a function.
		if (!isToolCall(chatCompletion)) {
			return chatCompletion;
		}

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
		return chatCompletionWithTools(conversationMessages, promptOptions);
	}

	private OpenAiApi.ChatCompletionRequest createChatCompletionRequest(
			List<ChatCompletionMessage> promptChatCompletionMessages, OpenAiChatOptions promptOptions, boolean stream) {

		ChatCompletionRequest request = new ChatCompletionRequest(promptChatCompletionMessages, stream);

		// Merges any default options into the request's options.
		if (this.defaultOptions != null) {
			// Only the non-null option values are merged.
			request = ModelOptionsUtils.merge(this.defaultOptions, request, ChatCompletionRequest.class);
		}

		// Merges any prompt options into the request's options.
		if (promptOptions != null) {
			request = ModelOptionsUtils.merge(promptOptions, request, ChatCompletionRequest.class);
		}

		// Merges any default messages (defined in the defaultOptions#messages) with the
		// prompt messages.
		// Note that the prompt messages are always added after the default messages.
		// if (request.messages() != null) {
		// var mergedMessages = new ArrayList<>(request.messages());
		// mergedMessages.addAll(promptChatCompletionMessages);
		// promptChatCompletionMessages = mergedMessages;
		// }

		// // If the te
		// var temperature = request.temperature();
		// if (temperature == null) {
		// if (request.getTemperature() != null) {
		// temperature = request.getTemperature().floatValue();
		// }
		// }

		// request = ModelOptionsUtils.merge(ChatCompletionRequestBuilder.builder()
		// .withMessages(promptChatCompletionMessages)
		// .withStream(stream)
		// .withTemperature(temperature)
		// .build(), request, ChatCompletionRequest.class);

		return request;
	}

}
