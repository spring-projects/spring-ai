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
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.inference.weights.Weights;
import org.beehive.gpullama3.model.Configuration;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.ModelType;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.tokenizer.Tokenizer;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3RuntimeTests {

	@Test
	// Verifies CPU runtime loading does not initialize GPU state or TornadoVM plan.
	void loadCreatesCpuRuntimeWithoutGpuPlan() {
		RecordingModel model = new RecordingModel();
		LoaderInvocation[] loaderInvocation = new LoaderInvocation[1];

		GpuLlama3Runtime runtime = GpuLlama3Runtime.load(loadOptions(false, 128),
				(modelPath, contextLength, loadWeights, useTornadovm) -> {
					loaderInvocation[0] = new LoaderInvocation(modelPath, contextLength, loadWeights, useTornadovm);
					return model;
				}, (state, loadedModel) -> {
					throw new AssertionError("CPU runtime must not initialize TornadoVM");
				});

		assertThat(runtime.model()).isSameAs(model);
		assertThat(runtime.isOnGpu()).isFalse();
		assertThat(runtime.contextLength()).isEqualTo(128);
		assertThat(loaderInvocation[0])
			.isEqualTo(new LoaderInvocation(Path.of("/models/llama.gguf"), 128, true, false));
		assertThat(model.createStateCalls).isZero();
	}

	@Test
	// Verifies GPU runtime loading rejects a missing TornadoVM plan after state creation.
	void loadRejectsNullGpuPlanAfterCreatingState() {
		RecordingModel model = new RecordingModel();
		State[] initializedState = new State[1];
		Model[] initializedModel = new Model[1];
		GpuLlama3ChatOptions options = loadOptions(true, 64);
		GpuLlama3Runtime.ModelLoaderFunction modelLoader = (modelPath, contextLength, loadWeights,
				useTornadovm) -> model;
		GpuLlama3Runtime.TornadoVmPlanInitializer planInitializer = (state, loadedModel) -> {
			initializedState[0] = state;
			initializedModel[0] = loadedModel;
			return null;
		};

		assertThatThrownBy(() -> GpuLlama3Runtime.load(options, modelLoader, planInitializer))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("planInitializer returned null");

		assertThat(model.createStateCalls).isEqualTo(1);
		assertThat(initializedState[0]).isSameAs(model.createdStates.get(0));
		assertThat(initializedModel[0]).isSameAs(model);
	}

	@Test
	// Verifies model loading IO failures are wrapped with model path context.
	void loadWrapsModelLoaderIOException() {
		GpuLlama3ChatOptions options = loadOptions(false, 64);
		GpuLlama3Runtime.ModelLoaderFunction failingModelLoader = failingModelLoader();
		GpuLlama3Runtime.TornadoVmPlanInitializer planInitializer = noOpPlanInitializer();

		assertThatThrownBy(() -> GpuLlama3Runtime.load(options, failingModelLoader, planInitializer))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to load GPULlama3 model from /models/llama.gguf")
			.hasRootCauseMessage("cannot read");
	}

	@Test
	// Verifies loading fails fast when the model path is missing.
	void loadRejectsMissingModelPath() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().contextLength(64).onGpu(false).build();
		GpuLlama3Runtime.ModelLoaderFunction unreachableModelLoader = unreachableModelLoader();
		GpuLlama3Runtime.TornadoVmPlanInitializer planInitializer = noOpPlanInitializer();

		assertThatNullPointerException()
			.isThrownBy(() -> GpuLlama3Runtime.load(options, unreachableModelLoader, planInitializer))
			.withMessage("modelPath must not be null");
	}

	@Test
	// Verifies loading fails fast when context length is missing.
	void loadRejectsMissingContextLength() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.build();
		GpuLlama3Runtime.ModelLoaderFunction unreachableModelLoader = unreachableModelLoader();
		GpuLlama3Runtime.TornadoVmPlanInitializer planInitializer = noOpPlanInitializer();

		assertThatNullPointerException()
			.isThrownBy(() -> GpuLlama3Runtime.load(options, unreachableModelLoader, planInitializer))
			.withMessage("contextLength must not be null");
	}

	@Test
	// Verifies CPU generation uses generateTokens and removes the trailing stop token.
	void generateUsesCpuPathAndRemovesStopToken() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);
		List<Integer> streamedTokens = new ArrayList<>();

		GpuLlama3GenerationResult result = runtime.generate(List.of(1, 2),
				GpuLlama3ChatOptions.builder().maxTokens(20).temperature(0.0).topP(1.0).seed(7L).build(),
				streamedTokens::add);

		assertThat(result.promptTokens()).containsExactly(1, 2);
		assertThat(result.completionTokens()).containsExactly(10, 11);
		assertThat(result.rawText()).isEqualTo("10|11");
		assertThat(result.finishReason()).isEqualTo(GpuLlama3GenerationResult.FINISH_REASON_STOP);
		assertThat(result.durationNanos()).isNotNegative();
		assertThat(streamedTokens).containsExactly(10, 11, 99);
		assertThat(model.createStateCalls).isEqualTo(1);
		assertThat(model.lastInvocation.gpu()).isFalse();
		assertThat(model.lastInvocation.startPosition()).isZero();
		assertThat(model.lastInvocation.promptTokens()).containsExactly(1, 2);
		assertThat(model.lastInvocation.stopTokens()).containsExactly(99);
		assertThat(model.lastInvocation.maxTokens()).isEqualTo(20);
		assertThat(model.lastInvocation.sampler()).isNotNull();
		assertThat(model.lastInvocation.echo()).isFalse();
	}

	@Test
	// Verifies GPU generation uses generateTokensGPU and reuses the GPU state and plan.
	void generateUsesGpuPathAndReusesGpuState() {
		RecordingModel model = new RecordingModel();
		model.gpuGeneratedTokens = List.of(12, 99);
		RecordingPlan tornadoVMPlan = new RecordingPlan();
		GpuLlama3Runtime runtime = gpuRuntime(model, 64, tornadoVMPlan);
		State gpuState = model.createdStates.get(0);

		GpuLlama3GenerationResult result = runtime.generate(List.of(1, 2),
				GpuLlama3ChatOptions.builder().maxTokens(20).build(), null);

		assertThat(result.completionTokens()).containsExactly(12);
		assertThat(result.rawText()).isEqualTo("12");
		assertThat(result.finishReason()).isEqualTo(GpuLlama3GenerationResult.FINISH_REASON_STOP);
		assertThat(model.createStateCalls).isEqualTo(1);
		assertThat(model.lastInvocation.gpu()).isTrue();
		assertThat(model.lastInvocation.state()).isSameAs(gpuState);
		assertThat(tornadoVMPlan.executionPlanCalls).isEqualTo(1);
		assertThat(model.lastInvocation.tornadoVMPlan()).isNull();
	}

	@Test
	// Verifies reused GPU state resets latestToken before each generation.
	void generateResetsGpuLatestTokenBeforeReusingState() {
		RecordingModel model = new RecordingModel();
		State gpuState = model.createNewState();
		gpuState.latestToken = 7;
		GpuLlama3Runtime runtime = new GpuLlama3Runtime(model, true, 64, gpuState, new RecordingPlan());

		gpuState.latestToken = 42;
		runtime.generate(List.of(1, 2), GpuLlama3ChatOptions.builder().maxTokens(20).build(), null);
		assertThat(model.lastInvocation.latestToken()).isEqualTo(7);

		gpuState.latestToken = 43;
		runtime.generate(List.of(3, 4), GpuLlama3ChatOptions.builder().maxTokens(20).build(), null);
		assertThat(model.lastInvocation.latestToken()).isEqualTo(7);
		assertThat(model.createStateCalls).isEqualTo(1);
	}

	@Test
	// Verifies missing stop token marks generation as length-limited.
	void generateMarksLengthWhenStopTokenIsMissing() {
		RecordingModel model = new RecordingModel();
		model.cpuGeneratedTokens = List.of(10, 11);
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);

		GpuLlama3GenerationResult result = runtime.generate(List.of(1, 2),
				GpuLlama3ChatOptions.builder().maxTokens(20).build(), null);

		assertThat(result.completionTokens()).containsExactly(10, 11);
		assertThat(result.rawText()).isEqualTo("10|11");
		assertThat(result.finishReason()).isEqualTo(GpuLlama3GenerationResult.FINISH_REASON_LENGTH);
	}

	@Test
	// Verifies request maxTokens is clamped to the loaded context length.
	void generateClampsMaxTokensToContextLength() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 32);

		runtime.generate(List.of(1, 2), GpuLlama3ChatOptions.builder().maxTokens(100).build(), null);

		assertThat(model.lastInvocation.maxTokens()).isEqualTo(32);
	}

	@Test
	// Verifies maxTokens equal to context length is accepted unchanged.
	void generateAcceptsMaxTokensEqualToContextLength() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 32);

		runtime.generate(List.of(1, 2), GpuLlama3ChatOptions.builder().maxTokens(32).build(), null);

		assertThat(model.lastInvocation.maxTokens()).isEqualTo(32);
	}

	@Test
	// Verifies the runtime default maxTokens is used when the request omits it.
	void generateUsesDefaultMaxTokensWhenRequestOptionIsMissing() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 1_000);

		runtime.generate(List.of(1, 2), GpuLlama3ChatOptions.builder().build(), null);

		assertThat(model.lastInvocation.maxTokens()).isEqualTo(GpuLlama3Runtime.DEFAULT_MAX_TOKENS);
	}

	@Test
	// Verifies non-positive maxTokens values are rejected.
	void generateRejectsNonPositiveMaxTokens() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(0).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxTokens must be positive");
	}

	@Test
	// Verifies prompts that fill the token budget are rejected before generation.
	void generateRejectsPromptWhenNoRoomWithinMaxTokens() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(2).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt length 2 leaves no room within maxTokens 2");

		assertThat(model.createStateCalls).isZero();
		assertThat(model.lastInvocation).isNull();
	}

	@Test
	// Verifies clamped context length must still leave room for completion tokens.
	void generateRejectsPromptWhenClampedContextLeavesNoRoom() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 2);
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(100).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt length 2 leaves no room within maxTokens 2");

		assertThat(model.createStateCalls).isZero();
		assertThat(model.lastInvocation).isNull();
	}

	@Test
	// Verifies invalid GPU requests are rejected before calling the engine or plan.
	void generateRejectsGpuPromptBeforeCallingEngineWhenNoRoomWithinMaxTokens() {
		RecordingModel model = new RecordingModel();
		RecordingPlan tornadoVMPlan = new RecordingPlan();
		GpuLlama3Runtime runtime = gpuRuntime(model, 64, tornadoVMPlan);
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(2).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2, 3), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt length 3 leaves no room within maxTokens 2");

		assertThat(model.lastInvocation).isNull();
		assertThat(tornadoVMPlan.executionPlanCalls).isZero();
		assertThat(tornadoVMPlan.freeCalls).isZero();
	}

	@Test
	// Verifies invalid temperature values are rejected before sampler creation.
	void generateRejectsInvalidTemperature() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);

		assertGenerationRejected(runtime, GpuLlama3ChatOptions.builder().maxTokens(20).temperature(-0.1).build(),
				"temperature must be greater than or equal to 0");
		assertGenerationRejected(runtime, GpuLlama3ChatOptions.builder().maxTokens(20).temperature(Double.NaN).build(),
				"temperature must be finite");
	}

	@Test
	// Verifies invalid top-p values are rejected before sampler creation.
	void generateRejectsInvalidTopP() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);

		assertGenerationRejected(runtime, GpuLlama3ChatOptions.builder().maxTokens(20).topP(0.0).build(),
				"topP must be greater than 0 and less than or equal to 1");
		assertGenerationRejected(runtime, GpuLlama3ChatOptions.builder().maxTokens(20).topP(1.1).build(),
				"topP must be greater than 0 and less than or equal to 1");
		assertGenerationRejected(runtime, GpuLlama3ChatOptions.builder().maxTokens(20).topP(Double.NaN).build(),
				"topP must be finite");
	}

	@Test
	// Verifies a null token list from the model is rejected.
	void generateRejectsNullModelGenerationResult() {
		RecordingModel model = new RecordingModel();
		model.cpuGeneratedTokens = null;
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(20).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("model generation returned null");
	}

	@Test
	// Verifies GPU runtime construction requires a TornadoVM plan.
	void constructorRejectsGpuRuntimeWithoutPlan() {
		RecordingModel model = new RecordingModel();
		State gpuState = model.createNewState();

		assertThatThrownBy(() -> new GpuLlama3Runtime(model, true, 64, gpuState, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("GPU runtime requires a TornadoVM plan");
	}

	@Test
	// Verifies close is idempotent and prevents later generation.
	void closeIsIdempotentAndRejectsFurtherGeneration() {
		RecordingModel model = new RecordingModel();
		GpuLlama3Runtime runtime = cpuRuntime(model, 64);

		runtime.close();
		runtime.close();
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder().maxTokens(20).build();

		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("GpuLlama3Runtime is closed");
	}

	@Test
	// Verifies closing a GPU runtime frees the TornadoVM plan only once.
	void closeFreesGpuPlanOnce() {
		RecordingModel model = new RecordingModel();
		RecordingPlan tornadoVMPlan = new RecordingPlan();
		GpuLlama3Runtime runtime = gpuRuntime(model, 64, tornadoVMPlan);

		runtime.close();
		runtime.close();

		assertThat(tornadoVMPlan.freeCalls).isEqualTo(1);
	}

	@Test
	// Verifies GPU plan release failures are logged and do not reopen the runtime.
	void closeDoesNotPropagateGpuPlanFreeFailureAndStillCloses() {
		RecordingModel model = new RecordingModel();
		RecordingPlan tornadoVMPlan = new RecordingPlan();
		tornadoVMPlan.freeFailure = new RuntimeException("boom");
		GpuLlama3Runtime runtime = gpuRuntime(model, 64, tornadoVMPlan);

		runtime.close();

		assertThat(tornadoVMPlan.freeCalls).isEqualTo(1);
		assertThatThrownBy(
				() -> runtime.generate(List.of(1, 2), GpuLlama3ChatOptions.builder().maxTokens(20).build(), null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("GpuLlama3Runtime is closed");
	}

	private static void assertGenerationRejected(GpuLlama3Runtime runtime, GpuLlama3ChatOptions options,
			String message) {
		assertThatThrownBy(() -> runtime.generate(List.of(1, 2), options, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(message);
	}

	private static GpuLlama3Runtime cpuRuntime(RecordingModel model, int contextLength) {
		return GpuLlama3Runtime.load(loadOptions(false, contextLength),
				(modelPath, loadedContextLength, loadWeights, useTornadovm) -> model, (state, loadedModel) -> {
					throw new AssertionError("CPU runtime must not initialize TornadoVM");
				});
	}

	private static GpuLlama3Runtime gpuRuntime(RecordingModel model, int contextLength) {
		return gpuRuntime(model, contextLength, new RecordingPlan());
	}

	private static GpuLlama3Runtime gpuRuntime(RecordingModel model, int contextLength,
			GpuLlama3Runtime.TornadoVmPlan tornadoVMPlan) {
		return new GpuLlama3Runtime(model, true, contextLength, model.createNewState(), tornadoVMPlan);
	}

	private static GpuLlama3Runtime.ModelLoaderFunction failingModelLoader() {
		return (modelPath, contextLength, loadWeights, useTornadovm) -> {
			throw new IOException("cannot read");
		};
	}

	private static GpuLlama3Runtime.ModelLoaderFunction unreachableModelLoader() {
		return (modelPath, contextLength, loadWeights, useTornadovm) -> {
			throw new AssertionError("loader must not be called");
		};
	}

	private static GpuLlama3Runtime.TornadoVmPlanInitializer noOpPlanInitializer() {
		return (state, loadedModel) -> null;
	}

	private static GpuLlama3ChatOptions loadOptions(boolean onGpu, int contextLength) {
		return GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.contextLength(contextLength)
			.onGpu(onGpu)
			.build();
	}

	private record LoaderInvocation(Path modelPath, int contextLength, boolean loadWeights, boolean useTornadovm) {
	}

	private record GenerationInvocation(boolean gpu, State state, int startPosition, List<Integer> promptTokens,
			Set<Integer> stopTokens, int maxTokens, Sampler sampler, boolean echo, boolean hasTokenConsumer,
			int latestToken, @Nullable TornadoVMMasterPlan tornadoVMPlan) {
	}

	private static final class RecordingPlan implements GpuLlama3Runtime.TornadoVmPlan {

		private int executionPlanCalls;

		private int freeCalls;

		@Nullable private RuntimeException freeFailure;

		@Override
		public @Nullable TornadoVMMasterPlan executionPlan() {
			this.executionPlanCalls++;
			return null;
		}

		@Override
		public void free() {
			this.freeCalls++;
			if (this.freeFailure != null) {
				throw this.freeFailure;
			}
		}

	}

	private static final class RecordingModel implements Model {

		private final Configuration configuration = new FakeConfiguration();

		private final Tokenizer tokenizer = new FakeTokenizer();

		private final ChatFormat chatFormat = new FakeChatFormat();

		private final List<State> createdStates = new ArrayList<>();

		@Nullable private List<Integer> cpuGeneratedTokens = List.of(10, 11, 99);

		@Nullable private List<Integer> gpuGeneratedTokens = List.of(12, 99);

		private int createStateCalls;

		private GenerationInvocation lastInvocation;

		@Override
		public Configuration configuration() {
			return this.configuration;
		}

		@Override
		public Tokenizer tokenizer() {
			return this.tokenizer;
		}

		@Override
		public Weights weights() {
			throw new UnsupportedOperationException("weights");
		}

		@Override
		public ChatFormat chatFormat() {
			return this.chatFormat;
		}

		@Override
		public TornadoVMMasterPlan tornadoVMPlan() {
			return null;
		}

		@Override
		public void setTornadoVMPlan(TornadoVMMasterPlan plan) {
			throw new UnsupportedOperationException("setTornadoVMPlan");
		}

		@Override
		public ModelType getModelType() {
			return ModelType.LLAMA_3;
		}

		@Override
		public State createNewState() {
			this.createStateCalls++;
			State state = new TestState(this.configuration);
			state.latestToken = this.chatFormat.getBeginOfText();
			this.createdStates.add(state);
			return state;
		}

		@Override
		public State createNewState(int batchsize) {
			return createNewState();
		}

		@Override
		public void forward(State state, int token, int position) {
			throw new UnsupportedOperationException("forward");
		}

		@Override
		public List<Integer> generateTokens(State state, int startPosition, List<Integer> promptTokens,
				Set<Integer> stopTokens, int maxTokens, Sampler sampler, boolean echo, IntConsumer onTokenGenerated) {
			this.lastInvocation = new GenerationInvocation(false, state, startPosition, List.copyOf(promptTokens),
					Set.copyOf(stopTokens), maxTokens, sampler, echo, onTokenGenerated != null, state.latestToken,
					null);
			return generatedTokens(this.cpuGeneratedTokens, onTokenGenerated);
		}

		@Override
		public List<Integer> generateTokensGPU(State state, int startPosition, List<Integer> promptTokens,
				Set<Integer> stopTokens, int maxTokens, Sampler sampler, boolean echo, IntConsumer onTokenGenerated,
				TornadoVMMasterPlan tornadoVMPlan) {
			this.lastInvocation = new GenerationInvocation(true, state, startPosition, List.copyOf(promptTokens),
					Set.copyOf(stopTokens), maxTokens, sampler, echo, onTokenGenerated != null, state.latestToken,
					tornadoVMPlan);
			return generatedTokens(this.gpuGeneratedTokens, onTokenGenerated);
		}

		@Nullable private static List<Integer> generatedTokens(@Nullable List<Integer> tokens, @Nullable IntConsumer consumer) {
			if (tokens == null) {
				return null;
			}
			if (consumer != null) {
				tokens.forEach(consumer::accept);
			}
			return new ArrayList<>(tokens);
		}

	}

	private static final class FakeConfiguration implements Configuration {

		@Override
		public String quantization() {
			return "F16";
		}

		@Override
		public int dim() {
			return 0;
		}

		@Override
		public int hiddenDim() {
			return 0;
		}

		@Override
		public int numberOfLayers() {
			return 0;
		}

		@Override
		public int numberOfHeads() {
			return 0;
		}

		@Override
		public int numberOfKeyValueHeads() {
			return 0;
		}

		@Override
		public int numberOfHeadsKey() {
			return 0;
		}

		@Override
		public int vocabularySize() {
			return 128;
		}

		@Override
		public int contextLength() {
			return 64;
		}

		@Override
		public int contextLengthModel() {
			return 64;
		}

		@Override
		public float rmsNormEps() {
			return 0;
		}

		@Override
		public float ropeTheta() {
			return 0;
		}

		@Override
		public int headSize() {
			return 0;
		}

		@Override
		public int kvDim() {
			return 0;
		}

		@Override
		public int kvMul() {
			return 0;
		}

	}

	private static final class FakeTokenizer implements Tokenizer {

		@Override
		public String regexPattern() {
			return "";
		}

		@Override
		public Map<String, Integer> getSpecialTokens() {
			return Map.of();
		}

		@Override
		public boolean isSpecialToken(int token) {
			return false;
		}

		@Override
		public boolean shouldDisplayToken(int token) {
			return true;
		}

		@Override
		public List<Integer> encode(String text, Set<String> allowedSpecial) {
			throw new UnsupportedOperationException("encode");
		}

		@Override
		public List<Integer> encodeAsList(String text) {
			throw new UnsupportedOperationException("encodeAsList");
		}

		@Override
		public String decode(List<Integer> tokens) {
			return String.join("|", tokens.stream().map(String::valueOf).toList());
		}

	}

	private static final class FakeChatFormat implements ChatFormat {

		@Override
		public List<Integer> encodeHeader(ChatFormat.Message message) {
			throw new UnsupportedOperationException("encodeHeader");
		}

		@Override
		public List<Integer> encodeMessage(ChatFormat.Message message) {
			throw new UnsupportedOperationException("encodeMessage");
		}

		@Override
		public int getBeginOfText() {
			return 1;
		}

		@Override
		public Set<Integer> getStopTokens() {
			return Set.of(99);
		}

	}

	private static final class TestState extends State {

		private TestState(Configuration configuration) {
			super(configuration, 1);
		}

		@Override
		protected StateFields createStateFields(Configuration config) {
			return new EmptyStateFields();
		}

		private static final class EmptyStateFields extends StateFields {

		}

	}

}
