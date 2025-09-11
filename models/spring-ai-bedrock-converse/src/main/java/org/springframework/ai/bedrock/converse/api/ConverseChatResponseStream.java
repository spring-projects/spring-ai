/*
 * Copyright 2025-2025 the original author or authors.
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import org.springframework.ai.chat.messages.AssistantMessage;
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
 *
 * @author Jared Rufer
 * @since 1.1.0
 */
public class ConverseChatResponseStream implements ConverseStreamResponseHandler.Visitor {

	private static final Logger logger = LoggerFactory.getLogger(ConverseChatResponseStream.class);

	public static final Sinks.EmitFailureHandler DEFAULT_EMIT_FAILURE_HANDLER = Sinks.EmitFailureHandler
		.busyLooping(Duration.ofSeconds(10));

	private final AtomicReference<String> requestIdRef = new AtomicReference<>("Unknown");

	private final AtomicReference<TokenUsage> tokenUsageRef = new AtomicReference<>();

	private final AtomicInteger promptTokens = new AtomicInteger();

	private final AtomicInteger generationTokens = new AtomicInteger();

	private final AtomicInteger totalTokens = new AtomicInteger();

	private final AtomicReference<String> stopReason = new AtomicReference<>();

	private final Map<Integer, StreamingToolCallBuilder> toolUseMap = new ConcurrentHashMap<>();

	private final Sinks.Many<ChatResponse> eventSink = Sinks.many().multicast().onBackpressureBuffer();

	private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private final ConverseStreamRequest converseStreamRequest;

	public ConverseChatResponseStream(BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
			ConverseStreamRequest converseStreamRequest, Usage accumulatedUsage) {

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
			this.emitChatResponse(new Generation(AssistantMessage.builder().content(event.delta().text()).build()));
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

		List<AssistantMessage.ToolCall> toolCalls = this.toolUseMap.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.map(StreamingToolCallBuilder::build)
			.toList();

		if (!toolCalls.isEmpty()) {
			this.emitChatResponse(new Generation(AssistantMessage.builder().content("").toolCalls(toolCalls).build(),
					generationMetadata));
		}
		else {
			this.emitChatResponse(new Generation(AssistantMessage.builder().content("").build(), generationMetadata));
		}
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
		return new DefaultUsage(this.promptTokens.get(), this.generationTokens.get(), this.totalTokens.get(),
				this.tokenUsageRef.get());
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
