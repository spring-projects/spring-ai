/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.google.genai.common.GoogleGenAiConstants;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.ai.google.genai.schema.GoogleGenAiToolCallingManager;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.retry.RetryTemplate;
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
 * 		.options(options)
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
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @since 0.8.1
 * @see GoogleGenAiChatOptions
 * @see ToolCallingManager
 * @see ChatModel
 */
public class GoogleGenAiChatModel implements ChatModel, DisposableBean {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final Log logger = LogFactory.getLog(getClass());

	private final Client genAiClient;

	private final GoogleGenAiChatOptions options;

	/**
	 * The retry template used to retry the API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * The cached content service for managing cached content.
	 */
	@Nullable private final GoogleGenAiCachedContentService cachedContentService;

	// GenerationConfig is now built dynamically per request

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Tool calling manager used to call tools.
	 */
	private final ToolCallingManager toolCallingManager;

	private final JsonMapper jsonMapper = JacksonUtils.getDefaultJsonMapper()
		.rebuild()
		.addMixIn(Schema.class, SchemaMixin.class)
		.build();

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new instance of GoogleGenAiChatModel.
	 * @param genAiClient the GenAI Client instance to use
	 * @param options the default options to use
	 * @param toolCallingManager the tool calling manager to use. It is wrapped in a
	 * {@link GoogleGenAiToolCallingManager} to ensure compatibility with Vertex AI's
	 * OpenAPI schema format.
	 * @param retryTemplate the retry template to use
	 * @param observationRegistry the observation registry to use
	 */
	public GoogleGenAiChatModel(Client genAiClient, GoogleGenAiChatOptions options,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {

		Assert.notNull(genAiClient, "GenAI Client must not be null");
		Assert.notNull(options, "GoogleGenAiChatOptions must not be null");
		Assert.notNull(options.getModel(), "GoogleGenAiChatOptions.modelName must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(toolCallingManager, "ToolCallingManager must not be null");

		this.genAiClient = genAiClient;
		this.options = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.cachedContentService = (genAiClient != null && genAiClient.caches != null && genAiClient.async != null
				&& genAiClient.async.caches != null) ? new GoogleGenAiCachedContentService(genAiClient) : null;

		if (toolCallingManager instanceof GoogleGenAiToolCallingManager) {
			this.toolCallingManager = toolCallingManager;
		}
		else {
			this.toolCallingManager = new GoogleGenAiToolCallingManager(toolCallingManager);
		}
	}

	private static GeminiMessageType toGeminiMessageType(MessageType type) {

		Assert.notNull(type, "Message type must not be null");

		return switch (type) {
			case SYSTEM, USER, TOOL -> GeminiMessageType.USER;
			case ASSISTANT -> GeminiMessageType.MODEL;
			default -> throw new IllegalArgumentException("Unsupported message type: " + type);
		};
	}

	List<Part> messageToGeminiParts(Message message) {

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
	private Map<String, Object> parseJsonToMap(String json) {
		try {
			// First, try to parse as an array
			Object parsed = this.jsonMapper.readValue(json, Object.class);
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

	private String mapToJson(Map<String, Object> map) {
		try {
			return this.jsonMapper.writeValueAsString(map);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert map to JSON", e);
		}
	}

	private Schema jsonToSchema(String json) {
		try {
			return this.jsonMapper.readValue(json, Schema.class);
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

	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
		Assert.notNull(options, "Options must not be null");

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(GoogleGenAiConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				return RetryUtils.execute(this.retryTemplate, () -> {

					var geminiRequest = createGeminiRequest(prompt);

					GenerateContentResponse generateContentResponse = this.getContentResponse(geminiRequest);

					List<Generation> generations = generateContentResponse.candidates()
						.orElse(List.of())
						.stream()
						.map(this::responseCandidateToGeneration)
						.flatMap(List::stream)
						.toList();

					var usage = generateContentResponse.usageMetadata();
					Usage currentUsage = (usage.isPresent()) ? getDefaultUsage(usage.get(), options)
							: getDefaultUsage(null, options);
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations,
							toChatResponseMetadata(cumulativeUsage, generateContentResponse.modelVersion().get()));

					observationContext.setResponse(chatResponse);
					return chatResponse;
				});
			});

		return response;

	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var requestPrompt = this.buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
		Assert.notNull(options, "Options must not be null");

		return Flux.deferContextual(contextView -> {

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(GoogleGenAiConstants.PROVIDER_NAME)
				.streaming(true)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			var request = createGeminiRequest(prompt);

			try {
				ResponseStream<GenerateContentResponse> responseStream = this.genAiClient.models
					.generateContentStream(request.modelName, request.contents, request.config);

				Flux<ChatResponse> chatResponseFlux = Flux.fromIterable(responseStream).concatMap(response -> {
					List<Generation> generations = response.candidates()
						.orElse(List.of())
						.stream()
						.map(this::responseCandidateToGeneration)
						.flatMap(List::stream)
						.toList();

					var usage = response.usageMetadata();
					Usage currentUsage = usage.isPresent() ? getDefaultUsage(usage.get(), options)
							: getDefaultUsage(null, options);
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations,
							toChatResponseMetadata(cumulativeUsage, response.modelVersion().get()));
					return Flux.just(chatResponse);
				});

				AtomicReference<ChatResponse> aggregatedResponseRef = new AtomicReference<>();

				Flux<ChatResponse> aggregatedFlux = new MessageAggregator().aggregate(chatResponseFlux,
						aggregatedResponse -> {
							aggregatedResponseRef.set(aggregatedResponse);
							observationContext.setResponse(aggregatedResponse);
						});

				return aggregatedFlux.doOnError(observation::error)
					.doFinally(s -> observation.stop())
					.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

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

			// Extract server-side tool invocations if present
			List<Map<String, Object>> serverSideToolInvocations = new ArrayList<>();
			for (Part part : parts) {
				if (part.toolCall().isPresent()) {
					com.google.genai.types.ToolCall tc = part.toolCall().get();
					Map<String, Object> inv = new HashMap<>();
					inv.put("type", "toolCall");
					inv.put("id", tc.id().orElse(""));
					inv.put("toolType", tc.toolType().map(Object::toString).orElse(""));
					inv.put("args", tc.args().orElse(Map.of()));
					serverSideToolInvocations.add(inv);
				}
				if (part.toolResponse().isPresent()) {
					com.google.genai.types.ToolResponse tr = part.toolResponse().get();
					Map<String, Object> inv = new HashMap<>();
					inv.put("type", "toolResponse");
					inv.put("id", tr.id().orElse(""));
					inv.put("toolType", tr.toolType().map(Object::toString).orElse(""));
					inv.put("response", tr.response().orElse(Map.of()));
					serverSideToolInvocations.add(inv);
				}
			}
			if (!serverSideToolInvocations.isEmpty()) {
				messageMetadata.put("serverSideToolInvocations", serverSideToolInvocations);
			}
		}

		ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.builder()
			.finishReason(candidateFinishReason.toString())
			.build();

		boolean isFunctionCall = candidate.content().isPresent() && candidate.content().get().parts().isPresent()
				&& candidate.content().get().parts().get().stream().anyMatch(part -> part.functionCall().isPresent());

		if (isFunctionCall) {
			List<AssistantMessage.ToolCall> assistantToolCalls = candidate.content()
				.get()
				.parts()
				.orElse(List.of())
				.stream()
				.filter(part -> part.functionCall().isPresent())
				.map(part -> {
					FunctionCall functionCall = part.functionCall().get();
					var functionName = functionCall.name().orElse("");
					String functionArguments = mapToJson(functionCall.args().orElse(Map.of()));
					return new AssistantMessage.ToolCall("", "function", functionName, functionArguments);
				})
				.toList();

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("")
				.properties(messageMetadata)
				.toolCalls(assistantToolCalls)
				.build();

			return List.of(new Generation(assistantMessage, chatGenerationMetadata));
		}
		else {
			List<Generation> generations = candidate.content()
				.get()
				.parts()
				.orElse(List.of())
				.stream()
				.filter(part -> part.toolCall().isEmpty() && part.toolResponse().isEmpty())
				.map(part -> {
					var partMessageMetadata = new HashMap<>(messageMetadata);
					partMessageMetadata.put("isThought", part.thought().orElse(false));
					return AssistantMessage.builder()
						.content(part.text().orElse(""))
						.properties(partMessageMetadata)
						.build();
				})
				.map(assistantMessage -> new Generation(assistantMessage, chatGenerationMetadata))
				.toList();

			// If all parts were server-side tool invocations, return a single generation
			// with empty text but with the server-side tool invocation metadata
			if (generations.isEmpty()) {
				AssistantMessage assistantMessage = AssistantMessage.builder()
					.content("")
					.properties(messageMetadata)
					.build();
				return List.of(new Generation(assistantMessage, chatGenerationMetadata));
			}

			return generations;
		}
	}

	private ChatResponseMetadata toChatResponseMetadata(Usage usage, String modelVersion) {
		return ChatResponseMetadata.builder().usage(usage).model(modelVersion).build();
	}

	private Usage getDefaultUsage(@Nullable GenerateContentResponseUsageMetadata usageMetadata,
			@Nullable GoogleGenAiChatOptions options) {
		// Check if extended metadata should be included (default to true if not
		// configured)
		boolean includeExtended = true;
		if (options != null && options.getIncludeExtendedUsageMetadata() != null) {
			includeExtended = options.getIncludeExtendedUsageMetadata();
		}
		else if (this.options.getIncludeExtendedUsageMetadata() != null) {
			includeExtended = this.options.getIncludeExtendedUsageMetadata();
		}

		if (includeExtended) {
			return GoogleGenAiUsage.from(usageMetadata);
		}
		else {
			// Fall back to basic usage for backward compatibility
			if (usageMetadata == null) {
				return new DefaultUsage(0, 0, 0);
			}
			return new DefaultUsage(usageMetadata.promptTokenCount().orElse(0),
					usageMetadata.candidatesTokenCount().orElse(0), usageMetadata.totalTokenCount().orElse(0));
		}
	}

	GeminiRequest createGeminiRequest(Prompt prompt) {

		GoogleGenAiChatOptions requestOptions = (GoogleGenAiChatOptions) prompt.getOptions();
		Assert.notNull(requestOptions, "Options must not be null");

		// Build GenerateContentConfig
		GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

		String modelName = requestOptions.getModel() != null ? requestOptions.getModel() : this.options.getModel();
		Assert.notNull(modelName, "Model name must not be null");

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

		if (requestOptions.getServiceTier() != null) {
			configBuilder.serviceTier(requestOptions.getServiceTier().getValue());
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

		if (prompt.getOptions() instanceof GoogleGenAiChatOptions googleGenAiChatOptions
				&& Boolean.TRUE.equals(googleGenAiChatOptions.getGoogleSearchRetrieval())) {
			var googleSearch = GoogleSearch.builder().build();
			final var googleSearchRetrievalTool = Tool.builder().googleSearch(googleSearch).build();
			tools.add(googleSearchRetrievalTool);
		}

		if (!CollectionUtils.isEmpty(tools)) {
			configBuilder.tools(tools);
		}

		if (requestOptions.getToolChoice() != null
				|| Boolean.TRUE.equals(requestOptions.getIncludeServerSideToolInvocations())) {

			ToolConfig.Builder toolConfigBuilder = ToolConfig.builder();

			// Build ToolConfig if includeServerSideToolInvocations is enabled
			if (Boolean.TRUE.equals(requestOptions.getIncludeServerSideToolInvocations())) {
				toolConfigBuilder.includeServerSideToolInvocations(true);
			}

			if (requestOptions.getToolChoice() != null) {
				GoogleGenAiChatOptions.ToolChoice toolChoice = requestOptions.getToolChoice();
				var fccBuilder = FunctionCallingConfig.builder()
					.mode(mapToFunctionCallingConfigMode(toolChoice.mode()));

				if ((toolChoice.mode() == GoogleGenAiChatOptions.ToolChoice.Mode.ANY
						|| toolChoice.mode() == GoogleGenAiChatOptions.ToolChoice.Mode.VALIDATED)
						&& !CollectionUtils.isEmpty(toolChoice.allowedFunctionNames())) {
					fccBuilder.allowedFunctionNames(toolChoice.allowedFunctionNames());
				}

				toolConfigBuilder.functionCallingConfig(fccBuilder.build());
			}

			configBuilder.toolConfig(toolConfigBuilder.build());
		}

		// Handle cached content
		if (requestOptions.getUseCachedContent() != null && requestOptions.getUseCachedContent()
				&& requestOptions.getCachedContentName() != null) {
			// Set the cached content name in the config
			configBuilder.cachedContent(requestOptions.getCachedContentName());
			if (logger.isDebugEnabled()) {
				logger.debug("Using cached content: " + requestOptions.getCachedContentName());
			}
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

	private static FunctionCallingConfigMode mapToFunctionCallingConfigMode(
			GoogleGenAiChatOptions.ToolChoice.Mode mode) {
		return switch (mode) {
			case AUTO -> new FunctionCallingConfigMode(FunctionCallingConfigMode.Known.AUTO);
			case ANY -> new FunctionCallingConfigMode(FunctionCallingConfigMode.Known.ANY);
			case VALIDATED -> new FunctionCallingConfigMode(FunctionCallingConfigMode.Known.VALIDATED);
			case NONE -> new FunctionCallingConfigMode(FunctionCallingConfigMode.Known.NONE);
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
		String lower = modelName.toLowerCase(Locale.ROOT);
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
		String lower = modelName.toLowerCase(Locale.ROOT);
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
			if (level == GoogleGenAiThinkingLevel.MINIMAL) {
				throw new IllegalArgumentException(
						String.format("ThinkingLevel.%s is not supported for Gemini 3 Pro models. "
								+ "Supported levels: LOW, MEDIUM, HIGH. Model: %s", level, modelName));
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

	/**
	 * @since 2.0.0
	 */
	@Override
	public GoogleGenAiChatOptions getOptions() {
		return this.options;
	}

	/**
	 * Gets the cached content service for managing cached content.
	 * @return the cached content service
	 */
	public @Nullable GoogleGenAiCachedContentService getCachedContentService() {
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

	/**
	 * Look at the options of the provided prompt. If none are provided, return a new
	 * prompt using this model
	 * {@link org.springframework.ai.chat.model.ChatModel#getOptions() options}.
	 * Otherwise, use the prompt as is.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		if (prompt.getOptions() == null) {
			return prompt.mutate().chatOptions(this.getOptions()).build();
		}
		else {
			return prompt;
		}
	}

	public static final class Builder {

		@Nullable private Client genAiClient;

		private GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();

		@Nullable private ToolCallingManager toolCallingManager;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder genAiClient(Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		public Builder options(GoogleGenAiChatOptions options) {
			this.options = options;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
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
			Assert.notNull(this.genAiClient, "GenAI Client must not be null");
			if (this.toolCallingManager != null) {
				return new GoogleGenAiChatModel(this.genAiClient, this.options, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry);
			}

			return new GoogleGenAiChatModel(this.genAiClient, this.options,
					ToolCallingManager.builder().observationRegistry(this.observationRegistry).build(),
					this.retryTemplate, this.observationRegistry);
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
		@Deprecated
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
		@Deprecated
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

		/**
		 * @deprecated Use {@link #GEMINI_3_1_PRO_PREVIEW} instead
		 */
		@Deprecated
		GEMINI_3_PRO_PREVIEW("gemini-3.1-pro-preview"),

		GEMINI_3_1_PRO_PREVIEW("gemini-3.1-pro-preview"),

		GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite"),

		GEMINI_3_5_FLASH("gemini-3.5-flash");

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

	@JsonDeserialize(builder = Schema.Builder.class)
	private static class SchemaMixin {

	}

}
