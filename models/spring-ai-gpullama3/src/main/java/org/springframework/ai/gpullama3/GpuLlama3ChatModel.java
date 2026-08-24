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

package org.springframework.ai.gpullama3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.tokenizer.impl.Tokenizer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

/**
 * Spring AI {@link ChatModel} implementation backed by the local GPULlama3 inference
 * engine.
 *
 * <p>
 * This class is the main Spring AI entry point for the GPULlama3 provider. It adapts
 * Spring AI {@link Prompt} requests into GPULlama3 prompt tokens, delegates generation to
 * {@link GpuLlama3Runtime}, parses optional thinking content, and converts the result
 * back into Spring AI {@link ChatResponse} objects.
 * </p>
 *
 * <p>
 * The class supports both synchronous {@link #call(Prompt)} and streaming
 * {@link #stream(Prompt)} generation. A single inference lock is used because the
 * underlying GPULlama3 state and GPU execution plan are not treated as safely reusable by
 * concurrent requests.
 * </p>
 */
public final class GpuLlama3ChatModel implements ChatModel, AutoCloseable {

	public static final String PROVIDER = "gpullama3";

	public static final String THINKING_METADATA_KEY = "thinking";

	public static final String DURATION_NANOS_METADATA_KEY = "durationNanos";

	public static final String ON_GPU_METADATA_KEY = "onGpu";

	private static final Logger logger = LoggerFactory.getLogger(GpuLlama3ChatModel.class);

	private final GpuLlama3ChatOptions defaultOptions;

	private final GpuLlama3Runtime runtime;

	private final GpuLlama3PromptEncoder promptEncoder;

	private final GpuLlama3ResponseParser responseParser;

	// GPU plans should not be used as concurrent, safe resources.
	private final ReentrantLock inferenceLock = new ReentrantLock();

	// Unsupported parameters will only be warned once in the log
	private final AtomicBoolean defaultUnsupportedOptionsWarned = new AtomicBoolean();

	public GpuLlama3ChatModel(GpuLlama3ChatOptions defaultOptions) {
		this(defaultOptions, GpuLlama3Runtime.load(defaultOptions), new GpuLlama3PromptEncoder(),
				new GpuLlama3ResponseParser());
	}

	GpuLlama3ChatModel(GpuLlama3ChatOptions defaultOptions, GpuLlama3Runtime runtime,
			GpuLlama3PromptEncoder promptEncoder, GpuLlama3ResponseParser responseParser) {
		this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");
		this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
		this.promptEncoder = Objects.requireNonNull(promptEncoder, "promptEncoder must not be null");
		this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
	}

	/**
	 * Generates a complete chat response for the given prompt.
	 * @param prompt Spring AI prompt containing messages and optional runtime options
	 * @return a complete Spring AI chat response with content, usage, and metadata
	 */
	@Override
	public @NonNull ChatResponse call(@NonNull Prompt prompt) {
		PreparedPrompt preparedPrompt = preparePrompt(prompt);
		GpuLlama3GenerationResult result = generate(preparedPrompt, null);
		return toChatResponse(result, false);
	}

	/**
	 * Streams chat response chunks for the given prompt.
	 *
	 * <p>
	 * Generation is scheduled on Reactor's bounded elastic scheduler because GPULlama3
	 * inference is blocking CPU/GPU work.
	 * </p>
	 * @param prompt Spring AI prompt containing messages and optional runtime options
	 * @return a Flux of partial response chunks followed by a final metadata chunk
	 */
	@Override
	public @NonNull Flux<ChatResponse> stream(@NonNull Prompt prompt) {
		return Flux.<ChatResponse>create(sink -> stream(prompt, sink)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public GpuLlama3ChatOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * Closes the underlying runtime and releases any held GPULlama3 resources.
	 */
	@Override
	public void close() {
		// Serialize access to the underlying runtime because GPULlama3 state is not
		// treated as concurrent-safe.
		this.inferenceLock.lock();
		try {
			this.runtime.close();
		}
		finally {
			this.inferenceLock.unlock();
		}
	}

	/**
	 * Merges request options, validates the prompt, and encodes it into GPULlama3 tokens.
	 * @param prompt original Spring AI prompt
	 * @return prepared options and prompt tokens ready for generation
	 */
	private PreparedPrompt preparePrompt(Prompt prompt) {
		Assert.notNull(prompt, "prompt must not be null");
		Assert.notEmpty(prompt.getInstructions(), "prompt must contain at least one message");

		GpuLlama3ChatOptions requestOptions = this.defaultOptions.mergeWithRuntimeOptions(prompt.getOptions());
		warnUnsupportedOptions(prompt.getOptions());
		warnDefaultUnsupportedOptionsOnce();

		Prompt requestPrompt = new Prompt(List.copyOf(prompt.getInstructions()), requestOptions);
		List<Integer> promptTokens = this.promptEncoder.encode(requestPrompt, this.runtime.model());
		return new PreparedPrompt(requestOptions, promptTokens);
	}

	/**
	 * Runs GPULlama3 generation under the inference lock.
	 * @param preparedPrompt merged options and encoded prompt tokens
	 * @param tokenConsumer optional token callback for streaming
	 * @return raw GPULlama3 generation result
	 */
	private GpuLlama3GenerationResult generate(PreparedPrompt preparedPrompt, @Nullable IntConsumer tokenConsumer) {
		this.inferenceLock.lock();
		try {
			return this.runtime.generate(preparedPrompt.promptTokens(), preparedPrompt.options(), tokenConsumer);
		}
		finally {
			this.inferenceLock.unlock();
		}
	}

	/**
	 * Bridges GPULlama3 token streaming into a Spring AI Flux sink.
	 * @param prompt original prompt
	 * @param sink Flux sink receiving response chunks
	 */
	private void stream(Prompt prompt, FluxSink<ChatResponse> sink) {
		PreparedPrompt preparedPrompt = preparePromptForStream(prompt, sink);
		if (preparedPrompt == null) {
			return;
		}

		AtomicBoolean cancelled = registerCancellation(sink);
		IntConsumer tokenConsumer = streamTokenConsumer(sink, cancelled);
		generateStream(preparedPrompt, tokenConsumer, sink, cancelled);
	}

	/**
	 * Prepares a prompt for streaming and routes preparation failures to the sink.
	 * @param prompt original prompt
	 * @param sink Flux sink used to signal errors
	 * @return prepared prompt, or null if preparation failed
	 */
	@Nullable private PreparedPrompt preparePromptForStream(Prompt prompt, FluxSink<ChatResponse> sink) {
		try {
			return preparePrompt(prompt);
		}
		catch (RuntimeException ex) {
			sink.error(ex);
			return null;
		}
	}

	/**
	 * Registers stream cancellation and disposal callbacks.
	 * @param sink Flux sink
	 * @return shared cancellation flag
	 */
	private static AtomicBoolean registerCancellation(FluxSink<ChatResponse> sink) {
		AtomicBoolean cancelled = new AtomicBoolean(false);
		sink.onCancel(() -> cancelled.set(true));
		sink.onDispose(() -> cancelled.set(true));
		return cancelled;
	}

	/**
	 * Creates a token consumer that emits incremental text chunks.
	 * @param sink Flux sink receiving partial responses
	 * @param cancelled cancellation flag
	 * @return token consumer passed to the runtime
	 */
	private IntConsumer streamTokenConsumer(FluxSink<ChatResponse> sink, AtomicBoolean cancelled) {
		List<Integer> streamedTokens = new ArrayList<>();
		StringBuilder emittedText = new StringBuilder();
		return token -> {
			if (cancelled.get() || shouldSkipStreamToken(token)) {
				return;
			}
			streamedTokens.add(token);
			String text = safeStreamPrefix(this.runtime.model().tokenizer().decode(streamedTokens));
			if (text.length() > emittedText.length()) {
				String delta = text.substring(emittedText.length());
				emittedText.append(delta);
				sink.next(toPartialChatResponse(delta));
			}
		};
	}

	/**
	 * Executes streaming generation and completes or errors the Flux sink.
	 */
	private void generateStream(PreparedPrompt preparedPrompt, IntConsumer tokenConsumer, FluxSink<ChatResponse> sink,
			AtomicBoolean cancelled) {
		try {
			GpuLlama3GenerationResult result = generate(preparedPrompt, tokenConsumer);
			completeStream(result, sink, cancelled);
		}
		catch (RuntimeException ex) {
			errorStream(sink, cancelled, ex);
		}
	}

	/**
	 * Emits the final stream chunk containing metadata and completes the sink.
	 */
	private void completeStream(GpuLlama3GenerationResult result, FluxSink<ChatResponse> sink,
			AtomicBoolean cancelled) {
		if (cancelled.get()) {
			return;
		}
		sink.next(toChatResponse(result, true));
		sink.complete();
	}

	/**
	 * Propagates a generation error unless the stream has already been cancelled.
	 */
	private static void errorStream(FluxSink<ChatResponse> sink, AtomicBoolean cancelled, RuntimeException ex) {
		if (!cancelled.get()) {
			sink.error(ex);
		}
	}

	/**
	 * Converts a GPULlama3 generation result into a Spring AI ChatResponse.
	 * @param result raw generation result from the runtime
	 * @param finalStreamChunk whether this response is the final stream metadata chunk
	 * @return Spring AI chat response
	 */
	private ChatResponse toChatResponse(GpuLlama3GenerationResult result, boolean finalStreamChunk) {
		GpuLlama3ResponseParser.ParsedResponse parsedResponse = this.responseParser.parse(result.rawText());
		// Final stream chunk carries metadata only; content must stay empty to avoid
		// duplicate streamed text
		String content = finalStreamChunk ? "" : parsedResponse.content();

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(content)
			.properties(assistantMessageProperties(parsedResponse.thinking()))
			.build();
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(result.finishReason())
			.metadata(DURATION_NANOS_METADATA_KEY, result.durationNanos())
			.build();

		Generation generation = new Generation(assistantMessage, generationMetadata);
		ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
			.model(resolveModelName())
			.usage(new DefaultUsage(result.promptTokens().size(), result.completionTokens().size()))
			.keyValue("provider", PROVIDER)
			.keyValue(ON_GPU_METADATA_KEY, this.runtime.isOnGpu())
			.keyValue(DURATION_NANOS_METADATA_KEY, result.durationNanos())
			.build();

		return new ChatResponse(List.of(generation), responseMetadata);
	}

	/**
	 * Creates a partial streaming response containing only newly generated text.
	 */
	private ChatResponse toPartialChatResponse(String content) {
		AssistantMessage assistantMessage = AssistantMessage.builder().content(content).build();
		return new ChatResponse(List.of(new Generation(assistantMessage)));
	}

	/**
	 * Builds assistant message properties for optional thinking content.
	 */
	private Map<String, Object> assistantMessageProperties(@Nullable String thinking) {
		return thinking == null ? Map.of() : Map.of(THINKING_METADATA_KEY, thinking);
	}

	/**
	 * Determines whether a generated token should be hidden from streamed output.
	 */
	private boolean shouldSkipStreamToken(int token) {
		if (this.runtime.model().chatFormat().getStopTokens().contains(token)) {
			return true;
		}
		Tokenizer tokenizer = this.runtime.model().tokenizer();
		return !tokenizer.shouldDisplayToken(token);
	}

	/**
	 * Returns the safely displayable prefix of decoded streaming text.
	 *
	 * <p>
	 * If decoding produced a replacement character, the text is likely waiting for more
	 * tokens to complete a multibyte character.
	 * </p>
	 */
	private static String safeStreamPrefix(String text) {
		int replacementCharacterIndex = text.indexOf('\uFFFD');
		return replacementCharacterIndex >= 0 ? text.substring(0, replacementCharacterIndex) : text;
	}

	/**
	 * Resolves the model name used in response metadata.
	 */
	private String resolveModelName() {
		String configuredModel = this.defaultOptions.getModel();
		if (configuredModel != null && !configuredModel.isBlank()) {
			return configuredModel;
		}
		Path modelPath = this.defaultOptions.getModelPath();
		if (modelPath != null) {
			return modelPath.getFileName().toString();
		}
		return PROVIDER;
	}

	/**
	 * Logs unsupported default options once to avoid repeated warnings.
	 */
	private void warnDefaultUnsupportedOptionsOnce() {
		if (this.defaultUnsupportedOptionsWarned.compareAndSet(false, true)) {
			warnUnsupportedOptions(this.defaultOptions);
		}
	}

	/**
	 * Logs Spring AI chat options accepted for compatibility but ignored by GPULlama3.
	 */
	private static void warnUnsupportedOptions(@Nullable ChatOptions options) {
		if (options == null) {
			return;
		}
		List<String> stopSequences = options.getStopSequences();
		if (stopSequences != null && !stopSequences.isEmpty()) {
			logger.warn("Ignoring stopSequences because GPULlama3 uses model-defined stop tokens");
		}
		if (options.getTopK() != null) {
			logger.warn("Ignoring topK because GPULlama3 sampler does not expose top-k sampling");
		}
		if (options.getFrequencyPenalty() != null) {
			logger.warn("Ignoring frequencyPenalty because GPULlama3 sampler does not expose frequency penalties");
		}
		if (options.getPresencePenalty() != null) {
			logger.warn("Ignoring presencePenalty because GPULlama3 sampler does not expose presence penalties");
		}
	}

	/**
	 * Prepared prompt data used after option merging and prompt encoding.
	 */
	private record PreparedPrompt(GpuLlama3ChatOptions options, List<Integer> promptTokens) {
	}

}
