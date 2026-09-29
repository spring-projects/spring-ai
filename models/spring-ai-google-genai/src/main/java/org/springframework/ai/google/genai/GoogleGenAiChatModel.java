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
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Blob;
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
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
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
import org.springframework.util.MimeTypeUtils;
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
 * A response candidate is one {@link Generation} whose {@link AssistantMessage#getParts()
 * parts} mirror the Gemini parts in order, each with its thought signature as an
 * {@link OpaquePayload}, so that the message replays to Gemini as it was produced and the
 * model keeps its reasoning context across tool-call rounds. See
 * {@link #responseCandidateToGeneration(Candidate)} for the mapping.
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
 * @author Dimitar Proynov
 * @since 0.8.1
 * @see GoogleGenAiChatOptions
 * @see ToolCallingManager
 * @see ChatModel
 */
public class GoogleGenAiChatModel implements ChatModel, DisposableBean {

	/**
	 * Metadata key that marked an
	 * {@link org.springframework.ai.chat.messages.AssistantMessage} as a model thought
	 * (reasoning) rather than a final response, when each thought was returned as a
	 * separate {@link org.springframework.ai.chat.model.Generation}.
	 * <p>
	 * A candidate is now a single generation and its thoughts are the
	 * {@link ReasoningPart}s of the message, see {@link AssistantMessage#getReasoning()}.
	 * The key is no longer written; an assistant message carrying it with {@code true},
	 * for example one restored from a chat memory written by an earlier version, still
	 * has its text replayed to Gemini as a thought.
	 * @since 2.0.2
	 * @deprecated since 2.1.0 in favor of {@link AssistantMessage#getReasoning()}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public static final String THOUGHT_METADATA_KEY = "isThought";

	/**
	 * The {@link OpaquePayload#provider()} of the parts produced by this model.
	 * @since 2.1.0
	 */
	public static final String GOOGLE_PROVIDER = "google";

	/**
	 * The {@link OpaquePayload#kind()} of a Gemini thought signature; the payload data is
	 * the Base64 encoded signature, replayed unmodified on the part that carried it.
	 * @since 2.1.0
	 */
	public static final String PAYLOAD_THOUGHT_SIGNATURE = "thought_signature";

	/**
	 * Metadata key holding every thought signature of a candidate as a list of byte
	 * arrays. Signatures now travel as part payloads; the key is still written for chat
	 * memories that keep metadata but not parts, and read back positionally when no part
	 * of an assistant message carries a Google payload.
	 */
	private static final String THOUGHT_SIGNATURES_METADATA_KEY = "thoughtSignatures";

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
			return userMessageToGeminiParts(userMessage);
		}
		else if (message instanceof AssistantMessage assistantMessage) {
			return assistantMessageToGeminiParts(assistantMessage);
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

	/**
	 * Map the parts of a user message to Gemini parts in their order, so text and media
	 * can interleave. A message built through the constructors or the {@code text()} and
	 * {@code media()} builder methods has its parts in the order text then media, so its
	 * wire order is unchanged. Google {@link UnknownPart}s are replayed verbatim; parts a
	 * user message cannot carry to Gemini are skipped.
	 */
	private List<Part> userMessageToGeminiParts(UserMessage userMessage) {
		List<Part> parts = new ArrayList<>();
		for (MessagePart part : userMessage.getParts()) {
			if (part instanceof TextPart textPart) {
				parts.add(Part.fromText(textPart.text()));
			}
			else if (part instanceof MediaPart mediaPart) {
				parts.add(mediaToGeminiPart(mediaPart.media()));
			}
			else if (part instanceof UnknownPart unknownPart) {
				unknownPartToGeminiPart(unknownPart).ifPresent(parts::add);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Skipping a " + part.getClass().getSimpleName() + " of a user message sent to Gemini");
			}
		}
		return parts;
	}

	/**
	 * Map an assistant message back to the Gemini parts it was produced from, one part
	 * per {@link MessagePart}, in order. Each thought signature is restored on the part
	 * that carried it, which is where Gemini validates it: a function call, a thought, a
	 * text or an empty text part closing a stream. Reasoning is replayed as thought text;
	 * reasoning another provider produced is skipped, since its payload means nothing to
	 * Gemini.
	 * <p>
	 * Two fallbacks keep assistant messages written by earlier versions replayable, for
	 * example ones restored from a chat memory: when no part carries a Google payload,
	 * the signatures of the {@code thoughtSignatures} metadata list are attached to the
	 * function calls in order, as before, and a message flagged with
	 * {@link #THOUGHT_METADATA_KEY} has its text sent as a thought.
	 * <p>
	 * Dispatch is an {@code instanceof} chain ending in a throw, so a part type added to
	 * the sealed hierarchy later cannot be dropped silently.
	 */
	private List<Part> assistantMessageToGeminiParts(AssistantMessage assistantMessage) {
		boolean hasGooglePayload = assistantMessage.getParts()
			.stream()
			.anyMatch(part -> getGooglePayload(part).isPresent());
		List<byte[]> legacySignatures = hasGooglePayload ? new ArrayList<>()
				: legacyThoughtSignatures(assistantMessage);
		boolean legacyThought = Boolean.TRUE.equals(assistantMessage.getMetadata().get(THOUGHT_METADATA_KEY));

		List<Part> parts = new ArrayList<>();
		for (MessagePart part : assistantMessage.getParts()) {
			if (part instanceof TextPart textPart) {
				// An empty text part without a signature, such as the one that closes an
				// assistant turn of tool calls only, has nothing to replay.
				if (StringUtils.hasText(textPart.text()) || getGooglePayload(part).isPresent()) {
					Part.Builder builder = Part.builder().text(textPart.text());
					if (legacyThought) {
						builder.thought(true);
					}
					parts.add(withThoughtSignature(builder, part).build());
				}
			}
			else if (part instanceof ReasoningPart reasoningPart) {
				reasoningToGeminiPart(reasoningPart).ifPresent(parts::add);
			}
			else if (part instanceof ToolCallPart toolCallPart) {
				Part.Builder builder = Part.builder().functionCall(toFunctionCall(toolCallPart.toolCall()));
				if (getGooglePayload(part).isPresent()) {
					withThoughtSignature(builder, part);
				}
				else if (!legacySignatures.isEmpty()) {
					builder.thoughtSignature(legacySignatures.remove(0));
				}
				parts.add(builder.build());
			}
			else if (part instanceof MediaPart mediaPart) {
				parts.add(withThoughtSignature(mediaToGeminiPart(mediaPart.media()).toBuilder(), part).build());
			}
			else if (part instanceof UnknownPart unknownPart) {
				unknownPartToGeminiPart(unknownPart).ifPresent(parts::add);
			}
			else if (part instanceof ToolResultPart) {
				// Reachable from application code: AssistantMessage.Builder.part() is
				// public, so this rejects a caller-supplied argument.
				throw new IllegalArgumentException(
						"Tool results belong in a tool response message, not an assistant message");
			}
			else {
				throw new IllegalStateException("Unhandled message part type: " + part.getClass().getName());
			}
		}
		return parts;
	}

	/**
	 * A Gemini thought part rebuilt from a reasoning part, or nothing when the reasoning
	 * was produced by another provider or carries neither text nor a signature.
	 */
	private Optional<Part> reasoningToGeminiPart(ReasoningPart part) {
		OpaquePayload payload = part.payload();
		if (payload != null && !GOOGLE_PROVIDER.equals(payload.provider())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping a reasoning part produced by " + payload.provider() + " on replay to Gemini");
			}
			return Optional.empty();
		}
		String text = part.text();
		if (!StringUtils.hasText(text) && payload == null) {
			return Optional.empty();
		}
		Part.Builder builder = Part.builder().thought(true);
		if (text != null) {
			builder.text(text);
		}
		return Optional.of(withThoughtSignature(builder, part).build());
	}

	private FunctionCall toFunctionCall(AssistantMessage.ToolCall toolCall) {
		FunctionCall.Builder builder = FunctionCall.builder()
			.name(toolCall.name())
			.args(parseJsonToMap(toolCall.arguments()));
		if (StringUtils.hasText(toolCall.id())) {
			builder.id(toolCall.id());
		}
		return builder.build();
	}

	/**
	 * Replay a part Gemini produced but this model does not map to a dedicated part type
	 * (a server-side tool call or response, executable code, a code execution result,
	 * file data) from the JSON it arrived as, thought signature included. Unknown parts
	 * from other providers are skipped.
	 */
	private Optional<Part> unknownPartToGeminiPart(UnknownPart part) {
		if (!GOOGLE_PROVIDER.equals(part.provider())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping an unknown part of kind " + part.kind() + " produced by " + part.provider()
						+ " on replay to Gemini");
			}
			return Optional.empty();
		}
		try {
			return Optional.of(Part.fromJson(part.rawJson()));
		}
		catch (RuntimeException ex) {
			// Better a turn missing one part than a failed turn.
			logger.warn("Could not replay the Gemini part of kind " + part.kind() + "; dropping it", ex);
			return Optional.empty();
		}
	}

	private static Optional<OpaquePayload> getGooglePayload(MessagePart part) {
		OpaquePayload payload = part.payload();
		if (payload != null && GOOGLE_PROVIDER.equals(payload.provider())
				&& PAYLOAD_THOUGHT_SIGNATURE.equals(payload.kind())) {
			return Optional.of(payload);
		}
		else {
			return Optional.empty();
		}
	}

	private static Part.Builder withThoughtSignature(Part.Builder builder, MessagePart part) {
		getGooglePayload(part)
			.ifPresent(payload -> builder.thoughtSignature(Base64.getDecoder().decode(payload.data())));
		return builder;
	}

	private static List<byte[]> legacyThoughtSignatures(AssistantMessage assistantMessage) {
		List<byte[]> signatures = new ArrayList<>();
		if (assistantMessage.getMetadata().get(THOUGHT_SIGNATURES_METADATA_KEY) instanceof List<?> list) {
			for (Object signature : list) {
				if (signature instanceof byte[] bytes) {
					signatures.add(bytes);
				}
			}
		}
		return signatures;
	}

	private static Part mediaToGeminiPart(Media media) {
		Object data = media.getData();
		String mimeType = media.getMimeType().toString();

		if (data instanceof byte[] bytes) {
			return Part.fromBytes(bytes, mimeType);
		}
		else if (data instanceof URI || data instanceof String) {
			// Handle URI or String URLs
			return Part.fromUri(data.toString(), mimeType);
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + data.getClass());
		}
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
							toChatResponseMetadata(cumulativeUsage, generateContentResponse));

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

				// One indexer per candidate, so that the parts of a candidate keep a
				// single
				// index sequence across chunks.
				Map<Integer, StreamingPartIndexer> indexers = new HashMap<>();
				Flux<ChatResponse> chatResponseFlux = Flux.fromIterable(responseStream).concatMap(response -> {
					List<Generation> generations = response.candidates()
						.orElse(List.of())
						.stream()
						.flatMap(candidate -> {
							StreamingPartIndexer indexer = indexers.computeIfAbsent(candidate.index().orElse(0),
									key -> new StreamingPartIndexer());
							return responseCandidateToGeneration(candidate).stream().map(indexer::stamp);
						})
						.toList();

					var usage = response.usageMetadata();
					Usage currentUsage = usage.isPresent() ? getDefaultUsage(usage.get(), options)
							: getDefaultUsage(null, options);
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations,
							toChatResponseMetadata(cumulativeUsage, response));
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

	/**
	 * Converts a response candidate to a single generation whose message holds the Gemini
	 * parts, in order, as {@link MessagePart}s: thought text as {@link ReasoningPart},
	 * answer text as {@link TextPart}, function calls as {@link ToolCallPart}, inline
	 * data as {@link MediaPart}, and anything else (server-side tool calls and responses,
	 * executable code, code execution results, file data) as {@link UnknownPart}, kept
	 * verbatim for replay. A thought signature becomes the {@link OpaquePayload} of the
	 * part that carried it.
	 * <p>
	 * The streaming path maps every chunk through this method too, so an override applies
	 * to both {@link #call(Prompt)} and {@link #stream(Prompt)}. When streaming, the
	 * parts of the returned message are stamped with a content block index; a message of
	 * an {@link AssistantMessage} subclass is passed through as returned instead, since
	 * its parts cannot be restamped without losing its type, and is aggregated the way
	 * messages without indexed parts are.
	 * @param candidate the response candidate
	 * @return a single-element list holding the generation
	 */
	protected List<Generation> responseCandidateToGeneration(Candidate candidate) {

		// TODO - The candidateIndex (e.g. choice must be assigned to the generation).
		int candidateIndex = candidate.index().orElse(0);
		FinishReason candidateFinishReason = candidate.finishReason().orElse(new FinishReason(FinishReason.Known.STOP));

		Map<String, Object> messageMetadata = new HashMap<>();
		messageMetadata.put("candidateIndex", candidateIndex);
		messageMetadata.put("finishReason", candidateFinishReason);

		List<Part> parts = candidate.content().flatMap(Content::parts).orElse(List.of());

		List<byte[]> thoughtSignatures = parts.stream()
			.filter(part -> part.thoughtSignature().isPresent())
			.map(part -> part.thoughtSignature().get())
			.toList();
		if (!thoughtSignatures.isEmpty()) {
			messageMetadata.put(THOUGHT_SIGNATURES_METADATA_KEY, thoughtSignatures);
		}

		List<Map<String, Object>> serverSideToolInvocations = serverSideToolInvocations(parts);
		if (!serverSideToolInvocations.isEmpty()) {
			messageMetadata.put("serverSideToolInvocations", serverSideToolInvocations);
		}

		AssistantMessage.Builder<?> messageBuilder = AssistantMessage.builder().properties(messageMetadata);
		if (parts.isEmpty()) {
			// A candidate without content, such as a blocked response or a usage-only
			// stream chunk, keeps the empty text it always had.
			messageBuilder.content("");
		}
		for (Part part : parts) {
			messageBuilder.part(toMessagePart(part));
		}

		ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.builder()
			.finishReason(candidateFinishReason.toString())
			.build();

		return List.of(new Generation(messageBuilder.build(), chatGenerationMetadata));
	}

	private MessagePart toMessagePart(Part part) {
		OpaquePayload payload = part.thoughtSignature()
			.map(signature -> new OpaquePayload(GOOGLE_PROVIDER, PAYLOAD_THOUGHT_SIGNATURE,
					Base64.getEncoder().encodeToString(signature)))
			.orElse(null);
		boolean thought = part.thought().orElse(false);

		if (part.functionCall().isPresent()) {
			FunctionCall functionCall = part.functionCall().get();
			AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(functionCall.id().orElse(""), "function",
					functionCall.name().orElse(""), mapToJson(functionCall.args().orElse(Map.of())));
			return new ToolCallPart(toolCall, payload, Map.of());
		}
		Optional<Blob> inlineData = part.inlineData();
		if (inlineData.isPresent() && inlineData.get().data().isPresent() && inlineData.get().mimeType().isPresent()) {
			Media media = Media.builder()
				.mimeType(MimeTypeUtils.parseMimeType(inlineData.get().mimeType().get()))
				.data(inlineData.get().data().get())
				.build();
			return new MediaPart(media, payload, Map.of());
		}
		if (part.text().isPresent() || isSignatureOnly(part)) {
			// A signature-only part, which Gemini sends for example to close a stream,
			// is kept as an empty part of its kind so that the signature replays.
			if (thought) {
				return new ReasoningPart(part.text().orElse(null), null, payload, Map.of());
			}
			return new TextPart(part.text().orElse(""), payload, Map.of());
		}
		return new UnknownPart(GOOGLE_PROVIDER, unknownPartKind(part), part.toJson(), payload, Map.of());
	}

	private static boolean isSignatureOnly(Part part) {
		return part.thoughtSignature().isPresent() && part.functionCall().isEmpty() && part.inlineData().isEmpty()
				&& part.fileData().isEmpty() && part.toolCall().isEmpty() && part.toolResponse().isEmpty()
				&& part.executableCode().isEmpty() && part.codeExecutionResult().isEmpty()
				&& part.functionResponse().isEmpty();
	}

	private static String unknownPartKind(Part part) {
		if (part.toolCall().isPresent()) {
			return "toolCall";
		}
		if (part.toolResponse().isPresent()) {
			return "toolResponse";
		}
		if (part.executableCode().isPresent()) {
			return "executableCode";
		}
		if (part.codeExecutionResult().isPresent()) {
			return "codeExecutionResult";
		}
		if (part.fileData().isPresent()) {
			return "fileData";
		}
		if (part.inlineData().isPresent()) {
			return "inlineData";
		}
		return "unknown";
	}

	private static List<Map<String, Object>> serverSideToolInvocations(List<Part> parts) {
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
		return serverSideToolInvocations;
	}

	/**
	 * Every chunk carries the response id, which {@link MessageAggregator} uses to keep
	 * the indexed parts of consecutive tool-call rounds apart.
	 */
	private ChatResponseMetadata toChatResponseMetadata(Usage usage, GenerateContentResponse response) {
		return ChatResponseMetadata.builder()
			.usage(usage)
			.model(response.modelVersion().orElse(""))
			.id(response.responseId().orElse(""))
			.build();
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
	 * Per-model support matrix for {@code thinkingLevel} on the {@code generateContent}
	 * API surface used by this client, keyed by exact model id (lower case). An empty set
	 * means the model rejects {@code thinkingLevel} entirely (use
	 * {@link GoogleGenAiChatOptions#getThinkingBudget() thinkingBudget} instead). Models
	 * absent from this map are not validated client-side; gemini-2.5-pro thinking level
	 * support is per account (API key) basis and cannot be validated here.
	 */
	private static final Map<String, Set<GoogleGenAiThinkingLevel>> THINKING_LEVEL_SUPPORT_BY_MODEL = Map.ofEntries(
			Map.entry(ChatModel.GEMINI_2_5_FLASH.getValue(), EnumSet.noneOf(GoogleGenAiThinkingLevel.class)),
			Map.entry(ChatModel.GEMINI_2_5_FLASH_LIGHT.getValue(), EnumSet.noneOf(GoogleGenAiThinkingLevel.class)),
			Map.entry(ChatModel.GEMINI_3_0_PRO_PREVIEW.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.LOW, GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.LOW, GoogleGenAiThinkingLevel.MEDIUM,
							GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_FLASH_PREVIEW.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.LOW,
							GoogleGenAiThinkingLevel.MEDIUM, GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_5_FLASH.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.LOW,
							GoogleGenAiThinkingLevel.MEDIUM, GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_5_FLASH_LITE.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.LOW,
							GoogleGenAiThinkingLevel.MEDIUM, GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_6_FLASH.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.LOW,
							GoogleGenAiThinkingLevel.MEDIUM, GoogleGenAiThinkingLevel.HIGH)),
			Map.entry(ChatModel.GEMINI_3_1_FLASH_LITE_IMAGE.getValue(),
					EnumSet.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.HIGH)));

	/**
	 * Validates that the requested {@code thinkingLevel} is supported by the target
	 * model, using {@link #THINKING_LEVEL_SUPPORT_BY_MODEL}. Models that are not part of
	 * that matrix are not validated here and are left to the API to accept or reject.
	 * @param level the thinking level to validate
	 * @param modelName the model name
	 * @throws IllegalArgumentException if the level is not supported for the model
	 */
	private static void validateThinkingLevelForModel(GoogleGenAiThinkingLevel level, String modelName) {
		if (level == null || level == GoogleGenAiThinkingLevel.THINKING_LEVEL_UNSPECIFIED || modelName == null) {
			return;
		}
		// Vertex AI style full resource names (e.g.
		// "projects/{project}/locations/{location}/publishers/google/models/{model}")
		// carry the model id as the last path segment.
		String modelId = modelName.substring(modelName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		Set<GoogleGenAiThinkingLevel> supportedLevels = THINKING_LEVEL_SUPPORT_BY_MODEL.get(modelId);
		if (supportedLevels == null) {
			return;
		}
		if (!supportedLevels.contains(level)) {
			if (supportedLevels.isEmpty()) {
				throw new IllegalArgumentException(String.format(
						"ThinkingLevel.%s is not supported for model '%s'. This model does not support thinkingLevel; use thinkingBudget instead.",
						level, modelName));
			}
			throw new IllegalArgumentException(
					String.format("ThinkingLevel.%s is not supported for model '%s'. Supported levels: %s.", level,
							modelName, supportedLevels.stream().map(Enum::name).collect(Collectors.joining(", "))));
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

		GEMINI_3_0_PRO_PREVIEW("gemini-3-pro-preview"),

		GEMINI_3_1_PRO_PREVIEW("gemini-3.1-pro-preview"),

		GEMINI_3_FLASH_PREVIEW("gemini-3-flash-preview"),

		GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite"),

		GEMINI_3_1_FLASH_LITE_IMAGE("gemini-3.1-flash-lite-image"),

		GEMINI_3_5_FLASH("gemini-3.5-flash"),

		GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite"),

		GEMINI_3_6_FLASH("gemini-3.6-flash");

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

	/**
	 * Gemini streams parts without a content block index, so this synthesizes one for the
	 * {@link StreamingParts} contract. Consecutive parts of the same delta kind (answer
	 * text, thought text) share an index and are stamped partial, so that
	 * {@link MessageAggregator} concatenates them and keeps the last thought signature,
	 * which Gemini sends on the final delta. Every other part is complete and gets an
	 * index of its own.
	 * <p>
	 * An empty text part without a signature, such as the placeholder of a chunk without
	 * content, carries nothing: it is left without an index and does not end the current
	 * delta, so that a chunk holding only usage or safety ratings in the middle of a
	 * thought does not split it in two. Not thread-safe: one instance serves one
	 * candidate of one stream.
	 */
	private static final class StreamingPartIndexer {

		private int index = -1;

		private @Nullable Class<? extends MessagePart> lastDeltaKind;

		Generation stamp(Generation generation) {
			AssistantMessage output = generation.getOutput();
			if (output.getClass() != AssistantMessage.class) {
				// An override of responseCandidateToGeneration returned its own message
				// type, which a rebuild would lose.
				return generation;
			}
			AssistantMessage.Builder<?> builder = AssistantMessage.builder().properties(output.getMetadata());
			if (output.getParts().isEmpty()) {
				builder.content("");
			}
			for (MessagePart part : output.getParts()) {
				builder.part(stamp(part));
			}
			return new Generation(builder.build(), generation.getMetadata());
		}

		private MessagePart stamp(MessagePart part) {
			if (part instanceof TextPart textPart && textPart.text().isEmpty() && textPart.payload() == null) {
				return part;
			}
			boolean delta = part instanceof TextPart || part instanceof ReasoningPart;
			if (!delta || part.getClass() != this.lastDeltaKind) {
				this.index++;
			}
			this.lastDeltaKind = delta ? part.getClass() : null;
			return delta ? StreamingParts.partial(part, this.index) : StreamingParts.complete(part, this.index);
		}

	}

	@JsonInclude(Include.NON_NULL)
	public record GeminiRequest(List<Content> contents, String modelName, GenerateContentConfig config) {

	}

	@JsonDeserialize(builder = Schema.Builder.class)
	private static class SchemaMixin {

	}

}
