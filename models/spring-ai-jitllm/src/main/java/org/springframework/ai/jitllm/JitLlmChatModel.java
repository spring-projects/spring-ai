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

package org.springframework.ai.jitllm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.beehive.jitllm.api.GenerationEvent;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationSession;
import org.beehive.jitllm.api.InsufficientDeviceMemoryException;
import org.beehive.jitllm.api.LocalModel;
import org.beehive.jitllm.api.LocalModels;
import org.beehive.jitllm.api.ModelOptions;
import org.beehive.jitllm.api.TextGenerationModel;
import org.beehive.jitllm.api.ThinkingMode;
import org.beehive.jitllm.runtime.backend.BackendId;
import org.beehive.jitllm.runtime.memory.MemoryPlan;
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
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ChatModel} backed by <a href="https://github.com/beehive-lab/jitllm">jitLLM</a>,
 * which runs GGUF models in-process on the CPU or, through TornadoVM, on a GPU.
 *
 * <p>
 * Supports synchronous and streaming chat, tool calling (the model returns tool calls;
 * executing them is the caller's, as with every Spring AI chat model) and separation of
 * {@code <think>} content, which is returned in the assistant message's
 * {@value #THINKING_METADATA_KEY} property. The engine runs one request at a time; this
 * class serializes requests.
 *
 * <p>
 * For the GPU, start the JVM through TornadoVM's {@code tornado} launcher with
 * {@code -Duse.tornadovm=true}. The backend (CUDA, OpenCL or Metal) is whichever one the
 * TornadoVM SDK provides.
 *
 * @author Yuheng Zhou
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
public final class JitLlmChatModel implements ChatModel, AutoCloseable {

	/** The provider name used in observations and response metadata. */
	public static final String PROVIDER = "jitllm";

	/** The assistant message property holding the model's thinking content. */
	public static final String THINKING_METADATA_KEY = "thinking";

	private static final Logger logger = LoggerFactory.getLogger(JitLlmChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final TextGenerationModel model;

	private final JitLlmChatOptions defaultOptions;

	private final ToolCallingManager toolCallingManager;

	private final ObservationRegistry observationRegistry;

	private final String modelName;

	private final boolean onGpu;

	private final JitLlmResponseParser responseParser = new JitLlmResponseParser();

	// The engine runs one generation at a time on a session.
	private final ReentrantLock lock = new ReentrantLock();

	private final AtomicBoolean unsupportedOptionsWarned = new AtomicBoolean();

	private @Nullable GenerationSession session;

	private boolean closed;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	JitLlmChatModel(TextGenerationModel model, String modelName, boolean onGpu, JitLlmChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry) {
		this.model = Objects.requireNonNull(model, "model must not be null");
		this.modelName = modelName;
		this.onGpu = onGpu;
		this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");
		this.toolCallingManager = Objects.requireNonNull(toolCallingManager, "toolCallingManager must not be null");
		this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(PROVIDER)
			.build();
		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				GenerationResult result = generate(requestPrompt, null);
				ChatResponse chatResponse = toChatResponse(result, false);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
		return Objects.requireNonNull(response);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			Prompt requestPrompt = buildRequestPrompt(prompt);
			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(requestPrompt)
				.provider(PROVIDER)
				.streaming(true)
				.build();
			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation).start();

			Flux<ChatResponse> chunks = Flux
				.<ChatResponse>create(sink -> streamInto(requestPrompt, sink), FluxSink.OverflowStrategy.BUFFER)
				.subscribeOn(Schedulers.boundedElastic())
				.doOnError(observation::error)
				.doFinally(signal -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			return new MessageAggregator().aggregate(chunks, observationContext::setResponse);
		});
	}

	@Override
	public JitLlmChatOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Releases the model and its accelerator resources.
	 */
	@Override
	public void close() {
		this.lock.lock();
		try {
			if (this.closed) {
				return;
			}
			this.closed = true;
			// The engine refuses to close a model with a live session.
			if (this.session != null) {
				this.session.close();
				this.session = null;
			}
			this.model.close();
		}
		finally {
			this.lock.unlock();
		}
	}

	private void streamInto(Prompt prompt, FluxSink<ChatResponse> sink) {
		AtomicBoolean cancelled = new AtomicBoolean();
		sink.onDispose(() -> cancelled.set(true));
		try {
			if (!resolveTools(prompt).isEmpty()) {
				// Tool calls are only known once generation ends, so a request that
				// offers
				// tools is answered in one chunk.
				GenerationResult result = generate(prompt, null);
				if (!cancelled.get()) {
					sink.next(toChatResponse(result, false));
					sink.complete();
				}
				return;
			}
			ThinkingFilter filter = new ThinkingFilter();
			GenerationResult result = generate(prompt, event -> {
				String visible = filter.accept(event.text());
				if (!cancelled.get() && !visible.isEmpty()) {
					sink.next(new ChatResponse(List.of(new Generation(new AssistantMessage(visible)))));
				}
			});
			if (!cancelled.get()) {
				sink.next(toChatResponse(result, true));
				sink.complete();
			}
		}
		catch (RuntimeException ex) {
			if (!cancelled.get()) {
				sink.error(ex);
			}
		}
		catch (Error ex) {
			// An Error (a missing TornadoVM class, say) would end the scheduler's task
			// and
			// leave the subscriber waiting forever, and Reactor drops JVM-fatal errors
			// such
			// as LinkageError rather than signalling them, so it is wrapped.
			if (!cancelled.get()) {
				sink.error(new IllegalStateException("jitLLM generation failed: " + ex, ex));
			}
		}
	}

	private GenerationResult generate(Prompt prompt, @Nullable Consumer<GenerationEvent> onEvent) {
		JitLlmChatOptions options = (JitLlmChatOptions) Objects.requireNonNull(prompt.getOptions());
		warnUnsupportedOptions(options);

		GenerationRequest.Builder request = GenerationRequest.builder()
			.messages(JitLlmConversions.toEngineMessages(prompt.getInstructions()));
		if (options.getMaxTokens() != null) {
			request.maxNewTokens(options.getMaxTokens());
		}
		if (options.getTemperature() != null) {
			request.temperature(options.getTemperature().floatValue());
		}
		if (options.getTopP() != null) {
			request.topP(options.getTopP().floatValue());
		}
		if (options.getSeed() != null) {
			request.seed(options.getSeed());
		}
		if (!CollectionUtils.isEmpty(options.getStopSequences())) {
			request.stopSequences(options.getStopSequences());
		}
		List<ToolDefinition> tools = resolveTools(prompt);
		if (!tools.isEmpty()) {
			request.tools(JitLlmConversions.toEngineTools(tools));
		}
		if (onEvent != null) {
			request.onEvent(onEvent);
		}

		this.lock.lock();
		try {
			Assert.state(!this.closed, "the model is closed");
			if (this.session == null) {
				this.session = this.model.newSession();
			}
			// The caller sends the whole conversation on every request, and the session
			// keeps its own history; without a reset each request would append the
			// conversation again. The engine reuses the encoded prefix when one request
			// extends the last.
			this.session.reset();
			GenerationResult result = this.session.generate(request.build());
			switch (result.finishReason()) {
				case MAX_TOKENS ->
					logger.warn("Generation stopped after reaching maxTokens ({}), so the response is truncated",
							options.getMaxTokens());
				case CONTEXT_FULL ->
					logger.warn("Generation stopped because the context window is full, so the response is truncated");
				default -> {
				}
			}
			return result;
		}
		finally {
			this.lock.unlock();
		}
	}

	private List<ToolDefinition> resolveTools(Prompt prompt) {
		return this.toolCallingManager
			.resolveToolDefinitions((JitLlmChatOptions) Objects.requireNonNull(prompt.getOptions()));
	}

	private ChatResponse toChatResponse(GenerationResult result, boolean finalStreamChunk) {
		List<AssistantMessage.ToolCall> toolCalls = JitLlmConversions.toSpringToolCalls(result.toolCalls());
		JitLlmResponseParser.ParsedResponse parsed = this.responseParser.parse(result.text());
		// A final stream chunk carries metadata only: its text was already streamed.
		String content = (finalStreamChunk || !toolCalls.isEmpty()) ? "" : parsed.content();
		Map<String, Object> properties = parsed.thinking() != null ? Map.of(THINKING_METADATA_KEY, parsed.thinking())
				: Map.of();

		AssistantMessage message = AssistantMessage.builder()
			.content(content)
			.properties(properties)
			.toolCalls(toolCalls)
			.build();
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(JitLlmConversions.toSpringFinishReason(result.finishReason()))
			.build();
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.model(this.modelName)
			.usage(new DefaultUsage(result.promptTokens(), result.generatedTokens()))
			.keyValue("on-gpu", this.onGpu)
			.keyValue("prompt-tokens-per-second", result.timings().promptTokensPerSecond())
			.keyValue("generated-tokens-per-second", result.timings().generatedTokensPerSecond())
			.build();
		return new ChatResponse(List.of(new Generation(message, generationMetadata)), metadata);
	}

	/**
	 * The request's options: the prompt's merged over this model's defaults.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		Assert.notNull(prompt, "prompt must not be null");
		Assert.notEmpty(prompt.getInstructions(), "prompt must contain at least one message");
		JitLlmChatOptions.Builder options = this.defaultOptions.mutate();
		ChatOptions runtimeOptions = prompt.getOptions();
		if (runtimeOptions != null) {
			options.combineWith(runtimeOptions.mutate());
		}
		return prompt.mutate().chatOptions(options.build()).build();
	}

	private void warnUnsupportedOptions(JitLlmChatOptions options) {
		if ((options.getTopK() != null || options.getFrequencyPenalty() != null || options.getPresencePenalty() != null)
				&& this.unsupportedOptionsWarned.compareAndSet(false, true)) {
			logger.warn("Ignoring topK, frequencyPenalty and presencePenalty: jitLLM's sampler does not support them");
		}
	}

	/**
	 * Hides {@code <think>} blocks from streamed text, holding back a partial tag until
	 * the next token shows whether it is one.
	 */
	static final class ThinkingFilter {

		private static final String OPEN = "<think>";

		private static final String CLOSE = "</think>";

		private final StringBuilder text = new StringBuilder();

		private int emitted;

		String accept(String delta) {
			this.text.append(delta);
			String visible = visiblePrefix(this.text.toString());
			if (this.emitted == 0) {
				visible = visible.stripLeading();
				if (visible.isEmpty()) {
					return "";
				}
				// Leading whitespace is never emitted; remember what was skipped.
				this.emitted = visiblePrefix(this.text.toString()).length() - visible.length();
				visible = visiblePrefix(this.text.toString());
			}
			String next = visible.substring(this.emitted);
			this.emitted = visible.length();
			return next;
		}

		private static String visiblePrefix(String text) {
			StringBuilder visible = new StringBuilder();
			int cursor = 0;
			while (cursor < text.length()) {
				int open = text.indexOf(OPEN, cursor);
				if (open < 0) {
					String tail = text.substring(cursor);
					visible.append(tail, 0, tail.length() - partialTagLength(tail, OPEN));
					break;
				}
				visible.append(text, cursor, open);
				int close = text.indexOf(CLOSE, open + OPEN.length());
				if (close < 0) {
					break;
				}
				cursor = close + CLOSE.length();
			}
			return visible.toString();
		}

		/** How many trailing characters could be the start of {@code tag}. */
		private static int partialTagLength(String text, String tag) {
			for (int length = Math.min(tag.length() - 1, text.length()); length > 0; length--) {
				if (text.endsWith(tag.substring(0, length))) {
					return length;
				}
			}
			return 0;
		}

	}

	/**
	 * Loads a model and builds a {@link JitLlmChatModel}.
	 */
	public static final class Builder {

		/** The default model cache: {@code ~/.cache/jitllm/models}. */
		public static final Path DEFAULT_CACHE_DIRECTORY = Path.of(System.getProperty("user.home"), ".cache", "jitllm",
				"models");

		private @Nullable Path modelPath;

		private @Nullable String modelUrl;

		private Path cacheDirectory = DEFAULT_CACHE_DIRECTORY;

		private @Nullable String huggingFaceToken;

		private ThinkingMode thinking = ThinkingMode.DEFAULT;

		private boolean onGpu;

		private int contextLength;

		private @Nullable String modelName;

		private JitLlmChatOptions defaultOptions = JitLlmChatOptions.builder().build();

		private @Nullable ToolCallingManager toolCallingManager;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		/**
		 * The GGUF model file to load. Set this or {@link #modelUrl(String)}.
		 * @param modelPath the model file
		 * @return this builder
		 */
		public Builder modelPath(@Nullable Path modelPath) {
			this.modelPath = modelPath;
			return this;
		}

		/**
		 * The GGUF model to download, once, into {@link #cacheDirectory(Path)}: an
		 * {@code https} URL, or {@code hf://<owner>/<repository>/<file>.gguf} for a file
		 * on a Hugging Face repository's {@code main} branch. Set this or
		 * {@link #modelPath(Path)}.
		 * @param modelUrl the model URL
		 * @return this builder
		 */
		public Builder modelUrl(@Nullable String modelUrl) {
			this.modelUrl = modelUrl;
			return this;
		}

		/**
		 * Where downloaded models are kept. Defaults to {@link #DEFAULT_CACHE_DIRECTORY}.
		 * @param cacheDirectory the cache directory
		 * @return this builder
		 */
		public Builder cacheDirectory(Path cacheDirectory) {
			this.cacheDirectory = cacheDirectory;
			return this;
		}

		/**
		 * The Hugging Face access token, for gated or private models.
		 * @param huggingFaceToken the token
		 * @return this builder
		 */
		public Builder huggingFaceToken(@Nullable String huggingFaceToken) {
			this.huggingFaceToken = huggingFaceToken;
			return this;
		}

		/**
		 * Whether a model with a reasoning phase, such as Qwen 3, uses it. Defaults to
		 * {@link ThinkingMode#DEFAULT}, the model's own behaviour. {@code ENABLED} and
		 * {@code DISABLED} are rejected by a model that cannot represent the control.
		 * @param thinking the reasoning mode
		 * @return this builder
		 */
		public Builder thinking(ThinkingMode thinking) {
			this.thinking = thinking;
			return this;
		}

		/**
		 * Whether to run on a GPU through TornadoVM. Defaults to {@code false}. Requires
		 * the JVM to be started through TornadoVM with {@code -Duse.tornadovm=true}.
		 * @param onGpu whether to run on a GPU
		 * @return this builder
		 */
		public Builder onGpu(boolean onGpu) {
			this.onGpu = onGpu;
			return this;
		}

		/**
		 * The context length to allocate, in tokens. Defaults to the model's own.
		 * @param contextLength the context length, or {@code 0} for the model's own
		 * @return this builder
		 */
		public Builder contextLength(int contextLength) {
			this.contextLength = contextLength;
			return this;
		}

		/**
		 * The model name reported in response metadata. Defaults to the model file name.
		 * @param modelName the model name
		 * @return this builder
		 */
		public Builder modelName(@Nullable String modelName) {
			this.modelName = modelName;
			return this;
		}

		public Builder defaultOptions(JitLlmChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Loads the model.
		 * @return the chat model
		 */
		public JitLlmChatModel build() {
			Assert.isTrue(this.modelPath != null ^ this.modelUrl != null,
					"exactly one of modelPath and modelUrl must be set");
			Assert.isTrue(this.contextLength >= 0, "contextLength must not be negative");
			Path modelFile = this.modelPath != null ? this.modelPath
					: new JitLlmModelDownloader(this.cacheDirectory, this.huggingFaceToken)
						.resolve(Objects.requireNonNull(this.modelUrl));
			ModelOptions.Builder options = ModelOptions.builder()
				.contextLength(this.contextLength)
				.thinkingMode(this.thinking);
			if (this.onGpu) {
				// Naming a backend (CUDA, say) makes the engine reject an SDK built for
				// another; leaving it unset runs on the one the SDK provides.
				Assert.state(Boolean.getBoolean("use.tornadovm"),
						"onGpu requires the JVM to be started through TornadoVM with -Duse.tornadovm=true");
			}
			else {
				options.backend(BackendId.CPU);
			}
			ModelOptions modelOptions = options.build();
			if (this.onGpu) {
				preflight(modelFile, modelOptions);
			}
			LocalModel loaded;
			try {
				loaded = LocalModels.load(modelFile, modelOptions);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Failed to load the model from " + modelFile, ex);
			}
			catch (InsufficientDeviceMemoryException ex) {
				throw new IllegalStateException(doesNotFit(modelFile, ex.plan()), ex);
			}
			if (!(loaded instanceof TextGenerationModel textModel)) {
				loaded.close();
				throw new IllegalArgumentException(modelFile + " is not a text generation model");
			}
			String name = this.modelName != null ? this.modelName : String.valueOf(modelFile.getFileName());
			return new JitLlmChatModel(textModel, name, this.onGpu, this.defaultOptions,
					Objects.requireNonNullElse(this.toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER),
					this.observationRegistry);
		}

		/**
		 * Reports the predicted device memory before loading. The engine itself refuses a
		 * load that a reliable prediction says cannot fit; a prediction that is only an
		 * upper bound is reported here instead.
		 */
		private static void preflight(Path modelFile, ModelOptions options) {
			MemoryPlan plan;
			try {
				plan = LocalModels.preflight(modelFile, options);
			}
			catch (IOException | RuntimeException ex) {
				logger.debug("No device memory prediction for {}", modelFile, ex);
				return;
			}
			logger.info("{}", plan.describe());
			if (!plan.fitsConfiguredBudget() && plan.confidence() == MemoryPlan.Confidence.CONSERVATIVE) {
				logger.warn(
						"{} may not fit the device memory budget (-Dtornado.device.memory): up to {} MiB "
								+ "predicted, {} MiB configured",
						modelFile.getFileName(), mib(plan.predictedBudgetBytes()), mib(plan.configuredBudgetBytes()));
			}
		}

		static String doesNotFit(Path modelFile, MemoryPlan plan) {
			return modelFile.getFileName() + " does not fit the device memory budget: "
					+ mib(plan.predictedBudgetBytes()) + " MiB predicted, " + mib(plan.configuredBudgetBytes())
					+ " MiB configured. Raise -Dtornado.device.memory, lower the context length, or use a smaller"
					+ " or more quantized model.\n" + plan.describe();
		}

		private static long mib(long bytes) {
			return bytes / (1024 * 1024);
		}

	}

}
