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
package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolDefinition;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestToolMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.azure.ai.openai.models.FunctionCall;
import com.azure.ai.openai.models.FunctionDefinition;
import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.azure.openai.metadata.AzureOpenAiChatResponseMetadata;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ChatClient} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Mark Pollack
 * @author Ueibin Kim
 * @author John Blum
 * @author Christian Tzolov
 * @author Grogdunn
 * @see ChatClient
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiChatClient
		extends AbstractFunctionCallSupport<ChatRequestMessage, ChatCompletionsOptions, ChatCompletions>
		implements ChatClient, StreamingChatClient {

	private static final String DEFAULT_DEPLOYMENT_NAME = "gpt-35-turbo";

	private static final Float DEFAULT_TEMPERATURE = 0.7f;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The configuration information for a chat completions request.
	 */
	private AzureOpenAiChatOptions defaultOptions;

	/**
	 * The {@link OpenAIClient} used to interact with the Azure OpenAI service.
	 */
	private final OpenAIClient openAIClient;

	public AzureOpenAiChatClient(OpenAIClient microsoftOpenAiClient) {
		this(microsoftOpenAiClient,
				AzureOpenAiChatOptions.builder()
					.withDeploymentName(DEFAULT_DEPLOYMENT_NAME)
					.withTemperature(DEFAULT_TEMPERATURE)
					.build());
	}

	public AzureOpenAiChatClient(OpenAIClient microsoftOpenAiClient, AzureOpenAiChatOptions options) {
		this(microsoftOpenAiClient, options, null);
	}

	public AzureOpenAiChatClient(OpenAIClient microsoftOpenAiClient, AzureOpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext) {
		super(functionCallbackContext);
		Assert.notNull(microsoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(options, "AzureOpenAiChatOptions must not be null");
		this.openAIClient = microsoftOpenAiClient;
		this.defaultOptions = options;
	}

	/**
	 * @deprecated since 0.8.0, use
	 * {@link #AzureOpenAiChatClient(OpenAIClient, AzureOpenAiChatOptions)} instead.
	 */
	@Deprecated(forRemoval = true, since = "0.8.0")
	public AzureOpenAiChatClient withDefaultOptions(AzureOpenAiChatOptions defaultOptions) {
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		this.defaultOptions = defaultOptions;
		return this;
	}

	public AzureOpenAiChatOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(false);

		logger.trace("Azure ChatCompletionsOptions: {}", options);
		ChatCompletions chatCompletions = this.callWithFunctionSupport(options);
		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		List<Generation> generations = chatCompletions.getChoices()
			.stream()
			.map(choice -> new Generation(choice.getMessage().getContent())
				.withGenerationMetadata(generateChoiceMetadata(choice)))
			.toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);

		return new ChatResponse(generations,
				AzureOpenAiChatResponseMetadata.from(chatCompletions, promptFilterMetadata));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(true);

		IterableStream<ChatCompletions> chatCompletionsStream = this.openAIClient
			.getChatCompletionsStream(options.getModel(), options);

		Flux<ChatCompletions> chatCompletionsFlux = Flux.fromIterable(chatCompletionsStream);

		final var isFunctionCall = new AtomicBoolean(false);
		final var accessibleChatCompletionsFlux = chatCompletionsFlux
			// Note: the first chat completions can be ignored when using Azure OpenAI
			// service which is a known service bug.
			.skip(1)
			.map(chatCompletions -> {
				final var toolCalls = chatCompletions.getChoices().get(0).getDelta().getToolCalls();
				isFunctionCall.set(toolCalls != null && !toolCalls.isEmpty());
				return chatCompletions;
			})
			.windowUntil(chatCompletions -> {
				if (isFunctionCall.get() && chatCompletions.getChoices()
					.get(0)
					.getFinishReason() == CompletionsFinishReason.TOOL_CALLS) {
					isFunctionCall.set(false);
					return true;
				}
				return false;
			}, false)
			.concatMapIterable(window -> {
				final var reduce = window.reduce(MergeUtils.emptyChatCompletions(), MergeUtils::mergeChatCompletions);
				return List.of(reduce);
			})
			.flatMap(mono -> mono);
		return accessibleChatCompletionsFlux
			.switchMap(accessibleChatCompletions -> handleFunctionCallOrReturnStream(options,
					Flux.just(accessibleChatCompletions)))
			.flatMapIterable(ChatCompletions::getChoices)
			.map(choice -> {
				var content = Optional.ofNullable(choice.getMessage()).orElse(choice.getDelta()).getContent();
				var generation = new Generation(content).withGenerationMetadata(generateChoiceMetadata(choice));
				return new ChatResponse(List.of(generation));
			});

	}

	/**
	 * Test access.
	 */
	ChatCompletionsOptions toAzureChatCompletionsOptions(Prompt prompt) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<ChatRequestMessage> azureMessages = prompt.getInstructions()
			.stream()
			.map(this::fromSpringAiMessage)
			.toList();

		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);

		if (this.defaultOptions != null) {
			// JSON merge doesn't due to Azure OpenAI service bug:
			// https://github.com/Azure/azure-sdk-for-java/issues/38183
			// options = ModelOptionsUtils.merge(options, this.defaultOptions,
			// ChatCompletionsOptions.class);
			options = merge(options, this.defaultOptions);

			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(defaultEnabledFunctions);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				AzureOpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, AzureOpenAiChatOptions.class);
				// JSON merge doesn't due to Azure OpenAI service bug:
				// https://github.com/Azure/azure-sdk-for-java/issues/38183
				// options = ModelOptionsUtils.merge(runtimeOptions, options,
				// ChatCompletionsOptions.class);
				options = merge(updatedRuntimeOptions, options);

				Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
						IS_RUNTIME_CALL);
				functionsForThisRequest.addAll(promptEnabledFunctions);

			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatCompletionsOptions:"
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		// Add the enabled functions definitions to the request's tools parameter.

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			List<ChatCompletionsFunctionToolDefinition> tools = this.getFunctionTools(functionsForThisRequest);
			List<ChatCompletionsToolDefinition> tools2 = tools.stream()
				.map(t -> ((ChatCompletionsToolDefinition) t))
				.toList();
			options.setTools(tools2);
		}

		return options;
	}

	private List<ChatCompletionsFunctionToolDefinition> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {

			FunctionDefinition functionDefinition = new FunctionDefinition(functionCallback.getName());
			functionDefinition.setDescription(functionCallback.getDescription());
			BinaryData parameters = BinaryData
				.fromObject(ModelOptionsUtils.jsonToMap(functionCallback.getInputTypeSchema()));
			functionDefinition.setParameters(parameters);
			return new ChatCompletionsFunctionToolDefinition(functionDefinition);
		}).toList();
	}

	private ChatRequestMessage fromSpringAiMessage(Message message) {

		switch (message.getMessageType()) {
			case USER:
				return new ChatRequestUserMessage(message.getContent());
			case SYSTEM:
				return new ChatRequestSystemMessage(message.getContent());
			case ASSISTANT:
				return new ChatRequestAssistantMessage(message.getContent());
			default:
				throw new IllegalArgumentException("Unknown message type " + message.getMessageType());
		}

	}

	private ChatGenerationMetadata generateChoiceMetadata(ChatChoice choice) {
		return ChatGenerationMetadata.from(String.valueOf(choice.getFinishReason()), choice.getContentFilterResults());
	}

	private PromptMetadata generatePromptMetadata(ChatCompletions chatCompletions) {

		List<ContentFilterResultsForPrompt> promptFilterResults = nullSafeList(
				chatCompletions.getPromptFilterResults());

		return PromptMetadata.of(promptFilterResults.stream()
			.map(promptFilterResult -> PromptFilterMetadata.from(promptFilterResult.getPromptIndex(),
					promptFilterResult.getContentFilterResults()))
			.toList());
	}

	private <T> List<T> nullSafeList(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}

	/**
	 * Merges the Azure's {@link ChatCompletionsOptions} (fromAzureOptions) into the
	 * Spring AI's {@link AzureOpenAiChatOptions} (toSpringAiOptions) and return a new
	 * {@link ChatCompletionsOptions} instance.
	 */
	private ChatCompletionsOptions merge(ChatCompletionsOptions fromAzureOptions,
			AzureOpenAiChatOptions toSpringAiOptions) {

		if (toSpringAiOptions == null) {
			return fromAzureOptions;
		}

		ChatCompletionsOptions mergedAzureOptions = new ChatCompletionsOptions(fromAzureOptions.getMessages());
		mergedAzureOptions.setStream(fromAzureOptions.isStream());

		mergedAzureOptions.setMaxTokens((fromAzureOptions.getMaxTokens() != null) ? fromAzureOptions.getMaxTokens()
				: toSpringAiOptions.getMaxTokens());

		mergedAzureOptions.setLogitBias(fromAzureOptions.getLogitBias() != null ? fromAzureOptions.getLogitBias()
				: toSpringAiOptions.getLogitBias());

		mergedAzureOptions
			.setStop(fromAzureOptions.getStop() != null ? fromAzureOptions.getStop() : toSpringAiOptions.getStop());

		mergedAzureOptions.setTemperature(fromAzureOptions.getTemperature());
		if (mergedAzureOptions.getTemperature() == null && toSpringAiOptions.getTemperature() != null) {
			mergedAzureOptions.setTemperature(toSpringAiOptions.getTemperature().doubleValue());
		}

		mergedAzureOptions.setTopP(fromAzureOptions.getTopP());
		if (mergedAzureOptions.getTopP() == null && toSpringAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(toSpringAiOptions.getTopP().doubleValue());
		}

		mergedAzureOptions.setFrequencyPenalty(fromAzureOptions.getFrequencyPenalty());
		if (mergedAzureOptions.getFrequencyPenalty() == null && toSpringAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(toSpringAiOptions.getFrequencyPenalty().doubleValue());
		}

		mergedAzureOptions.setPresencePenalty(fromAzureOptions.getPresencePenalty());
		if (mergedAzureOptions.getPresencePenalty() == null && toSpringAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(toSpringAiOptions.getPresencePenalty().doubleValue());
		}

		mergedAzureOptions.setN(fromAzureOptions.getN() != null ? fromAzureOptions.getN() : toSpringAiOptions.getN());

		mergedAzureOptions
			.setUser(fromAzureOptions.getUser() != null ? fromAzureOptions.getUser() : toSpringAiOptions.getUser());

		mergedAzureOptions.setModel(fromAzureOptions.getModel() != null ? fromAzureOptions.getModel()
				: toSpringAiOptions.getDeploymentName());

		return mergedAzureOptions;
	}

	/**
	 * Merges the {@link AzureOpenAiChatOptions}, fromSpringAiOptions, into the
	 * {@link ChatCompletionsOptions}, toAzureOptions, and returns a new
	 * {@link ChatCompletionsOptions} instance.
	 * @param fromSpringAiOptions the {@link AzureOpenAiChatOptions} to merge from.
	 * @param toAzureOptions the {@link ChatCompletionsOptions} to merge to.
	 * @return a new {@link ChatCompletionsOptions} instance.
	 */
	private ChatCompletionsOptions merge(AzureOpenAiChatOptions fromSpringAiOptions,
			ChatCompletionsOptions toAzureOptions) {

		if (fromSpringAiOptions == null) {
			return toAzureOptions;
		}

		ChatCompletionsOptions mergedAzureOptions = this.copy(toAzureOptions);

		if (fromSpringAiOptions.getMaxTokens() != null) {
			mergedAzureOptions.setMaxTokens(fromSpringAiOptions.getMaxTokens());
		}

		if (fromSpringAiOptions.getLogitBias() != null) {
			mergedAzureOptions.setLogitBias(fromSpringAiOptions.getLogitBias());
		}

		if (fromSpringAiOptions.getStop() != null) {
			mergedAzureOptions.setStop(fromSpringAiOptions.getStop());
		}

		if (fromSpringAiOptions.getTemperature() != null) {
			mergedAzureOptions.setTemperature(fromSpringAiOptions.getTemperature().doubleValue());
		}

		if (fromSpringAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(fromSpringAiOptions.getTopP().doubleValue());
		}

		if (fromSpringAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(fromSpringAiOptions.getFrequencyPenalty().doubleValue());
		}

		if (fromSpringAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(fromSpringAiOptions.getPresencePenalty().doubleValue());
		}

		if (fromSpringAiOptions.getN() != null) {
			mergedAzureOptions.setN(fromSpringAiOptions.getN());
		}

		if (fromSpringAiOptions.getUser() != null) {
			mergedAzureOptions.setUser(fromSpringAiOptions.getUser());
		}

		if (fromSpringAiOptions.getDeploymentName() != null) {
			mergedAzureOptions.setModel(fromSpringAiOptions.getDeploymentName());
		}

		return mergedAzureOptions;
	}

	/**
	 * Merges the fromOptions into the toOptions and returns a new ChatCompletionsOptions
	 * instance.
	 * @param fromOptions the ChatCompletionsOptions to merge from.
	 * @param toOptions the ChatCompletionsOptions to merge to.
	 * @return a new ChatCompletionsOptions instance.
	 */
	private ChatCompletionsOptions merge(ChatCompletionsOptions fromOptions, ChatCompletionsOptions toOptions) {

		if (fromOptions == null) {
			return toOptions;
		}

		ChatCompletionsOptions mergedOptions = this.copy(toOptions);

		if (fromOptions.getMaxTokens() != null) {
			mergedOptions.setMaxTokens(fromOptions.getMaxTokens());
		}
		if (fromOptions.getLogitBias() != null) {
			mergedOptions.setLogitBias(fromOptions.getLogitBias());
		}
		if (fromOptions.getStop() != null) {
			mergedOptions.setStop(fromOptions.getStop());
		}
		if (fromOptions.getTemperature() != null) {
			mergedOptions.setTemperature(fromOptions.getTemperature());
		}
		if (fromOptions.getTopP() != null) {
			mergedOptions.setTopP(fromOptions.getTopP());
		}
		if (fromOptions.getFrequencyPenalty() != null) {
			mergedOptions.setFrequencyPenalty(fromOptions.getFrequencyPenalty());
		}
		if (fromOptions.getPresencePenalty() != null) {
			mergedOptions.setPresencePenalty(fromOptions.getPresencePenalty());
		}
		if (fromOptions.getN() != null) {
			mergedOptions.setN(fromOptions.getN());
		}
		if (fromOptions.getUser() != null) {
			mergedOptions.setUser(fromOptions.getUser());
		}
		if (fromOptions.getModel() != null) {
			mergedOptions.setModel(fromOptions.getModel());
		}

		return mergedOptions;
	}

	/**
	 * Copy the fromOptions into a new ChatCompletionsOptions instance.
	 * @param fromOptions the ChatCompletionsOptions to copy from.
	 * @return a new ChatCompletionsOptions instance.
	 */
	private ChatCompletionsOptions copy(ChatCompletionsOptions fromOptions) {

		ChatCompletionsOptions copyOptions = new ChatCompletionsOptions(fromOptions.getMessages());
		copyOptions.setStream(fromOptions.isStream());

		if (fromOptions.getMaxTokens() != null) {
			copyOptions.setMaxTokens(fromOptions.getMaxTokens());
		}
		if (fromOptions.getLogitBias() != null) {
			copyOptions.setLogitBias(fromOptions.getLogitBias());
		}
		if (fromOptions.getStop() != null) {
			copyOptions.setStop(fromOptions.getStop());
		}
		if (fromOptions.getTemperature() != null) {
			copyOptions.setTemperature(fromOptions.getTemperature());
		}
		if (fromOptions.getTopP() != null) {
			copyOptions.setTopP(fromOptions.getTopP());
		}
		if (fromOptions.getFrequencyPenalty() != null) {
			copyOptions.setFrequencyPenalty(fromOptions.getFrequencyPenalty());
		}
		if (fromOptions.getPresencePenalty() != null) {
			copyOptions.setPresencePenalty(fromOptions.getPresencePenalty());
		}
		if (fromOptions.getN() != null) {
			copyOptions.setN(fromOptions.getN());
		}
		if (fromOptions.getUser() != null) {
			copyOptions.setUser(fromOptions.getUser());
		}
		if (fromOptions.getModel() != null) {
			copyOptions.setModel(fromOptions.getModel());
		}

		return copyOptions;
	}

	@Override
	protected boolean hasReturningFunction(ChatRequestMessage responseMessage) {
		return ((ChatRequestAssistantMessage) responseMessage).getToolCalls()
			.stream()
			.map(toolCall -> ((ChatCompletionsFunctionToolCall) toolCall).getFunction().getName())
			.map(functionName -> Optional.ofNullable(this.functionCallbackRegister.get(functionName)))
			.anyMatch(functionCallback -> functionCallback.map(FunctionCallback::returningFunction).orElse(false));
	}

	@Override
	protected ChatCompletionsOptions doCreateToolResponseRequest(ChatCompletionsOptions previousRequest,
			ChatRequestMessage responseMessage, List<ChatRequestMessage> conversationHistory) {

		// Every tool-call item requires a separate function call and a response (TOOL)
		// message.
		for (ChatCompletionsToolCall toolCall : ((ChatRequestAssistantMessage) responseMessage).getToolCalls()) {

			var functionName = ((ChatCompletionsFunctionToolCall) toolCall).getFunction().getName();
			String functionArguments = ((ChatCompletionsFunctionToolCall) toolCall).getFunction().getArguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			if (functionResponse != null) {
				// Add the function response to the conversation.
				conversationHistory.add(new ChatRequestToolMessage(functionResponse, toolCall.getId()));
			}
		}

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		ChatCompletionsOptions newRequest = new ChatCompletionsOptions(conversationHistory);

		newRequest = merge(previousRequest, newRequest);

		return newRequest;
	}

	@Override
	protected List<ChatRequestMessage> doGetUserMessages(ChatCompletionsOptions request) {
		return request.getMessages();
	}

	@Override
	protected ChatRequestMessage doGetToolResponseMessage(ChatCompletions response) {
		final var accessibleChatChoice = response.getChoices().get(0);
		var responseMessage = Optional.ofNullable(accessibleChatChoice.getMessage())
			.orElse(accessibleChatChoice.getDelta());
		ChatRequestAssistantMessage assistantMessage = new ChatRequestAssistantMessage("");
		final var toolCalls = responseMessage.getToolCalls();
		assistantMessage.setToolCalls(toolCalls.stream().map(tc -> {
			final var tc1 = (ChatCompletionsFunctionToolCall) tc;
			var toDowncast = new ChatCompletionsFunctionToolCall(tc.getId(),
					new FunctionCall(tc1.getFunction().getName(), tc1.getFunction().getArguments()));
			return ((ChatCompletionsToolCall) toDowncast);
		}).toList());
		return assistantMessage;
	}

	@Override
	protected ChatCompletions doChatCompletion(ChatCompletionsOptions request) {
		return this.openAIClient.getChatCompletions(request.getModel(), request);
	}

	@Override
	protected Flux<ChatCompletions> doChatCompletionStream(ChatCompletionsOptions request) {
		return Flux.fromIterable(this.openAIClient.getChatCompletionsStream(request.getModel(), request));
	}

	@Override
	protected boolean isToolFunctionCall(ChatCompletions chatCompletions) {

		if (chatCompletions == null || CollectionUtils.isEmpty(chatCompletions.getChoices())) {
			return false;
		}

		var choice = chatCompletions.getChoices().get(0);

		if (choice == null || choice.getFinishReason() == null) {
			return false;
		}

		return choice.getFinishReason() == CompletionsFinishReason.TOOL_CALLS;
	}

}
