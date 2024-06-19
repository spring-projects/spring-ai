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
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatCompletionsResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsTextResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolDefinition;
import com.azure.ai.openai.models.ChatMessageContentItem;
import com.azure.ai.openai.models.ChatMessageImageContentItem;
import com.azure.ai.openai.models.ChatMessageImageUrl;
import com.azure.ai.openai.models.ChatMessageTextContentItem;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestToolMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.azure.ai.openai.models.FunctionCall;
import com.azure.ai.openai.models.FunctionDefinition;
import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * @author timostark
 * @see ChatModel
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiChatModel extends AbstractToolCallSupport implements ChatModel {

	private static final String DEFAULT_DEPLOYMENT_NAME = "gpt-4o";

	private static final Float DEFAULT_TEMPERATURE = 0.7f;

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

	public AzureOpenAiChatOptions getDefaultOptions() {
		return AzureOpenAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(false);

		ChatCompletions chatCompletions = this.openAIClient.getChatCompletions(options.getModel(), options);

		if (isToolFunctionCall(chatCompletions)) {
			List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
					chatCompletions);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallMessageConversation, prompt.getOptions()));
		}

		List<Generation> generations = nullSafeList(chatCompletions.getChoices()).stream()
			.map(choice -> new Generation(choice.getMessage().getContent())
				.withGenerationMetadata(generateChoiceMetadata(choice)))
			.toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);

		return new ChatResponse(generations, from(chatCompletions, promptFilterMetadata));
	}

	public static ChatResponseMetadata from(ChatCompletions chatCompletions, PromptMetadata promptFilterMetadata) {
		Assert.notNull(chatCompletions, "Azure OpenAI ChatCompletions must not be null");
		String id = chatCompletions.getId();
		AzureOpenAiUsage usage = AzureOpenAiUsage.from(chatCompletions);
		ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder()
			.withId(id)
			.withUsage(usage)
			.withModel(chatCompletions.getModel())
			.withPromptMetadata(promptFilterMetadata)
			.withKeyValue("system-fingerprint", chatCompletions.getSystemFingerprint())
			.build();

		return chatResponseMetadata;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(true);

		IterableStream<ChatCompletions> chatCompletionsStream = this.openAIClient
			.getChatCompletionsStream(options.getModel(), options);

		final var isFunctionCall = new AtomicBoolean(false);
		final var accessibleChatCompletionsFlux = Flux.fromIterable(chatCompletionsStream)
			// Note: the first chat completions can be ignored when using Azure OpenAI
			// service which is a known service bug.
			// .skip(1)
			.filter(chatCompletions -> !CollectionUtils.isEmpty(chatCompletions.getChoices()))
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
				return !isFunctionCall.get();
			})
			.concatMapIterable(window -> {
				final var reduce = window.reduce(MergeUtils.emptyChatCompletions(), MergeUtils::mergeChatCompletions);
				return List.of(reduce);
			})
			.flatMap(mono -> mono);

		return accessibleChatCompletionsFlux.switchMap(chatCompletion -> {
			if (isToolFunctionCall(chatCompletion)) {
				List<Message> toolCallMessageConversation = this.handleToolCallRequests(prompt.getInstructions(),
						chatCompletion);
				return this.stream(new Prompt(toolCallMessageConversation, prompt.getOptions()));
			}

			return Mono.just(chatCompletion).flatMapIterable(ChatCompletions::getChoices).map(choice -> {
				var content = Optional.ofNullable(choice.getMessage()).orElse(choice.getDelta()).getContent();
				var generation = new Generation(content).withGenerationMetadata(generateChoiceMetadata(choice));
				return new ChatResponse(List.of(generation));
			});
		});
	}

	private List<Message> handleToolCallRequests(List<Message> previousMessages, ChatCompletions chatCompletion) {

		ChatRequestAssistantMessage nativeAssistantMessage = this.extractAssistantMessage(chatCompletion);

		List<AssistantMessage.ToolCall> assistantToolCalls = nativeAssistantMessage.getToolCalls()
			.stream()
			.map(tc -> (ChatCompletionsFunctionToolCall) tc)
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.getId(), toolCall.getType(),
					toolCall.getFunction().getName(), toolCall.getFunction().getArguments()))
			.toList();

		AssistantMessage assistantMessage = new AssistantMessage(nativeAssistantMessage.getContent(), Map.of(),
				assistantToolCalls);

		ToolResponseMessage toolResponseMessage = this.executeFuncitons(assistantMessage);

		// History
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);

		return messages;
	}

	private ChatRequestAssistantMessage extractAssistantMessage(ChatCompletions response) {
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

	/**
	 * Test access.
	 */
	ChatCompletionsOptions toAzureChatCompletionsOptions(Prompt prompt) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<ChatRequestMessage> azureMessages = prompt.getInstructions()
			.stream()
			.map(this::fromSpringAiMessage)
			.flatMap(List::stream)
			.toList();

		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);

		if (this.defaultOptions != null) {

			options = this.merge(options, this.defaultOptions);

			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(defaultEnabledFunctions);
		}

		if (prompt.getOptions() != null) {
			AzureOpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, AzureOpenAiChatOptions.class);
			options = this.merge(updatedRuntimeOptions, options);

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

	private List<ChatRequestMessage> fromSpringAiMessage(Message message) {

		switch (message.getMessageType()) {
			case USER:
				// https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/openai/azure-ai-openai/README.md#text-completions-with-images
				List<ChatMessageContentItem> items = new ArrayList<>();
				items.add(new ChatMessageTextContentItem(message.getContent()));
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						items.addAll(userMessage.getMedia()
							.stream()
							.map(media -> new ChatMessageImageContentItem(
									new ChatMessageImageUrl(media.getData().toString())))
							.toList());
					}
				}
				return List.of(new ChatRequestUserMessage(items));
			case SYSTEM:
				return List.of(new ChatRequestSystemMessage(message.getContent()));
			case ASSISTANT: {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				List<ChatCompletionsToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new FunctionCall(toolCall.name(), toolCall.arguments());
						return new ChatCompletionsFunctionToolCall(toolCall.id(), function);
					})
						.map(tc -> ((ChatCompletionsToolCall) tc)) // !!!
						.toList();
				}
				var azureAssistantMessage = new ChatRequestAssistantMessage(message.getContent());
				azureAssistantMessage.setToolCalls(toolCalls);
				return List.of(azureAssistantMessage);
			}
			case TOOL: {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
					Assert.isTrue(response.name() != null, "ToolResponseMessage must have a name");
				});

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatRequestToolMessage(tr.responseData(), tr.id()))
					.map(crtm -> ((ChatRequestMessage) crtm))
					.toList();
			}
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
