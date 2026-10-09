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

package org.springframework.ai.bedrock.converse.api;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;

/**
 * Sends a {@link ConverseStreamRequest} to Bedrock and returns {@link ChatResponse}
 * stream.
 * <p>
 * Every delta chunk carries one {@link MessagePart} stamped with the Converse content
 * block index it belongs to (see {@link StreamingParts}), so that
 * {@link org.springframework.ai.chat.model.MessageAggregator} can rebuild the parts of
 * the response in block order. Text and reasoning text deltas are partial parts. The
 * signature that closes a reasoning block is buffered and emitted once its block stops,
 * as a partial {@link ReasoningPart} with an empty text and the payload; redacted
 * reasoning is buffered too and emitted as one complete part. Tool use input is buffered
 * until the end of the response, and the final chunk carries the complete tool calls, any
 * reasoning whose block never stopped, and an empty text.
 *
 * @author Jared Rufer
 * @author Dimitar Proynov
 * @since 1.1.0
 */
public class ConverseChatResponseStream implements ConverseStreamResponseHandler.Visitor {

	private static final Log logger = LogFactory.getLog(ConverseChatResponseStream.class);

	public static final Sinks.EmitFailureHandler DEFAULT_EMIT_FAILURE_HANDLER = Sinks.EmitFailureHandler
		.busyLooping(Duration.ofSeconds(10));

	/**
	 * Set on every reasoning delta: a block's signature, if any, only arrives after its
	 * text, so an unsigned block can only be told apart from foreign reasoning this way.
	 */
	private static final Map<String, String> BEDROCK_ATTRIBUTES = Map.of(StreamingParts.PROVIDER_ATTRIBUTE,
			ConverseApiUtils.BEDROCK_PROVIDER);

	private final AtomicReference<String> requestIdRef = new AtomicReference<>("Unknown");

	private final AtomicReference<TokenUsage> tokenUsageRef = new AtomicReference<>();

	private final AtomicInteger promptTokens = new AtomicInteger();

	private final AtomicInteger generationTokens = new AtomicInteger();

	private final AtomicInteger totalTokens = new AtomicInteger();

	private final AtomicReference<String> stopReason = new AtomicReference<>();

	private final Map<Integer, StreamingToolCallBuilder> toolUseMap = new ConcurrentHashMap<>();

	private final Map<Integer, StringBuilder> signatureMap = new ConcurrentHashMap<>();

	private final Map<Integer, ByteArrayOutputStream> redactedContentMap = new ConcurrentHashMap<>();

	private final Sinks.Many<ChatResponse> eventSink = Sinks.many().multicast().onBackpressureBuffer();

	private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private final ConverseStreamRequest converseStreamRequest;

	public ConverseChatResponseStream(BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
			ConverseStreamRequest converseStreamRequest, @Nullable Usage accumulatedUsage) {

		Assert.notNull(bedrockRuntimeAsyncClient, "'bedrockRuntimeAsyncClient' must not be null");
		Assert.notNull(converseStreamRequest, "'converseStreamRequest' must not be null");

		this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
		this.converseStreamRequest = converseStreamRequest;
		if (accumulatedUsage != null) {
			this.totalTokens.set(accumulatedUsage.getTotalTokens());
			this.promptTokens.set(accumulatedUsage.getPromptTokens());
			this.generationTokens.set(accumulatedUsage.getCompletionTokens());
			if (accumulatedUsage.getNativeUsage() instanceof TokenUsage tokenUsage) {
				this.mergeNativeTokenUsage(tokenUsage);
			}
		}
	}

	@Override
	public void visitContentBlockStart(ContentBlockStartEvent event) {
		if (ContentBlockStart.Type.TOOL_USE.equals(event.start().type())) {
			this.toolUseMap.put(event.contentBlockIndex(),
					new StreamingToolCallBuilder().id(event.start().toolUse().toolUseId())
						.name(event.start().toolUse().name()));
		}
	}

	@Override
	public void visitContentBlockDelta(ContentBlockDeltaEvent event) {
		StreamingToolCallBuilder toolCallBuilder = this.toolUseMap.get(event.contentBlockIndex());

		if (toolCallBuilder != null) {
			toolCallBuilder.delta(event.delta().toolUse().input());
		}
		else if (ContentBlockDelta.Type.TEXT.equals(event.delta().type())) {
			this.emitPart(StreamingParts.partial(TextPart.of(event.delta().text()), event.contentBlockIndex()));
		}
		else if (ContentBlockDelta.Type.REASONING_CONTENT.equals(event.delta().type())) {
			this.acceptReasoningDelta(event.contentBlockIndex(), event.delta().reasoningContent());
		}
	}

	/**
	 * A reasoning delta carries either a piece of the reasoning text, the signature that
	 * closes a signed block, or redacted content.
	 */
	private void acceptReasoningDelta(int index, ReasoningContentBlockDelta delta) {
		if (delta.text() != null) {
			this.emitPart(
					StreamingParts.partial(new ReasoningPart(delta.text(), null, null, BEDROCK_ATTRIBUTES), index));
		}
		else if (delta.signature() != null) {
			// The aggregator replaces a payload rather than appending to it, and Bedrock
			// does not promise the signature arrives in one delta, so it is buffered
			// until the block stops.
			this.signatureMap.computeIfAbsent(index, key -> new StringBuilder()).append(delta.signature());
		}
		else if (delta.redactedContent() != null) {
			// Buffered until the block stops, for the same reason.
			this.redactedContentMap.computeIfAbsent(index, key -> new ByteArrayOutputStream())
				.writeBytes(delta.redactedContent().asByteArray());
		}
	}

	@Override
	public void visitContentBlockStop(ContentBlockStopEvent event) {
		StringBuilder signature = this.signatureMap.remove(event.contentBlockIndex());
		if (signature != null) {
			this.emitPart(StreamingParts.partial(signatureReasoningPart(signature), event.contentBlockIndex()));
		}
		ByteArrayOutputStream redactedContent = this.redactedContentMap.remove(event.contentBlockIndex());
		if (redactedContent != null) {
			this.emitPart(StreamingParts.complete(redactedReasoningPart(redactedContent), event.contentBlockIndex()));
		}
	}

	@Override
	public void visitMessageStop(MessageStopEvent event) {
		this.stopReason.set(event.stopReasonAsString());
	}

	@Override
	public void visitMetadata(ConverseStreamMetadataEvent event) {
		this.promptTokens.addAndGet(event.usage().inputTokens());
		this.generationTokens.addAndGet(event.usage().outputTokens());
		this.totalTokens.addAndGet(event.usage().totalTokens());
		this.mergeNativeTokenUsage(event.usage());

		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(this.stopReason.get())
			.build();

		// Any signature or redacted reasoning whose block never stopped, and the complete
		// tool calls, at their block index. The final chunk also carries an empty text,
		// so that a streamed chunk never reports a null text.
		List<MessagePart> parts = new ArrayList<>();
		this.signatureMap.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> parts
				.add(StreamingParts.partial(signatureReasoningPart(entry.getValue()), entry.getKey())));
		this.signatureMap.clear();
		this.redactedContentMap.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> parts
				.add(StreamingParts.complete(redactedReasoningPart(entry.getValue()), entry.getKey())));
		this.redactedContentMap.clear();
		this.toolUseMap.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> parts
				.add(StreamingParts.complete(ToolCallPart.of(entry.getValue().build()), entry.getKey())));
		// Appended rather than set through content(""), which would place the text
		// before the tool calls.
		parts.add(TextPart.of(""));

		this.emitChatResponse(new Generation(AssistantMessage.builder().parts(parts).build(), generationMetadata));
	}

	/**
	 * A partial part, so that the reasoning text already received for the block is kept,
	 * with an empty text so that a block streamed without any text delta still aggregates
	 * to a replayable part.
	 */
	private static ReasoningPart signatureReasoningPart(StringBuilder signature) {
		return new ReasoningPart("", null, new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER,
				ConverseApiUtils.PAYLOAD_SIGNATURE, signature.toString()), BEDROCK_ATTRIBUTES);
	}

	private static ReasoningPart redactedReasoningPart(ByteArrayOutputStream redactedContent) {
		return new ReasoningPart(null, null,
				new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER, ConverseApiUtils.PAYLOAD_REDACTED_CONTENT,
						Base64.getEncoder().encodeToString(redactedContent.toByteArray())),
				BEDROCK_ATTRIBUTES);
	}

	private void emitPart(MessagePart part) {
		this.emitChatResponse(new Generation(AssistantMessage.builder().part(part).build()));
	}

	private void mergeNativeTokenUsage(TokenUsage tokenUsage) {
		this.tokenUsageRef.accumulateAndGet(tokenUsage, (current, next) -> {
			if (current == null) {
				return next;
			}
			else {
				return TokenUsage.builder()
					.inputTokens(addTokens(current.inputTokens(), next.inputTokens()))
					.outputTokens(addTokens(current.outputTokens(), next.outputTokens()))
					.totalTokens(addTokens(current.totalTokens(), next.totalTokens()))
					.cacheReadInputTokens(addTokens(current.cacheReadInputTokens(), next.cacheReadInputTokens()))
					.cacheWriteInputTokens(addTokens(current.cacheWriteInputTokens(), next.cacheWriteInputTokens()))
					.build();
			}
		});
	}

	private static Integer addTokens(Integer current, Integer next) {
		if (current == null) {
			return next;
		}
		if (next == null) {
			return current;
		}
		return current + next;
	}

	private void emitChatResponse(Generation generation) {
		var metadataBuilder = ChatResponseMetadata.builder();
		metadataBuilder.id(this.requestIdRef.get());
		metadataBuilder.usage(this.getCurrentUsage());

		ChatResponse chatResponse = new ChatResponse(generation == null ? List.of() : List.of(generation),
				metadataBuilder.build());

		this.eventSink.emitNext(chatResponse, DEFAULT_EMIT_FAILURE_HANDLER);
	}

	private Usage getCurrentUsage() {
		TokenUsage nativeUsage = this.tokenUsageRef.get();
		Integer cacheReadInt = nativeUsage != null ? nativeUsage.cacheReadInputTokens() : null;
		Integer cacheWriteInt = nativeUsage != null ? nativeUsage.cacheWriteInputTokens() : null;
		return new DefaultUsage(this.promptTokens.get(), this.generationTokens.get(), this.totalTokens.get(),
				nativeUsage, cacheReadInt != null ? cacheReadInt.longValue() : null,
				cacheWriteInt != null ? cacheWriteInt.longValue() : null);
	}

	/**
	 * Invoke the model and return the chat response stream.
	 * @see <a href=
	 * "https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html">
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html</a>
	 * @see <a href=
	 * "https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html">
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html</a>
	 * @see <a href=
	 * "https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream">
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream</a>
	 */
	public Flux<ChatResponse> stream() {

		ConverseStreamResponseHandler responseHandler = ConverseStreamResponseHandler.builder()
			.subscriber(this)
			.onResponse(converseStreamResponse -> this.requestIdRef
				.set(converseStreamResponse.responseMetadata().requestId()))
			.onComplete(() -> {
				this.eventSink.emitComplete(DEFAULT_EMIT_FAILURE_HANDLER);
				logger.info("Completed streaming response.");
			})
			.onError(error -> {
				logger.error("Error handling Bedrock converse stream response", error);
				this.eventSink.emitError(error, DEFAULT_EMIT_FAILURE_HANDLER);
			})
			.build();
		this.bedrockRuntimeAsyncClient.converseStream(this.converseStreamRequest, responseHandler);

		return this.eventSink.asFlux();
	}

}
