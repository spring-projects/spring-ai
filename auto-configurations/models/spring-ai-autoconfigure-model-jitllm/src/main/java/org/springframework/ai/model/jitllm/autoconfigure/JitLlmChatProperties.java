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

package org.springframework.ai.model.jitllm.autoconfigure;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.jitllm.JitLlmChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jitLLM chat model.
 *
 * @author Yuheng Zhou
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
@ConfigurationProperties(JitLlmChatProperties.CONFIG_PREFIX)
public class JitLlmChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.jitllm.chat";

	/**
	 * The GGUF model file to load.
	 */
	private @Nullable Path modelPath;

	/**
	 * The model name reported in response metadata. Defaults to the model file name.
	 */
	private @Nullable String modelName;

	/**
	 * Whether to run on a GPU through TornadoVM. Requires the JVM to be started through
	 * TornadoVM's launcher with -Duse.tornadovm=true.
	 */
	private boolean onGpu = false;

	/**
	 * The context length to allocate, in tokens; 0 uses the model's own.
	 */
	private int contextLength = 0;

	/**
	 * The maximum number of tokens to generate.
	 */
	private @Nullable Integer maxTokens;

	/**
	 * The sampling temperature.
	 */
	private @Nullable Double temperature;

	/**
	 * The nucleus sampling probability mass.
	 */
	private @Nullable Double topP;

	/**
	 * The sampling seed.
	 */
	private @Nullable Long seed;

	/**
	 * Sequences that stop generation when they appear in the output.
	 */
	private @Nullable List<String> stopSequences;

	public @Nullable Path getModelPath() {
		return this.modelPath;
	}

	public void setModelPath(@Nullable Path modelPath) {
		this.modelPath = modelPath;
	}

	public @Nullable String getModelName() {
		return this.modelName;
	}

	public void setModelName(@Nullable String modelName) {
		this.modelName = modelName;
	}

	public boolean isOnGpu() {
		return this.onGpu;
	}

	public void setOnGpu(boolean onGpu) {
		this.onGpu = onGpu;
	}

	public int getContextLength() {
		return this.contextLength;
	}

	public void setContextLength(int contextLength) {
		this.contextLength = contextLength;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Long getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Long seed) {
		this.seed = seed;
	}

	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	/**
	 * The default request options these properties describe.
	 * @return the options
	 */
	public JitLlmChatOptions toOptions() {
		return JitLlmChatOptions.builder()
			.model(this.modelName)
			.maxTokens(this.maxTokens)
			.temperature(this.temperature)
			.topP(this.topP)
			.seed(this.seed)
			.stopSequences(this.stopSequences)
			.build();
	}

}
