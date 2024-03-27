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
import com.azure.ai.openai.models.*;
import com.azure.core.util.BinaryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.dto.AccessibleChatChoice;
import org.springframework.ai.azure.openai.dto.AccessibleChatCompletions;
import org.springframework.ai.azure.openai.dto.AccessibleChatCompletionsFunctionToolCall;
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
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ChatClient} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Mark Pollack
 * @author Ueibin Kim
 * @author John Blum
 * @author Christian Tzolov
 * @see ChatClient
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiChatClient
		extends AbstractFunctionCallSupport<ChatRequestMessage, ChatCompletionsOptions, AccessibleChatCompletions>
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
	private final OpenAIClientDecorator openAIClient;

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
		this.openAIClient = new OpenAIClientDecorator(microsoftOpenAiClient);
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
		AccessibleChatCompletions chatCompletions = this.callWithFunctionSupport(options);
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

		Flux<AccessibleChatCompletions> chatCompletionsStream = this.openAIClient
			.getChatCompletionsStream(options.getModel(), options);

		final var isFunctionCall = new AtomicBoolean(false);
		final var accessibleChatCompletionsFlux = chatCompletionsStream
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
				final var reduce = window.reduce(AccessibleChatCompletions.empty(), AccessibleChatCompletions::merge);
				return List.of(reduce);
			})
			.flatMap(mono -> mono);
		return accessibleChatCompletionsFlux
			.switchMap(accessibleChatCompletions -> handleFunctionCallOrReturnStream(options,
					Flux.just(accessibleChatCompletions)))
			.flatMapIterable(AccessibleChatCompletions::getChoices)
			.map(choice -> {
				var content = Optional.ofNullable(choice.message).orElse(choice.delta).getContent();
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

	private ChatGenerationMetadata generateChoiceMetadata(AccessibleChatChoice choice) {
		return ChatGenerationMetadata.from(String.valueOf(choice.getFinishReason()), choice.getContentFilterResults());
	}

	private PromptMetadata generatePromptMetadata(AccessibleChatCompletions chatCompletions) {

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

	// JSON merge doesn't due to Azure OpenAI service bug:
	// https://github.com/Azure/azure-sdk-for-java/issues/38183
	private ChatCompletionsOptions merge(ChatCompletionsOptions azureOptions, AzureOpenAiChatOptions springAiOptions) {

		if (springAiOptions == null) {
			return azureOptions;
		}

		ChatCompletionsOptions mergedAzureOptions = new ChatCompletionsOptions(azureOptions.getMessages());
		mergedAzureOptions.setStream(azureOptions.isStream());

		mergedAzureOptions.setMaxTokens(
				(azureOptions.getMaxTokens() != null) ? azureOptions.getMaxTokens() : springAiOptions.getMaxTokens());

		mergedAzureOptions.setLogitBias(
				azureOptions.getLogitBias() != null ? azureOptions.getLogitBias() : springAiOptions.getLogitBias());

		mergedAzureOptions.setStop(azureOptions.getStop() != null ? azureOptions.getStop() : springAiOptions.getStop());

		mergedAzureOptions.setTemperature(azureOptions.getTemperature());
		if (mergedAzureOptions.getTemperature() == null && springAiOptions.getTemperature() != null) {
			mergedAzureOptions.setTemperature(springAiOptions.getTemperature().doubleValue());
		}

		mergedAzureOptions.setTopP(azureOptions.getTopP());
		if (mergedAzureOptions.getTopP() == null && springAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(springAiOptions.getTopP().doubleValue());
		}

		mergedAzureOptions.setFrequencyPenalty(azureOptions.getFrequencyPenalty());
		if (mergedAzureOptions.getFrequencyPenalty() == null && springAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(springAiOptions.getFrequencyPenalty().doubleValue());
		}

		mergedAzureOptions.setPresencePenalty(azureOptions.getPresencePenalty());
		if (mergedAzureOptions.getPresencePenalty() == null && springAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(springAiOptions.getPresencePenalty().doubleValue());
		}

		mergedAzureOptions.setN(azureOptions.getN() != null ? azureOptions.getN() : springAiOptions.getN());

		mergedAzureOptions.setUser(azureOptions.getUser() != null ? azureOptions.getUser() : springAiOptions.getUser());

		mergedAzureOptions
			.setModel(azureOptions.getModel() != null ? azureOptions.getModel() : springAiOptions.getDeploymentName());

		return mergedAzureOptions;
	}

	// JSON merge doesn't due to Azure OpenAI service bug:
	// https://github.com/Azure/azure-sdk-for-java/issues/38183
	private ChatCompletionsOptions merge(AzureOpenAiChatOptions springAiOptions, ChatCompletionsOptions azureOptions) {
		if (springAiOptions == null) {
			return azureOptions;
		}

		ChatCompletionsOptions mergedAzureOptions = new ChatCompletionsOptions(azureOptions.getMessages());
		mergedAzureOptions = merge(azureOptions, mergedAzureOptions);

		mergedAzureOptions.setStream(azureOptions.isStream());

		if (springAiOptions.getMaxTokens() != null) {
			mergedAzureOptions.setMaxTokens(springAiOptions.getMaxTokens());
		}

		if (springAiOptions.getLogitBias() != null) {
			mergedAzureOptions.setLogitBias(springAiOptions.getLogitBias());
		}

		if (springAiOptions.getStop() != null) {
			mergedAzureOptions.setStop(springAiOptions.getStop());
		}

		if (springAiOptions.getTemperature() != null && springAiOptions.getTemperature() != null) {
			mergedAzureOptions.setTemperature(springAiOptions.getTemperature().doubleValue());
		}

		if (springAiOptions.getTopP() != null && springAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(springAiOptions.getTopP().doubleValue());
		}

		if (springAiOptions.getFrequencyPenalty() != null && springAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(springAiOptions.getFrequencyPenalty().doubleValue());
		}

		if (springAiOptions.getPresencePenalty() != null && springAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(springAiOptions.getPresencePenalty().doubleValue());
		}

		if (springAiOptions.getN() != null) {
			mergedAzureOptions.setN(springAiOptions.getN());
		}

		if (springAiOptions.getUser() != null) {
			mergedAzureOptions.setUser(springAiOptions.getUser());
		}

		if (springAiOptions.getDeploymentName() != null) {
			mergedAzureOptions.setModel(springAiOptions.getDeploymentName());
		}

		return mergedAzureOptions;
	}

	// https://github.com/Azure/azure-sdk-for-java/blob/azure-ai-openai_1.0.0-beta.6/sdk/openai/azure-ai-openai/src/samples/java/com/azure/ai/openai/usage/GetChatCompletionsToolCallSample.java

	private ChatCompletionsOptions merge(ChatCompletionsOptions fromOptions, ChatCompletionsOptions toOptions) {

		if (fromOptions == null) {
			return toOptions;
		}

		ChatCompletionsOptions mergedOptions = new ChatCompletionsOptions(toOptions.getMessages());
		mergedOptions.setStream(toOptions.isStream());

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

			// Add the function response to the conversation.
			conversationHistory.add(new ChatRequestToolMessage(functionResponse, toolCall.getId()));
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
	protected ChatRequestMessage doGetToolResponseMessage(AccessibleChatCompletions response) {
		final var accessibleChatChoice = response.getChoices().get(0);
		var responseMessage = Optional.ofNullable(accessibleChatChoice.getMessage())
			.orElse(accessibleChatChoice.getDelta());
		ChatRequestAssistantMessage assistantMessage = new ChatRequestAssistantMessage("");
		final var toolCalls = responseMessage.getToolCalls();
		assistantMessage.setToolCalls(toolCalls.stream().map(tc -> {
			final var tc1 = (AccessibleChatCompletionsFunctionToolCall) tc;
			var toDowncast = new ChatCompletionsFunctionToolCall(tc.id,
					new FunctionCall(tc1.function.name, tc1.function.arguments));
			return ((ChatCompletionsToolCall) toDowncast);
		}).toList());
		return assistantMessage;
	}

	@Override
	protected AccessibleChatCompletions doChatCompletion(ChatCompletionsOptions request) {
		return this.openAIClient.getChatCompletions(request.getModel(), request);
	}

	@Override
	protected Flux<AccessibleChatCompletions> doChatCompletionStream(ChatCompletionsOptions request) {
		return this.openAIClient.getChatCompletionsStream(request.getModel(), request);
	}

	@Override
	protected boolean isToolFunctionCall(AccessibleChatCompletions chatCompletions) {

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
