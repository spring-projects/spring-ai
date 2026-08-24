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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Runtime adapter around the GPULlama3 inference engine.
 *
 * <p>
 * This class owns the loaded GPULlama3 {@link Model} and hides the low-level inference
 * details from {@link GpuLlama3ChatModel}. It is responsible for loading the GGUF model,
 * managing CPU/GPU {@link State} instances, creating request-local {@link Sampler}
 * instances, invoking {@code generateTokens} or {@code generateTokensGPU}, removing model
 * stop tokens, decoding generated tokens, and returning a
 * {@link GpuLlama3GenerationResult}.
 * </p>
 *
 * <p>
 * CPU mode creates a fresh {@link State} for each request. GPU mode keeps a pre-created
 * {@link State} and {@link TornadoVMMasterPlan}, because the TornadoVM execution plan is
 * bound to the state used during initialization.
 * </p>
 *
 * <p>
 * The runtime is {@link AutoCloseable}; closing it releases the TornadoVM execution plan
 * when GPU mode is enabled.
 * </p>
 */
public final class GpuLlama3Runtime implements AutoCloseable {

	static final int DEFAULT_MAX_TOKENS = 512;
	static final double DEFAULT_TEMPERATURE = 0.1;
	static final double DEFAULT_TOP_P = 1.0;
	static final long DEFAULT_SEED = 12345L;

	private static final Logger logger = LoggerFactory.getLogger(GpuLlama3Runtime.class);

	private final Model model;

	private final boolean onGpu;

	private final int contextLength;

	@Nullable private final State gpuState;

	@Nullable private final TornadoVmPlan tornadoVMPlan;

	private final int initialGpuToken;

	private final ReentrantLock lifecycleLock = new ReentrantLock();

	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Loads a GPULlama3 runtime using the real GPULlama3 model loader and TornadoVM plan
	 * initializer.
	 * @param options the GPULlama3 chat options containing model path, context length,
	 * and CPU/GPU mode
	 * @return a loaded runtime ready to generate tokens
	 */
	public static GpuLlama3Runtime load(GpuLlama3ChatOptions options) {
		return load(options, ModelLoader::loadModel, GpuLlama3Runtime::initializeDefaultTornadoVmPlan);
	}

	/**
	 * Loads a GPULlama3 runtime with injectable loading functions.
	 *
	 * <p>
	 * This overload is mainly used by tests so the runtime can verify loading, CPU/GPU
	 * setup, and error handling without loading a real GGUF model or initializing
	 * TornadoVM.
	 * </p>
	 * @param options the load-time options
	 * @param modelLoader function used to load the GPULlama3 model
	 * @param planInitializer function used to initialize the TornadoVM plan in GPU mode
	 * @return a loaded runtime
	 */
	static GpuLlama3Runtime load(GpuLlama3ChatOptions options, ModelLoaderFunction modelLoader,
			TornadoVmPlanInitializer planInitializer) {
		// Verify that the relevant fields exist.
		Assert.notNull(options, "options must not be null");
		Assert.notNull(modelLoader, "modelLoader must not be null");
		Assert.notNull(planInitializer, "planInitializer must not be null");

		Path modelPath = Objects.requireNonNull(options.getModelPath(), "modelPath must not be null");
		int contextLength = requirePositive(options.getContextLength(), "contextLength");
		boolean onGpu = Boolean.TRUE.equals(options.getOnGpu());

		Model model;
		try {
			model = modelLoader.load(modelPath, contextLength, true, onGpu);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load GPULlama3 model from " + modelPath, ex);
		}
		Assert.notNull(model, "modelLoader returned null");

		// In GPU mode the TornadoVM plan is initialized against a specific State.
		// The State and plan must be reused together.
		State gpuState = null;
		TornadoVmPlan tornadoVMPlan = null;
		if (onGpu) {
			gpuState = model.createNewState();
			Assert.notNull(gpuState, "model.createNewState() returned null for GPU runtime");
			tornadoVMPlan = planInitializer.initialize(gpuState, model);
			Assert.notNull(tornadoVMPlan, "planInitializer returned null");
		}

		return new GpuLlama3Runtime(model, onGpu, contextLength, gpuState, tornadoVMPlan);
	}

	/**
	 * Creates a runtime around an already loaded model.
	 *
	 * <p>
	 * This constructor is package-private so tests can build a runtime with a fake model.
	 * Production code should normally use {@link #load(GpuLlama3ChatOptions)}.
	 * </p>
	 * @param model the loaded GPULlama3 model
	 * @param onGpu whether generation should use TornadoVM GPU execution
	 * @param contextLength the loaded model context length
	 * @param gpuState the pre-created GPU state, required only in GPU mode
	 * @param tornadoVMPlan the TornadoVM execution plan, required only in GPU mode
	 */
	GpuLlama3Runtime(Model model, boolean onGpu, int contextLength, @Nullable State gpuState,
			@Nullable TornadoVmPlan tornadoVMPlan) {
		this.model = Objects.requireNonNull(model, "model must not be null");
		this.onGpu = onGpu;
		this.contextLength = requirePositive(contextLength, "contextLength");
		if (onGpu) {
			Assert.notNull(gpuState, "GPU runtime requires a pre-created State");
			Assert.notNull(tornadoVMPlan, "GPU runtime requires a TornadoVM plan");
		}
		this.gpuState = gpuState;
		this.tornadoVMPlan = tornadoVMPlan;
		this.initialGpuToken = (gpuState != null) ? gpuState.latestToken : -1;
	}

	public Model model() {
		return this.model;
	}

	public boolean isOnGpu() {
		return this.onGpu;
	}

	public int contextLength() {
		return this.contextLength;
	}

	/**
	 * Generates completion tokens for an already encoded prompt.
	 *
	 * <p>
	 * The prompt must already be encoded by {@link GpuLlama3PromptEncoder}. This method
	 * serializes generation and close operations with a lifecycle lock so the runtime
	 * cannot free GPU resources while generation is in progress.
	 * </p>
	 * @param promptTokens encoded prompt tokens
	 * @param options effective request options after default/runtime option merging
	 * @param tokenConsumer optional callback for streaming generated token ids
	 * @return the raw generation result including tokens, text, finish reason, and timing
	 */
	public GpuLlama3GenerationResult generate(List<Integer> promptTokens, GpuLlama3ChatOptions options,
			@Nullable IntConsumer tokenConsumer) {
		this.lifecycleLock.lock();
		try {
			return doGenerate(promptTokens, options, tokenConsumer);
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	/**
	 * Performs the actual generation flow after the lifecycle lock has been acquired.
	 * @param promptTokens encoded prompt tokens
	 * @param options effective generation options
	 * @param tokenConsumer optional streaming token callback
	 * @return the completed generation result
	 */
	private GpuLlama3GenerationResult doGenerate(List<Integer> promptTokens, GpuLlama3ChatOptions options,
			@Nullable IntConsumer tokenConsumer) {
		Assert.notNull(promptTokens, "promptTokens must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.state(!this.closed.get(), "GpuLlama3Runtime is closed");

		// Copy the prompt tokens so this generation is isolated from later caller-side
		// list changes.
		List<Integer> promptTokenSnapshot = List.copyOf(promptTokens);
		ChatFormat chatFormat = this.model.chatFormat();
		Assert.notNull(chatFormat, "model chatFormat must not be null");

		// GPULlama3 uses model-defined stop tokens from the chat format, not arbitrary
		// Spring AI stop sequences.
		Set<Integer> stopTokens = chatFormat.getStopTokens();
		Assert.notNull(stopTokens, "model stopTokens must not be null");

		// maxTokens cannot exceed the loaded context length.
		int maxTokens = effectiveMaxTokens(options, promptTokenSnapshot.size());
		Sampler sampler = createSampler(options);
		State state = resolveState();

		long startNanos = System.nanoTime();
		List<Integer> generatedTokens = generateTokens(state, promptTokenSnapshot, stopTokens, maxTokens, sampler,
				tokenConsumer);
		long durationNanos = System.nanoTime() - startNanos;

		Assert.notNull(generatedTokens, "model generation returned null");
		List<Integer> completionTokens = new ArrayList<>(generatedTokens);
		String finishReason = removeStopToken(completionTokens, stopTokens)
				? GpuLlama3GenerationResult.FINISH_REASON_STOP : GpuLlama3GenerationResult.FINISH_REASON_LENGTH;
		String rawText = this.model.tokenizer().decode(completionTokens);

		return new GpuLlama3GenerationResult(promptTokenSnapshot, completionTokens, rawText, finishReason,
				durationNanos);
	}

	/**
	 * Dispatches token generation to the CPU or GPU GPULlama3 API.
	 * @param state the inference state to use
	 * @param promptTokens encoded prompt tokens
	 * @param stopTokens model-defined stop tokens
	 * @param maxTokens effective max token budget
	 * @param sampler request-local sampler
	 * @param tokenConsumer optional streaming token callback
	 * @return generated token ids, usually ending with a stop token when generation stops
	 * normally
	 */
	private List<Integer> generateTokens(State state, List<Integer> promptTokens, Set<Integer> stopTokens,
			int maxTokens, Sampler sampler, @Nullable IntConsumer tokenConsumer) {
		if (this.onGpu) {
			// GPU State is reused with the TornadoVM plan.
			// reset the latest token before each request to reduce cross-request state
			// leakage.
			resetGpuStateLatestToken(state);
			Assert.state(this.tornadoVMPlan != null, "GPU runtime requires a TornadoVM plan");
			return this.model.generateTokensGPU(state, 0, promptTokens, stopTokens, maxTokens, sampler, false,
					tokenConsumer, this.tornadoVMPlan.executionPlan());
		}
		return this.model.generateTokens(state, 0, promptTokens, stopTokens, maxTokens, sampler, false, tokenConsumer);
	}

	/**
	 * Closes the runtime and releases GPU resources if needed.
	 *
	 * <p>
	 * This method is idempotent. Generation after close is rejected, and GPU plan release
	 * failures are logged rather than rethrown.
	 * </p>
	 */
	@Override
	public void close() {
		this.lifecycleLock.lock();
		try {
			if (!this.closed.compareAndSet(false, true)) {
				return;
			}
			// Only GPU mode creates a TornadoVM plan. CPU mode has no plan to release.
			if (this.tornadoVMPlan != null) {
				try {
					this.tornadoVMPlan.free();
				}
				catch (RuntimeException ex) {
					logger.warn("Failed to free TornadoVM execution plan", ex);
				}
			}
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	/**
	 * Creates a request-local GPULlama3 sampler from the effective options.
	 * @param options effective request options
	 * @return a sampler configured with vocabulary size, temperature, top-p, and seed
	 */
	private Sampler createSampler(GpuLlama3ChatOptions options) {
		int vocabularySize = this.model.configuration().vocabularySize();
		double temperature = Objects.requireNonNullElse(options.getTemperature(), DEFAULT_TEMPERATURE);
		double topP = Objects.requireNonNullElse(options.getTopP(), DEFAULT_TOP_P);
		long seed = Objects.requireNonNullElse(options.getSeed(), DEFAULT_SEED);
		validateTemperature(temperature);
		validateTopP(topP);

		return Sampler.selectSampler(vocabularySize, (float) temperature, (float) topP, seed);
	}

	/**
	 * Resolves the inference State for the current request.
	 *
	 * <p>
	 * CPU requests get a fresh State. GPU requests reuse the pre-created State because
	 * the TornadoVM execution plan is bound to it.
	 * </p>
	 * @return the State to use for generation
	 */
	private State resolveState() {
		if (this.onGpu) {
			Assert.state(this.gpuState != null, "GPU runtime requires a pre-created State");
			return this.gpuState;
		}
		State state = this.model.createNewState();
		Assert.notNull(state, "model.createNewState() returned null");
		return state;
	}

	private void resetGpuStateLatestToken(State state) {
		state.latestToken = this.initialGpuToken;
	}

	/**
	 * Resolves and validates the effective max token budget for a request.
	 *
	 * <p>
	 * The request maxTokens value is clamped to the loaded context length. The prompt
	 * must leave at least one token of room for generation.
	 * </p>
	 * @param options effective request options
	 * @param promptTokenCount number of tokens in the encoded prompt
	 * @return the max token budget passed to GPULlama3
	 */
	private int effectiveMaxTokens(GpuLlama3ChatOptions options, int promptTokenCount) {
		// maxTokens is per-request generation budget. If absent, use provider default.
		int maxTokens = Objects.requireNonNullElse(options.getMaxTokens(), DEFAULT_MAX_TOKENS);
		Assert.isTrue(maxTokens > 0, "maxTokens must be positive");
		int effectiveMaxTokens = maxTokens;
		if (maxTokens > this.contextLength) {
			logger.warn("Clamping maxTokens from {} to contextLength {}", maxTokens, this.contextLength);
			effectiveMaxTokens = this.contextLength;
		}
		if (promptTokenCount >= effectiveMaxTokens) {
			throw new IllegalArgumentException(
					"prompt length " + promptTokenCount + " leaves no room within maxTokens " + effectiveMaxTokens);
		}
		return effectiveMaxTokens;
	}

	/**
	 * Validates the sampling temperature.
	 */
	private static void validateTemperature(double temperature) {
		Assert.isTrue(Double.isFinite(temperature), "temperature must be finite");
		Assert.isTrue(temperature >= 0.0, "temperature must be greater than or equal to 0");
	}

	/**
	 * Validates the top-p sampling value.
	 */
	private static void validateTopP(double topP) {
		Assert.isTrue(Double.isFinite(topP), "topP must be finite");
		Assert.isTrue(topP > 0.0 && topP <= 1.0, "topP must be greater than 0 and less than or equal to 1");
	}

	/**
	 * Removes a trailing model stop token from generated completion tokens.
	 * @param completionTokens mutable generated token list
	 * @param stopTokens model-defined stop token ids
	 * @return true if a stop token was removed, false otherwise
	 */
	private static boolean removeStopToken(List<Integer> completionTokens, Set<Integer> stopTokens) {
		if (completionTokens.isEmpty()) {
			return false;
		}
		int lastIndex = completionTokens.size() - 1;
		if (!stopTokens.contains(completionTokens.get(lastIndex))) {
			return false;
		}
		completionTokens.remove(lastIndex);
		return true;
	}

	/**
	 * Validates that a nullable integer option is present and positive.
	 */
	private static int requirePositive(@Nullable Integer value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		return requirePositive(value.intValue(), name);
	}

	@Nullable private static TornadoVmPlan initializeDefaultTornadoVmPlan(State state, Model model) {
		TornadoVMMasterPlan executionPlan = TornadoVMMasterPlan.initializeTornadoVMPlan(state, model);
		return (executionPlan != null) ? new DefaultTornadoVmPlan(executionPlan) : null;
	}

	/**
	 * Validates that an integer is positive.
	 */
	private static int requirePositive(int value, String name) {
		Assert.isTrue(value > 0, name + " must be positive");
		return value;
	}

	/**
	 * Abstraction over GPULlama3 model loading.
	 *
	 * <p>
	 * Production code delegates to
	 * {@link ModelLoader#loadModel(Path, int, boolean, boolean)}. Tests can provide a
	 * fake implementation to avoid loading a real GGUF file.
	 * </p>
	 */
	@FunctionalInterface
	interface ModelLoaderFunction {

		Model load(Path modelPath, int contextLength, boolean loadWeights, boolean useTornadovm) throws IOException;

	}

	/**
	 * Abstraction over TornadoVM plan initialization.
	 *
	 * <p>
	 * Production code delegates to
	 * {@link TornadoVMMasterPlan#initializeTornadoVMPlan(State, Model)}. Tests can
	 * provide a fake implementation to avoid starting TornadoVM.
	 * </p>
	 */
	@FunctionalInterface
	interface TornadoVmPlanInitializer {

		@Nullable TornadoVmPlan initialize(State state, Model model);

	}

	interface TornadoVmPlan {

		@Nullable TornadoVMMasterPlan executionPlan();

		void free();

	}

	private record DefaultTornadoVmPlan(TornadoVMMasterPlan executionPlan) implements TornadoVmPlan {

		private DefaultTornadoVmPlan {
			Objects.requireNonNull(executionPlan, "executionPlan must not be null");
		}

		@Override
		public void free() {
			this.executionPlan.freeTornadoExecutionPlan();
		}

	}

}
