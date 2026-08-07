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

import java.util.HashMap;
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
 * @author Sebastien Deleuze
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 * @since 0.8.0
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
	private final String model;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	private final @Nullable String keepAlive;


	/**
	 * The dimensions of the embedding output. This allows you to specify the size of the embedding vector
	 * that should be returned by the model. Not all models support this parameter.
	 */
	private final @Nullable Integer dimensions;


	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	private final @Nullable Boolean truncate;



	/**
	 * Random seed used for reproducible outputs
	 */
	private final @Nullable Integer seed;

	/**
	 * Controls randomness in generation (higher = more random)
	 */
	private final @Nullable Float temperature;

	/**
	 * Limits next token selection to the K most likely
	 */
	private final @Nullable Integer topK;

	/**
	 * Cumulative probability threshold for nucleus sampling
	 */
	private final @Nullable Float topP;
	/**
	 * Minimum probability threshold for token selection
	 */
	private final @Nullable Float minP;

	/**
	 * Stop sequences that will halt generation
	 */
	private final @Nullable String[] stop;

	/**
	 * Context length size (number of tokens)
	 */
	private final @Nullable Integer numCtx;

	/**
	 * Maximum number of tokens to generate
	 */
	private final @Nullable Integer numPredict;

	public OllamaEmbeddingOptions(String model, @Nullable String keepAlive, @Nullable Integer dimensions, @Nullable Boolean truncate, @Nullable Integer seed, @Nullable Float temperature, @Nullable Integer topK, @Nullable Float topP, @Nullable Float minP, @Nullable String[] stop, @Nullable Integer numCtx, @Nullable Integer numPredict) {
		this.model = model;
		this.keepAlive = keepAlive;
		this.dimensions = dimensions;
		this.truncate = truncate;
		this.seed = seed;
		this.temperature = temperature;
		this.topK = topK;
		this.topP = topP;
		this.minP = minP;
		this.stop = stop;
		this.numCtx = numCtx;
		this.numPredict = numPredict;
	}

	public static OllamaEmbeddingOptions.Builder builder() {
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

	// -------------------
	// Getters
	// -------------------
	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public @Nullable Float getTopP() {
		return this.topP;
	}

	public @Nullable Float getMinP() {
		return this.minP;
	}

	public @Nullable String[] getStop() {
		return this.stop;
	}

	public @Nullable Integer getNumCtx() {
		return this.numCtx;
	}

	public @Nullable Integer getNumPredict() {
		return this.numPredict;
	}

	/**
	 * Convert the {@link OllamaEmbeddingOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
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

		if (this.seed != null) {
			map.put("seed", this.seed);
		}

		if (this.temperature != null) {
			map.put("temperature", this.temperature);
		}

		if (this.topK != null) {
			map.put("top_k", this.topK);
		}

		if (this.topP != null) {
			map.put("top_p", this.topP);
		}

		if (this.minP != null) {
			map.put("min_p", this.minP);
		}

		if (this.stop != null && this.stop.length > 0) {
			map.put("stop", this.stop);
		}

		if (this.numCtx != null) {
			map.put("num_ctx", this.numCtx);
		}

		if (this.numPredict != null) {
			map.put("num_predict", this.numPredict);
		}

		return map;
	}

	@Override
	public boolean equals(@Nullable Object o) {
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

		/**
		 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
		 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
		 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
		 */
		private @Nullable String keepAlive;
		/**
		 * NOTE: Synthetic field not part of the official Ollama API.
		 * Used to allow overriding the model name with prompt options.
		 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
		 */
		private @Nullable String model;
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
		 * Random seed used for reproducible outputs
		 */
		private  @Nullable Integer seed;

		/**
		 * Controls randomness in generation (higher = more random)
		 */
		private  @Nullable Float temperature;

		/**
		 * Limits next token selection to the K most likely
		 */
		private  @Nullable Integer topK;

		/**
		 * Cumulative probability threshold for nucleus sampling
		 */
		private  @Nullable Float topP;
		/**
		 * Minimum probability threshold for token selection
		 */
		private  @Nullable Float minP;

		/**
		 * Stop sequences that will halt generation
		 */
		private  @Nullable String[] stop;

		/**
		 * Context length size (number of tokens)
		 */
		private  @Nullable Integer numCtx;

		/**
		 * Maximum number of tokens to generate
		 */
		private  @Nullable Integer numPredict;

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(OllamaModel model) {
			this.model = model.getName();
			return this;
		}

		public Builder keepAlive(@Nullable String keepAlive) {
			this.keepAlive = keepAlive;
			return this;
		}

		public Builder truncate(@Nullable Boolean truncate) {
			this.truncate = truncate;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topK(@Nullable Integer topK) {
			this.topK = topK;
			return this;
		}

		public Builder topP(@Nullable Float topP) {
			this.topP = topP;
			return this;
		}

		public Builder minP(@Nullable Float minP) {
			this.minP = minP;
			return this;
		}

		public Builder stop(@Nullable String[] stop) {
			this.stop = stop;
			return this;
		}

		public Builder numCtx(@Nullable Integer numCtx) {
			this.numCtx = numCtx;
			return this;
		}

		public Builder numPredict(@Nullable Integer numPredict) {
			this.numPredict = numPredict;
			return this;
		}

		public OllamaEmbeddingOptions build() {
			return new OllamaEmbeddingOptions(this.model, this.keepAlive, this.dimensions, this.truncate, this.seed, this.temperature, this.topK, this.topP, this.minP, this.stop, this.numCtx, this.numPredict);
		}
	}
}
