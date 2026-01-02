/*
 * Copyright 2023-2026 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptionsUtils;

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
@JsonInclude(Include.NON_NULL)
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
	@JsonProperty("model")
	private String model;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("keep_alive")
	private String keepAlive;


	/**
	 * The dimensions of the embedding output. This allows you to specify the size of the embedding vector
	 * that should be returned by the model. Not all models support this parameter.
	 */
	@JsonProperty("dimensions")
	private Integer dimensions;


	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	@JsonProperty("truncate")
	private Boolean truncate;

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	@JsonProperty("numa")
	private Boolean useNUMA;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	@JsonProperty("num_batch")
	private Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	 */
	@JsonProperty("num_gpu")
	private Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	@JsonProperty("main_gpu")
	private Integer mainGPU;

	/**
	 * (Default: false)
	 */
	@JsonProperty("low_vram")
	private Boolean lowVRAM;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	@JsonProperty("vocab_only")
	private Boolean vocabOnly;

	/**
	 * By default, models are mapped into memory, which allows the system to load only the necessary parts
	 * of the model as needed. However, if the model is larger than your total amount of RAM or if your system is low
	 * on available memory, using mmap might increase the risk of pageouts, negatively impacting performance.
	 * Disabling mmap results in slower load times but may reduce pageouts if you're not using mlock.
	 * Note that if the model is larger than the total amount of RAM, turning off mmap would prevent
	 * the model from loading at all.
	 * (Default: null)
	 */
	@JsonProperty("use_mmap")
	private Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	@JsonProperty("use_mlock")
	private Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	@JsonProperty("num_thread")
	private Integer numThread;


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
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Boolean truncate) {
		this.truncate = truncate;
	}

	public Boolean getUseNUMA() {
		return this.useNUMA;
	}

	public void setUseNUMA(Boolean useNUMA) {
		this.useNUMA = useNUMA;
	}

	public Integer getNumBatch() {
		return this.numBatch;
	}

	public void setNumBatch(Integer numBatch) {
		this.numBatch = numBatch;
	}

	public Integer getNumGPU() {
		return this.numGPU;
	}

	public void setNumGPU(Integer numGPU) {
		this.numGPU = numGPU;
	}

	public Integer getMainGPU() {
		return this.mainGPU;
	}

	public void setMainGPU(Integer mainGPU) {
		this.mainGPU = mainGPU;
	}

	public Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	public void setLowVRAM(Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
	}

	public Boolean getVocabOnly() {
		return this.vocabOnly;
	}

	public void setVocabOnly(Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
	}

	public Boolean getUseMMap() {
		return this.useMMap;
	}

	public void setUseMMap(Boolean useMMap) {
		this.useMMap = useMMap;
	}

	public Boolean getUseMLock() {
		return this.useMLock;
	}

	public void setUseMLock(Boolean useMLock) {
		this.useMLock = useMLock;
	}

	public Integer getNumThread() {
		return this.numThread;
	}

	public void setNumThread(Integer numThread) {
		this.numThread = numThread;
	}

	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	/**
	 * Convert the {@link OllamaEmbeddingOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
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

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(OllamaModel model) {
			this.options.model = model.getName();
			return this;
		}

		public Builder keepAlive(String keepAlive) {
			this.options.keepAlive = keepAlive;
			return this;
		}

		public Builder truncate(Boolean truncate) {
			this.options.truncate = truncate;
			return this;
		}

		public Builder useNUMA(Boolean useNUMA) {
			this.options.useNUMA = useNUMA;
			return this;
		}

		public Builder numBatch(Integer numBatch) {
			this.options.numBatch = numBatch;
			return this;
		}

		public Builder numGPU(Integer numGPU) {
			this.options.numGPU = numGPU;
			return this;
		}

		public Builder mainGPU(Integer mainGPU) {
			this.options.mainGPU = mainGPU;
			return this;
		}

		public Builder lowVRAM(Boolean lowVRAM) {
			this.options.lowVRAM = lowVRAM;
			return this;
		}

		public Builder vocabOnly(Boolean vocabOnly) {
			this.options.vocabOnly = vocabOnly;
			return this;
		}

		public Builder useMMap(Boolean useMMap) {
			this.options.useMMap = useMMap;
			return this;
		}

		public Builder useMLock(Boolean useMLock) {
			this.options.useMLock = useMLock;
			return this;
		}

		public Builder numThread(Integer numThread) {
			this.options.numThread = numThread;
			return this;
		}

		public Builder dimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public OllamaEmbeddingOptions build() {
			return this.options;
		}

	}

}
