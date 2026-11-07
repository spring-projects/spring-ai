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

package org.springframework.ai.mistralai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingPartIndexer;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion.Choice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ReferenceChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.TextChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ThinkChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ThinkingContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * Represents a Mistral AI Chat Model.
 * <p>
 * The assistant message of each choice is built from ordered {@link MessagePart}s that
 * follow the content chunks Mistral returns: a text chunk, or a plain string content,
 * becomes a {@link TextPart}, a thinking chunk a {@link ReasoningPart}, and any other
 * chunk, such as the reference of a citation, an {@link UnknownPart} that holds it
 * verbatim. Tool calls are a separate field of a Mistral message, so their
 * {@link ToolCallPart}s always follow the content.
 * <p>
 * An assistant message that carries Mistral reasoning or other Mistral chunks is replayed
 * as content chunks in part order, so a reasoning model gets its earlier thinking back
 * next to the answer it led to, as Mistral recommends. Any other assistant message is
 * sent as its text, as before.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Thomas Vitale
 * @author luocongqiu
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @author Nicolas Krier
 * @author Jason Smith
 * @author Sebastien Deleuze
 * @author Dimitar Proynov
 * @since 1.0.0
 */
public class MistralAiChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	public static final String REFERENCE_CONTENT_METADATA = "reference_content";

	public static final String REFERENCE_THINKING_CONTENT_METADATA = "reference_thinking_content";

	/**
	 * Key of the thinking text in the message metadata. Superseded by the
	 * {@link ReasoningPart}s of the message, see {@link AssistantMessage#getReasoning()}.
	 * Still written for backward compatibility, and read on replay only from an assistant
	 * message without reasoning parts, such as one built by an earlier version. Unlike
	 * the text of a {@link ReasoningPart}, it joins the text chunks of the thinking with
	 * a line separator.
	 * @deprecated since 2.1.0 in favor of {@link AssistantMessage#getReasoning()}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public static final String THINKING_CONTENT_METADATA = "thinking_content";

	/**
	 * The {@link OpaquePayload#kind() kind} of the payload that holds the signature of a
	 * thinking chunk.
	 * @since 2.1.0
	 */
	public static final String SIGNATURE_PAYLOAD_KIND = "signature";

	private static final String PROVIDER = MistralAiApi.PROVIDER_NAME;

	private static final JsonHelper jsonHelper = new JsonHelper();

	private static final Log logger = LogFactory.getLog(MistralAiChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MistralAiChatOptions options;

	/**
	 * Low-level access to the Mistral API.
	 */
	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public MistralAiChatModel(MistralAiApi mistralAiApi, MistralAiChatOptions options,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(mistralAiApi, "mistralAiApi cannot be null");
		Assert.notNull(options, "options cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		this.mistralAiApi = mistralAiApi;
		this.options = options;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	public static ChatResponseMetadata from(MistralAiApi.ChatCompletion result) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		var usage = result.usage();
		Assert.notNull(usage, "Mistral AI ChatCompletion usage must not be null");
		var defaultUsage = getDefaultUsage(usage);
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(defaultUsage)
			.keyValue("created", result.created())
			.build();
	}

	public static ChatResponseMetadata from(MistralAiApi.ChatCompletion result, Usage usage) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("created", result.created())
			.build();
	}

	private static DefaultUsage getDefaultUsage(MistralAiApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		MistralAiApi.ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(MistralAiApi.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
						() -> this.mistralAiApi.chatCompletionEntity(request));

				ChatCompletion chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					if (logger.isWarnEnabled()) {
						logger.warn("No chat completion returned for prompt: " + prompt);
					}
					return new ChatResponse(List.of());
				}

				List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
					var role = choice.message().role() != null ? choice.message().role().name() : "";
					var metadata = buildMetadata(choice, chatCompletion.id(), role);

					return buildGeneration(choice, metadata, null);
				}).toList();

				ChatCompletion completion = Objects.requireNonNull(completionEntity.getBody());
				var usage = Objects.requireNonNull(completion.usage());
				DefaultUsage defaultUsage = getDefaultUsage(usage);
				Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(defaultUsage, previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations,
						from(completionEntity.getBody(), cumulativeUsage));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			var request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(MistralAiApi.PROVIDER_NAME)
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

			Flux<ChatCompletionChunk> completionChunks = RetryUtils.execute(this.retryTemplate,
					() -> this.mistralAiApi.chatCompletionStream(request));

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			// Mistral streams content chunks without an index, so the parts of each
			// choice of this stream get one synthesized index sequence.
			ConcurrentHashMap<Integer, StreamingPartIndexer> partIndexers = new ConcurrentHashMap<>();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::toChatCompletion).map(chatCompletion -> {
				try {
					String id = chatCompletion.id();

					List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
						if (choice.message().role() != null) {
							roleMap.putIfAbsent(id, choice.message().role().name());
						}

						var role = roleMap.getOrDefault(id, "");
						var metadata = buildMetadata(choice, chatCompletion.id(), role);
						var partIndexer = partIndexers.computeIfAbsent(Objects.requireNonNullElse(choice.index(), 0),
								index -> new StreamingPartIndexer());

						return buildGeneration(choice, metadata, partIndexer);
					}).toList();

					// Every chunk carries the response id, which MessageAggregator groups
					// the streamed parts by.
					Usage usage = (chatCompletion.usage() != null) ? UsageCalculator.getCumulativeUsage(
							getDefaultUsage(chatCompletion.usage()), previousChatResponse) : new EmptyUsage();
					return new ChatResponse(generations, from(chatCompletion, usage));
				}
				catch (Exception e) {
					logger.error("Error processing chat completion", e);
					return new ChatResponse(List.of());
				}
			});

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = chatResponse.flatMap(response ->
					Flux.just(response))
				.doOnNext(ignored -> observationContext.recordTimeToFirstChunk())
				.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});

	}

	private Map<String, Object> buildMetadata(Choice choice, String id, String role) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("id", id);
		metadata.put("role", role);
		metadata.put("index", choice.index());
		metadata.put("finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
		var message = choice.message();
		var referenceContent = message.extractReferenceContent();

		if (referenceContent != null) {
			metadata.put(REFERENCE_CONTENT_METADATA, referenceContent);
		}

		var referenceThinkingContent = message.extractThinkingReferenceContent();

		if (referenceThinkingContent != null) {
			metadata.put(REFERENCE_THINKING_CONTENT_METADATA, referenceThinkingContent);
		}

		var thinkingContent = message.extractThinkingTextContent();

		if (thinkingContent != null) {
			metadata.put(THINKING_CONTENT_METADATA, thinkingContent);
		}

		return Map.copyOf(metadata);
	}

	/**
	 * Builds the generation of one choice. When {@code partIndexer} is not {@code null}
	 * the choice is a streamed chunk, and its parts are stamped with their stream index,
	 * see {@link StreamingPartIndexer}.
	 */
	private Generation buildGeneration(Choice choice, Map<String, Object> metadata,
			@Nullable StreamingPartIndexer partIndexer) {
		var assistantMessage = AssistantMessage.builder()
			.parts(toMessageParts(choice.message(), partIndexer))
			.properties(metadata)
			.build();
		var finishReason = choice.finishReason() != null ? choice.finishReason().name() : "";
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Maps a Mistral message to ordered {@link MessagePart}s: one part per content chunk,
	 * in chunk order, then one {@link ToolCallPart} per tool call.
	 * <p>
	 * An empty text yields no part, so that a tool-call answer has no empty text part
	 * before its tool calls. A message without any part, such as the role-only first
	 * chunk of a stream, keeps an empty text, so that its text is not {@code null}.
	 */
	private static List<MessagePart> toMessageParts(ChatCompletionMessage message,
			@Nullable StreamingPartIndexer partIndexer) {
		List<MessagePart> parts = new ArrayList<>();
		Object content = message.content();
		if (content instanceof String text) {
			// hasLength, not hasText: a streamed delta is often a single whitespace or
			// newline token that must be kept.
			if (StringUtils.hasLength(text)) {
				parts.add(TextPart.of(text));
			}
		}
		else if (content instanceof List<?> chunks) {
			for (Object chunk : chunks) {
				MessagePart part = toMessagePart(chunk);
				if (part != null) {
					parts.add(part);
				}
			}
		}
		if (message.toolCalls() != null) {
			for (ToolCall toolCall : message.toolCalls()) {
				parts.add(ToolCallPart.of(mapToolCall(toolCall)));
			}
		}
		if (partIndexer != null) {
			parts.replaceAll(partIndexer::stamp);
		}
		if (parts.isEmpty()) {
			parts.add(TextPart.of(""));
		}
		return parts;
	}

	private static @Nullable MessagePart toMessagePart(Object chunk) {
		if (chunk instanceof TextChunk textChunk) {
			return StringUtils.hasLength(textChunk.text()) ? TextPart.of(textChunk.text()) : null;
		}
		if (chunk instanceof ThinkChunk thinkChunk) {
			return toReasoningPart(thinkChunk);
		}
		if (chunk instanceof ReferenceChunk) {
			return new UnknownPart(PROVIDER, "reference", jsonHelper.toJson(chunk), null, Map.of());
		}
		if (chunk instanceof ImageUrlChunk) {
			return new UnknownPart(PROVIDER, "image_url", jsonHelper.toJson(chunk), null, Map.of());
		}
		throw new IllegalStateException("Unhandled content chunk type: " + chunk.getClass().getName());
	}

	/**
	 * Maps a thinking chunk to a {@link ReasoningPart} with the text of the chunk, the
	 * signature, if any, as its {@link #SIGNATURE_PAYLOAD_KIND} payload, and a
	 * {@link MessagePart#PROVIDER_ATTRIBUTE provider} attribute, so that reasoning
	 * Mistral returned without a signature can be told apart from reasoning produced by
	 * another provider on replay. References within the thinking are not part of the
	 * text; they remain available under {@link #REFERENCE_THINKING_CONTENT_METADATA}.
	 * @return the part, or {@code null} for a chunk with neither text nor signature, such
	 * as the closing thinking chunk of a stream
	 */
	private static @Nullable ReasoningPart toReasoningPart(ThinkChunk thinkChunk) {
		String text = Optional.of(thinkChunk.thinking())
			.stream()
			.flatMap(List::stream)
			.filter(TextChunk.class::isInstance)
			.map(TextChunk.class::cast)
			.map(TextChunk::text)
			.collect(Collectors.joining());
		String signature = thinkChunk.signature();
		OpaquePayload payload = StringUtils.hasLength(signature)
				? new OpaquePayload(PROVIDER, SIGNATURE_PAYLOAD_KIND, signature) : null;
		if (text.isEmpty() && payload == null) {
			return null;
		}
		return new ReasoningPart(text, null, payload, Map.of(MessagePart.PROVIDER_ATTRIBUTE, PROVIDER));
	}

	private static AssistantMessage.ToolCall mapToolCall(ToolCall toolCall) {
		return new AssistantMessage.ToolCall(toolCall.id(), "function", toolCall.function().name(),
				toolCall.function().arguments());
	}

	private ChatCompletion toChatCompletion(ChatCompletionChunk chunk) {
		// finishReason can be null in case of ChatCompletionChunk while it is not the
		// case for ChatCompletion that is why null checks are performed later on.
		List<Choice> choices = Objects.requireNonNull(chunk.choices())
			.stream()
			.map(cc -> new Choice(cc.index(), cc.delta(), cc.finishReason(), cc.logprobs()))
			.toList();

		return new ChatCompletion(chunk.id(), "chat.completion", Objects.requireNonNull(chunk.created()), chunk.model(),
				choices, chunk.usage());
	}

	/**
	 * Accessible for testing.
	 */
	MistralAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		// @formatter:off
		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
				.stream()
				.flatMap(this::createChatCompletionMessages)
				.toList();
		// @formatter:on

		var request = new MistralAiApi.ChatCompletionRequest(chatCompletionMessages, stream);
		MistralAiChatOptions options = (MistralAiChatOptions) Objects.requireNonNull(prompt.getOptions());

		// @formatter:off
		return new ChatCompletionRequest(
				ModelOptionsUtils.mergeOption(options.getModel(), request.model()),
				request.messages(),
				calculateToolsRequestParameter(options, request),
				ModelOptionsUtils.mergeOption(options.getToolChoice(), request.toolChoice()),
				ModelOptionsUtils.mergeOption(options.getTemperature(), request.temperature()),
				ModelOptionsUtils.mergeOption(options.getTopP(), request.topP()),
				ModelOptionsUtils.mergeOption(options.getMaxTokens(), request.maxTokens()),
				ModelOptionsUtils.mergeOption(options.getN(), request.n()),
				ModelOptionsUtils.mergeOption(options.getPresencePenalty(), request.presencePenalty()),
				ModelOptionsUtils.mergeOption(options.getFrequencyPenalty(), request.frequencyPenalty()),
				request.stream(),
				ModelOptionsUtils.mergeOption(options.getSafePrompt(), request.safePrompt()),
				ModelOptionsUtils.mergeOption(options.getStop(), request.stop()),
				ModelOptionsUtils.mergeOption(options.getReasoningEffort(), request.reasoningEffort()),
				ModelOptionsUtils.mergeOption(options.getRandomSeed(), request.randomSeed()),
				ModelOptionsUtils.mergeOption(options.getResponseFormat(), request.responseFormat())
		);
		// @formatter:on
	}

	private @Nullable List<MistralAiApi.FunctionTool> calculateToolsRequestParameter(MistralAiChatOptions options,
			ChatCompletionRequest request) {
		var tools = ModelOptionsUtils.mergeOption(options.getTools(), request.tools());
		var toolDefinitions = this.toolCallingManager.resolveToolDefinitions(options);

		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			tools = this.getFunctionTools(toolDefinitions);
		}

		return tools;
	}

	private Stream<ChatCompletionMessage> createChatCompletionMessages(Message message) {
		return switch (message.getMessageType()) {
			case USER -> Stream.of(createUserChatCompletionMessage(message));
			case SYSTEM -> Stream.of(createSystemChatCompletionMessage(message));
			case ASSISTANT -> Stream.of(createAssistantChatCompletionMessage(message));
			case TOOL -> createToolChatCompletionMessages(message);
			default -> throw new IllegalStateException("Unknown message type: " + message.getMessageType());
		};
	}

	private Stream<ChatCompletionMessage> createToolChatCompletionMessages(Message message) {
		if (message instanceof ToolResponseMessage toolResponseMessage) {
			// @formatter:off
			return toolResponseMessage.getResponses()
				.stream()
				.map(this::createToolChatCompletionMessage);
			// @formatter:on
		}
		else {
			throw new IllegalArgumentException("Unsupported tool message class: " + message.getClass().getName());
		}
	}

	private ChatCompletionMessage createToolChatCompletionMessage(ToolResponseMessage.ToolResponse toolResponse) {
		return new ChatCompletionMessage(toolResponse.responseData(), ChatCompletionMessage.Role.TOOL,
				toolResponse.name(), null, toolResponse.id());
	}

	private ChatCompletionMessage createAssistantChatCompletionMessage(Message message) {
		if (message instanceof AssistantMessage assistantMessage) {
			List<ToolCall> toolCalls = null;

			if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
				toolCalls = assistantMessage.getToolCalls().stream().map(this::mapToolCall).toList();
			}
			return new ChatCompletionMessage(assistantContent(assistantMessage), ChatCompletionMessage.Role.ASSISTANT,
					null, toolCalls, null);
		}
		else {
			throw new IllegalArgumentException("Unsupported assistant message class: " + message.getClass().getName());
		}
	}

	/**
	 * The content to send back for an assistant message: its parts as content chunks, in
	 * part order, when it carries a chunk other than text, else its text as before.
	 * <p>
	 * A {@link ReasoningPart} is replayed as a thinking chunk only when Mistral produced
	 * it, as told by its provider attribute, and its signature, if any, goes back with
	 * it. Mistral recommends replaying the thinking of its reasoning models, while
	 * reasoning from another provider is meaningless to it. A message without reasoning
	 * parts, such as one built by an earlier version, falls back to the thinking under
	 * {@link #THINKING_CONTENT_METADATA}, replayed first as Mistral returns it. An
	 * {@link UnknownPart} is replayed verbatim when it holds a Mistral chunk. Tool calls
	 * are sent in their own field, and media, which no Mistral chat model generates, is
	 * skipped.
	 */
	private static @Nullable Object assistantContent(AssistantMessage assistantMessage) {
		List<ContentChunk> chunks = new ArrayList<>();
		boolean textOnly = true;
		ThinkChunk legacyThinkChunk = legacyThinkChunk(assistantMessage);
		if (legacyThinkChunk != null) {
			chunks.add(legacyThinkChunk);
			textOnly = false;
		}
		for (MessagePart part : assistantMessage.getParts()) {
			if (part instanceof TextPart textPart) {
				if (!textPart.text().isEmpty()) {
					chunks.add(new TextChunk(textPart.text()));
				}
			}
			else if (part instanceof ReasoningPart reasoningPart) {
				ThinkChunk thinkChunk = toThinkChunk(reasoningPart);
				if (thinkChunk != null) {
					chunks.add(thinkChunk);
					textOnly = false;
				}
			}
			else if (part instanceof UnknownPart unknownPart) {
				ContentChunk contentChunk = toContentChunk(unknownPart);
				if (contentChunk != null) {
					chunks.add(contentChunk);
					textOnly = false;
				}
			}
			else if (part instanceof ToolResultPart) {
				// Reachable from application code, as AssistantMessage.Builder.part() is
				// public
				throw new IllegalArgumentException(
						"Tool results belong in a tool response message, not an assistant message");
			}
			else if (part instanceof MediaPart) {
				logger.debug("Not replaying media on an assistant message");
			}
			else if (!(part instanceof ToolCallPart)) {
				throw new IllegalStateException("Unhandled message part type: " + part.getClass().getName());
			}
		}
		return textOnly ? assistantMessage.getText() : chunks;
	}

	@SuppressWarnings("removal")
	private static @Nullable ThinkChunk legacyThinkChunk(AssistantMessage assistantMessage) {
		if (!assistantMessage.getReasoning().isEmpty()
				|| !(assistantMessage.getMetadata().get(THINKING_CONTENT_METADATA) instanceof String thinking)
				|| thinking.isEmpty()) {
			return null;
		}
		return new ThinkChunk(List.of(new TextChunk(thinking)), null);
	}

	private static @Nullable ThinkChunk toThinkChunk(ReasoningPart part) {
		OpaquePayload payload = part.payload();
		if (!PROVIDER.equals(part.attributes().get(MessagePart.PROVIDER_ATTRIBUTE)) || !part.replayableTo(PROVIDER)) {
			logger.debug("Not replaying reasoning that Mistral did not produce");
			return null;
		}
		String text = Objects.requireNonNullElse(part.text(), "");
		String signature = (payload != null && SIGNATURE_PAYLOAD_KIND.equals(payload.kind())) ? payload.data() : null;
		if (text.isEmpty() && signature == null) {
			return null;
		}
		List<ThinkingContentChunk> thinking = text.isEmpty() ? List.of() : List.of(new TextChunk(text));
		return new ThinkChunk(thinking, null, signature);
	}

	private static @Nullable ContentChunk toContentChunk(UnknownPart part) {
		if (!PROVIDER.equals(part.provider())) {
			logger.debug("Not replaying an unknown part of kind " + part.kind() + " produced by " + part.provider());
			return null;
		}
		try {
			return jsonHelper.fromJson(part.rawJson(), ContentChunk.class);
		}
		catch (RuntimeException ex) {
			// Better a turn missing one chunk than a failed turn
			logger.warn("Could not replay the Mistral content chunk of kind " + part.kind() + "; dropping it", ex);
			return null;
		}
	}

	private ChatCompletionMessage createSystemChatCompletionMessage(Message message) {
		String content = message.getText();
		Assert.state(content != null, "content must not be null");
		return new ChatCompletionMessage(content, ChatCompletionMessage.Role.SYSTEM);
	}

	private ChatCompletionMessage createUserChatCompletionMessage(Message message) {
		var content = message.getText();
		Assert.state(content != null, "content must not be null");

		if (message instanceof UserMessage userMessage && !CollectionUtils.isEmpty(userMessage.getMedia())) {
			// One chunk per part, in part order, so that text and images interleave as
			// the message was built. Messages built with text(...) and media(...) keep
			// sending the text first.
			List<ContentChunk> contentChunks = new ArrayList<>();
			for (MessagePart part : userMessage.getParts()) {
				if (part instanceof TextPart textPart) {
					if (!textPart.text().isEmpty()) {
						contentChunks.add(new TextChunk(textPart.text()));
					}
				}
				else if (part instanceof MediaPart mediaPart) {
					contentChunks.add(this.mapToImageUrlChunk(mediaPart.media()));
				}
				else {
					logger.debug("Skipping a user message part of type " + part.getClass().getSimpleName());
				}
			}

			return new ChatCompletionMessage(contentChunks, ChatCompletionMessage.Role.USER);
		}

		return new ChatCompletionMessage(content, ChatCompletionMessage.Role.USER);
	}

	private ToolCall mapToolCall(AssistantMessage.ToolCall toolCall) {
		var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());

		return new ToolCall(toolCall.id(), toolCall.type(), function, null);
	}

	private ImageUrlChunk mapToImageUrlChunk(Media media) {
		return new ImageUrlChunk(this.fromMediaData(media.getMimeType(), media.getData()));
	}

	private ChatCompletionMessage.ImageUrlChunk.ImageUrl fromMediaData(MimeType mimeType, Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			// Assume the bytes are an image.
			return ChatCompletionMessage.ImageUrlChunk.ImageUrl.fromImageData(mimeType, bytes);
		}
		else if (mediaData instanceof String text) {
			// Assume the text is a URL or a base64 encoded image prefixed by the user.
			return new ChatCompletionMessage.ImageUrlChunk.ImageUrl(text, null);
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}
	}

	private List<MistralAiApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new MistralAiApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new MistralAiApi.FunctionTool(function);
		}).toList();
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public MistralAiChatOptions getOptions() {
		return this.options;
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
	 * prompt using this model {@link ChatModel#getOptions() options}. Otherwise, use the
	 * prompt as is.
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

		private @Nullable MistralAiApi mistralAiApi;

		private MistralAiChatOptions options = MistralAiChatOptions.builder().build();

		private ToolCallingManager toolCallingManager = DEFAULT_TOOL_CALLING_MANAGER;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder mistralAiApi(MistralAiApi mistralAiApi) {
			this.mistralAiApi = mistralAiApi;
			return this;
		}

		public Builder options(MistralAiChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used for internal tool execution.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 * @deprecated since 2.0.0 for removal in 3.0.0 — internal tool execution in
		 * {@link MistralAiChatModel} is superseded by {@code ToolCallingAdvisor} used via
		 * {@code ChatClient}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
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

		public MistralAiChatModel build() {
			Assert.state(this.mistralAiApi != null, "MistralAiApi must not be null");
			return new MistralAiChatModel(this.mistralAiApi, this.options, this.toolCallingManager, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
