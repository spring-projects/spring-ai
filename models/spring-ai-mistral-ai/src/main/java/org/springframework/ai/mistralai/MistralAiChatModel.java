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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion.Choice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.metadata.MistralAiUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Thomas Vitale
 * @author luocongqiu
 * @since 0.8.1
 */
public class MistralAiChatModel extends AbstractToolCallSupport implements ChatModel {

	private final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MistralAiChatOptions defaultOptions;

	/**
	 * Low-level access to the OpenAI API.
	 */
	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	public MistralAiChatModel(MistralAiApi mistralAiApi) {
		this(mistralAiApi,
				MistralAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1f)
					.withSafePrompt(false)
					.withModel(MistralAiApi.ChatModel.OPEN_MISTRAL_7B.getValue())
					.build());
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options) {
		this(mistralAiApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options,
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

			ResponseEntity<ChatCompletion> completionEntity = this.mistralAiApi.chatCompletionEntity(request);

			if (this.isToolFunctionCall(completionEntity.getBody())) {
				List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
						completionEntity.getBody());
				// Recursively call the call method with the tool call message
				// conversation that contains the call responses.
				return this.call(new Prompt(toolCallMessageConversation, prompt.getOptions()));
			}

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

			return new ChatResponse(generations, from(chatCompletion));
		});
	}

	public static ChatResponseMetadata from(MistralAiApi.ChatCompletion result) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		MistralAiUsage usage = MistralAiUsage.from(result.usage());
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withModel(result.model())
			.withUsage(usage)
			.withKeyValue("created", result.created())
			.build();
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

			Flux<ChatCompletionChunk> completionChunks = this.mistralAiApi.chatCompletionStream(request);

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			return completionChunks.map(this::toChatCompletion).switchMap(chatCompletion -> {
				if (this.isToolFunctionCall(chatCompletion)) {
					var toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
							chatCompletion);
					// Recursively call the stream method with the tool call message
					// conversation that contains the call responses.
					return this.stream(new Prompt(toolCallMessageConversation, prompt.getOptions()));
				}

				return Mono.just(chatCompletion).map(chatCompletion2 -> {
					@SuppressWarnings("null")
					String id = chatCompletion2.id();

					List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
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
		});
	}

	private List<Message> handleToolCallRequests(List<Message> previousMessages, ChatCompletion chatCompletion) {

		ChatCompletionMessage nativeAssistantMessage = this.extractAssistantMessage(chatCompletion);

		List<AssistantMessage.ToolCall> assistantToolCalls = nativeAssistantMessage.toolCalls()
			.stream()
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function", toolCall.function().name(),
					toolCall.function().arguments()))
			.toList();

		AssistantMessage assistantMessage = new AssistantMessage(nativeAssistantMessage.content(), Map.of(),
				assistantToolCalls);

		ToolResponseMessage toolResponseMessage = this.executeFunctions(assistantMessage);

		// History
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);

		return messages;
	}

	private ChatCompletionMessage extractAssistantMessage(ChatCompletion chatCompletion) {
		ChatCompletionMessage msg = chatCompletion.choices().iterator().next().message();
		if (msg.role() == null) {
			// add missing role
			msg = new ChatCompletionMessage(msg.content(), ChatCompletionMessage.Role.ASSISTANT, msg.name(),
					msg.toolCalls());
		}
		return msg;
	}

	protected ToolResponseMessage executeFunctions(AssistantMessage assistantMessage) {

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			var functionName = toolCall.name();
			String functionArguments = toolCall.arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), functionName, functionResponse));
		}

		return new ToolResponseMessage(toolResponses, Map.of());
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

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message instanceof UserMessage userMessage) {
				return List.of(new MistralAiApi.ChatCompletionMessage(userMessage.getContent(),
						MistralAiApi.ChatCompletionMessage.Role.USER));
			}
			else if (message instanceof SystemMessage systemMessage) {
				return List.of(new MistralAiApi.ChatCompletionMessage(systemMessage.getContent(),
						MistralAiApi.ChatCompletionMessage.Role.SYSTEM));
			}
			else if (message instanceof AssistantMessage assistantMessage) {
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}

				return List.of(new MistralAiApi.ChatCompletionMessage(assistantMessage.getContent(),
						MistralAiApi.ChatCompletionMessage.Role.ASSISTANT, null, toolCalls, null));
			}
			else if (message instanceof ToolResponseMessage toolResponseMessage) {

				toolResponseMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
					Assert.isTrue(response.name() != null, "ToolResponseMessage must have a name");
				});

				return toolResponseMessage.getResponses()
					.stream()
					.map(toolResponse -> new MistralAiApi.ChatCompletionMessage(toolResponse.responseData(),
							MistralAiApi.ChatCompletionMessage.Role.TOOL, toolResponse.name(), null, toolResponse.id()))
					.toList();
			}
			else {
				throw new IllegalStateException("Unexpected message type: " + message);
			}
		}).flatMap(List::stream).toList();

		var request = new MistralAiApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (this.defaultOptions != null) {
			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);

			functionsForThisRequest.addAll(defaultEnabledFunctions);

			request = ModelOptionsUtils.merge(request, this.defaultOptions, MistralAiApi.ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					MistralAiChatOptions.class);

			Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
					IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(promptEnabledFunctions);

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, MistralAiApi.ChatCompletionRequest.class);
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

	protected boolean isToolFunctionCall(ChatCompletion chatCompletion) {

		var body = chatCompletion;
		if (body == null) {
			return false;
		}

		var choices = body.choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		return !CollectionUtils.isEmpty(choices.get(0).message().toolCalls());
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return MistralAiChatOptions.fromOptions(this.defaultOptions);
	}

}
