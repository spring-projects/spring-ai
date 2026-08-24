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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3ChatModelTests {

	@Test
	// Verifies call() encodes the prompt, generates tokens, and builds full response
	// metadata.
	void callEncodesPromptGeneratesAndBuildsChatResponse() {
		RecordingModel model = new RecordingModel();
		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			ChatResponse response = chatModel.call(new Prompt(new UserMessage("hello")));

			Generation generation = Objects.requireNonNull(response.getResult());
			assertThat(generation.getOutput().getText()).isEqualTo("Hello");
			assertThat(generation.getOutput().getMetadata()).containsEntry(GpuLlama3ChatModel.THINKING_METADATA_KEY,
					"reason");
			assertThat(generation.getMetadata().getFinishReason())
				.isEqualTo(GpuLlama3GenerationResult.FINISH_REASON_STOP);
			assertThat((Long) generation.getMetadata().get(GpuLlama3ChatModel.DURATION_NANOS_METADATA_KEY))
				.isNotNegative();
			assertThat(response.getMetadata().getModel()).isEqualTo("llama-test");
			assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
			assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(2);
			assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(6);
			String provider = response.getMetadata().get("provider");
			Boolean onGpu = response.getMetadata().get(GpuLlama3ChatModel.ON_GPU_METADATA_KEY);
			assertThat(provider).isEqualTo(GpuLlama3ChatModel.PROVIDER);
			assertThat(onGpu).isFalse();
			assertThat(model.lastInvocation.promptTokens()).containsExactly(1, 20, 5, 30);
			assertThat(model.lastInvocation.stopTokens()).containsExactly(99);
			assertThat(model.lastInvocation.maxTokens()).isEqualTo(32);
			assertThat(model.lastInvocation.echo()).isFalse();
			assertThat(model.lastInvocation.hasTokenConsumer()).isFalse();
		}
	}

	@Test
	// Verifies request runtime options override default options before generation.
	void callMergesRuntimeOptionsBeforeGeneration() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatOptions runtimeOptions = GpuLlama3ChatOptions.builder()
			.maxTokens(8)
			.temperature(0.8)
			.topP(0.7)
			.seed(99L)
			.build();

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			chatModel.call(new Prompt(new UserMessage("hello"), runtimeOptions));
		}

		assertThat(model.lastInvocation.maxTokens()).isEqualTo(8);
	}

	@Test
	// Verifies per-request load-time option changes are rejected.
	void callRejectsPerRequestLoadTimeOptionChanges() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatOptions runtimeOptions = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/other-llama.gguf"))
			.build();
		Prompt prompt = new Prompt(new UserMessage("hello"), runtimeOptions);

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			assertThatThrownBy(() -> chatModel.call(prompt)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("modelPath");
		}
	}

	@Test
	// Verifies empty prompts are rejected before encoding or generation.
	void callRejectsEmptyPrompt() {
		RecordingModel model = new RecordingModel();
		Prompt prompt = new Prompt(List.of());

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			assertThatThrownBy(() -> chatModel.call(prompt)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("prompt must contain at least one message");
		}
	}

	@Test
	// Verifies stream() emits displayable chunks plus a final metadata chunk.
	void streamEmitsDisplayableTokenChunksAndFinalMetadataChunk() {
		RecordingModel model = new RecordingModel();
		model.cpuGeneratedTokens = List.of(100, 102, 101, 99);
		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			List<ChatResponse> responses = collectStream(chatModel);

			assertThat(responses).hasSize(3);
			assertThat(responses.get(0).getResult().getOutput().getText()).isEqualTo("Hello ");
			assertThat(responses.get(1).getResult().getOutput().getText()).isEqualTo("<think>reason</think>");
			Generation finalGeneration = Objects.requireNonNull(responses.get(2).getResult());
			assertThat(finalGeneration.getOutput().getText()).isEmpty();
			assertThat(finalGeneration.getOutput().getMetadata())
				.containsEntry(GpuLlama3ChatModel.THINKING_METADATA_KEY, "reason");
			assertThat(finalGeneration.getMetadata().getFinishReason())
				.isEqualTo(GpuLlama3GenerationResult.FINISH_REASON_STOP);
			assertThat(responses.get(2).getMetadata().getUsage().getCompletionTokens()).isEqualTo(3);
			assertThat(model.lastInvocation.hasTokenConsumer()).isTrue();
		}
	}

	@Test
	// Verifies blocking stream generation runs on Reactor boundedElastic.
	void streamSubscribesOnBoundedElastic() {
		RecordingModel model = new RecordingModel();
		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			collectStream(chatModel);

			assertThat(model.lastGenerationThreadName).contains("boundedElastic");
		}
	}

	@Test
	// Verifies streaming decodes from accumulated tokens to handle split characters.
	void streamDecodesTextFromAccumulatedTokens() {
		RecordingModel model = new RecordingModel();
		model.cpuGeneratedTokens = List.of(200, 201, 100, 99);

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			List<String> chunks = collectStream(chatModel).stream().map(GpuLlama3ChatModelTests::outputText).toList();

			assertThat(chunks).containsExactly("€", "Hello ", "");
		}
	}

	@Test
	// Verifies stream cancellation stops additional chunks from being emitted.
	void streamStopsEmittingAfterCancellation() {
		RecordingModel model = new RecordingModel();
		model.cpuGeneratedTokens = List.of(100, 101, 99);

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			List<ChatResponse> responses = collectFirstStreamChunk(chatModel);

			assertThat(responses).hasSize(1);
			assertThat(responses.get(0).getResult().getOutput().getText()).isEqualTo("Hello ");
		}
	}

	@Test
	// Verifies stream generation errors are propagated to the subscriber.
	void streamPropagatesGenerationErrors() {
		RecordingModel model = new RecordingModel();
		model.generationFailure = new IllegalStateException("boom");

		try (GpuLlama3ChatModel chatModel = chatModel(model)) {
			assertThatThrownBy(() -> collectStream(chatModel)).isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");
		}
	}

	@Test
	// Verifies response metadata reports GPU mode when the runtime is on GPU.
	void callPropagatesOnGpuMetadata() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatOptions options = defaultOptions().mutate().onGpu(true).build();
		try (GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(options, gpuRuntime(model),
				new GpuLlama3PromptEncoder(), new GpuLlama3ResponseParser())) {
			ChatResponse response = chatModel.call(new Prompt(new UserMessage("hello")));

			Boolean onGpu = response.getMetadata().get(GpuLlama3ChatModel.ON_GPU_METADATA_KEY);
			assertThat(onGpu).isTrue();
		}
	}

	@Test
	// Verifies model metadata falls back from configured name to model path and provider.
	void resolveModelNameFallsBackToModelPathAndProvider() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatOptions pathOnlyOptions = defaultOptions().mutate().model(null).build();
		try (GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(pathOnlyOptions, cpuRuntime(model),
				new GpuLlama3PromptEncoder(), new GpuLlama3ResponseParser())) {
			ChatResponse response = chatModel.call(new Prompt(new UserMessage("hello")));

			assertThat(response.getMetadata().getModel()).isEqualTo("llama.gguf");
		}

		GpuLlama3ChatOptions providerOnlyOptions = defaultOptions().mutate().model(null).modelPath(null).build();
		try (GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(providerOnlyOptions, cpuRuntime(model),
				new GpuLlama3PromptEncoder(), new GpuLlama3ResponseParser())) {
			ChatResponse response = chatModel.call(new Prompt(new UserMessage("hello")));

			assertThat(response.getMetadata().getModel()).isEqualTo(GpuLlama3ChatModel.PROVIDER);
		}
	}

	@Test
	// Verifies getOptions() exposes the configured defaults.
	void getOptionsReturnsConfiguredDefaults() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatOptions options = defaultOptions();
		GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(options, cpuRuntime(model), new GpuLlama3PromptEncoder(),
				new GpuLlama3ResponseParser());

		assertThat(chatModel.getOptions()).isSameAs(options);
	}

	@Test
	// Verifies close() closes the runtime and prevents later calls.
	void closeClosesRuntime() {
		RecordingModel model = new RecordingModel();
		GpuLlama3ChatModel chatModel = chatModel(model);
		Prompt prompt = new Prompt(new UserMessage("hello"));

		chatModel.close();

		assertThatThrownBy(() -> chatModel.call(prompt)).isInstanceOf(IllegalStateException.class)
			.hasMessage("GpuLlama3Runtime is closed");
	}

	@Test
	// Verifies close() waits for an in-flight generation to release the inference lock.
	void closeWaitsForInFlightGeneration() throws Exception {
		RecordingModel model = new RecordingModel();
		CountDownLatch generationStarted = new CountDownLatch(1);
		CountDownLatch allowGenerationToFinish = new CountDownLatch(1);
		model.blockGenerationWith(generationStarted, allowGenerationToFinish);
		GpuLlama3ChatModel chatModel = chatModel(model);
		Prompt prompt = new Prompt(new UserMessage("hello"));
		ExecutorService generateExecutor = Executors.newSingleThreadExecutor();
		ExecutorService closeExecutor = Executors.newSingleThreadExecutor();

		try {
			Future<ChatResponse> generation = generateExecutor.submit(() -> chatModel.call(prompt));
			assertThat(generationStarted.await(2, TimeUnit.SECONDS)).isTrue();

			Future<?> close = closeExecutor.submit(chatModel::close);
			assertThatThrownBy(() -> close.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

			allowGenerationToFinish.countDown();
			assertThat(generation.get(2, TimeUnit.SECONDS).getResult().getOutput().getText()).isEqualTo("Hello");
			close.get(2, TimeUnit.SECONDS);
		}
		finally {
			allowGenerationToFinish.countDown();
			generateExecutor.shutdownNow();
			closeExecutor.shutdownNow();
		}
	}

	private static GpuLlama3ChatModel chatModel(RecordingModel model) {
		return new GpuLlama3ChatModel(defaultOptions(), cpuRuntime(model), new GpuLlama3PromptEncoder(),
				new GpuLlama3ResponseParser());
	}

	private static List<ChatResponse> collectStream(GpuLlama3ChatModel chatModel) {
		return Objects.requireNonNull(
				chatModel.stream(new Prompt(new UserMessage("hello"))).collectList().block(Duration.ofSeconds(2)));
	}

	private static List<ChatResponse> collectFirstStreamChunk(GpuLlama3ChatModel chatModel) {
		return Objects.requireNonNull(chatModel.stream(new Prompt(new UserMessage("hello")))
			.take(1)
			.collectList()
			.block(Duration.ofSeconds(2)));
	}

	private static String outputText(ChatResponse response) {
		return response.getResult().getOutput().getText();
	}

	private static GpuLlama3Runtime cpuRuntime(RecordingModel model) {
		return new GpuLlama3Runtime(model, false, 64, null, null);
	}

	private static GpuLlama3Runtime gpuRuntime(RecordingModel model) {
		return new GpuLlama3Runtime(model, true, 64, model.createNewState(), new NoOpPlan());
	}

	private static GpuLlama3ChatOptions defaultOptions() {
		return GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama-test")
			.onGpu(false)
			.contextLength(64)
			.maxTokens(32)
			.temperature(0.1)
			.topP(1.0)
			.seed(7L)
			.build();
	}

	private record GenerationInvocation(List<Integer> promptTokens, Set<Integer> stopTokens, int maxTokens,
			boolean echo, boolean hasTokenConsumer) {
	}

	private static final class NoOpPlan implements GpuLlama3Runtime.TornadoVmPlan {

		@Override
		public @Nullable TornadoVMMasterPlan executionPlan() {
			return null;
		}

		@Override
		public void free() {
		}

	}

	private static final class RecordingModel implements Model {

		private final Configuration configuration = new FakeConfiguration();

		private final Tokenizer tokenizer = new FakeTokenizer();

		private final ChatFormat chatFormat = new FakeChatFormat();

		private List<Integer> cpuGeneratedTokens = List.of(100, 101, 99);

		@Nullable private RuntimeException generationFailure;

		@Nullable private String lastGenerationThreadName;

		@Nullable private CountDownLatch generationStarted;

		@Nullable private CountDownLatch allowGenerationToFinish;

		private GenerationInvocation lastInvocation;

		void blockGenerationWith(CountDownLatch generationStarted, CountDownLatch allowGenerationToFinish) {
			this.generationStarted = generationStarted;
			this.allowGenerationToFinish = allowGenerationToFinish;
		}

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
			return new TestState(this.configuration);
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
			this.lastInvocation = new GenerationInvocation(List.copyOf(promptTokens), Set.copyOf(stopTokens), maxTokens,
					echo, onTokenGenerated != null);
			this.lastGenerationThreadName = Thread.currentThread().getName();
			waitIfBlocked();
			if (this.generationFailure != null) {
				throw this.generationFailure;
			}
			if (onTokenGenerated != null) {
				this.cpuGeneratedTokens.forEach(onTokenGenerated::accept);
			}
			return new ArrayList<>(this.cpuGeneratedTokens);
		}

		@Override
		public List<Integer> generateTokensGPU(State state, int startPosition, List<Integer> promptTokens,
				Set<Integer> stopTokens, int maxTokens, Sampler sampler, boolean echo, IntConsumer onTokenGenerated,
				TornadoVMMasterPlan tornadoVMPlan) {
			return generateTokens(state, startPosition, promptTokens, stopTokens, maxTokens, sampler, echo,
					onTokenGenerated);
		}

		private void waitIfBlocked() {
			if (this.generationStarted == null || this.allowGenerationToFinish == null) {
				return;
			}
			this.generationStarted.countDown();
			try {
				if (!this.allowGenerationToFinish.await(2, TimeUnit.SECONDS)) {
					throw new AssertionError("Timed out waiting to finish generation");
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting to finish generation", ex);
			}
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
			return 256;
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

		private final Map<Integer, String> tokenText = Map.of(100, "Hello ", 101, "<think>reason</think>", 102, "");

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
			return token != 102;
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
			StringBuilder decoded = new StringBuilder();
			for (int i = 0; i < tokens.size(); i++) {
				Integer token = tokens.get(i);
				if (token == 200) {
					if (i + 1 < tokens.size() && tokens.get(i + 1) == 201) {
						decoded.append("€");
						i++;
					}
					else {
						decoded.append('\uFFFD');
					}
					continue;
				}
				decoded.append(this.tokenText.getOrDefault(token, String.valueOf(token)));
			}
			return decoded.toString();
		}

	}

	private static final class FakeChatFormat implements ChatFormat {

		@Override
		public List<Integer> encodeHeader(ChatFormat.Message message) {
			return List.of(roleToken(message.role()));
		}

		@Override
		public List<Integer> encodeMessage(ChatFormat.Message message) {
			return List.of(roleToken(message.role()), message.content().length());
		}

		@Override
		public int getBeginOfText() {
			return 1;
		}

		@Override
		public Set<Integer> getStopTokens() {
			return Set.of(99);
		}

		private static int roleToken(ChatFormat.Role role) {
			if (ChatFormat.Role.SYSTEM.equals(role)) {
				return 10;
			}
			if (ChatFormat.Role.USER.equals(role)) {
				return 20;
			}
			if (ChatFormat.Role.ASSISTANT.equals(role)) {
				return 30;
			}
			throw new IllegalArgumentException("Unsupported role: " + role);
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
