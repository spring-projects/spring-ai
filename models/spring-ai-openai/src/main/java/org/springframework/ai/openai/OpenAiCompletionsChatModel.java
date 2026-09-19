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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.ResponseFormatText;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall.Function;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Internal implementation of {@link OpenAiChatModel} over OpenAI's
 * {@code /v1/chat/completions} endpoint: it builds the request from a {@link Prompt},
 * calls the OpenAI SDK, and maps what comes back onto a {@link ChatResponse}.
 * <p>
 * Not API. It implements {@link ChatModel} only so that {@link OpenAiChatModel} can
 * delegate to it, and nothing outside {@link OpenAiChatModel} may hold one: the
 * {@link Prompt} it is handed must already carry fully merged {@link OpenAiChatOptions},
 * and resolving those options, reporting observations and choosing between this class and
 * {@link OpenAiResponsesChatModel} all belong to {@link OpenAiChatModel}. Nothing here
 * reports an observation of its own.
 * <p>
 * {@code /v1/chat/completions} is the endpoint every OpenAI-compatible provider
 * implements and the one Spring AI has always talked to. Reasoning models are served here
 * too, but from GPT-5.4 onwards only without tool calling, and the chain of thought is
 * never returned: the {@code reasoningContent} metadata key carries whatever a non-OpenAI
 * provider chose to put in {@code reasoning_content}.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @author Eric Bottard
 * @author Taewoong Kim
 * @author Jewoo Shin
 * @author guan xu
 */
final class OpenAiCompletionsChatModel implements ChatModel {

	private static final String REASONING_CONTENT = "reasoningContent";

	static final String TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY = "openai.tool_calls.additional_properties";

	private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
	};

	// Jackson 2 required due to OpenAI deserializers
	private static final ObjectMapper objectMapper = new ObjectMapper();

	private final Log logger = LogFactory.getLog(OpenAiCompletionsChatModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final ToolCallingManager toolCallingManager;

	/**
	 * The model's own options, consulted only where a request that replaced them
	 * wholesale still has to fall back on them; see {@link #getChatCompletionTools}.
	 */
	private final OpenAiChatOptions options;

	OpenAiCompletionsChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync,
			ToolCallingManager toolCallingManager, OpenAiChatOptions options) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.toolCallingManager = toolCallingManager;
		this.options = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ChatCompletionCreateParams request = this.createRequest(prompt, false);
		RequestOptions requestOptions = requestOptions(options(prompt));

		ChatCompletion chatCompletion = this.openAiClient.chat().completions().create(request, requestOptions);

		List<ChatCompletion.Choice> choices = chatCompletion.choices();
		if (choices.isEmpty()) {
			if (logger.isWarnEnabled()) {
				logger.warn("No choices returned for prompt: " + prompt);
			}
			return new ChatResponse(List.of());
		}

		List<Generation> generations = choices.stream().map(choice -> {
			Map<String, Object> metadata = Map.of("id", chatCompletion.id(), "role",
					choice.message()._role().asString().isPresent() ? choice.message()._role().asStringOrThrow() : "",
					"index", choice.index(), "finishReason", choice.finishReason().value().toString(), "refusal",
					choice.message().refusal().orElse(""), "annotations",
					choice.message().annotations().orElse((List) List.of(Map.of())), REASONING_CONTENT,
					getReasoningContent(choice));
			return buildGeneration(choice, metadata, request);
		}).toList();

		CompletionUsage usage = chatCompletion.usage().orElse(null);
		Usage currentChatResponseUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
		Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage, null);
		return new ChatResponse(generations, from(chatCompletion, accumulatedUsage));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ChatCompletionCreateParams request = this.createRequest(prompt, true);
		RequestOptions requestOptions = requestOptions(options(prompt));
		ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, String> reasoningMap = new ConcurrentHashMap<>();

		// Convert from AsyncStreamResponse<ChatCompletionChunk> to Flux<CCC>
		Flux<ChatCompletionChunk> chunks = Flux.create(sink -> {
			AsyncStreamResponse<ChatCompletionChunk> response = this.openAiClientAsync.chat()
				.completions()
				.createStreaming(request, requestOptions);
			sink.onDispose(response::close);
			response.subscribe(sink::next).onCompleteFuture().whenComplete((unused, throwable) -> {
				if (throwable != null) {
					sink.error(throwable);
				}
				else {
					sink.complete();
				}
			});
		});

		// Next, aggregate CCCs that deal with tool calls together
		AtomicBoolean isInsideTool = new AtomicBoolean(false);
		Flux<ChatCompletion> aggregatedChatCompletions = chunks.doOnNext(chunk -> {
			if (ChunkMerger.hasToolCall(chunk)) {
				isInsideTool.set(true);
			}
		}).bufferUntil(chunk -> {
			if (isInsideTool.get() && ChunkMerger.toolCallsDone(chunk)) {
				isInsideTool.set(false);
				return true;
			}
			return !isInsideTool.get();
		}).map(ChunkMerger::mergeChunks).map(ChunkMerger::chunkToChatCompletion);

		return aggregatedChatCompletions.map(chatCompletion -> {
			String id = chatCompletion.id();
			List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
				roleMap.putIfAbsent(id, choice.message()._role().asString().isPresent()
						? choice.message()._role().asStringOrThrow() : "");

				// Accumulate reasoning fragments across the streamed chunks so the final
				// response of this stream carries the full reasoning content, surviving
				// last-wins metadata aggregation (e.g. MessageAggregator).
				String accumulatedReasoning = reasoningMap.merge(id + ":" + choice.index(), getReasoningContent(choice),
						String::concat);

				Map<String, Object> metadata = Map.of("id", id, //
						"role", roleMap.getOrDefault(id, ""), //
						"index", choice.index(), //
						"finishReason", choice.finishReason().value(), //
						"refusal", choice.message().refusal().orElse(""), //
						"annotations", choice.message().annotations().orElseGet(List::of), //
						REASONING_CONTENT, accumulatedReasoning //
				);

				return buildGeneration(choice, metadata, request);
			}).toList();
			CompletionUsage usage = chatCompletion.usage().orElse(null);
			Usage currentUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
			Usage accumulated = UsageCalculator.getCumulativeUsage(currentUsage, null);
			return new ChatResponse(generations, from(chatCompletion, accumulated));
		});
	}

	private static OpenAiChatOptions options(Prompt prompt) {
		OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
		Assert.state(options != null, "Prompt options must be OpenAiChatOptions type");
		return options;
	}

	/**
	 * Per-request SDK options. Only the timeout is set here; everything else about the
	 * transport is configured on the client, once.
	 */
	private static RequestOptions requestOptions(OpenAiChatOptions options) {
		RequestOptions.Builder requestOptions = RequestOptions.builder();
		if (options.getTimeout() != null) {
			requestOptions.timeout(options.getTimeout());
		}
		return requestOptions.build();
	}

	private Generation buildGeneration(ChatCompletion.Choice choice, Map<String, Object> metadata,
			ChatCompletionCreateParams request) {
		ChatCompletionMessage message = choice.message();
		Map<String, Object> assistantMessageMetadata = new LinkedHashMap<>(metadata);
		Map<String, String> toolCallAdditionalProperties = extractToolCallAdditionalProperties(message);
		if (!toolCallAdditionalProperties.isEmpty()) {
			assistantMessageMetadata.put(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY, toolCallAdditionalProperties);
		}
		List<AssistantMessage.ToolCall> toolCalls = message.toolCalls()
			.map(list -> list.stream().filter(tc -> tc.function().isPresent()).map(tc -> {
				var opt = tc.function();
				if (opt.isEmpty()) {
					return null;
				}
				var funcCall = opt.get();
				var functionDef = funcCall.function();
				String id = funcCall.id();
				String name = functionDef.name();
				String arguments = functionDef.arguments();
				return new AssistantMessage.ToolCall(id, "function", name, arguments);
			}).filter(Objects::nonNull).toList())
			.orElse(List.of());

		ChatCompletion.Choice.FinishReason.Value finishReasonValue = choice.finishReason().value();
		var generationMetadataBuilder = ChatGenerationMetadata.builder()
			.finishReason(finishReasonValue != ChatCompletion.Choice.FinishReason.Value._UNKNOWN
					? finishReasonValue.name() : null);

		String textContent = message.content().orElse("");

		List<Media> media = new ArrayList<>();

		if (message.audio().isPresent() && StringUtils.hasText(message.audio().get().data())
				&& request.audio().isPresent()) {
			var audioOutput = message.audio().get();
			String mimeType = String.format("audio/%s",
					request.audio().get().format().value().name().toLowerCase(Locale.ROOT));
			byte[] audioData = Base64.getDecoder().decode(audioOutput.data());
			Resource resource = new ByteArrayResource(audioData);
			Media.builder().mimeType(MimeTypeUtils.parseMimeType(mimeType)).data(resource).id(audioOutput.id()).build();
			media.add(Media.builder()
				.mimeType(MimeTypeUtils.parseMimeType(mimeType))
				.data(resource)
				.id(audioOutput.id())
				.build());
			if (!StringUtils.hasText(textContent)) {
				textContent = audioOutput.transcript();
			}
			generationMetadataBuilder.metadata("audioId", audioOutput.id());
			generationMetadataBuilder.metadata("audioExpiresAt", audioOutput.expiresAt());
		}

		// Unwrap Optional values so downstream repositories (Neo4j, MongoDB, JDBC,
		// etc.) can serialize the metadata map without failing on java.util.Optional.
		assistantMessageMetadata
			.replaceAll((key, value) -> value instanceof Optional<?> optional ? optional.orElse(null) : value);

		var assistantMessage = AssistantMessage.builder()
			.content(textContent)
			.properties(assistantMessageMetadata)
			.toolCalls(toolCalls)
			.media(media)
			.build();
		return new Generation(assistantMessage, generationMetadataBuilder.build());
	}

	private Map<String, String> extractToolCallAdditionalProperties(ChatCompletionMessage message) {
		Map<String, String> result = new LinkedHashMap<>();
		message.toolCalls()
			.ifPresent(toolCalls -> toolCalls.forEach(toolCall -> toolCall.function().ifPresent(functionToolCall -> {
				Map<String, JsonValue> props = functionToolCall._additionalProperties();
				if (!CollectionUtils.isEmpty(props)) {
					try {
						result.put(functionToolCall.id(), objectMapper.writeValueAsString(props));
					}
					catch (JsonProcessingException ex) {
						throw new RuntimeException(ex);
					}
				}
			})));
		return result;
	}

	private ChatResponseMetadata from(ChatCompletion result, Usage usage) {
		Assert.notNull(result, "OpenAI ChatCompletion must not be null");
		result.model();
		result.id();
		ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
			.id(result.id())
			.usage(usage)
			.model(result.model())
			.keyValue("created", getCreated(result));

		result._additionalProperties().forEach((key, jsonValue) -> {
			try {
				Object value = JacksonUtils.getDefaultJsonMapper().convertValue(jsonValue, Object.class);
				metadataBuilder.keyValue(key, value);
			}
			catch (Exception e) {
				if (logger.isErrorEnabled()) {
					logger.error("Error parsing JSON value for key '" + key + "': " + jsonValue, e);
				}
				metadataBuilder.keyValue(key, jsonValue);
			}
		});

		return metadataBuilder.build();
	}

	/**
	 * Extract the created timestamp from a ChatCompletion result, returning 0 if the
	 * field is absent. Some OpenAI-compatible providers (e.g. GitHub Copilot) do not
	 * include the created field in their response.
	 */
	private long getCreated(ChatCompletion result) {
		try {
			return result.created();
		}
		catch (OpenAIInvalidDataException ex) {
			return 0L;
		}
	}

	private DefaultUsage getDefaultUsage(CompletionUsage usage) {
		Long cacheRead = usage.promptTokensDetails().flatMap(details -> details.cachedTokens()).orElse(null);
		return new DefaultUsage(Math.toIntExact(usage.promptTokens()), Math.toIntExact(usage.completionTokens()),
				Math.toIntExact(usage.totalTokens()), usage, cacheRead, null);
	}

	/**
	 * Creates a chat completion request from the given prompt.
	 * @param prompt the prompt containing messages and options
	 * @param stream whether this is a streaming request
	 * @return the chat completion create parameters
	 */
	ChatCompletionCreateParams createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessageParam> chatCompletionMessageParams = prompt.getInstructions()
			.stream()
			.map(message -> {
				if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
					// Handle simple text content for user and system messages
					ChatCompletionUserMessageParam.Builder builder = ChatCompletionUserMessageParam.builder();

					if (message instanceof UserMessage userMessage
							&& !CollectionUtils.isEmpty(userMessage.getMedia())) {
						// Handle media content (images, audio, files)
						List<ChatCompletionContentPart> parts = new ArrayList<>();

						String messageText = message.getText();
						if (messageText != null && !messageText.isEmpty()) {
							parts.add(ChatCompletionContentPart
								.ofText(ChatCompletionContentPartText.builder().text(messageText).build()));
						}

						// Add media content parts
						userMessage.getMedia().forEach(media -> {
							String mimeType = media.getMimeType().toString();
							if (mimeType.startsWith("image/")) {
								if (media.getData() instanceof java.net.URI uri) {
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
												.url(uri.toString())
												.build())
											.build()));
								}
								else if (media.getData() instanceof String text) {
									// The org.springframework.ai.content.Media object
									// should store the URL as a java.net.URI but it
									// transforms it to String somewhere along the way,
									// for example in its Builder class. So, we accept
									// String as well here for image URLs.
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(
													ChatCompletionContentPartImage.ImageUrl.builder().url(text).build())
											.build()));
								}
								else if (media.getData() instanceof byte[] bytes) {
									// Assume the bytes are an image. So, convert the
									// bytes to a base64 encoded
									ChatCompletionContentPartImage.ImageUrl.Builder imageUrlBuilder = ChatCompletionContentPartImage.ImageUrl
										.builder();

									imageUrlBuilder.url("data:" + mimeType + ";base64,"
											+ Base64.getEncoder().encodeToString(bytes));
									parts.add(ChatCompletionContentPart
										.ofImageUrl(ChatCompletionContentPartImage.builder()
											.imageUrl(imageUrlBuilder.build())
											.build()));
								}
								else {
									if (logger.isInfoEnabled()) {
										logger.info("Could not process image media with data of type: "
												+ media.getData().getClass().getSimpleName()
												+ ". Only java.net.URI is supported for image URLs.");
									}
								}
							}
							else if (mimeType.startsWith("audio/")) {
								parts.add(ChatCompletionContentPart
									.ofInputAudio(ChatCompletionContentPartInputAudio.builder()
										.inputAudio(ChatCompletionContentPartInputAudio.builder()
											.inputAudio(ChatCompletionContentPartInputAudio.InputAudio.builder()
												.data(fromAudioData(media.getData()))
												.format(mimeType.contains("mp3")
														? ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
														: ChatCompletionContentPartInputAudio.InputAudio.Format.WAV)
												.build())
											.build()
											.inputAudio())
										.build()));
							}
							else if ("application/pdf".equals(mimeType)) {
								parts.add(ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder()
									.file(ChatCompletionContentPart.File.FileObject.builder()
										.filename(media.getName())
										.fileData(fromMediaData(media.getMimeType(), media.getData()))
										.build())
									.build()));
							}
							else {
								// Assume it's a file or other media type represented as a
								// data URL
								parts.add(ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
									.text(fromMediaData(media.getMimeType(), media.getData()))
									.build()));
							}
						});
						builder.contentOfArrayOfContentParts(parts);
					}
					else {
						// Simple text message
						String messageText = message.getText();
						if (messageText != null) {
							builder.content(ChatCompletionContentPartText.builder().text(messageText).build().text());
						}
					}

					if (message.getMessageType() == MessageType.USER) {
						builder.role(JsonValue.from(MessageType.USER.getValue()));
					}
					else {
						builder.role(JsonValue.from(MessageType.SYSTEM.getValue()));
					}

					return List.of(ChatCompletionMessageParam.ofUser(builder.build()));
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					var assistantMessage = (AssistantMessage) message;
					ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder()
						.role(JsonValue.from(MessageType.ASSISTANT.getValue()));

					if (assistantMessage.getText() != null) {
						builder.content(ChatCompletionAssistantMessageParam.builder()
							.content(assistantMessage.getText())
							.build()
							.content());
					}

					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						Map<String, String> toolCallAdditionalProperties = toolCallAdditionalPropertiesFromMetadata(
								assistantMessage);

						List<ChatCompletionMessageToolCall> toolCalls = assistantMessage.getToolCalls()
							.stream()
							.map(toolCall -> {
								ChatCompletionMessageFunctionToolCall.Builder toolCallBuilder = ChatCompletionMessageFunctionToolCall
									.builder()
									.id(toolCall.id())
									.function(ChatCompletionMessageFunctionToolCall.Function.builder()
										.name(toolCall.name())
										.arguments(toolCall.arguments())
										.build());

								String jsonProps = toolCallAdditionalProperties.get(toolCall.id());
								if (StringUtils.hasText(jsonProps)) {
									Map<String, JsonValue> additionalProperties = new LinkedHashMap<>();
									try {
										objectMapper.readValue(jsonProps, MAP_TYPE_REF)
											.forEach((k, v) -> additionalProperties.put(k, JsonValue.from(v)));
									}
									catch (JsonProcessingException ex) {
										throw new IllegalStateException("Conversion from JSON to %s failed"
											.formatted(MAP_TYPE_REF.getType().getTypeName()), ex);
									}
									toolCallBuilder.putAllAdditionalProperties(additionalProperties);
								}
								return ChatCompletionMessageToolCall.ofFunction(toolCallBuilder.build());
							})
							.toList();

						builder.toolCalls(toolCalls);
					}

					// Replay reasoning content only when present - plain OpenAI is
					// unaffected
					Object reasoningContent = assistantMessage.getMetadata().get(REASONING_CONTENT);
					if (reasoningContent instanceof String reasoning && StringUtils.hasText(reasoning)) {
						// "reasoning_content" is the wire field; REASONING_CONTENT is the
						// metadata key
						builder.putAdditionalProperty("reasoning_content", JsonValue.from(reasoning));
					}

					return List.of(ChatCompletionMessageParam.ofAssistant(builder.build()));
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					ToolResponseMessage toolMessage = (ToolResponseMessage) message;

					ChatCompletionToolMessageParam.Builder builder = ChatCompletionToolMessageParam.builder();
					builder.content(toolMessage.getText() != null ? toolMessage.getText() : "");
					builder.role(JsonValue.from(MessageType.TOOL.getValue()));

					if (toolMessage.getResponses().isEmpty()) {
						return List.of(ChatCompletionMessageParam.ofTool(builder.build()));
					}
					return toolMessage.getResponses().stream().map(response -> {
						String callId = response.id();
						String callResponse = response.responseData();

						return ChatCompletionMessageParam
							.ofTool(builder.toolCallId(callId).content(callResponse).build());
					}).toList();
				}
				else {
					throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
				}
			})
			.flatMap(List::stream)
			.toList();

		ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();

		chatCompletionMessageParams.forEach(builder::addMessage);

		OpenAiChatOptions requestOptions = (OpenAiChatOptions) prompt.getOptions();
		Assert.state(requestOptions != null, "ChatOptions must not be null");

		// Use deployment name if available (for Microsoft Foundry), otherwise use model
		// name
		if (requestOptions.getDeploymentName() != null) {
			builder.model(requestOptions.getDeploymentName());
		}
		else if (requestOptions.getModel() != null) {
			builder.model(requestOptions.getModel());
		}

		if (requestOptions.getFrequencyPenalty() != null) {
			builder.frequencyPenalty(requestOptions.getFrequencyPenalty());
		}
		if (requestOptions.getLogitBias() != null) {
			builder.logitBias(ChatCompletionCreateParams.LogitBias.builder()
				.putAllAdditionalProperties(requestOptions.getLogitBias()
					.entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
				.build());
		}
		if (requestOptions.getLogprobs() != null) {
			builder.logprobs(requestOptions.getLogprobs());
		}
		if (requestOptions.getTopLogprobs() != null) {
			builder.topLogprobs(requestOptions.getTopLogprobs());
		}
		if (requestOptions.getMaxTokens() != null) {
			builder.maxTokens(requestOptions.getMaxTokens());
		}
		if (requestOptions.getMaxCompletionTokens() != null) {
			builder.maxCompletionTokens(requestOptions.getMaxCompletionTokens());
		}
		if (requestOptions.getN() != null) {
			builder.n(requestOptions.getN());
		}
		if (requestOptions.getOutputModalities() != null) {
			builder.modalities(requestOptions.getOutputModalities()
				.stream()
				.map(modality -> ChatCompletionCreateParams.Modality.of(modality.toLowerCase(Locale.ROOT)))
				.toList());
		}
		if (requestOptions.getOutputAudio() != null) {
			builder.audio(requestOptions.getOutputAudio().toChatCompletionAudioParam());
		}
		if (requestOptions.getPresencePenalty() != null) {
			builder.presencePenalty(requestOptions.getPresencePenalty());
		}
		if (requestOptions.getResponseFormat() != null) {
			OpenAiChatModel.ResponseFormat responseFormat = requestOptions.getResponseFormat();
			if (responseFormat.getType().equals(OpenAiChatModel.ResponseFormat.Type.TEXT)) {
				builder.responseFormat(ResponseFormatText.builder().build());
			}
			else if (responseFormat.getType().equals(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)) {
				builder.responseFormat(ResponseFormatJsonObject.builder().build());
			}
			else if (responseFormat.getType().equals(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)) {
				String jsonSchemaString = responseFormat.getJsonSchema() != null ? responseFormat.getJsonSchema() : "";
				try {

					ResponseFormatJsonSchema.JsonSchema.Builder jsonSchemaBuilder = ResponseFormatJsonSchema.JsonSchema
						.builder();
					jsonSchemaBuilder.name("json_schema");
					Boolean strict = responseFormat.getStrict();
					jsonSchemaBuilder.strict(strict != null ? strict : true);

					ResponseFormatJsonSchema.JsonSchema.Schema schema = objectMapper.readValue(jsonSchemaString,
							ResponseFormatJsonSchema.JsonSchema.Schema.class);

					jsonSchemaBuilder.schema(schema);

					builder.responseFormat(
							ResponseFormatJsonSchema.builder().jsonSchema(jsonSchemaBuilder.build()).build());
				}
				catch (Exception e) {
					throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchemaString, e);
				}
			}
			else {
				throw new IllegalArgumentException("Unsupported response format type: " + responseFormat.getType());
			}
		}
		if (requestOptions.getSeed() != null) {
			builder.seed(requestOptions.getSeed());
		}
		if (requestOptions.getStop() != null && !requestOptions.getStop().isEmpty()) {
			if (requestOptions.getStop().size() == 1) {
				builder.stop(ChatCompletionCreateParams.Stop.ofString(requestOptions.getStop().get(0)));
			}
			else {
				builder.stop(ChatCompletionCreateParams.Stop.ofStrings(requestOptions.getStop()));
			}
		}
		if (requestOptions.getTemperature() != null) {
			builder.temperature(requestOptions.getTemperature());
		}
		if (requestOptions.getTopP() != null) {
			builder.topP(requestOptions.getTopP());
		}
		if (requestOptions.getUser() != null) {
			builder.user(requestOptions.getUser());
		}
		if (requestOptions.getParallelToolCalls() != null) {
			builder.parallelToolCalls(requestOptions.getParallelToolCalls());
		}
		if (requestOptions.getReasoningEffort() != null) {
			builder.reasoningEffort(ReasoningEffort.of(requestOptions.getReasoningEffort().toLowerCase(Locale.ROOT)));
		}
		if (requestOptions.getVerbosity() != null) {
			builder.verbosity(ChatCompletionCreateParams.Verbosity.of(requestOptions.getVerbosity()));
		}

		if (requestOptions.getStore() != null) {
			builder.store(requestOptions.getStore());
		}
		if (requestOptions.getMetadata() != null && !requestOptions.getMetadata().isEmpty()) {
			builder.metadata(ChatCompletionCreateParams.Metadata.builder()
				.putAllAdditionalProperties(requestOptions.getMetadata()
					.entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
				.build());
		}
		if (requestOptions.getServiceTier() != null) {
			builder.serviceTier(ChatCompletionCreateParams.ServiceTier.of(requestOptions.getServiceTier()));
		}

		if (requestOptions.getPromptCacheKey() != null) {
			builder.promptCacheKey(requestOptions.getPromptCacheKey());
		}

		if (requestOptions.getSafetyIdentifier() != null) {
			builder.safetyIdentifier(requestOptions.getSafetyIdentifier());
		}

		if (requestOptions.getCustomHeaders() != null && !requestOptions.getCustomHeaders().isEmpty()) {
			requestOptions.getCustomHeaders().forEach(builder::putAdditionalHeader);
		}

		if (stream) {
			if (requestOptions.getStreamOptions() != null) {
				ChatCompletionStreamOptions.Builder streamOptionsBuilder = ChatCompletionStreamOptions.builder();

				var ops = requestOptions.getStreamOptions();

				streamOptionsBuilder.includeObfuscation(ops.includeObfuscation() != null && ops.includeObfuscation());
				streamOptionsBuilder.includeUsage(ops.includeUsage() != null && ops.includeUsage());

				if (!CollectionUtils.isEmpty(ops.additionalProperties())) {
					Map<String, com.openai.core.JsonValue> nativeParams = ops.additionalProperties()
						.entrySet()
						.stream()
						.map(e -> Map.entry(e.getKey(), com.openai.core.JsonValue.from(e.getValue())))
						.collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);

					streamOptionsBuilder.putAllAdditionalProperties(nativeParams);
				}
				builder.streamOptions(streamOptionsBuilder.build());
			}
			else {
				builder.streamOptions(ChatCompletionStreamOptions.builder()
					.includeUsage(true) // Include usage by default for streaming
					.build());
			}
		}

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			builder.tools(getChatCompletionTools(toolDefinitions, requestOptions));
		}

		if (requestOptions.getToolChoice() != null) {
			if (requestOptions.getToolChoice() instanceof ChatCompletionToolChoiceOption toolChoiceOption) {
				builder.toolChoice(toolChoiceOption);
			}
			else if (requestOptions.getToolChoice() instanceof String json) {
				if (json.equals("auto")) {
					builder.toolChoice(ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO));
				}
				else if (json.equals("none")) {
					builder.toolChoice(ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE));
				}
				else if (json.equals("required")) {
					builder.toolChoice(
							ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED));
				}
				else {
					try {
						var node = JacksonUtils.getDefaultJsonMapper().readTree(json);
						builder.toolChoice(OpenAiChatModel.parseToolChoice(node));
					}
					catch (Exception e) {
						throw new IllegalArgumentException("Failed to parse toolChoice JSON: " + json, e);
					}
				}
			}
		}

		// Add extraBody parameters as additional body properties for OpenAI-compatible
		// providers
		if (requestOptions.getExtraBody() != null && !requestOptions.getExtraBody().isEmpty()) {
			Map<String, JsonValue> extraParams = requestOptions.getExtraBody()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue())));
			builder.additionalBodyProperties(extraParams);
		}

		return builder.build();
	}

	private Map<String, String> toolCallAdditionalPropertiesFromMetadata(AssistantMessage assistantMessage) {
		Object value = assistantMessage.getMetadata().get(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY);
		if (!(value instanceof Map<?, ?> rawMap)) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		rawMap.forEach((k, v) -> {
			if (k instanceof String id && v instanceof String json) {
				result.put(id, json);
			}
		});
		return result;
	}

	private String fromAudioData(Object audioData) {
		if (audioData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		throw new IllegalArgumentException("Unsupported audio data type: " + audioData.getClass().getSimpleName());
	}

	private String fromMediaData(org.springframework.util.MimeType mimeType, Object mediaContentData) {
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

	private List<ChatCompletionTool> getChatCompletionTools(List<ToolDefinition> toolDefinitions,
			@Nullable OpenAiChatOptions requestOptions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			FunctionParameters.Builder parametersBuilder = FunctionParameters.builder();
			// Defaults to false: OpenAI's strict mode requires every schema property to
			// appear in "required" (optionality is expressed via nullable types, not
			// omission), which JsonSchemaGenerator does not produce by default. When a
			// caller opts in via OpenAiChatOptions#strict, applyStrictModeRequirements
			// below rewrites the schema to satisfy that contract.
			Boolean strictMode = false;
			if (requestOptions != null && requestOptions.getStrict() != null) {
				strictMode = requestOptions.getStrict();
			}
			else if (this.options != null && this.options.getStrict() != null) {
				strictMode = this.options.getStrict();
			}
			if (!toolDefinition.inputSchema().isEmpty()) {
				// Parse the schema and add its properties directly
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> schemaMap = objectMapper.readValue(toolDefinition.inputSchema(), Map.class);

					if (Boolean.TRUE.equals(strictMode)) {
						applyStrictModeRequirements(schemaMap);
					}

					// Add each property from the schema to the parameters
					schemaMap
						.forEach((key, value) -> parametersBuilder.putAdditionalProperty(key, JsonValue.from(value)));
				}
				catch (Exception e) {
					logger.error("Failed to parse tool schema", e);
				}
			}
			FunctionDefinition functionDefinition = FunctionDefinition.builder()
				.name(toolDefinition.name())
				.description(toolDefinition.description())
				.parameters(parametersBuilder.build())
				.strict(strictMode)
				.build();

			return ChatCompletionTool
				.ofFunction(ChatCompletionFunctionTool.builder().function(functionDefinition).build());
		}).toList();
	}

	/**
	 * A JSON Schema fragment representing exactly the {@code null} type, used as the
	 * extra {@code anyOf} branch when widening a {@code $ref}/{@code anyOf}-based
	 * property to accept {@code null}.
	 */
	private static final Map<String, Object> NULL_TYPE_SCHEMA = Map.of("type", "null");

	/**
	 * Rewrites an object schema in place to satisfy OpenAI's strict function-calling
	 * mode: every property must be listed in "required", with optionality expressed via a
	 * nullable type rather than omission from "required", and every object level must pin
	 * {@code "additionalProperties"} to {@code false}. {@link JsonSchemaGenerator}
	 * produces conventional JSON Schema instead - it omits
	 * {@code @ToolParam(required = false)} properties from "required" - so this widens
	 * the type of each such property to also accept {@code null} and backfills "required"
	 * with every property key. Schemas from other sources (MCP servers, hand-written
	 * inputSchema JSON) may also lack "additionalProperties", so it is backfilled with
	 * {@code false} wherever absent; an explicit value is preserved. Recurses into nested
	 * object/array-item schemas and {@code $defs} definitions so their own optional
	 * properties are fixed up too.
	 */
	@SuppressWarnings("unchecked")
	private static void applyStrictModeRequirements(Map<String, Object> schema) {
		if (schema.get("$defs") instanceof Map<?, ?> defs) {
			for (Object definition : defs.values()) {
				if (definition instanceof Map<?, ?> definitionSchema) {
					applyStrictModeRequirements((Map<String, Object>) definitionSchema);
				}
			}
		}

		if (!(schema.get("properties") instanceof Map<?, ?> properties)) {
			return;
		}

		schema.putIfAbsent("additionalProperties", false);

		if (properties.isEmpty()) {
			return;
		}

		List<?> alreadyRequired = schema.get("required") instanceof List<?> required ? required : List.of();

		for (Object propertySchemaValue : properties.values()) {
			if (!(propertySchemaValue instanceof Map<?, ?> propertySchema)) {
				continue;
			}
			applyStrictModeRequirements((Map<String, Object>) propertySchema);
			if (propertySchema.get("items") instanceof Map<?, ?> itemsSchema) {
				applyStrictModeRequirements((Map<String, Object>) itemsSchema);
			}
		}

		for (Map.Entry<?, ?> property : properties.entrySet()) {
			if (alreadyRequired.contains(property.getKey())
					|| !(property.getValue() instanceof Map<?, ?> propertySchema)) {
				continue;
			}
			widenToNullable((Map<String, Object>) propertySchema);
		}

		schema.put("required", new ArrayList<>(properties.keySet()));
	}

	/**
	 * Widens a property schema's "type" to also accept {@code null}. Properties defined
	 * purely via {@code $ref} or {@code anyOf} (no "type" key of their own - typically a
	 * complex object parameter) are instead wrapped in an {@code anyOf} alongside a null
	 * branch, since there is no "type" value to widen directly.
	 */
	private static void widenToNullable(Map<String, Object> propertySchema) {
		Object type = propertySchema.get("type");
		if (type instanceof String typeName) {
			if (!"null".equals(typeName)) {
				propertySchema.put("type", new ArrayList<>(List.of(typeName, "null")));
			}
			return;
		}
		if (type instanceof List<?> typeList) {
			if (!typeList.contains("null")) {
				List<Object> widened = new ArrayList<>(typeList);
				widened.add("null");
				propertySchema.put("type", widened);
			}
			return;
		}
		if (propertySchema.get("anyOf") instanceof List<?> anyOf) {
			if (!anyOf.contains(NULL_TYPE_SCHEMA)) {
				List<Object> widened = new ArrayList<>(anyOf);
				widened.add(NULL_TYPE_SCHEMA);
				propertySchema.put("anyOf", widened);
			}
			return;
		}
		if (propertySchema.containsKey("$ref")) {
			Map<String, Object> refBranch = new LinkedHashMap<>(propertySchema);
			Object description = refBranch.remove("description");
			propertySchema.clear();
			if (description != null) {
				propertySchema.put("description", description);
			}
			propertySchema.put("anyOf", new ArrayList<>(List.of(refBranch, NULL_TYPE_SCHEMA)));
		}
	}

	private String getReasoningContent(ChatCompletion.Choice choice) {
		String reasoningContent = "";
		Map<String, JsonValue> additionalProperties = choice.message()._additionalProperties();
		if (additionalProperties.get("reasoning_content") != null) {
			reasoningContent = (String) additionalProperties.get("reasoning_content").asString().orElse("");
		}
		else {
			if (additionalProperties.get("reasoning") != null) {
				reasoningContent = (String) additionalProperties.get("reasoning").asString().orElse("");
			}
		}
		return reasoningContent;
	}

	static final class ChunkMerger {

		static boolean hasToolCall(ChatCompletionChunk chunk) {
			return !chunk.choices().isEmpty()
					&& chunk.choices().get(0).delta().toolCalls().filter(toolCalls -> !toolCalls.isEmpty()).isPresent();
		}

		static boolean toolCallsDone(ChatCompletionChunk chunk) {
			return !chunk.choices().isEmpty()
					&& FinishReason.TOOL_CALLS == chunk.choices().get(0).finishReason().orElse(null);
		}

		static ChatCompletionChunk mergeChunks(List<ChatCompletionChunk> chunks) {
			ChatCompletionChunk.Builder builder = chunks.get(0).toBuilder();
			Map<Long, Choice> choices = new LinkedHashMap<>();
			chunks.get(0).choices().forEach(choice -> choices.put(choice.index(), choice));

			for (int i = 1; i < chunks.size(); i++) {
				ChatCompletionChunk chunk = chunks.get(i);
				chunk.usage().ifPresent(builder::usage);
				chunk.serviceTier().ifPresent(builder::serviceTier);
				chunk.choices()
					.forEach(choice -> choices.compute(choice.index(),
							(ix, c) -> c == null ? choice : mergeChoices(c, choice)));
			}
			return builder.choices(new ArrayList<>(choices.values())).build();
		}

		private static Choice mergeChoices(Choice c1, Choice c2) {
			return Choice.builder()
				.index(c1.index())
				.finishReason(c1.finishReason().or(c2::finishReason))
				.logprobs(c1.logprobs().or(c2::logprobs))
				.delta(mergeDeltas(c1.delta(), c2.delta()))
				.build();
		}

		private static Delta mergeDeltas(Delta left, Delta right) {
			// Deltas of the same logical tool call share the required 'index' field.
			// Some OpenAI-compatible providers (e.g. DeepSeek) send an empty-string id
			// on continuation deltas instead of omitting it, so id presence cannot be
			// used to detect the start of a new tool call.
			var tcs = Stream.of(left.toolCalls(), right.toolCalls()).flatMap(Optional::stream).reduce((tcs1, tcs2) -> {
				if (tcs2.isEmpty()) {
					return tcs1;
				}
				Map<Long, ToolCall> mergedByIndex = new LinkedHashMap<>();
				tcs1.forEach(tc -> mergedByIndex.merge(tc.index(), tc, ChunkMerger::mergeToolCalls));
				tcs2.forEach(tc -> mergedByIndex.merge(tc.index(), tc, ChunkMerger::mergeToolCalls));
				return List.copyOf(mergedByIndex.values());
			}).orElse(List.of());

			Delta.Builder deltaBuilder = left.toBuilder().toolCalls(tcs);
			// Concatenate reasoning fragments (e.g. DeepSeek "reasoning_content") so
			// they survive the tool-call chunk merge instead of keeping only the first
			// chunk's value.
			for (String reasoningKey : List.of("reasoning_content", "reasoning")) {
				stringProperty(right, reasoningKey).filter(StringUtils::hasLength)
					.ifPresent(rightFragment -> deltaBuilder.putAdditionalProperty(reasoningKey,
							JsonValue.from(stringProperty(left, reasoningKey).orElse("") + rightFragment)));
			}
			return deltaBuilder.build();
		}

		private static Optional<String> stringProperty(Delta delta, String key) {
			return Optional.ofNullable(delta._additionalProperties().get(key)).flatMap(JsonValue::asString);
		}

		private static ToolCall mergeToolCalls(ToolCall previous, ToolCall current) {
			String arguments = Stream
				.of(previous.function().flatMap(Function::arguments), current.function().flatMap(Function::arguments))
				.flatMap(Optional::stream)
				.collect(Collectors.joining());
			return previous.toBuilder()
				.id(firstWithText(previous.id(), current.id()))
				.putAllAdditionalProperties(current._additionalProperties())
				.function(previous.function()
					.map(Function::toBuilder)
					.orElseGet(Function::builder)
					.name(firstWithText(previous.function().flatMap(Function::name),
							current.function().flatMap(Function::name)))
					.arguments(arguments)
					.build())
				.build();
		}

		private static String firstWithText(Optional<String> first, Optional<String> second) {
			return first.filter(StringUtils::hasText).or(() -> second.filter(StringUtils::hasText)).orElse("");
		}

		/**
		 * Convert a ChatCompletionChunk into a ChatCompletion.
		 */
		static ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
			List<ChatCompletion.Choice> choices = chunk.choices().stream().map(cccc -> {
				ChatCompletion.Choice.Builder choiceBuilder = ChatCompletion.Choice.builder();

				choiceBuilder.index(cccc.index());

				choiceBuilder.finishReason(ChatCompletion.Choice.FinishReason.of(""));
				cccc.finishReason()
					.ifPresent(finishReason -> choiceBuilder.finishReason(ChatCompletion.Choice.FinishReason
						.of(finishReason.value().name().toLowerCase(Locale.ROOT))));

				if (cccc.logprobs().isPresent()) {
					var logprobs = cccc.logprobs().get();
					choiceBuilder.logprobs(ChatCompletion.Choice.Logprobs.builder()
						.content(logprobs.content())
						.refusal(logprobs.refusal())
						.build());
				}
				else {
					choiceBuilder.logprobs(
							ChatCompletion.Choice.Logprobs.builder().content(List.of()).refusal(List.of()).build());
				}

				ChatCompletionMessage.Builder msgBuilder = ChatCompletionMessage.builder()
					.content(cccc.delta().content())
					.refusal(cccc.delta().refusal())
					// Carry over provider-specific delta fields (e.g. reasoning_content)
					// so they are readable on the message, as in the non-streaming path.
					.putAllAdditionalProperties(cccc.delta()._additionalProperties());
				cccc.delta().toolCalls().ifPresent(ccctcs -> {
					msgBuilder.toolCalls(ccctcs.stream().map(tc -> {
						ChatCompletionMessageFunctionToolCall.Builder toolCallBuilder = ChatCompletionMessageFunctionToolCall
							.builder();
						Function function = tc.function()
							.orElseThrow(() -> new IllegalStateException("Tool call function is missing"));
						String id = tc.id()
							.filter(StringUtils::hasText)
							.orElseThrow(() -> new IllegalStateException("Tool call id is missing"));
						String name = function.name()
							.filter(StringUtils::hasText)
							.orElseThrow(() -> new IllegalStateException("Tool call function name is missing"));
						toolCallBuilder.putAllAdditionalProperties(tc._additionalProperties());
						toolCallBuilder.id(id);
						toolCallBuilder.function(ChatCompletionMessageFunctionToolCall.Function.builder()
							.name(name)
							.arguments(function.arguments().orElse(""))
							.build());
						return ChatCompletionMessageToolCall.ofFunction(toolCallBuilder.build());
					}).toList());
				});
				choiceBuilder.message(msgBuilder.build());
				return choiceBuilder.build();
			}).toList();

			return ChatCompletion.builder()
				.id(chunk.id())
				.choices(choices)
				.created(getCreated(chunk))
				.model(chunk.model())
				.usage(chunk.usage()
					.orElse(CompletionUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build()))
				.putAllAdditionalProperties(chunk._additionalProperties())
				.build();
		}

		/**
		 * Extract the created timestamp from a ChatCompletionChunk, returning 0 if
		 * absent.
		 */
		private static long getCreated(ChatCompletionChunk chunk) {
			try {
				return chunk.created();
			}
			catch (OpenAIInvalidDataException ex) {
				return 0L;
			}
		}

	}

}
