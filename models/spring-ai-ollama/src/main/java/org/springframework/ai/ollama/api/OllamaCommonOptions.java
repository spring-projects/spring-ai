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

package org.springframework.ai.ollama.api;

import org.jspecify.annotations.Nullable;

/**
 * Abstract base class holding the model-loading options and common request fields shared
 * between {@link OllamaChatOptions} and {@link OllamaEmbeddingOptions}.
 *
 * <p>The model-loading fields correspond to llama.cpp parameters that must be set when a
 * model is loaded into memory. The request fields ({@code model}, {@code keepAlive},
 * {@code truncate}) appear in both Ollama Chat and Embedding requests.
 *
 * @author Adira Denis Muhando
 * @since 1.1.0
 * @see OllamaChatOptions
 * @see OllamaEmbeddingOptions
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
public abstract class OllamaCommonOptions {

	// -------------------------------------------------------------------------
	// Model-loading options (must be set when the model is loaded into memory).
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md
	// -------------------------------------------------------------------------

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	protected final @Nullable Boolean useNUMA;

	/**
	 * Sets the size of the context window used to generate the next token. (Default: 2048)
	 */
	protected final @Nullable Integer numCtx;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	protected final @Nullable Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	 */
	protected final @Nullable Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	protected final @Nullable Integer mainGPU;

	/**
	 * (Default: false)
	 */
	protected final @Nullable Boolean lowVRAM;

	/**
	 * (Default: true)
	 */
	protected final @Nullable Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 */
	protected final @Nullable Boolean logitsAll;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	protected final @Nullable Boolean vocabOnly;

	/**
	 * By default, models are mapped into memory, which allows the system to load only the necessary parts
	 * of the model as needed. However, if the model is larger than your total amount of RAM or if your system is low
	 * on available memory, using mmap might increase the risk of pageouts, negatively impacting performance.
	 * Disabling mmap results in slower load times but may reduce pageouts if you're not using mlock.
	 * Note that if the model is larger than the total amount of RAM, turning off mmap would prevent
	 * the model from loading at all.
	 * (Default: null)
	 */
	protected final @Nullable Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	protected final @Nullable Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	protected final @Nullable Integer numThread;

	// -------------------------------------------------------------------------
	// Common request-level fields present in both Chat and Embedding requests.
	// -------------------------------------------------------------------------

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 */
	protected final String model;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 */
	protected final @Nullable String keepAlive;

	/**
	 * Truncates the end of each input to fit within context length. Returns error if false
	 * and context length is exceeded. Defaults to true.
	 */
	protected final @Nullable Boolean truncate;

	// @formatter:on

	protected OllamaCommonOptions(String model, @Nullable String keepAlive, @Nullable Boolean truncate,
			@Nullable Boolean useNUMA, @Nullable Integer numCtx, @Nullable Integer numBatch, @Nullable Integer numGPU,
			@Nullable Integer mainGPU, @Nullable Boolean lowVRAM, @Nullable Boolean f16KV,
			@Nullable Boolean logitsAll, @Nullable Boolean vocabOnly, @Nullable Boolean useMMap,
			@Nullable Boolean useMLock, @Nullable Integer numThread) {
		this.model = model;
		this.keepAlive = keepAlive;
		this.truncate = truncate;
		this.useNUMA = useNUMA;
		this.numCtx = numCtx;
		this.numBatch = numBatch;
		this.numGPU = numGPU;
		this.mainGPU = mainGPU;
		this.lowVRAM = lowVRAM;
		this.f16KV = f16KV;
		this.logitsAll = logitsAll;
		this.vocabOnly = vocabOnly;
		this.useMMap = useMMap;
		this.useMLock = useMLock;
		this.numThread = numThread;
	}

	// -------------------------------------------------------------------------
	// Getters (no setters — instances are immutable)
	// -------------------------------------------------------------------------

	public String getModel() {
		return this.model;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public @Nullable Boolean getUseNUMA() {
		return this.useNUMA;
	}

	public @Nullable Integer getNumCtx() {
		return this.numCtx;
	}

	public @Nullable Integer getNumBatch() {
		return this.numBatch;
	}

	public @Nullable Integer getNumGPU() {
		return this.numGPU;
	}

	public @Nullable Integer getMainGPU() {
		return this.mainGPU;
	}

	public @Nullable Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	public @Nullable Boolean getF16KV() {
		return this.f16KV;
	}

	public @Nullable Boolean getLogitsAll() {
		return this.logitsAll;
	}

	public @Nullable Boolean getVocabOnly() {
		return this.vocabOnly;
	}

	public @Nullable Boolean getUseMMap() {
		return this.useMMap;
	}

	public @Nullable Boolean getUseMLock() {
		return this.useMLock;
	}

	public @Nullable Integer getNumThread() {
		return this.numThread;
	}

}
