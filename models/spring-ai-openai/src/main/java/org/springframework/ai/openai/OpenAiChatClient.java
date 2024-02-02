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
package org.springframework.ai.openai;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
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
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

	private static final String MODEL = "gpt-3.5-turbo";

	private OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
		.withModel(MODEL)
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

	public OpenAiChatClient withDefaultOptions(OpenAiChatOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return this.call(prompt, null, null, MODEL);
	}

	/**
	 * This method supports function calling and can be included in the
	 * <code>ChatClient</code> interface eventually and implemented by clients that
	 * support this feature and UnsupportedException for others.
	 * @param prompt Promt for the model.
	 * @param toolList List of OpenAiApi.FunctionTool objects.
	 * @param functionService The service that contains the function calls needed to
	 * support this prompt.
	 * @return ChatResponse
	 */
	public ChatResponse call(Prompt prompt, List<OpenAiApi.FunctionTool> toolList, Object functionService,
			String model) {

		return this.retryTemplate.execute(ctx -> {
			List<ChatCompletionMessage> cumulativeMessageList = new ArrayList<>();
			List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
				.stream()
				.map(m -> new ChatCompletionMessage(m.getContent(),
						ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
				.toList();
			cumulativeMessageList.addAll(chatCompletionMessages);

			ResponseEntity<ChatCompletion> completionEntity;
			ChatCompletion chatCompletion;

			do {
				OpenAiApi.ChatCompletionRequest ccr;
				if (toolList != null && toolList.size() > 0) {
					ccr = new OpenAiApi.ChatCompletionRequest(cumulativeMessageList, (model == null ? MODEL : model),
							toolList, null);
				}
				else {
					ccr = new OpenAiApi.ChatCompletionRequest(cumulativeMessageList, (model == null ? MODEL : model),
							this.defaultOptions.getTemperature());
				}
				ccr = this.mergeRequest(prompt, ccr);
				completionEntity = this.openAiApi.chatCompletionEntity(ccr);

				chatCompletion = completionEntity.getBody();
				if (logger.isDebugEnabled() && chatCompletion.choices() != null) {
					for (ChatCompletion.Choice c : chatCompletion.choices()) {
						if ((c.message() != null
								&& StringUtils.equalsIgnoreCase(c.message().role().name(), "assistant"))) {
							logger.debug("Response: " + c.message().content());
						}
					}
				}
				if ((chatCompletion != null) && (chatCompletion.choices() != null)
						&& (chatCompletion.choices().size() > 0)) {
					if (chatCompletion.choices()
						.get(0)
						.finishReason() == OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS) {
						if (chatCompletion.choices().get(0).message().toolCalls() != null) {
							chatCompletion.choices()
								.get(0)
								.message()
								.toolCalls()
								.stream()
								.filter(ccm -> (StringUtils.equalsIgnoreCase(ccm.type(), "function")))
								.forEach(tc -> {
									String name = tc.function().name();
									String arguments = tc.function().arguments();
									String id = tc.id();

									// Create an assistant message with the returned
									// function to call with the returned arguments. Use
									// the returned toolCallId also.
									List<ChatCompletionMessage.ToolCall> toolCalls = List
										.of(new ChatCompletionMessage.ToolCall(id, "function",
												new ChatCompletionMessage.ChatCompletionFunction(name, arguments)));
									ChatCompletionMessage chatCompletionMessageAsAssistant = new ChatCompletionMessage(
											"none", ChatCompletionMessage.Role.ASSISTANT, name, id, toolCalls);
									cumulativeMessageList.add(chatCompletionMessageAsAssistant);

									String answerAsJsonString = processFunctionWithArguments(name, arguments,
											functionService);

									// Create a ToolMessage with the response to the
									// function call. Use the same ToolCallId
									ChatCompletionMessage chatCompletionMessageAsTool = new ChatCompletionMessage(
											answerAsJsonString, ChatCompletionMessage.Role.TOOL, name, id, null);
									cumulativeMessageList.add(chatCompletionMessageAsTool);
								});
						}
					}
				}
				else {
					logger.warn("No chat completion returned for request: {}", cumulativeMessageList);
					return new ChatResponse(List.of());
				}
			}
			while (chatCompletion.choices() != null && chatCompletion.choices().size() > 0
					&& chatCompletion.choices().get(0).finishReason() == OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS
					&& (chatCompletion.choices().get(0).message().toolCalls() != null));

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

		request = mergeRequest(prompt, request);

		return request;
	}

	private ChatCompletionRequest mergeRequest(Prompt prompt, ChatCompletionRequest request) {
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
				throw new IllegalArgumentException("Prompt options are not of type ChatCompletionRequest:"
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}
		return request;
	}

	private String processFunctionWithArguments(String name, String arguments, Object functionService) {

		String answerAsJsonString;
		try {
			Method methodToInvoke = functionService.getClass().getMethod(name, String.class);

			Method methodToExtractArguments = functionService.getClass()
				.getMethod("argumentValueExtractor", String.class, String.class);
			String argumentValue = (String) methodToExtractArguments.invoke(functionService, name, arguments);

			answerAsJsonString = (String) methodToInvoke.invoke(functionService, argumentValue);
		}
		catch (Exception e) {
			throw new RuntimeException(String.format("Error calling function %s with arguments %s", name, arguments),
					e);
		}
		return answerAsJsonString;
	}

}
