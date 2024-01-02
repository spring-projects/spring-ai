/*
 * Copyright 2023-2023 the original author or authors.
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
package org.springframework.ai.openai.client;

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
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.RateLimit;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.ToolFunctionCallback;
import org.springframework.ai.openai.api.ChatCompletionRequestBuilder;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.metadata.OpenAiGenerationMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.prompt.Prompt;
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

	private Map<String, ToolFunctionCallback> toolCallbacks = new HashMap<>();

	private ChatCompletionRequest defaultOptions = ChatCompletionRequestBuilder.builder()
		.withModel("gpt-3.5-turbo")
		.withTemperature(0.7f)
		.build();

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
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		this.openAiApi = openAiApi;
	}

	public ChatCompletionRequest getDefaultOptions() {
		return defaultOptions;
	}

	public OpenAiChatClient withDefaultOptions(ChatCompletionRequest defaultOptions) {
		this.defaultOptions = defaultOptions;
		return this;
	}

	@Override
	public ChatResponse generate(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {

			OpenAiApi.ChatCompletionRequest request2 = createRequest(prompt, false);

			ResponseEntity<ChatCompletion> completionEntity = this.openAiApi.chatCompletionEntity(request2);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for request: {}", request2);
				return new ChatResponse(List.of());
			}

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
				return new Generation(choice.message().content(), toMap(choice.message()))
					.withChoiceMetadata(ChoiceMetadata.from(choice.finishReason().name(), null));
			}).toList();

			return new ChatResponse(generations,
					OpenAiGenerationMetadata.from(completionEntity.getBody()).withRateLimit(rateLimits))
				.withRawResponse(chatCompletion);
		});
	}

	@Override
	public Flux<ChatResponse> generateStream(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {
			OpenAiApi.ChatCompletionRequest request2 = createRequest(prompt, true);

			Flux<OpenAiApi.ChatCompletionChunk> completionChunks = this.openAiApi.chatCompletionStream(request2);

			// For chunked responses, only the first chunk contains the choice properties.
			// The rest of the chunks with same ID share the same properties.
			ConcurrentHashMap<String, Object> propertiesMap = new ConcurrentHashMap<>();

			return completionChunks.map(chunk -> {
				String chunkId = chunk.id();
				List<Generation> generations = chunk.choices().stream().map(choice -> {
					if (choice.delta().role() != null) {
						propertiesMap.putAll(toMap(choice.delta()));
						// propertiesMap.putIfAbsent(chunkId,
						// choice.delta().role().name());
					}
					var generation = new Generation(choice.delta().content(), propertiesMap);
					// Map.of("role", propertiesMap.get(chunkId)));
					if (choice.finishReason() != null) {
						generation = generation
							.withChoiceMetadata(ChoiceMetadata.from(choice.finishReason().name(), null));
					}
					return generation;
				}).toList();
				return new ChatResponse(generations).withRawResponse(chunk);
			});
		});
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

	private OpenAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		OpenAiApi.ChatCompletionRequest request = ChatCompletionRequestBuilder.builder().build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(this.defaultOptions, request, ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatCompletionRequest options) {
				request = ModelOptionsUtils.merge(options, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatCompletionRequest:"
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		List<ChatCompletionMessage> promptChatCompletionMessages = prompt.getMessages().stream().map(m -> {
			String content = m.getContent();
			var role = ChatCompletionMessage.Role.valueOf(m.getMessageType().getValue());
			String toolCallId = null;
			List<ToolCall> toolCalls = null;
			if (role == ChatCompletionMessage.Role.tool && m.getProperties().containsKey("tool_call_id")) {
				toolCallId = m.getProperties().get("tool_call_id").toString();
			}
			if (role == ChatCompletionMessage.Role.assistant && m.getProperties().containsKey("tool_calls")) {
				toolCalls = (List<ToolCall>) m.getProperties().get("tool_calls");
			}
			return new ChatCompletionMessage(content, role, null, toolCallId, toolCalls);
		}).toList();

		// Merges any default messages with the prompt messages.
		if (request.messages() != null) {
			var mergedMessages = new ArrayList<>(promptChatCompletionMessages);
			mergedMessages.addAll(request.messages());
			promptChatCompletionMessages = mergedMessages;
		}

		// If the te
		var temperature = request.temperature();
		if (temperature == null) {
			if (request.getPortableOptions().getTemperature() != null) {
				temperature = request.getPortableOptions().getTemperature().floatValue();
			}
		}

		request = ModelOptionsUtils.merge(ChatCompletionRequestBuilder.builder()
			.withMessages(promptChatCompletionMessages)
			.withStream(stream)
			.withTemperature(temperature)
			.build(), request, ChatCompletionRequest.class);

		return request;
	}

	public OpenAiChatClient withFunctionCallback(ToolFunctionCallback functionCallback) {

		var function = new OpenAiApi.FunctionTool.Function(functionCallback.getDescription(),
				functionCallback.getName(), functionCallback.getInputTypeSchema());

		var updatedDefaults = ChatCompletionRequestBuilder.builder()
			.withTools(List.of(new OpenAiApi.FunctionTool(function)))
			.build();

		this.defaultOptions = ModelOptionsUtils.merge(updatedDefaults, this.defaultOptions,
				ChatCompletionRequest.class);

		this.toolCallbacks.put(functionCallback.getName(), functionCallback);

		return this;
	}

	public ChatResponse generateWithTools(Prompt prompt) {

		List<ChatCompletionMessage> promptChatCompletionMessages = prompt.getMessages().stream().map(m -> {
			String content = m.getContent();
			var role = ChatCompletionMessage.Role.valueOf(m.getMessageType().getValue());
			String toolCallId = null;
			List<ToolCall> toolCalls = null;
			return new ChatCompletionMessage(content, role, null, toolCallId, toolCalls);
		}).toList();

		ChatCompletionRequest promptOptions = (prompt.getOptions() != null
				&& prompt.getOptions() instanceof ChatCompletionRequest options) ? options : null;

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
				.withChoiceMetadata(ChoiceMetadata.from(choice.finishReason().name(), null));
		}).toList();

		return new ChatResponse(generations,
				OpenAiGenerationMetadata.from(completionEntity.getBody()).withRateLimit(rateLimits))
			.withRawResponse(chatCompletion);

	}

	private ResponseEntity<ChatCompletion> chatCompletionWithTools(
			List<ChatCompletionMessage> promptChatCompletionMessages, ChatCompletionRequest promptOptions) {

		OpenAiApi.ChatCompletionRequest request = createChatCompletionRequest(promptChatCompletionMessages,
				promptOptions, false);

		List<ChatCompletionMessage> conversationMessages = new ArrayList<>(request.messages());

		ResponseEntity<ChatCompletion> chatCompletion = this.openAiApi.chatCompletionEntity(request);

		// Return if the model doesn't call a function.
		if (!isToolCall(chatCompletion)) {
			return chatCompletion;
		}

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		// extend conversation with assistant's reply.
		conversationMessages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();

			String functionArguments = toolCall.function().arguments();
			ToolFunctionCallback functionCallback = this.toolCallbacks.get(functionName);
			String functionResponse = functionCallback.call(functionArguments);

			// extend conversation with function response.
			conversationMessages.add(new ChatCompletionMessage(functionResponse, Role.tool, null, toolCall.id(), null));
		}

		return chatCompletionWithTools(conversationMessages, promptOptions);
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

	private OpenAiApi.ChatCompletionRequest createChatCompletionRequest(
			List<ChatCompletionMessage> promptChatCompletionMessages, ChatCompletionRequest promptOptions,
			boolean stream) {

		OpenAiApi.ChatCompletionRequest request = ChatCompletionRequestBuilder.builder().build();

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
		if (request.messages() != null) {
			var mergedMessages = new ArrayList<>(request.messages());
			mergedMessages.addAll(promptChatCompletionMessages);
			promptChatCompletionMessages = mergedMessages;
		}

		// If the te
		var temperature = request.temperature();
		if (temperature == null) {
			if (request.getPortableOptions().getTemperature() != null) {
				temperature = request.getPortableOptions().getTemperature().floatValue();
			}
		}

		request = ModelOptionsUtils.merge(ChatCompletionRequestBuilder.builder()
			.withMessages(promptChatCompletionMessages)
			.withStream(stream)
			.withTemperature(temperature)
			.build(), request, ChatCompletionRequest.class);

		return request;
	}

}
