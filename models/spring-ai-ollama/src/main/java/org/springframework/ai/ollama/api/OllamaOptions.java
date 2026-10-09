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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Nicolas Krier
 * @author Sebastien Deleuze
 * @author Raphael Zanarelli
 * @since 0.8.0
 * @deprecated since 1.0.0-M2 in favor of {@link OllamaChatOptions} for chat completions
 * and {@link OllamaEmbeddingOptions} for embedding operations. Use
 * {@link OllamaChatOptions} or {@link OllamaEmbeddingOptions} instead.
 * @see OllamaChatOptions
 * @see OllamaEmbeddingOptions
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
@Deprecated(since = "1.0.0-M2", forRemoval = false)
public class OllamaOptions extends OllamaChatOptions implements EmbeddingOptions {

	private final @Nullable Integer dimensions;

	protected OllamaOptions(@Nullable Boolean useNUMA, @Nullable Integer numCtx, @Nullable Integer numBatch,
			@Nullable Integer numGPU, @Nullable Integer mainGPU, @Nullable Boolean lowVRAM, @Nullable Boolean f16KV,
			@Nullable Boolean logitsAll, @Nullable Boolean vocabOnly, @Nullable Boolean useMMap,
			@Nullable Boolean useMLock, @Nullable Integer numThread, @Nullable Integer numKeep, @Nullable Integer seed,
			@Nullable Integer numPredict, @Nullable Integer topK, @Nullable Double topP, @Nullable Double minP,
			@Nullable Float tfsZ, @Nullable Float typicalP, @Nullable Integer repeatLastN, @Nullable Double temperature,
			@Nullable Double repeatPenalty, @Nullable Double presencePenalty, @Nullable Double frequencyPenalty,
			@Nullable Integer mirostat, @Nullable Float mirostatTau, @Nullable Float mirostatEta,
			@Nullable Boolean penalizeNewline, @Nullable List<String> stop, @Nullable String model,
			@Nullable Object format, @Nullable String keepAlive, @Nullable Boolean truncate,
			@Nullable ThinkOption thinkOption, @Nullable List<ToolCallback> toolCallbacks,
			@Nullable Map<String, Object> toolContext, @Nullable Integer dimensions) {
		super(useNUMA, numCtx, numBatch, numGPU, mainGPU, lowVRAM, f16KV, logitsAll, vocabOnly, useMMap, useMLock,
				numThread, numKeep, seed, numPredict, topK, topP, minP, tfsZ, typicalP, repeatLastN, temperature,
				repeatPenalty, presencePenalty, frequencyPenalty, mirostat, mirostatTau, mirostatEta, penalizeNewline,
				stop, model, format, keepAlive, truncate, thinkOption, toolCallbacks, toolContext);
		this.dimensions = dimensions;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	/**
	 * Convert this {@link OllamaOptions} instance to {@link OllamaEmbeddingOptions}.
	 * @return a new {@link OllamaEmbeddingOptions} instance populated with matching
	 * options.
	 */
	public OllamaEmbeddingOptions toEmbeddingOptions() {
		return OllamaEmbeddingOptions.builder()
			.model(getModel())
			.keepAlive(getKeepAlive())
			.dimensions(this.dimensions)
			.truncate(getTruncate())
			.useNUMA(getUseNUMA())
			.numCtx(getNumCtx())
			.numBatch(getNumBatch())
			.numGPU(getNumGPU())
			.mainGPU(getMainGPU())
			.lowVRAM(getLowVRAM())
			.f16KV(getF16KV())
			.logitsAll(getLogitsAll())
			.vocabOnly(getVocabOnly())
			.useMMap(getUseMMap())
			.useMLock(getUseMLock())
			.numThread(getNumThread())
			.build();
	}

	@Override
	public Map<String, Object> toMap() {
		Map<String, Object> map = new java.util.HashMap<>(super.toMap());
		if (this.dimensions != null) {
			map.put("dimensions", this.dimensions);
		}
		return map;
	}

	@Override
	public Builder mutate() {
		return OllamaOptions.builder()
			.dimensions(this.dimensions)
			.model(getModel())
			.frequencyPenalty(getFrequencyPenalty())
			.maxTokens(getNumPredict())
			.presencePenalty(getPresencePenalty())
			.stop(getStop())
			.temperature(getTemperature())
			.topK(getTopK())
			.topP(getTopP())
			.toolCallbacks(getToolCallbacks())
			.toolContext(getToolContext())
			.format(getFormat())
			.keepAlive(getKeepAlive())
			.truncate(getTruncate())
			.thinkOption(getThinkOption())
			.useNUMA(getUseNUMA())
			.numCtx(getNumCtx())
			.numBatch(getNumBatch())
			.numGPU(getNumGPU())
			.mainGPU(getMainGPU())
			.lowVRAM(getLowVRAM())
			.f16KV(getF16KV())
			.logitsAll(getLogitsAll())
			.vocabOnly(getVocabOnly())
			.useMMap(getUseMMap())
			.useMLock(getUseMLock())
			.numThread(getNumThread())
			.numKeep(getNumKeep())
			.seed(getSeed())
			.minP(getMinP())
			.tfsZ(getTfsZ())
			.typicalP(getTypicalP())
			.repeatLastN(getRepeatLastN())
			.repeatPenalty(getRepeatPenalty())
			.mirostat(getMirostat())
			.mirostatTau(getMirostatTau())
			.mirostatEta(getMirostatEta())
			.penalizeNewline(getPenalizeNewline());
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		OllamaOptions that = (OllamaOptions) o;
		return Objects.equals(this.dimensions, that.dimensions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.dimensions);
	}

	public static class Builder extends OllamaChatOptions.Builder {

		protected @Nullable Integer dimensions;

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		@Override
		public Builder model(@Nullable String model) {
			super.model(model);
			return this;
		}

		@Override
		public Builder temperature(@Nullable Double temperature) {
			super.temperature(temperature);
			return this;
		}

		@Override
		public Builder topK(@Nullable Integer topK) {
			super.topK(topK);
			return this;
		}

		@Override
		public Builder topP(@Nullable Double topP) {
			super.topP(topP);
			return this;
		}

		@Override
		public Builder maxTokens(@Nullable Integer maxTokens) {
			super.maxTokens(maxTokens);
			return this;
		}

		@Override
		public Builder numPredict(@Nullable Integer numPredict) {
			super.numPredict(numPredict);
			return this;
		}

		@Override
		public Builder keepAlive(@Nullable String keepAlive) {
			super.keepAlive(keepAlive);
			return this;
		}

		@Override
		public Builder truncate(@Nullable Boolean truncate) {
			super.truncate(truncate);
			return this;
		}

		@Override
		public Builder useNUMA(@Nullable Boolean useNUMA) {
			super.useNUMA(useNUMA);
			return this;
		}

		@Override
		public Builder numCtx(@Nullable Integer numCtx) {
			super.numCtx(numCtx);
			return this;
		}

		@Override
		public Builder numBatch(@Nullable Integer numBatch) {
			super.numBatch(numBatch);
			return this;
		}

		@Override
		public Builder numGPU(@Nullable Integer numGPU) {
			super.numGPU(numGPU);
			return this;
		}

		@Override
		public Builder mainGPU(@Nullable Integer mainGPU) {
			super.mainGPU(mainGPU);
			return this;
		}

		@Override
		public Builder lowVRAM(@Nullable Boolean lowVRAM) {
			super.lowVRAM(lowVRAM);
			return this;
		}

		@Override
		public Builder f16KV(@Nullable Boolean f16KV) {
			super.f16KV(f16KV);
			return this;
		}

		@Override
		public Builder logitsAll(@Nullable Boolean logitsAll) {
			super.logitsAll(logitsAll);
			return this;
		}

		@Override
		public Builder vocabOnly(@Nullable Boolean vocabOnly) {
			super.vocabOnly(vocabOnly);
			return this;
		}

		@Override
		public Builder useMMap(@Nullable Boolean useMMap) {
			super.useMMap(useMMap);
			return this;
		}

		@Override
		public Builder useMLock(@Nullable Boolean useMLock) {
			super.useMLock(useMLock);
			return this;
		}

		@Override
		public Builder numThread(@Nullable Integer numThread) {
			super.numThread(numThread);
			return this;
		}

		@Override
		public Builder numKeep(@Nullable Integer numKeep) {
			super.numKeep(numKeep);
			return this;
		}

		@Override
		public Builder seed(@Nullable Integer seed) {
			super.seed(seed);
			return this;
		}

		@Override
		public Builder minP(@Nullable Double minP) {
			super.minP(minP);
			return this;
		}

		@Override
		public Builder tfsZ(@Nullable Float tfsZ) {
			super.tfsZ(tfsZ);
			return this;
		}

		@Override
		public Builder typicalP(@Nullable Float typicalP) {
			super.typicalP(typicalP);
			return this;
		}

		@Override
		public Builder repeatLastN(@Nullable Integer repeatLastN) {
			super.repeatLastN(repeatLastN);
			return this;
		}

		@Override
		public Builder repeatPenalty(@Nullable Double repeatPenalty) {
			super.repeatPenalty(repeatPenalty);
			return this;
		}

		@Override
		public Builder presencePenalty(@Nullable Double presencePenalty) {
			super.presencePenalty(presencePenalty);
			return this;
		}

		@Override
		public Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
			super.frequencyPenalty(frequencyPenalty);
			return this;
		}

		@Override
		public Builder mirostat(@Nullable Integer mirostat) {
			super.mirostat(mirostat);
			return this;
		}

		@Override
		public Builder mirostatTau(@Nullable Float mirostatTau) {
			super.mirostatTau(mirostatTau);
			return this;
		}

		@Override
		public Builder mirostatEta(@Nullable Float mirostatEta) {
			super.mirostatEta(mirostatEta);
			return this;
		}

		@Override
		public Builder penalizeNewline(@Nullable Boolean penalizeNewline) {
			super.penalizeNewline(penalizeNewline);
			return this;
		}

		@Override
		public Builder stop(@Nullable List<String> stop) {
			super.stop(stop);
			return this;
		}

		@Override
		public Builder format(@Nullable Object format) {
			super.format(format);
			return this;
		}

		@Override
		public Builder thinkOption(@Nullable ThinkOption thinkOption) {
			super.thinkOption(thinkOption);
			return this;
		}

		@Override
		public Builder toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
			super.toolCallbacks(toolCallbacks);
			return this;
		}

		@Override
		public Builder toolContext(@Nullable Map<String, Object> toolContext) {
			super.toolContext(toolContext);
			return this;
		}

		@Override
		public Builder combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof Builder options) {
				if (options.dimensions != null) {
					this.dimensions = options.dimensions;
				}
			}
			return this;
		}

		@Override
		public OllamaOptions build() {
			return new OllamaOptions(this.useNUMA, this.numCtx, this.numBatch, this.numGPU, this.mainGPU, this.lowVRAM,
					this.f16KV, this.logitsAll, this.vocabOnly, this.useMMap, this.useMLock, this.numThread,
					this.numKeep, this.seed, this.maxTokens, this.topK, this.topP, this.minP, this.tfsZ, this.typicalP,
					this.repeatLastN, this.temperature, this.repeatPenalty, this.presencePenalty, this.frequencyPenalty,
					this.mirostat, this.mirostatTau, this.mirostatEta, this.penalizeNewline, this.stopSequences,
					this.model, this.format, this.keepAlive, this.truncate, this.thinkOption, this.toolCallbacks,
					this.toolContext, this.dimensions);
		}

	}

}
