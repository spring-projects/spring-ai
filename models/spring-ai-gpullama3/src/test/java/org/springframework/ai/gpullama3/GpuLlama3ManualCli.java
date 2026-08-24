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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Manual command-line runner for local GPULlama3 smoke testing.
 * <p>
 * This class is in test sources so it is available to developers but is not packaged in
 * the library jar.
 */
public final class GpuLlama3ManualCli {

	private static final String DEFAULT_PROMPT = "Briefly describe the University of Manchester.";

	private static final int DEFAULT_CONTEXT_LENGTH = 1024;

	private static final int DEFAULT_MAX_TOKENS = 512;

	private GpuLlama3ManualCli() {
	}

	@SuppressWarnings("RedundantModifier")
	public static void main(String[] args) {
		CliOptions cliOptions = CliOptions.parse(args);

		GpuLlama3ChatOptions chatOptions = GpuLlama3ChatOptions.builder()
			.modelPath(cliOptions.modelPath())
			.model("llama-3.2-1b-instruct")
			.onGpu(cliOptions.onGpu())
			.contextLength(cliOptions.contextLength())
			.maxTokens(cliOptions.maxTokens())
			.temperature(0.0)
			.topP(1.0)
			.seed(7L)
			.build();

		long startNanos = System.nanoTime();
		try (GpuLlama3ChatModel chatModel = new GpuLlama3ChatModel(chatOptions)) {
			printRequest(cliOptions);
			if (cliOptions.stream()) {
				runStream(chatModel, cliOptions, startNanos);
			}
			else {
				runCall(chatModel, cliOptions, startNanos);
			}
		}
	}

	private static void printRequest(CliOptions cliOptions) {
		System.out.println("GPULlama3 CLI model: " + cliOptions.modelPath());
		System.out.println("GPULlama3 CLI contextLength: " + cliOptions.contextLength());
		System.out.println("GPULlama3 CLI maxTokens: " + cliOptions.maxTokens());
		System.out.println("GPULlama3 CLI mode: " + (cliOptions.stream() ? "stream" : "call"));
		System.out.println("GPULlama3 CLI prompt: " + cliOptions.prompt());
	}

	private static void runCall(GpuLlama3ChatModel chatModel, CliOptions cliOptions, long startNanos) {
		ChatResponse response = chatModel.call(new Prompt(new UserMessage(cliOptions.prompt())));
		Generation generation = Objects.requireNonNull(response.getResult(), "response result must not be null");
		String answer = Objects.requireNonNullElse(generation.getOutput().getText(), "");
		if (answer.isBlank()) {
			throw new IllegalStateException("GPULlama3 returned a blank answer");
		}

		System.out.println("GPULlama3 CLI answer:");
		System.out.println(answer);
		printMetadata(response, generation, startNanos);
	}

	private static void runStream(GpuLlama3ChatModel chatModel, CliOptions cliOptions, long startNanos) {
		StringBuilder answer = new StringBuilder();
		AtomicInteger chunkCount = new AtomicInteger();
		AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();

		System.out.println("GPULlama3 CLI stream answer:");
		chatModel.stream(new Prompt(new UserMessage(cliOptions.prompt()))).doOnNext(response -> {
			Generation generation = Objects.requireNonNull(response.getResult(), "response result must not be null");
			String finishReason = generation.getMetadata().getFinishReason();
			if (finishReason != null) {
				finalResponse.set(response);
				return;
			}

			String chunk = Objects.requireNonNullElse(generation.getOutput().getText(), "");
			answer.append(chunk);
			chunkCount.incrementAndGet();
			if (cliOptions.showChunks()) {
				System.out.printf("%nGPULlama3 CLI stream chunk %03d: %s%n", chunkCount.get(), printableChunk(chunk));
			}
			else {
				System.out.print(chunk);
			}
		}).blockLast();
		System.out.println();

		ChatResponse response = Objects.requireNonNull(finalResponse.get(), "stream final response must not be null");
		Generation generation = Objects.requireNonNull(response.getResult(), "response result must not be null");
		if (answer.isEmpty()) {
			throw new IllegalStateException("GPULlama3 returned no stream chunks");
		}

		System.out.println("GPULlama3 CLI stream chunks: " + chunkCount.get());
		System.out.println("GPULlama3 CLI stream assembled answer:");
		System.out.println(answer);
		printMetadata(response, generation, startNanos);
	}

	private static void printMetadata(ChatResponse response, Generation generation, long startNanos) {
		long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
		System.out.println("GPULlama3 CLI finishReason: " + generation.getMetadata().getFinishReason());
		System.out.println("GPULlama3 CLI promptTokens: " + response.getMetadata().getUsage().getPromptTokens());
		System.out
			.println("GPULlama3 CLI completionTokens: " + response.getMetadata().getUsage().getCompletionTokens());
		System.out.println("GPULlama3 CLI totalTokens: " + response.getMetadata().getUsage().getTotalTokens());
		System.out.println("GPULlama3 CLI elapsedMillis: " + elapsedMillis);
	}

	private static String printableChunk(String chunk) {
		return "\"" + chunk.replace("\n", "\\n").replace("\r", "\\r") + "\"";
	}

	private record CliOptions(Path modelPath, String prompt, int contextLength, int maxTokens, boolean onGpu,
			boolean stream, boolean showChunks) {

		private static CliOptions parse(String[] args) {
			Map<String, String> values = parseKeyValueArgs(args);
			Path modelPath = requireModelPath(values.get("model"));
			String prompt = requirePrompt(values.getOrDefault("prompt", DEFAULT_PROMPT));
			int contextLength = parsePositiveInt(values.get("context-length"), DEFAULT_CONTEXT_LENGTH,
					"context-length");
			int maxTokens = parsePositiveInt(values.get("max-tokens"), DEFAULT_MAX_TOKENS, "max-tokens");
			boolean onGpu = Boolean.parseBoolean(values.getOrDefault("gpu", "false"));
			boolean stream = Boolean.parseBoolean(values.getOrDefault("stream", "false"));
			boolean showChunks = Boolean.parseBoolean(values.getOrDefault("show-chunks", "false"));
			return new CliOptions(modelPath, prompt, contextLength, maxTokens, onGpu, stream, showChunks);
		}

		private static Map<String, String> parseKeyValueArgs(String[] args) {
			Map<String, String> values = new LinkedHashMap<>();
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (!arg.startsWith("--")) {
					throw new IllegalArgumentException("Unexpected argument: " + arg);
				}
				String key = arg.substring(2);
				if ("gpu".equals(key) || "stream".equals(key) || "show-chunks".equals(key)) {
					values.put(key, "true");
					continue;
				}
				if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
					throw new IllegalArgumentException("Missing value for --" + key);
				}
				values.put(key, args[++i]);
			}
			return values;
		}

		private static Path requireModelPath(String value) {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("Missing required --model /path/to/llama.gguf");
			}
			Path modelPath = Path.of(value);
			if (!Files.isRegularFile(modelPath)) {
				throw new IllegalArgumentException("Model file does not exist: " + modelPath);
			}
			return modelPath;
		}

		private static String requirePrompt(String value) {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("prompt must not be blank");
			}
			return value;
		}

		private static int parsePositiveInt(String value, int defaultValue, String name) {
			if (value == null || value.isBlank()) {
				return defaultValue;
			}
			int parsed = Integer.parseInt(value);
			if (parsed <= 0) {
				throw new IllegalArgumentException(name + " must be positive");
			}
			return parsed;
		}

	}

}
