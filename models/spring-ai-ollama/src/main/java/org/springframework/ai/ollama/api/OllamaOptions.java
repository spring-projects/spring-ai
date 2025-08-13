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

package org.springframework.ai.ollama.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 0.8.0
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
@JsonInclude(Include.NON_NULL)
public class OllamaOptions implements ToolCallingChatOptions, EmbeddingOptions {

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "format", "keep_alive", "truncate");

	// Following fields are options which must be set when the model is loaded into
	// memory.
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("numa")
	@Deprecated
	private Boolean useNUMA;

	/**
	 * Sets the size of the context window used to generate the next token. (Default: 2048)
	 */
	@JsonProperty("num_ctx")
	private Integer numCtx;

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
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("low_vram")
	@Deprecated
	private Boolean lowVRAM;

	/**
	 * (Default: true)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("f16_kv")
	@Deprecated
	private Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("logits_all")
	@Deprecated
	private Boolean logitsAll;

	/**
	 * Load only the vocabulary, not the weights.
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("vocab_only")
	@Deprecated
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
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("use_mlock")
	@Deprecated
	private Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	@JsonProperty("num_thread")
	private Integer numThread;

	// Following fields are predict options used at runtime.

	/**
	 * (Default: 4)
	 */
	@JsonProperty("num_keep")
	private Integer numKeep;

	/**
	 * Sets the random number seed to use for generation. Setting this to a
	 * specific number will make the model generate the same text for the same prompt.
	 * (Default: -1)
	 */
	@JsonProperty("seed")
	private Integer seed;

	/**
	 * Maximum number of tokens to predict when generating text.
	 * (Default: 128, -1 = infinite generation, -2 = fill context)
	 */
	@JsonProperty("num_predict")
	private Integer numPredict;

	/**
	 * Reduces the probability of generating nonsense. A higher value (e.g.
	 * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
	 * conservative. (Default: 40)
	 */
	@JsonProperty("top_k")
	private Integer topK;

	/**
	 * Works together with top-k. A higher value (e.g., 0.95) will lead to
	 * more diverse text, while a lower value (e.g., 0.5) will generate more focused and
	 * conservative text. (Default: 0.9)
	 */
	@JsonProperty("top_p")
	private Double topP;

	/**
	 * Alternative to the top_p, and aims to ensure a balance of quality and variety.
	 * The parameter p represents the minimum probability for a token to be considered,
	 * relative to the probability of the most likely token. For example, with p=0.05 and
	 * the most likely token having a probability of 0.9, logits with a value
	 * less than 0.045 are filtered out. (Default: 0.0)
	 */
	@JsonProperty("min_p")
	private Double minP;

	/**
	 * Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("tfs_z")
	@Deprecated
	private Float tfsZ;

	/**
	 * (Default: 1.0)
	 */
	@JsonProperty("typical_p")
	private Float typicalP;

	/**
	 * Sets how far back for the model to look back to prevent
	 * repetition. (Default: 64, 0 = disabled, -1 = num_ctx)
	 */
	@JsonProperty("repeat_last_n")
	private Integer repeatLastN;

	/**
	 * The temperature of the model. Increasing the temperature will
	 * make the model answer more creatively. (Default: 0.8)
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.5) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 0.9) will be more lenient. (Default: 1.1)
	 */
	@JsonProperty("repeat_penalty")
	private Double repeatPenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("presence_penalty")
	private Double presencePenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("frequency_penalty")
	private Double frequencyPenalty;

	/**
	 * Enable Mirostat sampling for controlling perplexity. (default: 0, 0
	 * = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("mirostat")
	@Deprecated
	private Integer mirostat;

	/**
	 * Controls the balance between coherence and diversity of the output.
	 * A lower value will result in more focused and coherent text. (Default: 5.0)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("mirostat_tau")
	@Deprecated
	private Float mirostatTau;

	/**
	 * Influences how quickly the algorithm responds to feedback from the generated text.
	 * A lower learning rate will result in slower adjustments, while a higher learning rate
	 * will make the algorithm more responsive. (Default: 0.1)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("mirostat_eta")
	@Deprecated
	private Float mirostatEta;

	/**
	 * (Default: true)
	 *
	 * @deprecated Not supported in Ollama anymore.
	 */
	@JsonProperty("penalize_newline")
	@Deprecated
	private Boolean penalizeNewline;

	/**
	 * Sets the stop sequences to use. When this pattern is encountered the
	 * LLM will stop generating text and return. Multiple stop patterns may be set by
	 * specifying multiple separate stop parameters in a modelfile.
	 */
	@JsonProperty("stop")
	private List<String> stop;


	// Following fields are not part of the Ollama Options API but part of the Request.

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * Sets the desired format of output from the LLM. The only valid values are null or "json".
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("format")
	private Object format;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("keep_alive")
	private String keepAlive;

	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	@JsonProperty("truncate")
	private Boolean truncate;

	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the toolCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the toolCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the toolCallbacks registry.
	 * The {@link #toolCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

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

	public static OllamaOptions fromOptions(OllamaOptions fromOptions) {
		return builder()
				.model(fromOptions.getModel())
				.format(fromOptions.getFormat())
				.keepAlive(fromOptions.getKeepAlive())
				.truncate(fromOptions.getTruncate())
				.useNUMA(fromOptions.getUseNUMA())
				.numCtx(fromOptions.getNumCtx())
				.numBatch(fromOptions.getNumBatch())
				.numGPU(fromOptions.getNumGPU())
				.mainGPU(fromOptions.getMainGPU())
				.lowVRAM(fromOptions.getLowVRAM())
				.f16KV(fromOptions.getF16KV())
				.logitsAll(fromOptions.getLogitsAll())
				.vocabOnly(fromOptions.getVocabOnly())
				.useMMap(fromOptions.getUseMMap())
				.useMLock(fromOptions.getUseMLock())
				.numThread(fromOptions.getNumThread())
				.numKeep(fromOptions.getNumKeep())
				.seed(fromOptions.getSeed())
				.numPredict(fromOptions.getNumPredict())
				.topK(fromOptions.getTopK())
				.topP(fromOptions.getTopP())
				.minP(fromOptions.getMinP())
				.tfsZ(fromOptions.getTfsZ())
				.typicalP(fromOptions.getTypicalP())
				.repeatLastN(fromOptions.getRepeatLastN())
				.temperature(fromOptions.getTemperature())
				.repeatPenalty(fromOptions.getRepeatPenalty())
				.presencePenalty(fromOptions.getPresencePenalty())
				.frequencyPenalty(fromOptions.getFrequencyPenalty())
				.mirostat(fromOptions.getMirostat())
				.mirostatTau(fromOptions.getMirostatTau())
				.mirostatEta(fromOptions.getMirostatEta())
				.penalizeNewline(fromOptions.getPenalizeNewline())
				.stop(fromOptions.getStop())
				.toolNames(fromOptions.getToolNames())
				.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
				.toolCallbacks(fromOptions.getToolCallbacks())
				.toolContext(fromOptions.getToolContext()).build();
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

	public Object getFormat() {
		return this.format;
	}

	public void setFormat(Object format) {
		this.format = format;
	}

	public String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getUseNUMA() {
		return this.useNUMA;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setUseNUMA(Boolean useNUMA) {
		this.useNUMA = useNUMA;
	}

	public Integer getNumCtx() {
		return this.numCtx;
	}

	public void setNumCtx(Integer numCtx) {
		this.numCtx = numCtx;
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

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setLowVRAM(Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getF16KV() {
		return this.f16KV;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setF16KV(Boolean f16kv) {
		this.f16KV = f16kv;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getLogitsAll() {
		return this.logitsAll;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setLogitsAll(Boolean logitsAll) {
		this.logitsAll = logitsAll;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getVocabOnly() {
		return this.vocabOnly;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setVocabOnly(Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
	}

	public Boolean getUseMMap() {
		return this.useMMap;
	}

	public void setUseMMap(Boolean useMMap) {
		this.useMMap = useMMap;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getUseMLock() {
		return this.useMLock;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setUseMLock(Boolean useMLock) {
		this.useMLock = useMLock;
	}

	public Integer getNumThread() {
		return this.numThread;
	}

	public void setNumThread(Integer numThread) {
		this.numThread = numThread;
	}

	public Integer getNumKeep() {
		return this.numKeep;
	}

	public void setNumKeep(Integer numKeep) {
		this.numKeep = numKeep;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	@Override
	@JsonIgnore
	public Integer getMaxTokens() {
		return getNumPredict();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setNumPredict(maxTokens);
	}

	public Integer getNumPredict() {
		return this.numPredict;
	}

	public void setNumPredict(Integer numPredict) {
		this.numPredict = numPredict;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public Double getMinP() {
		return this.minP;
	}

	public void setMinP(Double minP) {
		this.minP = minP;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Float getTfsZ() {
		return this.tfsZ;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setTfsZ(Float tfsZ) {
		this.tfsZ = tfsZ;
	}

	public Float getTypicalP() {
		return this.typicalP;
	}

	public void setTypicalP(Float typicalP) {
		this.typicalP = typicalP;
	}

	public Integer getRepeatLastN() {
		return this.repeatLastN;
	}

	public void setRepeatLastN(Integer repeatLastN) {
		this.repeatLastN = repeatLastN;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public Double getRepeatPenalty() {
		return this.repeatPenalty;
	}

	public void setRepeatPenalty(Double repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Integer getMirostat() {
		return this.mirostat;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setMirostat(Integer mirostat) {
		this.mirostat = mirostat;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Float getMirostatTau() {
		return this.mirostatTau;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setMirostatTau(Float mirostatTau) {
		this.mirostatTau = mirostatTau;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Float getMirostatEta() {
		return this.mirostatEta;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setMirostatEta(Float mirostatEta) {
		this.mirostatEta = mirostatEta;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public Boolean getPenalizeNewline() {
		return this.penalizeNewline;
	}

	/**
	 * @deprecated Not supported in Ollama anymore.
	 */
	@Deprecated
	public void setPenalizeNewline(Boolean penalizeNewline) {
		this.penalizeNewline = penalizeNewline;
	}

	@Override
	@JsonIgnore
	public List<String> getStopSequences() {
		return getStop();
	}

	@JsonIgnore
	public void setStopSequences(List<String> stopSequences) {
		setStop(stopSequences);
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	public Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Boolean truncate) {
		this.truncate = truncate;
	}

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	@JsonIgnore
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	@JsonIgnore
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable
	@JsonIgnore
	public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

	@Override
	@JsonIgnore
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	@JsonIgnore
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	/**
	 * Convert the {@link OllamaOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	@Override
	public OllamaOptions copy() {
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
		OllamaOptions that = (OllamaOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.format, that.format)
				&& Objects.equals(this.keepAlive, that.keepAlive) && Objects.equals(this.truncate, that.truncate)
				&& Objects.equals(this.useNUMA, that.useNUMA) && Objects.equals(this.numCtx, that.numCtx)
				&& Objects.equals(this.numBatch, that.numBatch) && Objects.equals(this.numGPU, that.numGPU)
				&& Objects.equals(this.mainGPU, that.mainGPU) && Objects.equals(this.lowVRAM, that.lowVRAM)
				&& Objects.equals(this.f16KV, that.f16KV) && Objects.equals(this.logitsAll, that.logitsAll)
				&& Objects.equals(this.vocabOnly, that.vocabOnly) && Objects.equals(this.useMMap, that.useMMap)
				&& Objects.equals(this.useMLock, that.useMLock) && Objects.equals(this.numThread, that.numThread)
				&& Objects.equals(this.numKeep, that.numKeep) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.numPredict, that.numPredict) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.minP, that.minP)
				&& Objects.equals(this.tfsZ, that.tfsZ) && Objects.equals(this.typicalP, that.typicalP)
				&& Objects.equals(this.repeatLastN, that.repeatLastN)
				&& Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.repeatPenalty, that.repeatPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.mirostat, that.mirostat) && Objects.equals(this.mirostatTau, that.mirostatTau)
				&& Objects.equals(this.mirostatEta, that.mirostatEta)
				&& Objects.equals(this.penalizeNewline, that.penalizeNewline) && Objects.equals(this.stop, that.stop)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolNames, that.toolNames) && Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.format, this.keepAlive, this.truncate, this.useNUMA, this.numCtx,
				this.numBatch, this.numGPU, this.mainGPU, this.lowVRAM, this.f16KV, this.logitsAll, this.vocabOnly,
				this.useMMap, this.useMLock, this.numThread, this.numKeep, this.seed, this.numPredict, this.topK,
				this.topP, this.minP, this.tfsZ, this.typicalP, this.repeatLastN, this.temperature, this.repeatPenalty,
				this.presencePenalty, this.frequencyPenalty, this.mirostat, this.mirostatTau, this.mirostatEta,
				this.penalizeNewline, this.stop, this.toolCallbacks, this.toolNames, this.internalToolExecutionEnabled,
				this.toolContext);
	}

	public static class Builder {

		private final OllamaOptions options = new OllamaOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(OllamaModel model) {
			this.options.model = model.getName();
			return this;
		}

		public Builder format(Object format) {
			this.options.format = format;
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

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder useNUMA(Boolean useNUMA) {
			this.options.useNUMA = useNUMA;
			return this;
		}

		public Builder numCtx(Integer numCtx) {
			this.options.numCtx = numCtx;
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

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder lowVRAM(Boolean lowVRAM) {
			this.options.lowVRAM = lowVRAM;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder f16KV(Boolean f16KV) {
			this.options.f16KV = f16KV;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder logitsAll(Boolean logitsAll) {
			this.options.logitsAll = logitsAll;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder vocabOnly(Boolean vocabOnly) {
			this.options.vocabOnly = vocabOnly;
			return this;
		}

		public Builder useMMap(Boolean useMMap) {
			this.options.useMMap = useMMap;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder useMLock(Boolean useMLock) {
			this.options.useMLock = useMLock;
			return this;
		}

		public Builder numThread(Integer numThread) {
			this.options.numThread = numThread;
			return this;
		}

		public Builder numKeep(Integer numKeep) {
			this.options.numKeep = numKeep;
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.seed = seed;
			return this;
		}

		public Builder numPredict(Integer numPredict) {
			this.options.numPredict = numPredict;
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder minP(Double minP) {
			this.options.minP = minP;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder tfsZ(Float tfsZ) {
			this.options.tfsZ = tfsZ;
			return this;
		}

		public Builder typicalP(Float typicalP) {
			this.options.typicalP = typicalP;
			return this;
		}

		public Builder repeatLastN(Integer repeatLastN) {
			this.options.repeatLastN = repeatLastN;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder repeatPenalty(Double repeatPenalty) {
			this.options.repeatPenalty = repeatPenalty;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder mirostat(Integer mirostat) {
			this.options.mirostat = mirostat;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder mirostatTau(Float mirostatTau) {
			this.options.mirostatTau = mirostatTau;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder mirostatEta(Float mirostatEta) {
			this.options.mirostatEta = mirostatEta;
			return this;
		}

		/**
		 * @deprecated Not supported in Ollama anymore.
		 */
		@Deprecated
		public Builder penalizeNewline(Boolean penalizeNewline) {
			this.options.penalizeNewline = penalizeNewline;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.toolNames.addAll(Set.of(toolNames));
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public OllamaOptions build() {
			return this.options;
		}

	}

}
