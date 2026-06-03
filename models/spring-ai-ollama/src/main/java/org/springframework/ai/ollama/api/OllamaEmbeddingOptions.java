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
 * Options for the Ollama Embedding API. Extends {@link OllamaCommonOptions} to inherit
 * the shared model-loading fields ({@code useNUMA}, {@code numCtx}, {@code numBatch},
 * {@code numGPU}, {@code mainGPU}, {@code lowVRAM}, {@code f16KV}, {@code logitsAll},
 * {@code vocabOnly}, {@code useMMap}, {@code useMLock}, {@code numThread}) and common
 * request fields ({@code model}, {@code keepAlive}, {@code truncate}).
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @since 0.8.0
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
public class OllamaEmbeddingOptions extends OllamaCommonOptions implements EmbeddingOptions {

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "keep_alive", "truncate", "dimensions");

	// @formatter:off

	/**
	 * The dimensions of the embedding output. This allows you to specify the size of the embedding vector
	 * that should be returned by the model. Not all models support this parameter.
	 */
	private final @Nullable Integer dimensions;

	// @formatter:on

	protected OllamaEmbeddingOptions(@Nullable String model, @Nullable String keepAlive,
			@Nullable Integer dimensions, @Nullable Boolean truncate, @Nullable Boolean useNUMA,
			@Nullable Integer numCtx, @Nullable Integer numBatch, @Nullable Integer numGPU,
			@Nullable Integer mainGPU, @Nullable Boolean lowVRAM, @Nullable Boolean f16KV,
			@Nullable Boolean logitsAll, @Nullable Boolean vocabOnly, @Nullable Boolean useMMap,
			@Nullable Boolean useMLock, @Nullable Integer numThread) {
		super(model != null ? model : OllamaModel.MXBAI_EMBED_LARGE.id(), keepAlive, truncate, useNUMA, numCtx,
				numBatch, numGPU, mainGPU, lowVRAM, f16KV, logitsAll, vocabOnly, useMMap, useMLock, numThread);
		this.dimensions = dimensions;
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

	// getModel, getKeepAlive, getTruncate, getUseNUMA, getNumCtx, getNumBatch,
	// getNumGPU, getMainGPU, getLowVRAM, getF16KV, getLogitsAll, getVocabOnly,
	// getUseMMap, getUseMLock, getNumThread are inherited from OllamaCommonOptions.

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
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
		if (this.numCtx != null) {
			map.put("num_ctx", this.numCtx);
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
		if (this.f16KV != null) {
			map.put("f16_kv", this.f16KV);
		}
		if (this.logitsAll != null) {
			map.put("logits_all", this.logitsAll);
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
	// @formatter:on

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

		private @Nullable String model;

		private @Nullable String keepAlive;

		private @Nullable Integer dimensions;

		private @Nullable Boolean truncate;

		private @Nullable Boolean useNUMA;

		private @Nullable Integer numCtx;

		private @Nullable Integer numBatch;

		private @Nullable Integer numGPU;

		private @Nullable Integer mainGPU;

		private @Nullable Boolean lowVRAM;

		private @Nullable Boolean f16KV;

		private @Nullable Boolean logitsAll;

		private @Nullable Boolean vocabOnly;

		private @Nullable Boolean useMMap;

		private @Nullable Boolean useMLock;

		private @Nullable Integer numThread;

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

		public Builder useNUMA(@Nullable Boolean useNUMA) {
			this.useNUMA = useNUMA;
			return this;
		}

		public Builder numCtx(@Nullable Integer numCtx) {
			this.numCtx = numCtx;
			return this;
		}

		public Builder numBatch(@Nullable Integer numBatch) {
			this.numBatch = numBatch;
			return this;
		}

		public Builder numGPU(@Nullable Integer numGPU) {
			this.numGPU = numGPU;
			return this;
		}

		public Builder mainGPU(@Nullable Integer mainGPU) {
			this.mainGPU = mainGPU;
			return this;
		}

		public Builder lowVRAM(@Nullable Boolean lowVRAM) {
			this.lowVRAM = lowVRAM;
			return this;
		}

		public Builder f16KV(@Nullable Boolean f16KV) {
			this.f16KV = f16KV;
			return this;
		}

		public Builder logitsAll(@Nullable Boolean logitsAll) {
			this.logitsAll = logitsAll;
			return this;
		}

		public Builder vocabOnly(@Nullable Boolean vocabOnly) {
			this.vocabOnly = vocabOnly;
			return this;
		}

		public Builder useMMap(@Nullable Boolean useMMap) {
			this.useMMap = useMMap;
			return this;
		}

		public Builder useMLock(@Nullable Boolean useMLock) {
			this.useMLock = useMLock;
			return this;
		}

		public Builder numThread(@Nullable Integer numThread) {
			this.numThread = numThread;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public OllamaEmbeddingOptions build() {
			return new OllamaEmbeddingOptions(this.model, this.keepAlive, this.dimensions, this.truncate, this.useNUMA,
					this.numCtx, this.numBatch, this.numGPU, this.mainGPU, this.lowVRAM, this.f16KV, this.logitsAll,
					this.vocabOnly, this.useMMap, this.useMLock, this.numThread);
		}

	}

}
