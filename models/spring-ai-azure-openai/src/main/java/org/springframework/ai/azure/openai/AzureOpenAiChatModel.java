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
import org.springframework.ai.azure.openai.metadata.AzureOpenAiChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractToolCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ChatModel} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Mark Pollack
 * @author Ueibin Kim
 * @author John Blum
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Benoit Moussaud
 * @author luocongqiu
 * @see ChatModel
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiChatModel extends AbstractToolCallSupport<ChatCompletions> implements ChatModel {

	private static final String DEFAULT_DEPLOYMENT_NAME = "gpt-35-turbo";

	private static final Float DEFAULT_TEMPERATURE = 0.7f;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The {@link OpenAIClient} used to interact with the Azure OpenAI service.
	 */
	private final OpenAIClient openAIClient;

	/**
	 * The configuration information for a chat completions request.
	 */
	private AzureOpenAiChatOptions defaultOptions;

	public AzureOpenAiChatModel(OpenAIClient microsoftOpenAiClient) {
		this(microsoftOpenAiClient,
				AzureOpenAiChatOptions.builder()
					.withDeploymentName(DEFAULT_DEPLOYMENT_NAME)
					.withTemperature(DEFAULT_TEMPERATURE)
					.build());
	}

	public AzureOpenAiChatModel(OpenAIClient microsoftOpenAiClient, AzureOpenAiChatOptions options) {
		this(microsoftOpenAiClient, options, null);
	}

	public AzureOpenAiChatModel(OpenAIClient microsoftOpenAiClient, AzureOpenAiChatOptions options,
			FunctionCallbackContext functionCallbackContext) {
		super(functionCallbackContext);
		Assert.notNull(microsoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(options, "AzureOpenAiChatOptions must not be null");
		this.openAIClient = microsoftOpenAiClient;
		this.defaultOptions = options;
	}

	/**
	 * @deprecated since 0.8.0, use
	 * {@link #AzureOpenAiChatModel(OpenAIClient, AzureOpenAiChatOptions)} instead.
	 */
	@Deprecated(forRemoval = true, since = "0.8.0")
	public AzureOpenAiChatModel withDefaultOptions(AzureOpenAiChatOptions defaultOptions) {
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		this.defaultOptions = defaultOptions;
		return this;
	}

	public AzureOpenAiChatOptions getDefaultOptions() {
		return AzureOpenAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(false);

		logger.trace("Azure ChatCompletionsOptions: {}", options);
		ChatCompletions chatCompletion = this.openAIClient.getChatCompletions(options.getModel(), options);
		logger.trace("Azure ChatCompletions: {}", chatCompletion);

		if (isToolFunctionCall(chatCompletion)) {
			List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
					chatCompletion);

			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallMessageConversation, prompt.getOptions()));
		}

		List<Generation> generations = nullSafeList(chatCompletion.getChoices()).stream()
			.map(choice -> new Generation(choice.getMessage().getContent())
				.withGenerationMetadata(generateChoiceMetadata(choice)))
			.toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletion);

		return new ChatResponse(generations,
				AzureOpenAiChatResponseMetadata.from(chatCompletion, promptFilterMetadata));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(true);

		Flux<ChatCompletions> completionChunks = Flux
			.fromIterable(this.openAIClient.getChatCompletionsStream(options.getModel(), options));

		// For chunked responses, only the first chunk contains the choice role.
		// The rest of the chunks with same ID share the same role.
		ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

		final ChatCompletions[] currentChatCompletion = { MergeUtils.emptyChatCompletions() };
		return completionChunks.filter(this::filterNotRelevantDeltaChunks).switchMap(chatCompletion -> {
			currentChatCompletion[0] = MergeUtils.mergeChatCompletions(currentChatCompletion[0], chatCompletion);

			if (this.isToolFunctionCall(currentChatCompletion[0])) {
				var toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
						currentChatCompletion[0]);

				// Recursively call the stream method with the tool call message
				// conversation that contains the call responses.
				return this.stream(new Prompt(toolCallMessageConversation, prompt.getOptions()));
			}

			// Non function calling.
			return Mono.just(chatCompletion).map(chatCompletion2 -> {
				try {
					@SuppressWarnings("null")
					String id = chatCompletion2.getId();

					List<Generation> generations = chatCompletion2.getChoices().stream().map(choice -> {
						if (choice.getDelta().getRole() != null) {
							roleMap.putIfAbsent(id, choice.getDelta().getRole().toString());
						}
						String finish = (choice.getFinishReason() != null ? choice.getFinishReason().toString() : "");
						var generation = new Generation(Optional.ofNullable(choice.getDelta().getContent()).orElse(""),
								Map.of("id", id, "role", roleMap.getOrDefault(id, ""), "finishReason", finish));
						if (choice.getFinishReason() != null) {
							generation = generation.withGenerationMetadata(
									ChatGenerationMetadata.from(choice.getFinishReason().toString(), null));
						}
						return generation;
					}).toList();

					if (chatCompletion2.getUsage() != null) {
						return new ChatResponse(generations, AzureOpenAiChatResponseMetadata.from(chatCompletion2,
								generatePromptMetadata(chatCompletion2)));
					}
					else {
						return new ChatResponse(generations);
					}
				}
				catch (Exception e) {
					logger.error("Error processing chat completion", e);
					return new ChatResponse(List.of());
				}
			});
		});
	}

	private boolean filterNotRelevantDeltaChunks(ChatCompletions chatCompletion) {
		if (chatCompletion.getChoices() == null || chatCompletion.getChoices().isEmpty()) {
			return false;
		}
		return chatCompletion.getChoices().stream().anyMatch(choice -> {
			if (choice.getFinishReason() != null) {
				return true;
			}

			if (choice.getDelta() == null) {
				return false;
			}

			return choice.getDelta().getContent() != null
					|| (choice.getDelta().getToolCalls() != null && !choice.getDelta().getToolCalls().isEmpty());
		});
	}

	private ChatResponseMessage extractAssistantMessage(ChatCompletions chatCompletion) {
		return Optional.ofNullable(chatCompletion.getChoices().iterator().next().getMessage())
			.orElse(chatCompletion.getChoices().iterator().next().getDelta());
	}

	private List<Message> handleToolCallRequests(List<Message> previousMessages, ChatCompletions chatCompletion) {
		ChatResponseMessage nativeAssistantMessage = this.extractAssistantMessage(chatCompletion);

		List<AssistantMessage.ToolCall> assistantToolCalls = nativeAssistantMessage.getToolCalls()
			.stream()
			.filter(x -> x instanceof ChatCompletionsFunctionToolCall)
			.map(x -> (ChatCompletionsFunctionToolCall) x)
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.getId(), "function",
					toolCall.getFunction().getName(), toolCall.getFunction().getArguments()))
			.toList();

		AssistantMessage assistantMessage = new AssistantMessage(nativeAssistantMessage.getContent(), Map.of(),
				assistantToolCalls);

		List<ToolResponseMessage> toolResponseMessages = this.executeFuncitons(assistantMessage);

		// History
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.addAll(toolResponseMessages);

		return messages;
	}

	List<ChatRequestMessage> toAzureChatMessage(List<Message> messages) {
		return messages.stream().map(this::fromSpringAiMessage).toList();
	}

	/**
	 * Test access.
	 */
	ChatCompletionsOptions toAzureChatCompletionsOptions(Prompt prompt) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<ChatRequestMessage> azureMessages = toAzureChatMessage(prompt.getInstructions());

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
			AzureOpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
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
				// https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/openai/azure-ai-openai/README.md#text-completions-with-images
				List<ChatMessageContentItem> items = new ArrayList<>();
				items.add(new ChatMessageTextContentItem(message.getContent()));
				if (!CollectionUtils.isEmpty(message.getMedia())) {
					items.addAll(message.getMedia()
						.stream()
						.map(media -> new ChatMessageImageContentItem(
								new ChatMessageImageUrl(media.getData().toString())))
						.toList());
				}
				return new ChatRequestUserMessage(items);
			case SYSTEM:
				return new ChatRequestSystemMessage(message.getContent());
			case ASSISTANT:
				var assistantMessage = (AssistantMessage) message;
				List<ChatCompletionsToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls()
						.stream()
						.map(toolCall -> (ChatCompletionsToolCall) new ChatCompletionsFunctionToolCall(toolCall.id(),
								new FunctionCall(toolCall.name(), toolCall.arguments())))
						.toList();
				}
				var azureAssistantMessage = new ChatRequestAssistantMessage(message.getContent());
				if (toolCalls != null) {
					azureAssistantMessage.setToolCalls(toolCalls);
				}
				return azureAssistantMessage;
			case TOOL:
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;
				return new ChatRequestToolMessage(message.getContent(), toolMessage.getId());
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

		mergedAzureOptions.setResponseFormat(fromAzureOptions.getResponseFormat());
		if (mergedAzureOptions.getResponseFormat() == null && toSpringAiOptions.getResponseFormat() != null) {
			mergedAzureOptions.setResponseFormat(toAzureResponseFormat(toSpringAiOptions.getResponseFormat()));
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

		if (fromSpringAiOptions.getResponseFormat() != null) {
			mergedAzureOptions.setResponseFormat(toAzureResponseFormat(fromSpringAiOptions.getResponseFormat()));
		}

		return mergedAzureOptions;
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
		if (fromOptions.getResponseFormat() != null) {
			copyOptions.setResponseFormat(fromOptions.getResponseFormat());
		}

		return copyOptions;
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

	/**
	 * Maps the SpringAI response format to the Azure response format
	 * @param responseFormat SpringAI response format
	 * @return Azure response format
	 */
	private ChatCompletionsResponseFormat toAzureResponseFormat(AzureOpenAiResponseFormat responseFormat) {
		if (responseFormat == AzureOpenAiResponseFormat.JSON) {
			return new ChatCompletionsJsonResponseFormat();
		}
		return new ChatCompletionsTextResponseFormat();
	}

}
