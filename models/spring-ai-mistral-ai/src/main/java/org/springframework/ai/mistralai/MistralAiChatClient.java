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
package org.springframework.ai.mistralai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion.Choice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Grogdunn
 * @since 0.8.1
 */
public class MistralAiChatClient extends
		AbstractFunctionCallSupport<MistralAiApi.ChatCompletionMessage, MistralAiApi.ChatCompletionRequest, ResponseEntity<MistralAiApi.ChatCompletion>>
		implements ChatClient, StreamingChatClient {

	private final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private MistralAiChatOptions defaultOptions;

	/**
	 * Low-level access to the OpenAI API.
	 */
	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	public MistralAiChatClient(MistralAiApi mistralAiApi) {
		this(mistralAiApi,
				MistralAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1f)
					.withSafePrompt(false)
					.withModel(MistralAiApi.ChatModel.TINY.getValue())
					.build());
	}

	public MistralAiChatClient(MistralAiApi mistralAiApi, MistralAiChatOptions options) {
		this(mistralAiApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiChatClient(MistralAiApi mistralAiApi, MistralAiChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		super(functionCallbackContext);
		Assert.notNull(mistralAiApi, "MistralAiApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.mistralAiApi = mistralAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		var request = createRequest(prompt, false);

		return retryTemplate.execute(ctx -> {

			ResponseEntity<ChatCompletion> completionEntity = this.callWithFunctionSupport(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				log.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(List.of());
			}

			List<Generation> generations = chatCompletion.choices()
				.stream()
				.map(choice -> new Generation(choice.message().content(), toMap(chatCompletion.id(), choice))
					.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null)))
				.toList();

			return new ChatResponse(generations);
		});
	}

	private Map<String, Object> toMap(String id, ChatCompletion.Choice choice) {
		Map<String, Object> map = new HashMap<>();

		var message = choice.message();
		if (message.role() != null) {
			map.put("role", message.role().name());
		}
		if (choice.finishReason() != null) {
			map.put("finishReason", choice.finishReason().name());
		}
		map.put("id", id);
		return map;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var request = createRequest(prompt, true);

		return retryTemplate.execute(ctx -> {

			var completionChunks = this.mistralAiApi.chatCompletionStream(request);

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			return completionChunks.map(chunk -> toChatCompletion(chunk))
				.switchMap(
						cc -> handleFunctionCallOrReturnStream(request, Flux.just(ResponseEntity.of(Optional.of(cc)))))
				.map(ResponseEntity::getBody)
				.map(chatCompletion -> {
					@SuppressWarnings("null")
					String id = chatCompletion.id();

					List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
						if (choice.message().role() != null) {
							roleMap.putIfAbsent(id, choice.message().role().name());
						}
						String finish = (choice.finishReason() != null ? choice.finishReason().name() : "");
						var generation = new Generation(choice.message().content(),
								Map.of("id", id, "role", roleMap.get(id), "finishReason", finish));
						if (choice.finishReason() != null) {
							generation = generation.withGenerationMetadata(
									ChatGenerationMetadata.from(choice.finishReason().name(), null));
						}
						return generation;
					}).toList();
					return new ChatResponse(generations);
				});
		});
	}

	private ChatCompletion toChatCompletion(ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(cc -> new Choice(cc.index(), cc.delta(), cc.finishReason(), cc.logprobs()))
			.toList();

		return new ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.model(), choices, null);
	}

	/**
	 * Accessible for testing.
	 */
	MistralAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> functionsForThisRequest = new HashSet<>();

		var chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new MistralAiApi.ChatCompletionMessage(m.getContent(),
					MistralAiApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();

		var request = new MistralAiApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (this.defaultOptions != null) {
			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);

			functionsForThisRequest.addAll(defaultEnabledFunctions);

			request = ModelOptionsUtils.merge(request, this.defaultOptions, MistralAiApi.ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions, ChatOptions.class,
						MistralAiChatOptions.class);

				Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
						IS_RUNTIME_CALL);
				functionsForThisRequest.addAll(promptEnabledFunctions);

				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request,
						MistralAiApi.ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			request = ModelOptionsUtils.merge(
					MistralAiChatOptions.builder().withTools(this.getFunctionTools(functionsForThisRequest)).build(),
					request, ChatCompletionRequest.class);
		}

		return request;
	}

	private List<MistralAiApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new MistralAiApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			return new MistralAiApi.FunctionTool(function);
		}).toList();
	}

	//
	// Function Calling Support
	//
	@Override
	protected ChatCompletionRequest doCreateToolResponseRequest(ChatCompletionRequest previousRequest,
			ChatCompletionMessage responseMessage, List<ChatCompletionMessage> conversationHistory) {

		// Every tool-call item requires a separate function call and a response (TOOL)
		// message.
		for (ToolCall toolCall : responseMessage.toolCalls()) {

			String id = toolCall.id();
			String functionName = toolCall.function().name();
			String functionArguments = toolCall.function().arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			// Add the function response to the conversation.
			conversationHistory.add(new ChatCompletionMessage(functionResponse, ChatCompletionMessage.Role.TOOL,
					functionName, null, id));
		}

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		ChatCompletionRequest newRequest = new ChatCompletionRequest(conversationHistory, previousRequest.stream());
		newRequest = ModelOptionsUtils.merge(newRequest, previousRequest, ChatCompletionRequest.class);

		return newRequest;
	}

	@Override
	protected List<ChatCompletionMessage> doGetUserMessages(ChatCompletionRequest request) {
		return request.messages();
	}

	@SuppressWarnings("null")
	@Override
	protected ChatCompletionMessage doGetToolResponseMessage(ResponseEntity<ChatCompletion> chatCompletion) {
		ChatCompletionMessage msg = chatCompletion.getBody().choices().iterator().next().message();
		if (msg.role() == null) {
			// add missing role
			msg = new ChatCompletionMessage(msg.content(), ChatCompletionMessage.Role.ASSISTANT, msg.name(),
					msg.toolCalls());
		}
		return msg;
	}

	@Override
	protected ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.mistralAiApi.chatCompletionEntity(request);
	}

	@Override
	protected Flux<ResponseEntity<ChatCompletion>> doChatCompletionStream(ChatCompletionRequest request) {
		return this.mistralAiApi.chatCompletionStream(request)
			.map(this::toChatCompletion)
			.map(Optional::ofNullable)
			.map(ResponseEntity::of);
	}

	@Override
	protected boolean isToolFunctionCall(ResponseEntity<ChatCompletion> chatCompletion) {

		var body = chatCompletion.getBody();
		if (body == null) {
			return false;
		}

		var choices = body.choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		return !CollectionUtils.isEmpty(choices.get(0).message().toolCalls());
	}

}
