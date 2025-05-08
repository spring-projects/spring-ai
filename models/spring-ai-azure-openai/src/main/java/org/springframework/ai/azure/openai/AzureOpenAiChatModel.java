/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.implementation.accesshelpers.ChatCompletionsOptionsAccessHelper;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletionStreamOptions;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinitionFunction;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormatJsonSchema;
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
import com.azure.ai.openai.models.CompletionsUsage;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.azure.ai.openai.models.FunctionCall;
import com.azure.ai.openai.models.ReasoningEffortValue;
import com.azure.core.util.BinaryData;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.azure.openai.AzureOpenAiResponseFormat.JsonSchema;
import org.springframework.ai.azure.openai.AzureOpenAiResponseFormat.Type;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
 * @author Thomas Vitale
 * @author luocongqiu
 * @author timostark
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @author Berjan Jonker
 * @author Andres da Silva Santos
 * @author Bart Veenstra
 * @see ChatModel
 * @see com.azure.ai.openai.OpenAIClient
 * @since 1.0.0
 */
public class AzureOpenAiChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatModel.class);

	private static final String DEFAULT_DEPLOYMENT_NAME = "gpt-4o";

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	/**
	 * The {@link OpenAIClient} used to interact with the Azure OpenAI service.
	 */
	private final OpenAIClient openAIClient;

	/**
	 * The {@link OpenAIAsyncClient} used for streaming async operations.
	 */
	private final OpenAIAsyncClient openAIAsyncClient;

	/**
	 * The configuration information for a chat completions request.
	 */
	private final AzureOpenAiChatOptions defaultOptions;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * ToolCalling manager used for ToolCalling support.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	public AzureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder, AzureOpenAiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry) {
		this(openAIClientBuilder, defaultOptions, toolCallingManager, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public AzureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder, AzureOpenAiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(openAIClientBuilder, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.openAIClient = openAIClientBuilder.buildClient();
		this.openAIAsyncClient = openAIClientBuilder.buildAsyncClient();
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	public static ChatResponseMetadata from(ChatCompletions chatCompletions, PromptMetadata promptFilterMetadata,
			Usage usage) {
		Assert.notNull(chatCompletions, "Azure OpenAI ChatCompletions must not be null");
		String id = chatCompletions.getId();
		return ChatResponseMetadata.builder()
			.id(id)
			.usage(usage)
			.model(chatCompletions.getModel())
			.promptMetadata(promptFilterMetadata)
			.keyValue("system-fingerprint", chatCompletions.getSystemFingerprint())
			.build();
	}

	public static ChatResponseMetadata from(ChatCompletions chatCompletions, PromptMetadata promptFilterMetadata) {
		Usage usage = (chatCompletions.getUsage() != null) ? getDefaultUsage(chatCompletions.getUsage())
				: new EmptyUsage();
		return from(chatCompletions, promptFilterMetadata, usage);
	}

	public static ChatResponseMetadata from(ChatCompletions chatCompletions, PromptMetadata promptFilterMetadata,
			CompletionsUsage usage) {
		return from(chatCompletions, promptFilterMetadata, getDefaultUsage(usage));
	}

	public static ChatResponseMetadata from(ChatResponse chatResponse, Usage usage) {
		Assert.notNull(chatResponse, "ChatResponse must not be null");
		ChatResponseMetadata chatResponseMetadata = chatResponse.getMetadata();
		ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder();
		builder.id(chatResponseMetadata.getId())
			.usage(usage)
			.model(chatResponseMetadata.getModel())
			.promptMetadata(chatResponseMetadata.getPromptMetadata());
		if (chatResponseMetadata.containsKey("system-fingerprint")) {
			builder.keyValue("system-fingerprint", chatResponseMetadata.get("system-fingerprint"));
		}
		return builder.build();
	}

	private static DefaultUsage getDefaultUsage(CompletionsUsage usage) {
		return new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), usage);
	}

	public AzureOpenAiChatOptions getDefaultOptions() {
		return AzureOpenAiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.AZURE_OPENAI.value())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
				ChatCompletionsOptionsAccessHelper.setStream(options, false);

				ChatCompletions chatCompletions = this.openAIClient.getChatCompletions(options.getModel(), options);
				ChatResponse chatResponse = toChatResponse(chatCompletions, previousChatResponse);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});

		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						response);
			}
		}

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {

		return Flux.deferContextual(contextView -> {
			ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
			ChatCompletionsOptionsAccessHelper.setStream(options, true);

			Flux<ChatCompletions> chatCompletionsStream = this.openAIAsyncClient
				.getChatCompletionsStream(options.getModel(), options);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.AZURE_OPENAI.value())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			final var isFunctionCall = new AtomicBoolean(false);

			final Flux<ChatCompletions> accessibleChatCompletionsFlux = chatCompletionsStream
				// Note: the first chat completions can be ignored when using Azure OpenAI
				// service which is a known service bug.
				// The last element, when using stream_options will contain the usage data
				.filter(chatCompletions -> !CollectionUtils.isEmpty(chatCompletions.getChoices())
						|| chatCompletions.getUsage() != null)
				.map(chatCompletions -> {
					if (!chatCompletions.getChoices().isEmpty()) {
						ChatChoice chatChoice = chatCompletions.getChoices().get(0);
						List<ChatCompletionsToolCall> toolCalls = null;
						if (chatChoice.getDelta() != null) {
							toolCalls = chatChoice.getDelta().getToolCalls();
						}
						isFunctionCall.set(toolCalls != null && !toolCalls.isEmpty());
					}
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
					final var reduce = window.reduce(MergeUtils.emptyChatCompletions(),
							MergeUtils::mergeChatCompletions);
					return List.of(reduce);
				})
				.flatMap(mono -> mono);

			final Flux<ChatResponse> chatResponseFlux = accessibleChatCompletionsFlux.map(chatCompletion -> {
				if (previousChatResponse == null) {
					return toChatResponse(chatCompletion);
				}
				// Accumulate the usage from the previous chat response
				CompletionsUsage usage = chatCompletion.getUsage();
				Usage currentChatResponseUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
						previousChatResponse);
				return toChatResponse(chatCompletion, accumulatedUsage);
			}).buffer(2, 1).map(bufferList -> {
				ChatResponse chatResponse1 = bufferList.get(0);
				if (options.getStreamOptions() != null && options.getStreamOptions().isIncludeUsage()) {
					if (bufferList.size() == 2) {
						ChatResponse chatResponse2 = bufferList.get(1);
						if (chatResponse2 != null && chatResponse2.getMetadata() != null
								&& !UsageCalculator.isEmpty(chatResponse2.getMetadata().getUsage())) {
							return toChatResponse(chatResponse1, chatResponse2.getMetadata().getUsage());
						}
					}
				}
				return chatResponse1;
			});

			return chatResponseFlux.flatMap(chatResponse -> {
				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)) {
					// FIXME: bounded elastic needs to be used since tool calling
					// is currently only synchronous
					return Flux.defer(() -> {
						var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
						if (toolExecutionResult.returnDirect()) {
							// Return tool execution result directly to the client.
							return Flux.just(ChatResponse.builder()
								.from(chatResponse)
								.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
								.build());
						}
						else {
							// Send the tool execution result back to the model.
							return this.internalStream(
									new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
									chatResponse);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}

				Flux<ChatResponse> flux = Flux.just(chatResponse)
					.doOnError(observation::error)
					.doFinally(s -> observation.stop())
					.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

				return new MessageAggregator().aggregate(flux, observationContext::setResponse);
			});

		});

	}

	private ChatResponse toChatResponse(ChatCompletions chatCompletions) {

		List<Generation> generations = nullSafeList(chatCompletions.getChoices()).stream().map(choice -> {
			// @formatter:off
			Map<String, Object> metadata = Map.of(
					"id", chatCompletions.getId() != null ? chatCompletions.getId() : "",
					"choiceIndex", choice.getIndex(),
					"finishReason", choice.getFinishReason() != null ? String.valueOf(choice.getFinishReason()) : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);

		return new ChatResponse(generations, from(chatCompletions, promptFilterMetadata));
	}

	private ChatResponse toChatResponse(ChatCompletions chatCompletions, Usage usage) {

		List<Generation> generations = nullSafeList(chatCompletions.getChoices()).stream().map(choice -> {
			// @formatter:off
			Map<String, Object> metadata = Map.of(
					"id", chatCompletions.getId() != null ? chatCompletions.getId() : "",
					"choiceIndex", choice.getIndex(),
					"finishReason", choice.getFinishReason() != null ? String.valueOf(choice.getFinishReason()) : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);

		return new ChatResponse(generations, from(chatCompletions, promptFilterMetadata, usage));
	}

	private ChatResponse toChatResponse(ChatResponse chatResponse, Usage usage) {
		return new ChatResponse(chatResponse.getResults(), from(chatResponse, usage));
	}

	private ChatResponse toChatResponse(ChatCompletions chatCompletions, ChatResponse previousChatResponse) {

		List<Generation> generations = nullSafeList(chatCompletions.getChoices()).stream().map(choice -> {
			// @formatter:off
			Map<String, Object> metadata = Map.of(
				"id", chatCompletions.getId() != null ? chatCompletions.getId() : "",
				"choiceIndex", choice.getIndex(),
				"finishReason", choice.getFinishReason() != null ? String.valueOf(choice.getFinishReason()) : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);
		Usage currentUsage = null;
		if (chatCompletions.getUsage() != null) {
			currentUsage = getDefaultUsage(chatCompletions.getUsage());
		}
		Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
		return new ChatResponse(generations, from(chatCompletions, promptFilterMetadata, cumulativeUsage));
	}

	private Generation buildGeneration(ChatChoice choice, Map<String, Object> metadata) {

		var responseMessage = Optional.ofNullable(choice.getMessage()).orElse(choice.getDelta());

		List<AssistantMessage.ToolCall> toolCalls = List.of();
		if (responseMessage != null && responseMessage.getToolCalls() != null) {
			toolCalls = responseMessage.getToolCalls().stream().map(toolCall -> {
				final var tc1 = (ChatCompletionsFunctionToolCall) toolCall;
				String id = tc1.getId();
				String name = tc1.getFunction().getName();
				String arguments = tc1.getFunction().getArguments();
				return new AssistantMessage.ToolCall(id, "function", name, arguments);
			}).toList();
		}

		var content = responseMessage == null ? "" : responseMessage.getContent();
		var assistantMessage = new AssistantMessage(content, metadata, toolCalls);
		var generationMetadata = generateChoiceMetadata(choice);

		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Test access.
	 */
	ChatCompletionsOptions toAzureChatCompletionsOptions(Prompt prompt) {

		List<ToolDefinition> functionsForThisRequest = new ArrayList<>();

		List<ChatRequestMessage> azureMessages = prompt.getInstructions()
			.stream()
			.map(this::fromSpringAiMessage)
			.flatMap(List::stream)
			.toList();

		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);

		options = this.merge(options, this.defaultOptions);

		AzureOpenAiChatOptions updatedRuntimeOptions;

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions,
						ToolCallingChatOptions.class, AzureOpenAiChatOptions.class);
			}
			else {
				updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						AzureOpenAiChatOptions.class);
			}
			options = this.merge(updatedRuntimeOptions, options);

			// Add the tool definitions to the request's tools parameter.
			functionsForThisRequest.addAll(this.toolCallingManager.resolveToolDefinitions(updatedRuntimeOptions));
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			List<ChatCompletionsFunctionToolDefinition> tools = this.getFunctionTools(functionsForThisRequest);
			List<ChatCompletionsToolDefinition> tools2 = tools.stream()
				.map(t -> ((ChatCompletionsToolDefinition) t))
				.toList();
			options.setTools(tools2);
		}

		Boolean enableStreamUsage = (prompt.getOptions() instanceof AzureOpenAiChatOptions azureOpenAiChatOptions
				&& azureOpenAiChatOptions.getStreamUsage() != null) ? azureOpenAiChatOptions.getStreamUsage()
						: this.defaultOptions.getStreamUsage();

		if (Boolean.TRUE.equals(enableStreamUsage) && options.getStreamOptions() == null) {
			ChatCompletionsOptionsAccessHelper.setStreamOptions(options,
					new ChatCompletionStreamOptions().setIncludeUsage(true));
		}

		return options;
	}

	private List<ChatCompletionsFunctionToolDefinition> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			ChatCompletionsFunctionToolDefinitionFunction functionDefinition = new ChatCompletionsFunctionToolDefinitionFunction(
					toolDefinition.name());
			functionDefinition.setDescription(toolDefinition.description());
			BinaryData parameters = BinaryData.fromObject(ModelOptionsUtils.jsonToMap(toolDefinition.inputSchema()));
			functionDefinition.setParameters(parameters);
			return new ChatCompletionsFunctionToolDefinition(functionDefinition);
		}).toList();
	}

	private List<ChatRequestMessage> fromSpringAiMessage(Message message) {

		switch (message.getMessageType()) {
			case USER:
				// https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/openai/azure-ai-openai/README.md#text-completions-with-images
				List<ChatMessageContentItem> items = new ArrayList<>();
				items.add(new ChatMessageTextContentItem(message.getText()));
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						items.addAll(userMessage.getMedia()
							.stream()
							.map(media -> new ChatMessageImageContentItem(new ChatMessageImageUrl(getMediaUrl(media))))
							.toList());
					}
				}
				return List.of(new ChatRequestUserMessage(items));
			case SYSTEM:
				return List.of(new ChatRequestSystemMessage(message.getText()));
			case ASSISTANT:
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
				var azureAssistantMessage = new ChatRequestAssistantMessage(message.getText());
				azureAssistantMessage.setToolCalls(toolCalls);
				return List.of(azureAssistantMessage);
			case TOOL:
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses()
					.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatRequestToolMessage(tr.responseData(), tr.id()))
					.map(crtm -> ((ChatRequestMessage) crtm))
					.toList();
			default:
				throw new IllegalArgumentException("Unknown message type " + message.getMessageType());
		}
	}

	private String getMediaUrl(Media media) {
		Object data = media.getData();
		if (data instanceof String dataUrl) {
			return dataUrl;
		}
		else if (data instanceof byte[] dataBytes) {
			String base64EncodedData = Base64.getEncoder().encodeToString(dataBytes);
			return "data:" + media.getMimeType() + ";base64," + base64EncodedData;
		}
		else {
			throw new IllegalArgumentException("Unknown media data type " + data.getClass().getName());
		}
	}

	private ChatGenerationMetadata generateChoiceMetadata(ChatChoice choice) {
		return ChatGenerationMetadata.builder()
			.finishReason(String.valueOf(choice.getFinishReason()))
			.metadata("contentFilterResults", choice.getContentFilterResults())
			.metadata("logprobs", choice.getLogprobs())
			.build();
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

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		AzureOpenAiChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						AzureOpenAiChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						AzureOpenAiChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		AzureOpenAiChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				AzureOpenAiChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setStreamUsage(ModelOptionsUtils.mergeOption(runtimeOptions.getStreamUsage(),
					this.defaultOptions.getStreamUsage()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setStreamUsage(this.defaultOptions.getStreamUsage());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
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

		ChatCompletionsOptionsAccessHelper.setStream(mergedAzureOptions,
				fromAzureOptions.isStream() != null ? fromAzureOptions.isStream() : false);

		ChatCompletionsOptionsAccessHelper.setStreamOptions(mergedAzureOptions,
				fromAzureOptions.getStreamOptions() != null ? fromAzureOptions.getStreamOptions()
						: toSpringAiOptions.getStreamOptions());

		mergedAzureOptions.setMaxTokens((fromAzureOptions.getMaxTokens() != null) ? fromAzureOptions.getMaxTokens()
				: toSpringAiOptions.getMaxTokens());

		mergedAzureOptions.setLogitBias(fromAzureOptions.getLogitBias() != null ? fromAzureOptions.getLogitBias()
				: toSpringAiOptions.getLogitBias());

		mergedAzureOptions
			.setStop(fromAzureOptions.getStop() != null ? fromAzureOptions.getStop() : toSpringAiOptions.getStop());

		mergedAzureOptions.setTemperature(fromAzureOptions.getTemperature());
		if (mergedAzureOptions.getTemperature() == null && toSpringAiOptions.getTemperature() != null) {
			mergedAzureOptions.setTemperature(toSpringAiOptions.getTemperature());
		}

		mergedAzureOptions.setTopP(fromAzureOptions.getTopP());
		if (mergedAzureOptions.getTopP() == null && toSpringAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(toSpringAiOptions.getTopP());
		}

		mergedAzureOptions.setFrequencyPenalty(fromAzureOptions.getFrequencyPenalty());
		if (mergedAzureOptions.getFrequencyPenalty() == null && toSpringAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(toSpringAiOptions.getFrequencyPenalty());
		}

		mergedAzureOptions.setPresencePenalty(fromAzureOptions.getPresencePenalty());
		if (mergedAzureOptions.getPresencePenalty() == null && toSpringAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(toSpringAiOptions.getPresencePenalty());
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

		mergedAzureOptions
			.setSeed(fromAzureOptions.getSeed() != null ? fromAzureOptions.getSeed() : toSpringAiOptions.getSeed());

		mergedAzureOptions.setLogprobs((fromAzureOptions.isLogprobs() != null && fromAzureOptions.isLogprobs())
				|| (toSpringAiOptions.isLogprobs() != null && toSpringAiOptions.isLogprobs()));

		mergedAzureOptions.setTopLogprobs(fromAzureOptions.getTopLogprobs() != null ? fromAzureOptions.getTopLogprobs()
				: toSpringAiOptions.getTopLogProbs());

		mergedAzureOptions.setEnhancements(fromAzureOptions.getEnhancements() != null
				? fromAzureOptions.getEnhancements() : toSpringAiOptions.getEnhancements());

		ReasoningEffortValue reasoningEffort = (fromAzureOptions.getReasoningEffort() != null)
				? fromAzureOptions.getReasoningEffort() : (StringUtils.hasText(toSpringAiOptions.getReasoningEffort())
						? ReasoningEffortValue.fromString(toSpringAiOptions.getReasoningEffort()) : null);

		if (reasoningEffort != null) {
			mergedAzureOptions.setReasoningEffort(reasoningEffort);
		}

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
			mergedAzureOptions.setTemperature(fromSpringAiOptions.getTemperature());
		}

		if (fromSpringAiOptions.getTopP() != null) {
			mergedAzureOptions.setTopP(fromSpringAiOptions.getTopP());
		}

		if (fromSpringAiOptions.getFrequencyPenalty() != null) {
			mergedAzureOptions.setFrequencyPenalty(fromSpringAiOptions.getFrequencyPenalty());
		}

		if (fromSpringAiOptions.getPresencePenalty() != null) {
			mergedAzureOptions.setPresencePenalty(fromSpringAiOptions.getPresencePenalty());
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

		if (fromSpringAiOptions.getSeed() != null) {
			mergedAzureOptions.setSeed(fromSpringAiOptions.getSeed());
		}

		if (fromSpringAiOptions.isLogprobs() != null) {
			mergedAzureOptions.setLogprobs(fromSpringAiOptions.isLogprobs());
		}

		if (fromSpringAiOptions.getTopLogProbs() != null) {
			mergedAzureOptions.setTopLogprobs(fromSpringAiOptions.getTopLogProbs());
		}

		if (fromSpringAiOptions.getEnhancements() != null) {
			mergedAzureOptions.setEnhancements(fromSpringAiOptions.getEnhancements());
		}

		if (fromSpringAiOptions.getStreamOptions() != null) {
			ChatCompletionsOptionsAccessHelper.setStreamOptions(mergedAzureOptions,
					fromSpringAiOptions.getStreamOptions());
		}

		if (fromSpringAiOptions.getEnhancements() != null) {
			mergedAzureOptions.setEnhancements(fromSpringAiOptions.getEnhancements());
		}

		if (StringUtils.hasText(fromSpringAiOptions.getReasoningEffort())) {
			mergedAzureOptions
				.setReasoningEffort(ReasoningEffortValue.fromString(fromSpringAiOptions.getReasoningEffort()));
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

		if (fromOptions.isStream() != null) {
			ChatCompletionsOptionsAccessHelper.setStream(copyOptions, fromOptions.isStream());
		}
		if (fromOptions.getStreamOptions() != null) {
			ChatCompletionsOptionsAccessHelper.setStreamOptions(copyOptions, fromOptions.getStreamOptions());
		}
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
		if (fromOptions.getSeed() != null) {
			copyOptions.setSeed(fromOptions.getSeed());
		}

		copyOptions.setLogprobs(fromOptions.isLogprobs());

		if (fromOptions.getTopLogprobs() != null) {
			copyOptions.setTopLogprobs(fromOptions.getTopLogprobs());
		}

		if (fromOptions.getEnhancements() != null) {
			copyOptions.setEnhancements(fromOptions.getEnhancements());
		}

		if (fromOptions.getReasoningEffort() != null) {
			copyOptions.setReasoningEffort(fromOptions.getReasoningEffort());
		}

		return copyOptions;
	}

	/**
	 * Maps the SpringAI response format to the Azure response format
	 * @param responseFormat SpringAI response format
	 * @return Azure response format
	 */
	private ChatCompletionsResponseFormat toAzureResponseFormat(AzureOpenAiResponseFormat responseFormat) {
		if (responseFormat.getType() == Type.JSON_OBJECT) {
			return new ChatCompletionsJsonResponseFormat();
		}
		if (responseFormat.getType() == Type.JSON_SCHEMA) {
			JsonSchema jsonSchema = responseFormat.getJsonSchema();
			var responseFormatJsonSchema = new ChatCompletionsJsonSchemaResponseFormatJsonSchema(jsonSchema.getName());
			String jsonString = ModelOptionsUtils.toJsonString(jsonSchema.getSchema());
			responseFormatJsonSchema.setSchema(BinaryData.fromString(jsonString));
			return new ChatCompletionsJsonSchemaResponseFormat(responseFormatJsonSchema);
		}
		return new ChatCompletionsTextResponseFormat();
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder to construct {@link AzureOpenAiChatModel}.
	 */
	public static final class Builder {

		private OpenAIClientBuilder openAIClientBuilder;

		private AzureOpenAiChatOptions defaultOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(DEFAULT_DEPLOYMENT_NAME)
			.temperature(DEFAULT_TEMPERATURE)
			.build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder openAIClientBuilder(OpenAIClientBuilder openAIClientBuilder) {
			this.openAIClientBuilder = openAIClientBuilder;
			return this;
		}

		public Builder defaultOptions(AzureOpenAiChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public AzureOpenAiChatModel build() {
			if (this.toolCallingManager != null) {
				return new AzureOpenAiChatModel(this.openAIClientBuilder, this.defaultOptions, this.toolCallingManager,
						this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new AzureOpenAiChatModel(this.openAIClientBuilder, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

}
