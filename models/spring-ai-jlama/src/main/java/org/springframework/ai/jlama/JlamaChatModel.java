/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.jlama;

import java.io.File;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.jlama.api.JlamaChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} implementation for {@literal Jlama}. Jlama allows developers to run
 * large language models directly within the JVM.
 *
 * @author chabinhwang
 */
public class JlamaChatModel implements ChatModel, DisposableBean {

	private static final float DEFAULT_TEMPERATURE = 0.7f;

	private static final int DEFAULT_MAX_TOKENS = 256;

	private static final String DEFAULT_WORKING_DIRECTORY = new File(System.getProperty("java.io.tmpdir"),
			"spring-ai-jlama")
		.getAbsolutePath();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final JlamaChatOptions defaultOptions;

	private final AbstractModel model;

	public JlamaChatModel(String modelPath, JlamaChatOptions defaultOptions) {
		this(modelPath, null, defaultOptions);
	}

	public JlamaChatModel(String modelPath, String workingDirectory, JlamaChatOptions defaultOptions) {
		Assert.hasText(modelPath, "Model path must not be empty");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		this.defaultOptions = defaultOptions;
		ensureVectorApiAvailable();
		File resolvedModel = resolveModel(modelPath, workingDirectory);
		this.model = ModelSupport.loadModel(resolvedModel, DType.F32, DType.I8);
	}

	JlamaChatModel(JlamaChatOptions defaultOptions, AbstractModel model) {
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(model, "model must not be null");
		this.defaultOptions = defaultOptions;
		this.model = model;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		String formattedPrompt = formatPrompt(prompt);

		JlamaChatOptions options = ModelOptionsUtils.merge(prompt.getOptions(), this.defaultOptions,
				JlamaChatOptions.class);
		validateSupportedOptions(options);

		float temperature = options.getTemperature() != null ? options.getTemperature().floatValue()
				: DEFAULT_TEMPERATURE;
		int maxTokens = options.getMaxTokens() != null ? options.getMaxTokens().intValue() : DEFAULT_MAX_TOKENS;

		PromptContext promptContext = PromptContext.of(formattedPrompt);
		StringBuilder output = new StringBuilder();

		this.model.generate(UUID.randomUUID(), promptContext, temperature, maxTokens,
				(token, score) -> output.append(token));

		Generation generation = new Generation(new AssistantMessage(output.toString()));
		return new ChatResponse(Collections.singletonList(generation));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		String formattedPrompt = formatPrompt(prompt);

		JlamaChatOptions options = ModelOptionsUtils.merge(prompt.getOptions(), this.defaultOptions,
				JlamaChatOptions.class);
		validateSupportedOptions(options);

		float temperature = options.getTemperature() != null ? options.getTemperature().floatValue()
				: DEFAULT_TEMPERATURE;
		int maxTokens = options.getMaxTokens() != null ? options.getMaxTokens().intValue() : DEFAULT_MAX_TOKENS;

		PromptContext promptContext = PromptContext.of(formattedPrompt);

		return Flux.<ChatResponse>create(fluxSink -> generateStreaming(promptContext, temperature, maxTokens, fluxSink))
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public void destroy() throws Exception {
		this.model.close();
	}

	private void generateStreaming(PromptContext promptContext, float temperature, int maxTokens,
			FluxSink<ChatResponse> sink) {
		try {
			this.model.generate(UUID.randomUUID(), promptContext, temperature, maxTokens, (token, score) -> {
				if (sink.isCancelled()) {
					throw new CancellationException("Jlama stream cancelled");
				}
				emitChunk(sink, token);
			});
			if (!sink.isCancelled()) {
				sink.complete();
			}
		}
		catch (CancellationException e) {
			sink.complete();
		}
		catch (Exception e) {
			if (!sink.isCancelled()) {
				sink.error(e);
			}
		}
	}

	private static void emitChunk(FluxSink<ChatResponse> sink, String token) {
		if (sink.isCancelled()) {
			return;
		}
		Generation generation = new Generation(new AssistantMessage(token));
		sink.next(new ChatResponse(Collections.singletonList(generation)));
	}

	private void ensureVectorApiAvailable() {
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException(
					"Vector API not available. Please run with --add-modules jdk.incubator.vector");
		}
	}

	private File resolveModel(String modelPath, String workingDirectory) {
		File localModel = new File(modelPath);
		if (localModel.exists()) {
			return localModel;
		}

		if (StringUtils.hasText(workingDirectory)) {
			File candidate = new File(workingDirectory, modelPath);
			if (candidate.exists()) {
				return candidate;
			}
		}

		if (modelPath.startsWith("http://") || modelPath.startsWith("https://")) {
			throw new IllegalArgumentException(
					"HTTP URLs not yet supported, please use local file path or HuggingFace 'owner/repo'");
		}

		try {
			Downloader downloader = createDownloader(modelPath, workingDirectory);
			return downloader.huggingFaceModel();
		}
		catch (Exception ex) {
			logger.warn("Failed to download from HuggingFace model '{}'", modelPath, ex);
			throw new IllegalArgumentException("Model not found: " + modelPath, ex);
		}
	}

	static Downloader createDownloader(String modelPath, String workingDirectory) {
		String resolvedWorkingDirectory = StringUtils.hasText(workingDirectory) ? workingDirectory
				: defaultWorkingDirectory();
		return new Downloader(resolvedWorkingDirectory, modelPath);
	}

	static String defaultWorkingDirectory() {
		return DEFAULT_WORKING_DIRECTORY;
	}

	static void validateSupportedOptions(JlamaChatOptions options) {
		Assert.notNull(options, "options must not be null");
		if (options.getTopK() != null || options.getTopP() != null || options.getFrequencyPenalty() != null
				|| options.getPresencePenalty() != null || options.getSeed() != null
				|| (options.getStopSequences() != null && !options.getStopSequences().isEmpty())) {
			throw new IllegalArgumentException(
					"Jlama supports only 'temperature' and 'maxTokens' chat options at this time");
		}
	}

	private String formatPrompt(Prompt prompt) {
		String messages = prompt.getInstructions()
			.stream()
			.map(JlamaChatModel::messageLine)
			.collect(Collectors.joining("\n"));
		return messages + "\nAssistant:";
	}

	private static String messageLine(Message message) {
		return String.valueOf(message.getMessageType()) + ": " + message.getText();
	}

}
