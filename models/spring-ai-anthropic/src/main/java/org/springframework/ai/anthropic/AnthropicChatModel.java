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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Source;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.anthropic.api.CitationDocument;
import org.springframework.ai.anthropic.api.utils.CacheEligibilityResolver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
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
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * The {@link ChatModel} implementation for the Anthropic service.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 * @author Claudio Silva Junior
 * @author Alexandros Pappas
 * @author Jonghoon Park
 * @author Soby Chacko
 * @author Austin Dase
 * @since 1.0.0
 */
public class AnthropicChatModel implements ChatModel {

	public static final String DEFAULT_MODEL_NAME = AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getValue();

	public static final Integer DEFAULT_MAX_TOKENS = 500;

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The lower-level API for the Anthropic service.
	 */
	private final AnthropicApi anthropicApi;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final AnthropicChatOptions defaultOptions;

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

	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		this(anthropicApi, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		Assert.notNull(anthropicApi, "anthropicApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AnthropicApi.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletionResponse> completionEntity = RetryUtils.execute(this.retryTemplate,
						() -> this.anthropicApi.chatCompletionEntity(request, this.getAdditionalHttpHeaders(prompt)));

				AnthropicApi.ChatCompletionResponse completionResponse = Objects
					.requireNonNull(completionEntity.getBody());

				AnthropicApi.Usage usage = completionResponse.usage();
				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(usage) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
						previousChatResponse);

				ChatResponse chatResponse = toChatResponse(completionEntity.getBody(), accumulatedUsage);
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

	private DefaultUsage getDefaultUsage(AnthropicApi.@Nullable Usage usage) {
		Integer inputTokens = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0;
		Integer outputTokens = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0;
		return new DefaultUsage(inputTokens, outputTokens, inputTokens + outputTokens, usage);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AnthropicApi.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(request,
					this.getAdditionalHttpHeaders(prompt));

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = response.flatMap(chatCompletionResponse -> {
				AnthropicApi.Usage usage = chatCompletionResponse.usage();
				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(usage) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);
				ChatResponse chatResponse = toChatResponse(chatCompletionResponse, accumulatedUsage);

			if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)) {

				if (chatResponse.hasFinishReasons(Set.of("tool_use"))) {
					return Flux.deferContextual(ctx -> {
						// TODO: factor out the tool execution logic with setting context into a utility.
						ToolCallReactiveContextHolder.setContext(ctx);
						return this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse)
							.doFinally(s -> ToolCallReactiveContextHolder.clearContext())
							.flatMapMany(toolExecutionResult -> {
								if (toolExecutionResult.returnDirect()) {
									// Return tool execution result directly to the client.
									return Flux.just(ChatResponse.builder().from(chatResponse)
										.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
										.build());
								}
								else {
									// Send the tool execution result back to the model.
									return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
											chatResponse);
								}
							});
					});
				}
				else {
					return Mono.empty();
				}
			}
			else {
				// If internal tool execution is not required, just return the chat response.
				return Mono.just(chatResponse);
			}
			})
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	private ChatResponse toChatResponse(@Nullable ChatCompletionResponse chatCompletion, Usage usage) {

		if (chatCompletion == null) {
			logger.warn("Null chat completion returned");
			return new ChatResponse(List.of());
		}

		List<Generation> generations = new ArrayList<>();
		List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
		CitationContext citationContext = new CitationContext();
		for (ContentBlock content : chatCompletion.content()) {
			switch (content.type()) {
				case TEXT, TEXT_DELTA:
					Generation textGeneration = processTextContent(content, chatCompletion.stopReason(),
							citationContext);
					generations.add(textGeneration);
					break;
				case THINKING:
					Map<String, Object> thinkingProperties = new HashMap<>();
					String signature = content.signature();
					Assert.notNull(signature, "The signature of the content can't be null");
					Assert.notNull(content.thinking(), "The thinking of the content can't be null");
					thinkingProperties.put("signature", signature);
					generations.add(new Generation(
							AssistantMessage.builder()
								.content(content.thinking())
								.properties(thinkingProperties)
								.build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case THINKING_DELTA:
					Assert.notNull(content.thinking(), "The thinking of the content can't be null");
					generations.add(new Generation(AssistantMessage.builder().content(content.thinking()).build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case SIGNATURE_DELTA:
					Map<String, Object> signatureProperties = new HashMap<>();
					String sig = content.signature();
					Assert.notNull(sig, "The signature of the content can't be null");
					signatureProperties.put("signature", sig);
					generations.add(new Generation(
							AssistantMessage.builder().content("").properties(signatureProperties).build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case REDACTED_THINKING:
					Map<String, Object> redactedProperties = new HashMap<>();
					String data = content.data();
					Assert.notNull(data, "The data of the content must not be null");
					redactedProperties.put("data", data);
					generations.add(new Generation(AssistantMessage.builder().properties(redactedProperties).build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case TOOL_USE:
					var functionCallId = content.id();
					Assert.notNull(functionCallId, "The id of the content must not be null");
					var functionName = content.name();
					Assert.notNull(functionName, "The name of the content must not be null");
					var functionArguments = JsonParser.toJson(content.input());
					toolCalls.add(
							new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
					break;
				default:
					logger.warn("Unsupported content block type: {}", content.type());
			}
		}

		if (chatCompletion.stopReason() != null && generations.isEmpty()) {
			Generation generation = new Generation(AssistantMessage.builder().content("").properties(Map.of()).build(),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			generations.add(generation);
		}

		if (!CollectionUtils.isEmpty(toolCalls)) {
			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(toolCalls)
				.build();
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			generations.add(toolCallGeneration);
		}

		// Create response metadata with citation information if present
		ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
			.id(chatCompletion.id())
			.model(chatCompletion.model())
			.usage(usage)
			.keyValue("stop-reason", chatCompletion.stopReason())
			.keyValue("stop-sequence", chatCompletion.stopSequence())
			.keyValue("type", chatCompletion.type())
			.keyValue("anthropic-response", chatCompletion);

		// Add citation metadata if citations were found
		if (citationContext.hasCitations()) {
			metadataBuilder.keyValue("citations", citationContext.getAllCitations())
				.keyValue("citationCount", citationContext.getTotalCitationCount());
		}

		ChatResponseMetadata responseMetadata = metadataBuilder.build();

		return new ChatResponse(generations, responseMetadata);
	}

	private Generation processTextContent(ContentBlock content, @Nullable String stopReason,
			CitationContext citationContext) {
		// Extract citations if present in the content block
		if (content.citations() instanceof List) {
			try {
				@SuppressWarnings("unchecked")
				List<Object> citationObjects = (List<Object>) content.citations();

				List<Citation> citations = new ArrayList<>();
				for (Object citationObj : citationObjects) {
					if (citationObj instanceof Map) {
						// Convert Map to CitationResponse using manual parsing
						AnthropicApi.CitationResponse citationResponse = parseCitationFromMap((Map<?, ?>) citationObj);
						citations.add(convertToCitation(citationResponse));
					}
					else {
						logger.warn("Unexpected citation object type: {}. Expected Map but got: {}. Skipping citation.",
								citationObj.getClass().getName(), citationObj);
					}
				}

				if (!citations.isEmpty()) {
					citationContext.addCitations(citations);
				}

			}
			catch (Exception e) {
				logger.warn("Failed to parse citations from content block", e);
			}
		}

		return new Generation(new AssistantMessage(content.text()),
				ChatGenerationMetadata.builder().finishReason(stopReason).build());
	}

	/**
	 * Parse citation data from Map (typically from JSON deserialization). Assumes all
	 * required fields are present and of correct types.
	 * @param citationMap the map containing citation data from API response
	 * @return parsed CitationResponse
	 */
	private AnthropicApi.CitationResponse parseCitationFromMap(Map<?, ?> citationMap) {
		String type = (String) citationMap.get("type");
		Assert.notNull(type, "The citation map must contain a 'type' entry");
		String citedText = (String) citationMap.get("cited_text");
		Assert.notNull(citedText, "The citation map must contain a 'cited_text' entry");
		Integer documentIndex = (Integer) citationMap.get("document_index");
		Assert.notNull(documentIndex, "The citation map must contain a 'document_index' entry");

		String documentTitle = (String) citationMap.get("document_title");
		Integer startCharIndex = (Integer) citationMap.get("start_char_index");
		Integer endCharIndex = (Integer) citationMap.get("end_char_index");
		Integer startPageNumber = (Integer) citationMap.get("start_page_number");
		Integer endPageNumber = (Integer) citationMap.get("end_page_number");
		Integer startBlockIndex = (Integer) citationMap.get("start_block_index");
		Integer endBlockIndex = (Integer) citationMap.get("end_block_index");

		return new AnthropicApi.CitationResponse(type, citedText, documentIndex, documentTitle, startCharIndex,
				endCharIndex, startPageNumber, endPageNumber, startBlockIndex, endBlockIndex);
	}

	/**
	 * Convert CitationResponse to Citation object. This method handles the conversion to
	 * avoid circular dependencies.
	 */
	private Citation convertToCitation(AnthropicApi.CitationResponse citationResponse) {
		return switch (citationResponse.type()) {
			case "char_location" -> {
				Integer startCharIndex = citationResponse.startCharIndex();
				Assert.notNull(startCharIndex, "citationResponse.startCharIndex() must not be null");
				Integer endCharIndex = citationResponse.endCharIndex();
				Assert.notNull(endCharIndex, "citationResponse.endCharIndex() must not be null");
				yield Citation.ofCharLocation(citationResponse.citedText(), citationResponse.documentIndex(),
						citationResponse.documentTitle(), startCharIndex, endCharIndex);
			}
			case "page_location" -> {
				Integer startPageNumber = citationResponse.startPageNumber();
				Assert.notNull(startPageNumber, "citationResponse.startPageNumber() must not be null");
				Integer endPageNumber = citationResponse.endPageNumber();
				Assert.notNull(endPageNumber, "citationResponse.endPageNumber() must not be null");
				yield Citation.ofPageLocation(citationResponse.citedText(), citationResponse.documentIndex(),
						citationResponse.documentTitle(), startPageNumber, endPageNumber);
			}
			case "content_block_location" -> {
				Integer startBlockIndex = citationResponse.startBlockIndex();
				Assert.notNull(startBlockIndex, "citationResponse.startBlockIndex() must not be null");
				Integer endBlockIndex = citationResponse.endBlockIndex();
				Assert.notNull(endBlockIndex, "citationResponse.endBlockIndex() must not be null");
				yield Citation.ofContentBlockLocation(citationResponse.citedText(), citationResponse.documentIndex(),
						citationResponse.documentTitle(), startBlockIndex, endBlockIndex);
			}
			default -> throw new IllegalArgumentException("Unknown citation type: " + citationResponse.type());
		};
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result) {
		return from(result, this.getDefaultUsage(result.usage()));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result, Usage usage) {
		Assert.notNull(result, "Anthropic ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("stop-reason", result.stopReason())
			.keyValue("stop-sequence", result.stopSequence())
			.keyValue("type", result.type())
			.build();
	}

	private Source getSourceByMedia(Media media) {
		String data = this.fromMediaData(media.getData());

		// http is not allowed and redirect not allowed
		if (data.startsWith("https://")) {
			return new Source(data);
		}
		else {
			return new Source(media.getMimeType().toString(), data);
		}
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}

	}

	private Type getContentBlockTypeByMedia(Media media) {
		String mimeType = media.getMimeType().toString();
		if (mimeType.startsWith("image")) {
			return Type.IMAGE;
		}
		else if (mimeType.contains("pdf")) {
			return Type.DOCUMENT;
		}
		throw new IllegalArgumentException("Unsupported media type: " + mimeType
				+ ". Supported types are: images (image/*) and PDF documents (application/pdf)");
	}

	private HttpHeaders getAdditionalHttpHeaders(Prompt prompt) {

		Map<String, String> headers = new HashMap<>(this.defaultOptions.getHttpHeaders());
		if (prompt.getOptions() instanceof AnthropicChatOptions chatOptions) {
			headers.putAll(chatOptions.getHttpHeaders());
		}
		HttpHeaders httpHeaders = new HttpHeaders();
		headers.forEach(httpHeaders::add);
		return httpHeaders;
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		AnthropicChatOptions runtimeOptions = null;
		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
					AnthropicChatOptions.class);
		}
		else {
			runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					AnthropicChatOptions.class);
		}

		// Define request options by merging runtime options and default options
		AnthropicChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				AnthropicChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			if (runtimeOptions.getFrequencyPenalty() != null) {
				logger.warn("The frequencyPenalty option is not supported by Anthropic API. Ignoring.");
			}
			if (runtimeOptions.getPresencePenalty() != null) {
				logger.warn("The presencePenalty option is not supported by Anthropic API. Ignoring.");
			}
			requestOptions.setHttpHeaders(
					mergeHttpHeaders(runtimeOptions.getHttpHeaders(), this.defaultOptions.getHttpHeaders()));
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));

			// Merge cache options that are Json-ignored
			requestOptions.setCacheOptions(runtimeOptions.getCacheOptions());

			// Merge citation documents that are Json-ignored
			if (!runtimeOptions.getCitationDocuments().isEmpty()) {
				requestOptions.setCitationDocuments(runtimeOptions.getCitationDocuments());
			}
			else if (!this.defaultOptions.getCitationDocuments().isEmpty()) {
				requestOptions.setCitationDocuments(this.defaultOptions.getCitationDocuments());
			}

			// Merge skillContainer that is Json-ignored
			if (runtimeOptions.getSkillContainer() != null) {
				requestOptions.setSkillContainer(runtimeOptions.getSkillContainer());
			}
			else if (this.defaultOptions.getSkillContainer() != null) {
				requestOptions.setSkillContainer(this.defaultOptions.getSkillContainer());
			}
		}
		else {
			requestOptions.setHttpHeaders(this.defaultOptions.getHttpHeaders());
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
			requestOptions.setCitationDocuments(this.defaultOptions.getCitationDocuments());
			requestOptions.setSkillContainer(this.defaultOptions.getSkillContainer());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	private Map<String, String> mergeHttpHeaders(Map<String, String> runtimeHttpHeaders,
			Map<String, String> defaultHttpHeaders) {
		var mergedHttpHeaders = new HashMap<>(defaultHttpHeaders);
		mergedHttpHeaders.putAll(runtimeHttpHeaders);
		return mergedHttpHeaders;
	}

	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		// Get caching strategy and options from the request
		AnthropicChatOptions requestOptions = null;
		if (prompt.getOptions() instanceof AnthropicChatOptions anthropicOptions) {
			requestOptions = anthropicOptions;
		}

		AnthropicCacheOptions cacheOptions = requestOptions != null ? requestOptions.getCacheOptions()
				: AnthropicCacheOptions.DISABLED;

		CacheEligibilityResolver cacheEligibilityResolver = CacheEligibilityResolver.from(cacheOptions);

		// Process system - as array if caching, string otherwise
		Object systemContent = buildSystemContent(prompt, cacheEligibilityResolver);

		// Build messages WITHOUT blanket cache control - strategic placement only
		List<AnthropicMessage> userMessages = buildMessages(prompt, cacheEligibilityResolver);

		// Build base request
		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getModel(), userMessages,
				systemContent, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(), stream);

		// Save toolChoice for later application (after code_execution tool is added)
		AnthropicApi.ToolChoice savedToolChoice = requestOptions != null ? requestOptions.getToolChoice() : null;
		AnthropicChatOptions mergeOptions = requestOptions;
		if (savedToolChoice != null && requestOptions != null) {
			// Create a copy without toolChoice to avoid premature merge
			mergeOptions = requestOptions.copy();
			mergeOptions.setToolChoice(null);
		}

		request = ModelOptionsUtils.merge(mergeOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions with potential caching
		Assert.state(requestOptions != null, "AnthropicChatOptions must not be null");
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
			List<AnthropicApi.Tool> tools = getFunctionTools(toolDefinitions);

			// Apply caching to tools if strategy includes them
			tools = addCacheToLastTool(tools, cacheEligibilityResolver);

			request = ChatCompletionRequest.from(request).tools(tools).build();
		}

		// Add Skills container from options if present
		AnthropicApi.SkillContainer skillContainer = null;
		if (requestOptions != null && requestOptions.getSkillContainer() != null) {
			skillContainer = requestOptions.getSkillContainer();
		}
		else if (this.defaultOptions.getSkillContainer() != null) {
			skillContainer = this.defaultOptions.getSkillContainer();
		}

		if (skillContainer != null) {
			request = ChatCompletionRequest.from(request).container(skillContainer).build();

			// Skills require the code_execution tool to be enabled
			// Add it if not already present
			List<AnthropicApi.Tool> existingTools = request.tools() != null ? new ArrayList<>(request.tools())
					: new ArrayList<>();
			boolean hasCodeExecution = existingTools.stream().anyMatch(tool -> "code_execution".equals(tool.name()));

			if (!hasCodeExecution) {
				existingTools
					.add(new AnthropicApi.Tool(AnthropicApi.CODE_EXECUTION_TOOL_TYPE, "code_execution", null, null));
				request = ChatCompletionRequest.from(request).tools(existingTools).build();
			}

			// Apply saved toolChoice now that code_execution tool has been added
			if (savedToolChoice != null) {
				request = ChatCompletionRequest.from(request).toolChoice(savedToolChoice).build();
			}
		}
		else if (savedToolChoice != null) {
			// No Skills but toolChoice was set - apply it now
			request = ChatCompletionRequest.from(request).toolChoice(savedToolChoice).build();
		}

		// Add beta headers if needed
		if (requestOptions != null) {
			Map<String, String> headers = new HashMap<>(requestOptions.getHttpHeaders());
			boolean needsUpdate = false;

			// Add Skills beta headers if Skills are present
			// Skills require three beta headers: skills, code-execution, and files-api
			if (skillContainer != null) {
				String existingBeta = headers.get("anthropic-beta");
				String requiredBetas = AnthropicApi.BETA_SKILLS + "," + AnthropicApi.BETA_CODE_EXECUTION + ","
						+ AnthropicApi.BETA_FILES_API;

				if (existingBeta != null) {
					// Add missing beta headers
					if (!existingBeta.contains(AnthropicApi.BETA_SKILLS)) {
						existingBeta = existingBeta + "," + AnthropicApi.BETA_SKILLS;
					}
					if (!existingBeta.contains(AnthropicApi.BETA_CODE_EXECUTION)) {
						existingBeta = existingBeta + "," + AnthropicApi.BETA_CODE_EXECUTION;
					}
					if (!existingBeta.contains(AnthropicApi.BETA_FILES_API)) {
						existingBeta = existingBeta + "," + AnthropicApi.BETA_FILES_API;
					}
					headers.put("anthropic-beta", existingBeta);
				}
				else {
					headers.put("anthropic-beta", requiredBetas);
				}
				needsUpdate = true;
			}

			// Add extended cache TTL beta header if needed
			if (cacheOptions.getMessageTypeTtl().containsValue(AnthropicCacheTtl.ONE_HOUR)) {
				String existingBeta = headers.get("anthropic-beta");
				if (existingBeta != null && !existingBeta.contains(AnthropicApi.BETA_EXTENDED_CACHE_TTL)) {
					headers.put("anthropic-beta", existingBeta + "," + AnthropicApi.BETA_EXTENDED_CACHE_TTL);
				}
				else if (existingBeta == null) {
					headers.put("anthropic-beta", AnthropicApi.BETA_EXTENDED_CACHE_TTL);
				}
				needsUpdate = true;
			}

			if (needsUpdate) {
				requestOptions.setHttpHeaders(headers);
			}
		}

		return request;
	}

	/**
	 * Helper method to serialize content from ContentBlock. The content field can be
	 * either a String or a complex object (for Skills responses).
	 * @param content The content to serialize
	 * @return String representation of the content, or null if content is null
	 */
	private static @Nullable String serializeContent(@Nullable Object content) {
		if (content == null) {
			return null;
		}
		if (content instanceof String s) {
			return s;
		}
		return JsonParser.toJson(content);
	}

	private static ContentBlock cacheAwareContentBlock(ContentBlock contentBlock, MessageType messageType,
			CacheEligibilityResolver cacheEligibilityResolver) {
		String basisForLength = switch (contentBlock.type()) {
			case TEXT, TEXT_DELTA -> contentBlock.text();
			case TOOL_RESULT -> serializeContent(contentBlock.content());
			case TOOL_USE -> JsonParser.toJson(contentBlock.input());
			case THINKING, THINKING_DELTA -> contentBlock.thinking();
			case REDACTED_THINKING -> contentBlock.data();
			default -> null;
		};
		return cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver, basisForLength);
	}

	private static ContentBlock cacheAwareContentBlock(ContentBlock contentBlock, MessageType messageType,
			CacheEligibilityResolver cacheEligibilityResolver, @Nullable String basisForLength) {
		ChatCompletionRequest.CacheControl cacheControl = cacheEligibilityResolver.resolve(messageType, basisForLength);
		if (cacheControl == null) {
			return contentBlock;
		}
		cacheEligibilityResolver.useCacheBlock();
		return ContentBlock.from(contentBlock).cacheControl(cacheControl).build();
	}

	private List<AnthropicApi.Tool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var name = toolDefinition.name();
			var description = toolDefinition.description();
			String inputSchema = toolDefinition.inputSchema();
			return new AnthropicApi.Tool(name, description, JsonParser.fromJson(inputSchema, new TypeReference<>() {
			}));
		}).toList();
	}

	/**
	 * Build messages strategically, applying cache control only where specified by the
	 * strategy.
	 */
	private List<AnthropicMessage> buildMessages(Prompt prompt, CacheEligibilityResolver cacheEligibilityResolver) {

		List<Message> allMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.toList();

		// Find the last user message (current question) for CONVERSATION_HISTORY strategy
		int lastUserIndex = -1;
		if (cacheEligibilityResolver.isCachingEnabled()) {
			for (int i = allMessages.size() - 1; i >= 0; i--) {
				if (allMessages.get(i).getMessageType() == MessageType.USER) {
					lastUserIndex = i;
					break;
				}
			}
		}

		// Get citation documents from options
		List<CitationDocument> citationDocuments = null;
		if (prompt.getOptions() instanceof AnthropicChatOptions anthropicOptions) {
			citationDocuments = anthropicOptions.getCitationDocuments();
		}

		List<AnthropicMessage> result = new ArrayList<>();
		for (int i = 0; i < allMessages.size(); i++) {
			Message message = allMessages.get(i);
			MessageType messageType = message.getMessageType();
			if (messageType == MessageType.USER) {
				List<ContentBlock> contentBlocks = new ArrayList<>();
				// Add citation documents to the FIRST user message only
				if (i == 0 && citationDocuments != null && !citationDocuments.isEmpty()) {
					for (CitationDocument doc : citationDocuments) {
						contentBlocks.add(doc.toContentBlock());
					}
				}
				String content = message.getText();
				// For conversation history caching, apply cache control to the
				// last user message to cache the entire conversation up to that point.
				boolean isLastUserMessage = (lastUserIndex >= 0) && (i == lastUserIndex);
				ContentBlock contentBlock = new ContentBlock(content);
				if (isLastUserMessage && cacheEligibilityResolver.isCachingEnabled()) {
					// Combine text from all messages (user, assistant, tool) up to and
					// including the last user message as the basis for cache eligibility
					// checks
					String combinedMessagesText = combineEligibleMessagesText(allMessages, lastUserIndex);
					contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver,
							combinedMessagesText));
				}
				else {
					contentBlocks.add(contentBlock);
				}
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<ContentBlock> mediaContent = userMessage.getMedia().stream().map(media -> {
							Type contentBlockType = getContentBlockTypeByMedia(media);
							var source = getSourceByMedia(media);
							return new ContentBlock(contentBlockType, source);
						}).toList();
						contentBlocks.addAll(mediaContent);
					}
				}
				result.add(new AnthropicMessage(contentBlocks, Role.valueOf(message.getMessageType().name())));
			}
			else if (messageType == MessageType.ASSISTANT) {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				List<ContentBlock> contentBlocks = new ArrayList<>();
				if (StringUtils.hasText(message.getText())) {
					ContentBlock contentBlock = new ContentBlock(message.getText());
					contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver));
				}
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
						ContentBlock contentBlock = new ContentBlock(Type.TOOL_USE, toolCall.id(), toolCall.name(),
								ModelOptionsUtils.jsonToMap(toolCall.arguments()));
						contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver));
					}
				}
				result.add(new AnthropicMessage(contentBlocks, Role.ASSISTANT));
			}
			else if (messageType == MessageType.TOOL) {
				List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
					.stream()
					.map(toolResponse -> new ContentBlock(Type.TOOL_RESULT, toolResponse.id(),
							toolResponse.responseData()))
					.map(contentBlock -> cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver))
					.toList();
				result.add(new AnthropicMessage(toolResponses, Role.USER));
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}
		return result;
	}

	private String combineEligibleMessagesText(List<Message> allMessages, int lastUserIndex) {
		// Only 20 content blocks are considered by anthropic, so limit the number of
		// message content to consider. We include all message types (user, assistant,
		// tool)
		// up to and including the last user message for aggregate eligibility checking.
		int startIndex = Math.max(0, lastUserIndex - 19);
		int endIndex = Math.min(allMessages.size(), lastUserIndex + 1);
		StringBuilder sb = new StringBuilder();
		for (int i = startIndex; i < endIndex; i++) {
			Message message = allMessages.get(i);
			String text = message.getText();
			if (StringUtils.hasText(text)) {
				sb.append(text);
			}
		}
		return sb.toString();
	}

	/**
	 * Build system content - as array if caching, string otherwise.
	 */
	private @Nullable Object buildSystemContent(Prompt prompt, CacheEligibilityResolver cacheEligibilityResolver) {

		String systemText = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(Message::getText)
			.collect(Collectors.joining(System.lineSeparator()));

		if (!StringUtils.hasText(systemText)) {
			return null;
		}

		// Use array format when caching system
		if (cacheEligibilityResolver.isCachingEnabled()) {
			return List
				.of(cacheAwareContentBlock(new ContentBlock(systemText), MessageType.SYSTEM, cacheEligibilityResolver));
		}

		// Use string format when not caching (backward compatible)
		return systemText;
	}

	/**
	 * Add cache control to the last tool for deterministic caching.
	 */
	private List<AnthropicApi.Tool> addCacheToLastTool(List<AnthropicApi.Tool> tools,
			CacheEligibilityResolver cacheEligibilityResolver) {

		ChatCompletionRequest.CacheControl cacheControl = cacheEligibilityResolver.resolveToolCacheControl();

		if (cacheControl == null || tools == null || tools.isEmpty()) {
			return tools;
		}

		List<AnthropicApi.Tool> modifiedTools = new ArrayList<>();
		for (int i = 0; i < tools.size(); i++) {
			AnthropicApi.Tool tool = tools.get(i);
			if (i == tools.size() - 1) {
				// Add cache control to last tool
				tool = new AnthropicApi.Tool(tool.type(), tool.name(), tool.description(), tool.inputSchema(),
						cacheControl);
				cacheEligibilityResolver.useCacheBlock();
			}
			modifiedTools.add(tool);
		}
		return modifiedTools;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
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

		private @Nullable AnthropicApi anthropicApi;

		private AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(DEFAULT_MODEL_NAME)
			.maxTokens(DEFAULT_MAX_TOKENS)
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private @Nullable ToolCallingManager toolCallingManager;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private Builder() {
		}

		public Builder anthropicApi(AnthropicApi anthropicApi) {
			this.anthropicApi = anthropicApi;
			return this;
		}

		public Builder defaultOptions(AnthropicChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
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

		public AnthropicChatModel build() {
			Assert.state(this.anthropicApi != null, "AnthropicApi must not be null");
			return new AnthropicChatModel(this.anthropicApi, this.defaultOptions,
					Objects.requireNonNullElse(this.toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER),
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

	/**
	 * Context object for tracking citations during response processing. Aggregates
	 * citations from multiple content blocks in a single response.
	 */
	class CitationContext {

		private final List<Citation> allCitations = new ArrayList<>();

		public void addCitations(List<Citation> citations) {
			this.allCitations.addAll(citations);
		}

		public boolean hasCitations() {
			return !this.allCitations.isEmpty();
		}

		public List<Citation> getAllCitations() {
			return new ArrayList<>(this.allCitations);
		}

		public int getTotalCitationCount() {
			return this.allCitations.size();
		}

	}

}
