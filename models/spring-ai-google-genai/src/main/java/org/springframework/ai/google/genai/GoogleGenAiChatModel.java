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

package org.springframework.ai.google.genai;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
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
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.google.genai.common.GoogleGenAiConstants;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.ai.google.genai.schema.GoogleGenAiToolCallingManager;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.lang.NonNull;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Google GenAI Chat Model implementation that provides access to Google's Gemini language
 * models.
 *
 * <p>
 * Key features include:
 * <ul>
 * <li>Support for multiple Gemini model versions including Gemini Pro, Gemini 1.5 Pro,
 * Gemini 1.5/2.0 Flash variants</li>
 * <li>Tool/Function calling capabilities through {@link ToolCallingManager}</li>
 * <li>Streaming support via {@link #stream(Prompt)} method</li>
 * <li>Configurable safety settings through {@link GoogleGenAiSafetySetting}</li>
 * <li>Support for system messages and multi-modal content (text and images)</li>
 * <li>Built-in retry mechanism and observability through Micrometer</li>
 * <li>Google Search Retrieval integration</li>
 * </ul>
 *
 * <p>
 * The model can be configured with various options including temperature, top-k, top-p
 * sampling, maximum output tokens, and candidate count through
 * {@link GoogleGenAiChatOptions}.
 *
 * <p>
 * Use the {@link Builder} to create instances with custom configurations:
 *
 * <pre>{@code
 * GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
 * 		.genAiClient(genAiClient)
 * 		.defaultOptions(options)
 * 		.toolCallingManager(toolManager)
 * 		.build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Grogdunn
 * @author luocongqiu
 * @author Chris Turchin
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @author Dan Dobrin
 * @since 0.8.1
 * @see GoogleGenAiChatOptions
 * @see ToolCallingManager
 * @see ChatModel
 */
public class GoogleGenAiChatModel implements ChatModel, DisposableBean {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Client genAiClient;

	private final GoogleGenAiChatOptions defaultOptions;

	/**
	 * The retry template used to retry the API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * The cached content service for managing cached content.
	 */
	private final GoogleGenAiCachedContentService cachedContentService;

	// GenerationConfig is now built dynamically per request

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Tool calling manager used to call tools.
	 */
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

	/**
	 * Creates a new instance of GoogleGenAiChatModel.
	 * @param genAiClient the GenAI Client instance to use
	 * @param defaultOptions the default options to use
	 * @param toolCallingManager the tool calling manager to use. It is wrapped in a
	 * {@link GoogleGenAiToolCallingManager} to ensure compatibility with Vertex AI's
	 * OpenAPI schema format.
	 * @param retryTemplate the retry template to use
	 * @param observationRegistry the observation registry to use
	 */
	public GoogleGenAiChatModel(Client genAiClient, GoogleGenAiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		this(genAiClient, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Creates a new instance of GoogleGenAiChatModel.
	 * @param genAiClient the GenAI Client instance to use
	 * @param defaultOptions the default options to use
	 * @param toolCallingManager the tool calling manager to use. It is wrapped in a
	 * {@link GoogleGenAiToolCallingManager} to ensure compatibility with Vertex AI's
	 * OpenAPI schema format.
	 * @param retryTemplate the retry template to use
	 * @param observationRegistry the observation registry to use
	 * @param toolExecutionEligibilityPredicate the tool execution eligibility predicate
	 */
	public GoogleGenAiChatModel(Client genAiClient, GoogleGenAiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		Assert.notNull(genAiClient, "GenAI Client must not be null");
		Assert.notNull(defaultOptions, "GoogleGenAiChatOptions must not be null");
		Assert.notNull(defaultOptions.getModel(), "GoogleGenAiChatOptions.modelName must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(toolCallingManager, "ToolCallingManager must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "ToolExecutionEligibilityPredicate must not be null");

		this.genAiClient = genAiClient;
		this.defaultOptions = defaultOptions;
		// GenerationConfig is now created per request
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
		// Initialize cached content service only if the client supports it
		this.cachedContentService = (genAiClient != null && genAiClient.caches != null && genAiClient.async != null
				&& genAiClient.async.caches != null) ? new GoogleGenAiCachedContentService(genAiClient) : null;

		// Wrap the provided tool calling manager in a GoogleGenAiToolCallingManager to
		// ensure
		// compatibility with Vertex AI's OpenAPI schema format.
		if (toolCallingManager instanceof GoogleGenAiToolCallingManager) {
			this.toolCallingManager = toolCallingManager;
		}
		else {
			this.toolCallingManager = new GoogleGenAiToolCallingManager(toolCallingManager);
		}
	}

	private static GeminiMessageType toGeminiMessageType(@NonNull MessageType type) {

		Assert.notNull(type, "Message type must not be null");

		return switch (type) {
			case SYSTEM, USER, TOOL -> GeminiMessageType.USER;
			case ASSISTANT -> GeminiMessageType.MODEL;
			default -> throw new IllegalArgumentException("Unsupported message type: " + type);
		};
	}

	static List<Part> messageToGeminiParts(Message message) {

		if (message instanceof SystemMessage systemMessage) {

			List<Part> parts = new ArrayList<>();

			if (systemMessage.getText() != null) {
				parts.add(Part.fromText(systemMessage.getText()));
			}

			return parts;
		}
		else if (message instanceof UserMessage userMessage) {
			List<Part> parts = new ArrayList<>();
			if (userMessage.getText() != null) {
				parts.add(Part.fromText(userMessage.getText()));
			}

			parts.addAll(mediaToParts(userMessage.getMedia()));

			return parts;
		}
		else if (message instanceof AssistantMessage assistantMessage) {
			List<Part> parts = new ArrayList<>();

			// Check if there are thought signatures to restore.
			// Per Google's documentation, thought signatures must be attached to the
			// first functionCall part in each step of the current turn.
			// See: https://ai.google.dev/gemini-api/docs/thought-signatures
			List<byte[]> thoughtSignatures = null;
			if (assistantMessage.getMetadata() != null
					&& assistantMessage.getMetadata().containsKey("thoughtSignatures")) {
				Object signaturesObj = assistantMessage.getMetadata().get("thoughtSignatures");
				if (signaturesObj instanceof List) {
					thoughtSignatures = new ArrayList<>((List<byte[]>) signaturesObj);
				}
			}

			// Add text part (without thought signature - signatures go on functionCall
			// parts)
			if (StringUtils.hasText(assistantMessage.getText())) {
				parts.add(Part.builder().text(assistantMessage.getText()).build());
			}

			// Add function call parts with thought signatures attached.
			// Per Google's docs: "The first functionCall part in each step of the
			// current turn must include its thought_signature."
			if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
				List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
				for (int i = 0; i < toolCalls.size(); i++) {
					AssistantMessage.ToolCall toolCall = toolCalls.get(i);
					Part.Builder partBuilder = Part.builder()
						.functionCall(FunctionCall.builder()
							.name(toolCall.name())
							.args(parseJsonToMap(toolCall.arguments()))
							.build());

					// Attach thought signature to function call part if available
					if (thoughtSignatures != null && !thoughtSignatures.isEmpty()) {
						partBuilder.thoughtSignature(thoughtSignatures.remove(0));
					}

					parts.add(partBuilder.build());
				}
			}

			return parts;
		}
		else if (message instanceof ToolResponseMessage toolResponseMessage) {

			return toolResponseMessage.getResponses()
				.stream()
				.map(response -> Part.builder()
					.functionResponse(FunctionResponse.builder()
						.name(response.name())
						.response(parseJsonToMap(response.responseData()))
						.build())
					.build())
				.toList();
		}
		else {
			throw new IllegalArgumentException("Gemini doesn't support message type: " + message.getClass());
		}
	}

	private static List<Part> mediaToParts(Collection<Media> media) {
		List<Part> parts = new ArrayList<>();

		List<Part> mediaParts = media.stream().map(mediaData -> {
			Object data = mediaData.getData();
			String mimeType = mediaData.getMimeType().toString();

			if (data instanceof byte[]) {
				return Part.fromBytes((byte[]) data, mimeType);
			}
			else if (data instanceof URI || data instanceof String) {
				// Handle URI or String URLs
				String uri = data.toString();
				return Part.fromUri(uri, mimeType);
			}
			else {
				throw new IllegalArgumentException("Unsupported media data type: " + data.getClass());
			}
		}).toList();

		if (!CollectionUtils.isEmpty(mediaParts)) {
			parts.addAll(mediaParts);
		}

		return parts;
	}

	// Helper methods for JSON/Map conversion
	private static Map<String, Object> parseJsonToMap(String json) {
		try {
			// First, try to parse as an array
			Object parsed = ModelOptionsUtils.OBJECT_MAPPER.readValue(json, Object.class);
			if (parsed instanceof List) {
				// It's an array, wrap it in a map with "result" key
				Map<String, Object> wrapper = new HashMap<>();
				wrapper.put("result", parsed);
				return wrapper;
			}
			else if (parsed instanceof Map) {
				// It's already a map, return it
				return (Map<String, Object>) parsed;
			}
			else {
				// It's a primitive or other type, wrap it
				Map<String, Object> wrapper = new HashMap<>();
				wrapper.put("result", parsed);
				return wrapper;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse JSON: " + json, e);
		}
	}

	private static String mapToJson(Map<String, Object> map) {
		try {
			return ModelOptionsUtils.OBJECT_MAPPER.writeValueAsString(map);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert map to JSON", e);
		}
	}

	private static Schema jsonToSchema(String json) {
		try {
			// Parse JSON into Schema using OBJECT_MAPPER
			return ModelOptionsUtils.OBJECT_MAPPER.readValue(json, Schema.class);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/GenerationConfig.html
	@Override
	public ChatResponse call(Prompt prompt) {
		var requestPrompt = this.buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(GoogleGenAiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.retryTemplate.execute(context -> {

				var geminiRequest = createGeminiRequest(prompt);

				GenerateContentResponse generateContentResponse = this.getContentResponse(geminiRequest);

				List<Generation> generations = generateContentResponse.candidates()
					.orElse(List.of())
					.stream()
					.map(this::responseCandidateToGeneration)
					.flatMap(List::stream)
					.toList();

				var usage = generateContentResponse.usageMetadata();
				GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
				Usage currentUsage = (usage.isPresent()) ? getDefaultUsage(usage.get(), options)
						: getDefaultUsage(null, options);
				Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations,
						toChatResponseMetadata(cumulativeUsage, generateContentResponse.modelVersion().get()));

				observationContext.setResponse(chatResponse);
				return chatResponse;
			}));

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

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		GoogleGenAiChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						GoogleGenAiChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						GoogleGenAiChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		GoogleGenAiChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				GoogleGenAiChatOptions.class);

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

			requestOptions.setGoogleSearchRetrieval(ModelOptionsUtils.mergeOption(
					runtimeOptions.getGoogleSearchRetrieval(), this.defaultOptions.getGoogleSearchRetrieval()));
			requestOptions.setSafetySettings(ModelOptionsUtils.mergeOption(runtimeOptions.getSafetySettings(),
					this.defaultOptions.getSafetySettings()));
			requestOptions
				.setLabels(ModelOptionsUtils.mergeOption(runtimeOptions.getLabels(), this.defaultOptions.getLabels()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());

			requestOptions.setGoogleSearchRetrieval(this.defaultOptions.getGoogleSearchRetrieval());
			requestOptions.setSafetySettings(this.defaultOptions.getSafetySettings());
			requestOptions.setLabels(this.defaultOptions.getLabels());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var requestPrompt = this.buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(GoogleGenAiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			var request = createGeminiRequest(prompt);

			try {
				ResponseStream<GenerateContentResponse> responseStream = this.genAiClient.models
					.generateContentStream(request.modelName, request.contents, request.config);

				Flux<ChatResponse> chatResponseFlux = Flux.fromIterable(responseStream).switchMap(response -> {
					List<Generation> generations = response.candidates()
						.orElse(List.of())
						.stream()
						.map(this::responseCandidateToGeneration)
						.flatMap(List::stream)
						.toList();

					var usage = response.usageMetadata();
					GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
					Usage currentUsage = usage.isPresent() ? getDefaultUsage(usage.get(), options)
							: getDefaultUsage(null, options);
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations,
							toChatResponseMetadata(cumulativeUsage, response.modelVersion().get()));
					return Flux.just(chatResponse);
				});

				// @formatter:off
				Flux<ChatResponse> flux = chatResponseFlux.flatMap(response -> {
					if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
						// FIXME: bounded elastic needs to be used since tool calling
						//  is currently only synchronous
						return Flux.deferContextual(ctx -> {
							ToolExecutionResult toolExecutionResult;
							try {
								ToolCallReactiveContextHolder.setContext(ctx);
								toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
							}
							finally {
								ToolCallReactiveContextHolder.clearContext();
							}
							if (toolExecutionResult.returnDirect()) {
								// Return tool execution result directly to the client.
								return Flux.just(ChatResponse.builder().from(response)
										.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
										.build());
							}
							else {
								// Send the tool execution result back to the model.
								return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
							}
						}).subscribeOn(Schedulers.boundedElastic());
					}
					else {
						return Flux.just(response);
					}
				})
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				// @formatter:on;

				return new MessageAggregator().aggregate(flux, observationContext::setResponse);

			}
			catch (Exception e) {
				throw new RuntimeException("Failed to generate content", e);
			}

		});
	}

	protected List<Generation> responseCandidateToGeneration(Candidate candidate) {

		// TODO - The candidateIndex (e.g. choice must be assigned to the generation).
		int candidateIndex = candidate.index().orElse(0);
		FinishReason candidateFinishReason = candidate.finishReason().orElse(new FinishReason(FinishReason.Known.STOP));

		Map<String, Object> messageMetadata = new HashMap<>();
		messageMetadata.put("candidateIndex", candidateIndex);
		messageMetadata.put("finishReason", candidateFinishReason);

		// Extract thought signatures from response parts if present
		if (candidate.content().isPresent() && candidate.content().get().parts().isPresent()) {
			List<Part> parts = candidate.content().get().parts().get();
			List<byte[]> thoughtSignatures = parts.stream()
				.filter(part -> part.thoughtSignature().isPresent())
				.map(part -> part.thoughtSignature().get())
				.toList();

			if (!thoughtSignatures.isEmpty()) {
				messageMetadata.put("thoughtSignatures", thoughtSignatures);
			}
		}

		ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.builder()
			.finishReason(candidateFinishReason.toString())
			.build();

		List<Part> parts = candidate.content().get().parts().orElse(List.of());

		List<AssistantMessage.ToolCall> assistantToolCalls = parts.stream()
			.filter(part -> part.functionCall().isPresent())
			.map(part -> {
				FunctionCall functionCall = part.functionCall().get();
				var functionName = functionCall.name().orElse("");
				String functionArguments = mapToJson(functionCall.args().orElse(Map.of()));
				return new AssistantMessage.ToolCall("", "function", functionName, functionArguments);
			})
			.toList();

		String text = parts.stream()
			.filter(part -> part.text().isPresent() && !part.text().get().isEmpty())
			.map(part -> part.text().get())
			.collect(Collectors.joining(" "));

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(text)
			.properties(messageMetadata)
			.toolCalls(assistantToolCalls)
			.build();

		return List.of(new Generation(assistantMessage, chatGenerationMetadata));
	}

	private ChatResponseMetadata toChatResponseMetadata(Usage usage, String modelVersion) {
		return ChatResponseMetadata.builder().usage(usage).model(modelVersion).build();
	}

	private Usage getDefaultUsage(com.google.genai.types.GenerateContentResponseUsageMetadata usageMetadata,
			GoogleGenAiChatOptions options) {
		// Check if extended metadata should be included (default to true if not
		// configured)
		boolean includeExtended = true;
		if (options != null && options.getIncludeExtendedUsageMetadata() != null) {
			includeExtended = options.getIncludeExtendedUsageMetadata();
		}
		else if (this.defaultOptions.getIncludeExtendedUsageMetadata() != null) {
			includeExtended = this.defaultOptions.getIncludeExtendedUsageMetadata();
		}

		if (includeExtended) {
			return GoogleGenAiUsage.from(usageMetadata);
		}
		else {
			// Fall back to basic usage for backward compatibility
			return new DefaultUsage(usageMetadata.promptTokenCount().orElse(0),
					usageMetadata.candidatesTokenCount().orElse(0), usageMetadata.totalTokenCount().orElse(0));
		}
	}

	GeminiRequest createGeminiRequest(Prompt prompt) {

		GoogleGenAiChatOptions requestOptions = (GoogleGenAiChatOptions) prompt.getOptions();

		// Build GenerateContentConfig
		GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

		String modelName = requestOptions.getModel() != null ? requestOptions.getModel()
				: this.defaultOptions.getModel();

		// Set generation config parameters directly on configBuilder
		if (requestOptions.getTemperature() != null) {
			configBuilder.temperature(requestOptions.getTemperature().floatValue());
		}
		if (requestOptions.getMaxOutputTokens() != null) {
			configBuilder.maxOutputTokens(requestOptions.getMaxOutputTokens());
		}
		if (requestOptions.getTopK() != null) {
			configBuilder.topK(requestOptions.getTopK().floatValue());
		}
		if (requestOptions.getTopP() != null) {
			configBuilder.topP(requestOptions.getTopP().floatValue());
		}
		if (requestOptions.getCandidateCount() != null) {
			configBuilder.candidateCount(requestOptions.getCandidateCount());
		}
		if (requestOptions.getStopSequences() != null) {
			configBuilder.stopSequences(requestOptions.getStopSequences());
		}
		if (requestOptions.getResponseMimeType() != null) {
			configBuilder.responseMimeType(requestOptions.getResponseMimeType());
		}
		if (requestOptions.getResponseSchema() != null) {
			configBuilder.responseJsonSchema(jsonToSchema(requestOptions.getResponseSchema()));
		}
		if (requestOptions.getFrequencyPenalty() != null) {
			configBuilder.frequencyPenalty(requestOptions.getFrequencyPenalty().floatValue());
		}
		if (requestOptions.getPresencePenalty() != null) {
			configBuilder.presencePenalty(requestOptions.getPresencePenalty().floatValue());
		}

		// Build thinking config if any thinking option is set
		if (requestOptions.getThinkingBudget() != null || requestOptions.getIncludeThoughts() != null
				|| requestOptions.getThinkingLevel() != null) {
			// Validate thinkingLevel for model compatibility
			if (requestOptions.getThinkingLevel() != null) {
				validateThinkingLevelForModel(requestOptions.getThinkingLevel(), modelName);
			}
			ThinkingConfig.Builder thinkingBuilder = ThinkingConfig.builder();
			if (requestOptions.getThinkingBudget() != null) {
				thinkingBuilder.thinkingBudget(requestOptions.getThinkingBudget());
			}
			if (requestOptions.getIncludeThoughts() != null) {
				thinkingBuilder.includeThoughts(requestOptions.getIncludeThoughts());
			}
			if (requestOptions.getThinkingLevel() != null) {
				thinkingBuilder.thinkingLevel(mapToGenAiThinkingLevel(requestOptions.getThinkingLevel()));
			}
			configBuilder.thinkingConfig(thinkingBuilder.build());
		}

		if (requestOptions.getLabels() != null && !requestOptions.getLabels().isEmpty()) {
			configBuilder.labels(requestOptions.getLabels());
		}

		// Add safety settings
		if (!CollectionUtils.isEmpty(requestOptions.getSafetySettings())) {
			configBuilder.safetySettings(toGeminiSafetySettings(requestOptions.getSafetySettings()));
		}

		// Add tools
		List<Tool> tools = new ArrayList<>();
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			final List<FunctionDeclaration> functionDeclarations = toolDefinitions.stream()
				.map(toolDefinition -> FunctionDeclaration.builder()
					.name(toolDefinition.name())
					.description(toolDefinition.description())
					.parameters(jsonToSchema(toolDefinition.inputSchema()))
					.build())
				.toList();
			tools.add(Tool.builder().functionDeclarations(functionDeclarations).build());
		}

		if (prompt.getOptions() instanceof GoogleGenAiChatOptions options && options.getGoogleSearchRetrieval()) {
			var googleSearch = GoogleSearch.builder().build();
			final var googleSearchRetrievalTool = Tool.builder().googleSearch(googleSearch).build();
			tools.add(googleSearchRetrievalTool);
		}

		if (!CollectionUtils.isEmpty(tools)) {
			configBuilder.tools(tools);
		}

		// Handle cached content
		if (requestOptions.getUseCachedContent() != null && requestOptions.getUseCachedContent()
				&& requestOptions.getCachedContentName() != null) {
			// Set the cached content name in the config
			configBuilder.cachedContent(requestOptions.getCachedContentName());
			logger.debug("Using cached content: {}", requestOptions.getCachedContentName());
		}

		// Handle system instruction
		List<Content> systemContents = toGeminiContent(
				prompt.getInstructions().stream().filter(m -> m.getMessageType() == MessageType.SYSTEM).toList());

		if (!CollectionUtils.isEmpty(systemContents)) {
			Assert.isTrue(systemContents.size() <= 1, "Only one system message is allowed in the prompt");
			configBuilder.systemInstruction(systemContents.get(0));
		}

		GenerateContentConfig config = configBuilder.build();

		// Create message contents
		return new GeminiRequest(toGeminiContent(
				prompt.getInstructions().stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).toList()),
				modelName, config);
	}

	// Helper methods for mapping safety settings enums
	private static com.google.genai.types.HarmCategory mapToGenAiHarmCategory(
			GoogleGenAiSafetySetting.HarmCategory category) {
		return switch (category) {
			case HARM_CATEGORY_UNSPECIFIED -> new com.google.genai.types.HarmCategory(
					com.google.genai.types.HarmCategory.Known.HARM_CATEGORY_UNSPECIFIED);
			case HARM_CATEGORY_HATE_SPEECH -> new com.google.genai.types.HarmCategory(
					com.google.genai.types.HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH);
			case HARM_CATEGORY_DANGEROUS_CONTENT -> new com.google.genai.types.HarmCategory(
					com.google.genai.types.HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT);
			case HARM_CATEGORY_HARASSMENT -> new com.google.genai.types.HarmCategory(
					com.google.genai.types.HarmCategory.Known.HARM_CATEGORY_HARASSMENT);
			case HARM_CATEGORY_SEXUALLY_EXPLICIT -> new com.google.genai.types.HarmCategory(
					com.google.genai.types.HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT);
			default -> throw new IllegalArgumentException("Unknown HarmCategory: " + category);
		};
	}

	private static com.google.genai.types.HarmBlockThreshold mapToGenAiHarmBlockThreshold(
			GoogleGenAiSafetySetting.HarmBlockThreshold threshold) {
		return switch (threshold) {
			case HARM_BLOCK_THRESHOLD_UNSPECIFIED -> new com.google.genai.types.HarmBlockThreshold(
					com.google.genai.types.HarmBlockThreshold.Known.HARM_BLOCK_THRESHOLD_UNSPECIFIED);
			case BLOCK_LOW_AND_ABOVE -> new com.google.genai.types.HarmBlockThreshold(
					com.google.genai.types.HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE);
			case BLOCK_MEDIUM_AND_ABOVE -> new com.google.genai.types.HarmBlockThreshold(
					com.google.genai.types.HarmBlockThreshold.Known.BLOCK_MEDIUM_AND_ABOVE);
			case BLOCK_ONLY_HIGH -> new com.google.genai.types.HarmBlockThreshold(
					com.google.genai.types.HarmBlockThreshold.Known.BLOCK_ONLY_HIGH);
			case BLOCK_NONE -> new com.google.genai.types.HarmBlockThreshold(
					com.google.genai.types.HarmBlockThreshold.Known.BLOCK_NONE);
			case OFF ->
				new com.google.genai.types.HarmBlockThreshold(com.google.genai.types.HarmBlockThreshold.Known.OFF);
			default -> throw new IllegalArgumentException("Unknown HarmBlockThreshold: " + threshold);
		};
	}

	private static ThinkingLevel mapToGenAiThinkingLevel(GoogleGenAiThinkingLevel level) {
		return switch (level) {
			case THINKING_LEVEL_UNSPECIFIED -> new ThinkingLevel(ThinkingLevel.Known.THINKING_LEVEL_UNSPECIFIED);
			case MINIMAL -> new ThinkingLevel(ThinkingLevel.Known.MINIMAL);
			case LOW -> new ThinkingLevel(ThinkingLevel.Known.LOW);
			case MEDIUM -> new ThinkingLevel(ThinkingLevel.Known.MEDIUM);
			case HIGH -> new ThinkingLevel(ThinkingLevel.Known.HIGH);
		};
	}

	/**
	 * Checks if the model name indicates a Gemini 3 Pro model.
	 * @param modelName the model name to check
	 * @return true if the model is a Gemini 3 Pro model
	 */
	private static boolean isGemini3ProModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		return lower.contains("gemini-3") && lower.contains("pro") && !lower.contains("flash");
	}

	/**
	 * Checks if the model name indicates a Gemini 3 Flash model.
	 * @param modelName the model name to check
	 * @return true if the model is a Gemini 3 Flash model
	 */
	private static boolean isGemini3FlashModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		return lower.contains("gemini-3") && lower.contains("flash");
	}

	/**
	 * Validates ThinkingLevel compatibility with the model. Gemini 3 Pro only supports
	 * LOW and HIGH. Gemini 3 Flash supports all levels.
	 * @param level the thinking level to validate
	 * @param modelName the model name
	 * @throws IllegalArgumentException if the level is not supported for the model
	 */
	private static void validateThinkingLevelForModel(GoogleGenAiThinkingLevel level, String modelName) {
		if (level == null || level == GoogleGenAiThinkingLevel.THINKING_LEVEL_UNSPECIFIED) {
			return;
		}
		if (isGemini3ProModel(modelName)) {
			if (level == GoogleGenAiThinkingLevel.MINIMAL || level == GoogleGenAiThinkingLevel.MEDIUM) {
				throw new IllegalArgumentException(
						String.format("ThinkingLevel.%s is not supported for Gemini 3 Pro models. "
								+ "Supported levels: LOW, HIGH. Model: %s", level, modelName));
			}
		}
	}

	private List<Content> toGeminiContent(List<Message> instructions) {

		List<Content> contents = instructions.stream()
			.map(message -> Content.builder()
				.role(toGeminiMessageType(message.getMessageType()).getValue())
				.parts(messageToGeminiParts(message))
				.build())
			.toList();

		return contents;
	}

	private List<SafetySetting> toGeminiSafetySettings(List<GoogleGenAiSafetySetting> safetySettings) {
		return safetySettings.stream()
			.map(safetySetting -> SafetySetting.builder()
				.category(mapToGenAiHarmCategory(safetySetting.getCategory()))
				.threshold(mapToGenAiHarmBlockThreshold(safetySetting.getThreshold()))
				.build())
			.toList();
	}

	/**
	 * Generates the content response based on the provided Gemini request. Package
	 * protected for testing purposes.
	 * @param request the GeminiRequest containing the content and model information
	 * @return a GenerateContentResponse containing the generated content
	 * @throws RuntimeException if content generation fails
	 */
	GenerateContentResponse getContentResponse(GeminiRequest request) {
		try {
			return this.genAiClient.models.generateContent(request.modelName, request.contents, request.config);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to generate content", e);
		}
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return GoogleGenAiChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Gets the cached content service for managing cached content.
	 * @return the cached content service
	 */
	public GoogleGenAiCachedContentService getCachedContentService() {
		return this.cachedContentService;
	}

	@Override
	public void destroy() throws Exception {
		// GenAI Client doesn't need explicit closing
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

		private Client genAiClient;

		private GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
			.temperature(0.7)
			.topP(1.0)
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH)
			.build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder genAiClient(Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		public Builder defaultOptions(GoogleGenAiChatOptions defaultOptions) {
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

		public GoogleGenAiChatModel build() {
			if (this.toolCallingManager != null) {
				return new GoogleGenAiChatModel(this.genAiClient, this.defaultOptions, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new GoogleGenAiChatModel(this.genAiClient, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

	public enum GeminiMessageType {

		USER("user"),

		MODEL("model");

		public final String value;

		GeminiMessageType(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	public enum ChatModel implements ChatModelDescription {

		/**
		 * <b>gemini-1.5-pro</b> is recommended to upgrade to <b>gemini-2.0-flash</b>
		 * <p>
		 * Discontinuation date: September 24, 2025
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versions#stable-version">stable-version</a>
		 */
		GEMINI_1_5_PRO("gemini-1.5-pro-002"),

		/**
		 * <b>gemini-1.5-flash</b> is recommended to upgrade to
		 * <b>gemini-2.0-flash-lite</b>
		 * <p>
		 * Discontinuation date: September 24, 2025
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versions#stable-version">stable-version</a>
		 */
		GEMINI_1_5_FLASH("gemini-1.5-flash-002"),

		/**
		 * <b>gemini-2.0-flash</b> delivers next-gen features and improved capabilities,
		 * including superior speed, built-in tool use, multimodal generation, and a 1M
		 * token context window.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text,
		 * Audio(Experimental), Images(Experimental) - 8,192 tokens
		 * <p>
		 * Knowledge cutoff: June 2024
		 * <p>
		 * Model ID: gemini-2.0-flash
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-0-flash">gemini-2.0-flash</a>
		 */
		GEMINI_2_0_FLASH("gemini-2.0-flash-001"),

		/**
		 * <b>gemini-2.0-flash-lite</b> is the fastest and most cost efficient Flash
		 * model. It's an upgrade path for 1.5 Flash users who want better quality for the
		 * same price and speed.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 8,192 tokens
		 * <p>
		 * Knowledge cutoff: June 2024
		 * <p>
		 * Model ID: gemini-2.0-flash-lite
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-0-flash-lite">gemini-2.0-flash-lite</a>
		 */
		GEMINI_2_0_FLASH_LIGHT("gemini-2.0-flash-lite-001"),

		/**
		 * <b>gemini-2.5-pro</b> is the most advanced reasoning Gemini model, capable of
		 * solving complex problems.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 65,536 tokens
		 * <p>
		 * Knowledge cutoff: January 2025
		 * <p>
		 * Model ID: gemini-2.5-pro-preview-05-06
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro">gemini-2.5-pro</a>
		 */
		GEMINI_2_5_PRO("gemini-2.5-pro"),

		/**
		 * <b>gemini-2.5-flash</b> is a thinking model that offers great, well-rounded
		 * capabilities. It is designed to offer a balance between price and performance.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 65,536 tokens
		 * <p>
		 * Knowledge cutoff: January 2025
		 * <p>
		 * Model ID: gemini-2.5-flash-preview-04-17
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash">gemini-2.5-flash</a>
		 */
		GEMINI_2_5_FLASH("gemini-2.5-flash"),

		/**
		 * <b>gemini-2.5-flash-lite</b> is the fastest and most cost efficient Flash
		 * model. It's an upgrade path for 2.0 Flash users who want better quality for the
		 * same price and speed.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 8,192 tokens
		 * <p>
		 * Knowledge cutoff: Jan 2025
		 * <p>
		 * Model ID: gemini-2.5-flash-lite
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash-lite">gemini-2.5-flash-lite</a>
		 */
		GEMINI_2_5_FLASH_LIGHT("gemini-2.5-flash-lite"),

		GEMINI_3_PRO_PREVIEW("gemini-3-pro-preview"),

		GEMINI_3_FLASH_PREVIEW("gemini-3-flash-preview");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	@JsonInclude(Include.NON_NULL)
	public record GeminiRequest(List<Content> contents, String modelName, GenerateContentConfig config) {

	}

}
