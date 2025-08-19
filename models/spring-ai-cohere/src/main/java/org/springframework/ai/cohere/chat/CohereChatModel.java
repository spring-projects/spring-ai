package org.springframework.ai.cohere.chat;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Represents a Cohere Chat Model.
 *
 * @author Ricken Bazolo
 */
public class CohereChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private final CohereChatOptions defaultOptions;

	/**
	 * Low-level access to the Cohere API.
	 */
	private final CohereApi cohereApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public CohereChatModel(CohereApi cohereApi, CohereChatOptions defaultOptions,
						   ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
						   ObservationRegistry observationRegistry) {
		this(cohereApi, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public CohereChatModel(CohereApi cohereApi, CohereChatOptions defaultOptions,
						   ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
						   ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(cohereApi, "cohereApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.cohereApi = cohereApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	public static ChatResponseMetadata from(CohereApi.ChatCompletion result) {
		Assert.notNull(result, "Cohere ChatCompletion must not be null");
		DefaultUsage usage = getDefaultUsage(result.usage());
		return ChatResponseMetadata.builder()
				.id(result.id())
				.usage(usage)
				.build();
	}

	public static ChatResponseMetadata from(CohereApi.ChatCompletion result, Usage usage) {
		Assert.notNull(result, "Cohere ChatCompletion must not be null");
		return ChatResponseMetadata.builder()
				.id(result.id())
				.usage(usage)
				.build();
	}

	private static DefaultUsage getDefaultUsage(CohereApi.Usage usage) {
		return new DefaultUsage(null, null, null, usage);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return Flux.error(new UnsupportedOperationException("Streaming is not supported yet"));
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		CohereChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						CohereChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						CohereChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		CohereChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				CohereChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(CohereApi.PROVIDER_NAME)
				.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry)
				.observe(() -> {

					ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
							.execute(ctx -> this.cohereApi.chatCompletionEntity(request));

					ChatCompletion chatCompletion = completionEntity.getBody();

					if (chatCompletion == null) {
						logger.warn("No chat completion returned for prompt: {}", prompt);
						return new ChatResponse(List.of());
					}

					List<Generation> generations = chatCompletion.message().content().stream().map(content -> {
						Map<String, Object> metadata = Map.of(
								"id", chatCompletion.id() != null ? chatCompletion.id() : "",
								"role", chatCompletion.message().role() != null ? chatCompletion.message().role().name() : "",
								"finishReason", chatCompletion.finishReason() != null ? chatCompletion.finishReason().name() : "");
						return buildGeneration(content, chatCompletion, metadata);
					}).toList();

					DefaultUsage usage = getDefaultUsage(completionEntity.getBody().usage());
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(usage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations,
							from(completionEntity.getBody(), cumulativeUsage));

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

	private Generation buildGeneration(ChatCompletionMessage.MessageContent content, ChatCompletion completion, Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = completion.message().toolCalls() == null ? List.of()
				: completion.message()
				.toolCalls()
				.stream()
				.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function",
						toolCall.function().name(), toolCall.function().arguments()))
				.toList();

		var assistantMessage = new AssistantMessage(content.text(), metadata, toolCalls);
		String finishReason = (completion.finishReason() != null ? completion.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Accessible for testing.
	 */
	CohereApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message instanceof UserMessage userMessage) {
				Object content = message.getText();

				if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
					List<ChatCompletionMessage.MediaContent> contentList = new ArrayList<>(
							List.of(new ChatCompletionMessage.MediaContent(message.getText())));

					contentList.addAll(userMessage.getMedia().stream().map(this::mapToMediaContent).toList());

					content = contentList;
				}

				return List
						.of(new ChatCompletionMessage(content, Role.USER));
			}
			else if (message instanceof SystemMessage systemMessage) {
				return List.of(new ChatCompletionMessage(systemMessage.getText(),
						Role.SYSTEM));
			}
			else if (message instanceof AssistantMessage assistantMessage) {
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function, null);
					}).toList();
				}

				return List.of(new ChatCompletionMessage(assistantMessage.getText(),
						Role.ASSISTANT, toolCalls));
			}
			else if (message instanceof ToolResponseMessage toolResponseMessage) {
				toolResponseMessage.getResponses()
						.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

				return toolResponseMessage.getResponses()
						.stream()
						.map(toolResponse -> new ChatCompletionMessage(toolResponse.responseData(),
								Role.TOOL))
						.toList();
			}
			else {
				throw new IllegalStateException("Unexpected message type: " + message);
			}
		}).flatMap(List::stream).toList();

		var request = new ChatCompletionRequest(chatCompletionMessages, stream);

		CohereChatOptions requestOptions = (CohereChatOptions) prompt.getOptions();
		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(
					CohereChatOptions.builder().tools(this.getFunctionTools(toolDefinitions)).build(), request,
					ChatCompletionRequest.class);
		}

		return request;
	}

	private ChatCompletionMessage.MediaContent mapToMediaContent(Media media) {
		return new ChatCompletionMessage.MediaContent(new ChatCompletionMessage.MediaContent.ImageUrl(
				this.fromMediaData(media.getMimeType(), media.getData())));
	}

	private String fromMediaData(MimeType mimeType, Object mediaContentData) {
		if (mediaContentData instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64 encoded
			// following the prefix pattern.
			return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
		}
		else if (mediaContentData instanceof String text) {
			// Assume the text is a URLs or a base64 encoded image prefixed by the user.
			return text;
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
		}
	}

	private List<FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new FunctionTool(function);
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return CohereChatOptions.fromOptions(this.defaultOptions);
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

	public static final class Builder {

		private CohereApi cohereApi;

		private CohereChatOptions defaultOptions = CohereChatOptions.builder()
				.temperature(0.3)
				.topP(1.0)
				.model(CohereApi.ChatModel.COMMAND_R7B.getValue())
				.build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder cohereApi(CohereApi cohereApi) {
			this.cohereApi = cohereApi;
			return this;
		}

		public Builder defaultOptions(CohereChatOptions defaultOptions) {
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

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public CohereChatModel build() {
			if (this.toolCallingManager != null) {
				return new CohereChatModel(this.cohereApi, this.defaultOptions, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new CohereChatModel(this.cohereApi, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

}
