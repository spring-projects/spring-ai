/*
 * Copyright 2023-2023 the original author or authors.
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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper class for building strongly typed the Ollama request options.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
// @formatter:on
public class OllamaApiOptions {

	/**
	 * Runner options which must be set when the model is loaded into memory.
	 *
	 * @param useNUMA Whether to use NUMA.
	 * @param numCtx Sets the size of the context window used to generate the next token.
	 * (Default: 2048)
	 * @param numBatch ???
	 * @param numGQA The number of GQA groups in the transformer layer. Required for some
	 * models, for example it is 8 for llama2:70b.
	 * @param numGPU The number of layers to send to the GPU(s). On macOS it defaults to 1
	 * to enable metal support, 0 to disable.
	 * @param mainGPU ???
	 * @param lowVRAM ???
	 * @param f16KV ???
	 * @param logitsAll ???
	 * @param vocabOnly ???
	 * @param useMMap ???
	 * @param useMLock ???
	 * @param embeddingOnly ???
	 * @param ropeFrequencyBase ???
	 * @param ropeFrequencyScale ???
	 * @param numThread Sets the number of threads to use during computation. By default,
	 * Ollama will detect this for optimal performance. It is recommended to set this
	 * value to the number of physical CPU cores your system has (as opposed to the
	 * logical number of cores).
	 *
	 * Options specified in GenerateRequest.
	 * @param mirostat Enable Mirostat sampling for controlling perplexity. (default: 0, 0
	 * = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
	 * @param mirostatTau Influences how quickly the algorithm responds to feedback from
	 * the generated text. A lower learning rate will result in slower adjustments, while
	 * a higher learning rate will make the algorithm more responsive. (Default: 0.1).
	 * @param mirostatEta Controls the balance between coherence and diversity of the
	 * output. A lower value will result in more focused and coherent text. (Default:
	 * 5.0).
	 * @param numKeep Unknown.
	 * @param seed Sets the random number seed to use for generation. Setting this to a
	 * specific number will make the model generate the same text for the same prompt.
	 * (Default: 0)
	 * @param numPredict Maximum number of tokens to predict when generating text.
	 * (Default: 128, -1 = infinite generation, -2 = fill context)
	 * @param topK Reduces the probability of generating nonsense. A higher value (e.g.
	 * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
	 * conservative. (Default: 40)
	 * @param topP Works together with top-k. A higher value (e.g., 0.95) will lead to
	 * more diverse text, while a lower value (e.g., 0.5) will generate more focused and
	 * conservative text. (Default: 0.9)
	 * @param tfsZ Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 * @param typicalP Unknown.
	 * @param repeatLastN Sets how far back for the model to look back to prevent
	 * repetition. (Default: 64, 0 = disabled, -1 = num_ctx)
	 * @param temperature The temperature of the model. Increasing the temperature will
	 * make the model answer more creatively. (Default: 0.8)
	 * @param repeatPenalty Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.5) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 0.9) will be more lenient. (Default: 1.1)
	 * @param presencePenalty Unknown.
	 * @param frequencyPenalty Unknown.
	 * @param penalizeNewline Unknown.
	 * @param stop Sets the stop sequences to use. When this pattern is encountered the
	 * LLM will stop generating text and return. Multiple stop patterns may be set by
	 * specifying multiple separate stop parameters in a modelfile.
	 * @see <a href=
	 * "https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama
	 * Valid Parameters and Values</a>
	 * @see <a href="https://github.com/jmorganca/ollama/blob/main/api/types.go">Ollama Go
	 * Types</a>
	 */
	@JsonInclude(Include.NON_NULL)
	public record Options(
			// Runner options which must be set when the model is loaded into memory.
			@JsonProperty("numa") Boolean useNUMA, @JsonProperty("num_ctx") Integer numCtx,
			@JsonProperty("num_batch") Integer numBatch, @JsonProperty("num_gqa") Integer numGQA,
			@JsonProperty("num_gpu") Integer numGPU, @JsonProperty("main_gpu") Integer mainGPU,
			@JsonProperty("low_vram") Boolean lowVRAM, @JsonProperty("f16_kv") Boolean f16KV,
			@JsonProperty("logits_all") Boolean logitsAll, @JsonProperty("vocab_only") Boolean vocabOnly,
			@JsonProperty("use_mmap") Boolean useMMap, @JsonProperty("use_mlock") Boolean useMLock,
			@JsonProperty("embedding_only") Boolean embeddingOnly,
			@JsonProperty("rope_frequency_base") Float ropeFrequencyBase,
			@JsonProperty("rope_frequency_scale") Float ropeFrequencyScale,
			@JsonProperty("num_thread") Integer numThread,

			// Options specified in GenerateRequest.
			@JsonProperty("num_keep") Integer numKeep, @JsonProperty("seed") Integer seed,
			@JsonProperty("num_predict") Integer numPredict, @JsonProperty("top_k") Integer topK,
			@JsonProperty("top_p") Float topP, @JsonProperty("tfs_z") Float tfsZ,
			@JsonProperty("typical_p") Float typicalP, @JsonProperty("repeat_last_n") Integer repeatLastN,
			@JsonProperty("temperature") Float temperature, @JsonProperty("repeat_penalty") Float repeatPenalty,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("frequency_penalty") Float frequencyPenalty, @JsonProperty("mirostat") Integer mirostat,
			@JsonProperty("mirostat_tau") Float mirostatTau, @JsonProperty("mirostat_eta") Float mirostatEta,
			@JsonProperty("penalize_newline") Boolean penalizeNewline, @JsonProperty("stop") String[] stop) {

		/**
		 * Convert the {@link Options} object to a {@link Map} of key/value pairs.
		 * @return The {@link Map} of key/value pairs.
		 */
		public Map<String, Object> toMap() {
			try {
				var json = new ObjectMapper().writeValueAsString(this);
				return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
				});
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private Boolean useNUMA;

			private Integer numCtx;

			private Integer numBatch;

			private Integer numGQA;

			private Integer numGPU;

			private Integer mainGPU;

			private Boolean lowVRAM;

			private Boolean f16KV;

			private Boolean logitsAll;

			private Boolean vocabOnly;

			private Boolean useMMap;

			private Boolean useMLock;

			private Boolean embeddingOnly;

			private Float ropeFrequencyBase;

			private Float ropeFrequencyScale;

			private Integer numThread;

			private Integer numKeep;

			private Integer seed;

			private Integer numPredict;

			private Integer topK;

			private Float topP;

			private Float tfsZ;

			private Float typicalP;

			private Integer repeatLastN;

			private Float temperature;

			private Float repeatPenalty;

			private Float presencePenalty;

			private Float frequencyPenalty;

			private Integer mirostat;

			private Float mirostatTau;

			private Float mirostatEta;

			private Boolean penalizeNewline;

			private String[] stop;

			public Builder withUseNUMA(Boolean useNUMA) {
				this.useNUMA = useNUMA;
				return this;
			}

			public Builder withNumCtx(Integer numCtx) {
				this.numCtx = numCtx;
				return this;
			}

			public Builder withNumBatch(Integer numBatch) {
				this.numBatch = numBatch;
				return this;
			}

			public Builder withNumGQA(Integer numGQA) {
				this.numGQA = numGQA;
				return this;
			}

			public Builder withNumGPU(Integer numGPU) {
				this.numGPU = numGPU;
				return this;
			}

			public Builder withMainGPU(Integer mainGPU) {
				this.mainGPU = mainGPU;
				return this;
			}

			public Builder withLowVRAM(Boolean lowVRAM) {
				this.lowVRAM = lowVRAM;
				return this;
			}

			public Builder withF16KV(Boolean f16KV) {
				this.f16KV = f16KV;
				return this;
			}

			public Builder withLogitsAll(Boolean logitsAll) {
				this.logitsAll = logitsAll;
				return this;
			}

			public Builder withVocabOnly(Boolean vocabOnly) {
				this.vocabOnly = vocabOnly;
				return this;
			}

			public Builder withUseMMap(Boolean useMMap) {
				this.useMMap = useMMap;
				return this;
			}

			public Builder withUseMLock(Boolean useMLock) {
				this.useMLock = useMLock;
				return this;
			}

			public Builder withEmbeddingOnly(Boolean embeddingOnly) {
				this.embeddingOnly = embeddingOnly;
				return this;
			}

			public Builder withRopeFrequencyBase(Float ropeFrequencyBase) {
				this.ropeFrequencyBase = ropeFrequencyBase;
				return this;
			}

			public Builder withRopeFrequencyScale(Float ropeFrequencyScale) {
				this.ropeFrequencyScale = ropeFrequencyScale;
				return this;
			}

			public Builder withNumThread(Integer numThread) {
				this.numThread = numThread;
				return this;
			}

			public Builder withNumKeep(Integer numKeep) {
				this.numKeep = numKeep;
				return this;
			}

			public Builder withSeed(Integer seed) {
				this.seed = seed;
				return this;
			}

			public Builder withNumPredict(Integer numPredict) {
				this.numPredict = numPredict;
				return this;
			}

			public Builder withTopK(Integer topK) {
				this.topK = topK;
				return this;
			}

			public Builder withTopP(Float topP) {
				this.topP = topP;
				return this;
			}

			public Builder withTfsZ(Float tfsZ) {
				this.tfsZ = tfsZ;
				return this;
			}

			public Builder withTypicalP(Float typicalP) {
				this.typicalP = typicalP;
				return this;
			}

			public Builder withRepeatLastN(Integer repeatLastN) {
				this.repeatLastN = repeatLastN;
				return this;
			}

			public Builder withTemperature(Float temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withRepeatPenalty(Float repeatPenalty) {
				this.repeatPenalty = repeatPenalty;
				return this;
			}

			public Builder withPresencePenalty(Float presencePenalty) {
				this.presencePenalty = presencePenalty;
				return this;
			}

			public Builder withFrequencyPenalty(Float frequencyPenalty) {
				this.frequencyPenalty = frequencyPenalty;
				return this;
			}

			public Builder withMirostat(Integer mirostat) {
				this.mirostat = mirostat;
				return this;
			}

			public Builder withMirostatTau(Float mirostatTau) {
				this.mirostatTau = mirostatTau;
				return this;
			}

			public Builder withMirostatEta(Float mirostatEta) {
				this.mirostatEta = mirostatEta;
				return this;
			}

			public Builder withPenalizeNewline(Boolean penalizeNewline) {
				this.penalizeNewline = penalizeNewline;
				return this;
			}

			public Builder withStop(String[] stop) {
				this.stop = stop;
				return this;
			}

			public Options build() {
				return new Options(useNUMA, numCtx, numBatch, numGQA, numGPU, mainGPU, lowVRAM, f16KV, logitsAll,
						vocabOnly, useMMap, useMLock, embeddingOnly, ropeFrequencyBase, ropeFrequencyScale, numThread,
						numKeep, seed, numPredict, topK, topP, tfsZ, typicalP, repeatLastN, temperature, repeatPenalty,
						presencePenalty, frequencyPenalty, mirostat, mirostatTau, mirostatEta, penalizeNewline, stop);
			}

		}
	}

}
// @formatter:on