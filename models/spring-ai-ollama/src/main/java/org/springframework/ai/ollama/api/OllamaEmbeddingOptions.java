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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 0.8.0
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
public class OllamaEmbeddingOptions implements EmbeddingOptions {

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "keep_alive", "truncate", "dimensions");

	// Following fields are options which must be set when the model is loaded into
	// memory.
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

	// @formatter:off


	// Following fields are not part of the Ollama Options API but part of the Request.

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
	 */
	private @Nullable String model;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	private @Nullable String keepAlive;


	/**
	 * The dimensions of the embedding output. This allows you to specify the size of the embedding vector
	 * that should be returned by the model. Not all models support this parameter.
	 */
	private @Nullable Integer dimensions;


	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	private @Nullable Boolean truncate;

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	private @Nullable Boolean useNUMA;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	private @Nullable Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	 */
	private @Nullable Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	private @Nullable Integer mainGPU;

	/**
	 * (Default: false)
	 */
	private @Nullable Boolean lowVRAM;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	private @Nullable Boolean vocabOnly;

	/**
	 * By default, models are mapped into memory, which allows the system to load only the necessary parts
	 * of the model as needed. However, if the model is larger than your total amount of RAM or if your system is low
	 * on available memory, using mmap might increase the risk of pageouts, negatively impacting performance.
	 * Disabling mmap results in slower load times but may reduce pageouts if you're not using mlock.
	 * Note that if the model is larger than the total amount of RAM, turning off mmap would prevent
	 * the model from loading at all.
	 * (Default: null)
	 */
	private @Nullable Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	private @Nullable Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	private @Nullable Integer numThread;


	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Filter out the non-supported fields from the options.
	 * @param options The options to filter.
	 * @return The filtered options.
	 */
	public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
		return options.entrySet().stream()
				.filter(e -> !NON_SUPPORTED_FIELDS.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public static OllamaEmbeddingOptions fromOptions(OllamaEmbeddingOptions fromOptions) {
		return builder()
				.model(fromOptions.getModel())
				.keepAlive(fromOptions.getKeepAlive())
				.truncate(fromOptions.getTruncate())
				.useNUMA(fromOptions.getUseNUMA())
				.numBatch(fromOptions.getNumBatch())
				.numGPU(fromOptions.getNumGPU())
				.mainGPU(fromOptions.getMainGPU())
				.lowVRAM(fromOptions.getLowVRAM())
				.vocabOnly(fromOptions.getVocabOnly())
				.useMMap(fromOptions.getUseMMap())
				.useMLock(fromOptions.getUseMLock())
				.numThread(fromOptions.getNumThread())
				.dimensions(fromOptions.getDimensions())
				.build();
	}

	// -------------------
	// Getters and Setters
	// -------------------
	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(@Nullable String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(@Nullable Boolean truncate) {
		this.truncate = truncate;
	}

	public @Nullable Boolean getUseNUMA() {
		return this.useNUMA;
	}

	public void setUseNUMA(@Nullable Boolean useNUMA) {
		this.useNUMA = useNUMA;
	}

	public @Nullable Integer getNumBatch() {
		return this.numBatch;
	}

	public void setNumBatch(@Nullable Integer numBatch) {
		this.numBatch = numBatch;
	}

	public @Nullable Integer getNumGPU() {
		return this.numGPU;
	}

	public void setNumGPU(@Nullable Integer numGPU) {
		this.numGPU = numGPU;
	}

	public @Nullable Integer getMainGPU() {
		return this.mainGPU;
	}

	public void setMainGPU(@Nullable Integer mainGPU) {
		this.mainGPU = mainGPU;
	}

	public @Nullable Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	public void setLowVRAM(@Nullable Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
	}

	public @Nullable Boolean getVocabOnly() {
		return this.vocabOnly;
	}

	public void setVocabOnly(@Nullable Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
	}

	public @Nullable Boolean getUseMMap() {
		return this.useMMap;
	}

	public void setUseMMap(@Nullable Boolean useMMap) {
		this.useMMap = useMMap;
	}

	public @Nullable Boolean getUseMLock() {
		return this.useMLock;
	}

	public void setUseMLock(@Nullable Boolean useMLock) {
		this.useMLock = useMLock;
	}

	public @Nullable Integer getNumThread() {
		return this.numThread;
	}

	public void setNumThread(@Nullable Integer numThread) {
		this.numThread = numThread;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	/**
	 * Convert the {@link OllamaEmbeddingOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new java.util.HashMap<>();
		if (this.model != null) {
			map.put("model", this.model);
		}
		if (this.keepAlive != null) {
			map.put("keep_alive", this.keepAlive);
		}
		if (this.dimensions != null) {
			map.put("dimensions", this.dimensions);
		}
		if (this.truncate != null) {
			map.put("truncate", this.truncate);
		}
		if (this.useNUMA != null) {
			map.put("numa", this.useNUMA);
		}
		if (this.numBatch != null) {
			map.put("num_batch", this.numBatch);
		}
		if (this.numGPU != null) {
			map.put("num_gpu", this.numGPU);
		}
		if (this.mainGPU != null) {
			map.put("main_gpu", this.mainGPU);
		}
		if (this.lowVRAM != null) {
			map.put("low_vram", this.lowVRAM);
		}
		if (this.vocabOnly != null) {
			map.put("vocab_only", this.vocabOnly);
		}
		if (this.useMMap != null) {
			map.put("use_mmap", this.useMMap);
		}
		if (this.useMLock != null) {
			map.put("use_mlock", this.useMLock);
		}
		if (this.numThread != null) {
			map.put("num_thread", this.numThread);
		}
		return map;
	}

	public OllamaEmbeddingOptions copy() {
		return fromOptions(this);
	}
	// @formatter:on

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OllamaEmbeddingOptions that = (OllamaEmbeddingOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.keepAlive, that.keepAlive)
				&& Objects.equals(this.truncate, that.truncate) && Objects.equals(this.dimensions, that.dimensions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.keepAlive, this.truncate, this.dimensions);
	}

	public static final class Builder {

		private final OllamaEmbeddingOptions options = new OllamaEmbeddingOptions();

		public Builder model(@Nullable String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(OllamaModel model) {
			this.options.model = model.getName();
			return this;
		}

		public Builder keepAlive(@Nullable String keepAlive) {
			this.options.keepAlive = keepAlive;
			return this;
		}

		public Builder truncate(@Nullable Boolean truncate) {
			this.options.truncate = truncate;
			return this;
		}

		public Builder useNUMA(@Nullable Boolean useNUMA) {
			this.options.useNUMA = useNUMA;
			return this;
		}

		public Builder numBatch(@Nullable Integer numBatch) {
			this.options.numBatch = numBatch;
			return this;
		}

		public Builder numGPU(@Nullable Integer numGPU) {
			this.options.numGPU = numGPU;
			return this;
		}

		public Builder mainGPU(@Nullable Integer mainGPU) {
			this.options.mainGPU = mainGPU;
			return this;
		}

		public Builder lowVRAM(@Nullable Boolean lowVRAM) {
			this.options.lowVRAM = lowVRAM;
			return this;
		}

		public Builder vocabOnly(@Nullable Boolean vocabOnly) {
			this.options.vocabOnly = vocabOnly;
			return this;
		}

		public Builder useMMap(@Nullable Boolean useMMap) {
			this.options.useMMap = useMMap;
			return this;
		}

		public Builder useMLock(@Nullable Boolean useMLock) {
			this.options.useMLock = useMLock;
			return this;
		}

		public Builder numThread(@Nullable Integer numThread) {
			this.options.numThread = numThread;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public OllamaEmbeddingOptions build() {
			return this.options;
		}

	}

}
