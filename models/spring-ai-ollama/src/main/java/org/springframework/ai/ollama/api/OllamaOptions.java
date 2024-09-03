/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.ollama.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
@JsonInclude(Include.NON_NULL)
public class OllamaOptions implements FunctionCallingOptions, ChatOptions, EmbeddingOptions {

	public static final String DEFAULT_MODEL = OllamaModel.MISTRAL.id();

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "format", "keep_alive", "truncate");

	// Following fields are options which must be set when the model is loaded into
	// memory.
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	@JsonProperty("numa") private Boolean useNUMA;

	/**
	 * Sets the size of the context window used to generate the next token. (Default: 2048)
	 */
	@JsonProperty("num_ctx") private Integer numCtx;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	@JsonProperty("num_batch") private Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	*/
	@JsonProperty("num_gpu") private Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	@JsonProperty("main_gpu")private Integer mainGPU;

	/**
	 * (Default: false)
	 */
	@JsonProperty("low_vram") private Boolean lowVRAM;

	/**
	 * (Default: true)
	 */
	@JsonProperty("f16_kv") private Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 */
	@JsonProperty("logits_all") private Boolean logitsAll;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	@JsonProperty("vocab_only") private Boolean vocabOnly;

	/**
	 * By default, models are mapped into memory, which allows the system to load only the necessary parts
	 * of the model as needed. However, if the model is larger than your total amount of RAM or if your system is low
	 * on available memory, using mmap might increase the risk of pageouts, negatively impacting performance.
	 * Disabling mmap results in slower load times but may reduce pageouts if you're not using mlock.
	 * Note that if the model is larger than the total amount of RAM, turning off mmap would prevent
	 * the model from loading at all.
	 * (Default: null)
	 */
	@JsonProperty("use_mmap") private Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	@JsonProperty("use_mlock") private Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	@JsonProperty("num_thread") private Integer numThread;

	// Following fields are predict options used at runtime.

	/**
	 * (Default: 4)
	 */
	@JsonProperty("num_keep") private Integer numKeep;

	/**
	 * Sets the random number seed to use for generation. Setting this to a
	 * specific number will make the model generate the same text for the same prompt.
	 * (Default: -1)
	 */
	@JsonProperty("seed") private Integer seed;

	/**
	 * Maximum number of tokens to predict when generating text.
	 * (Default: 128, -1 = infinite generation, -2 = fill context)
	 */
	@JsonProperty("num_predict") private Integer numPredict;

	/**
	 * Reduces the probability of generating nonsense. A higher value (e.g.
	 * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
	 * conservative. (Default: 40)
	 */
	@JsonProperty("top_k") private Integer topK;

	/**
	 * Works together with top-k. A higher value (e.g., 0.95) will lead to
	 * more diverse text, while a lower value (e.g., 0.5) will generate more focused and
	 * conservative text. (Default: 0.9)
	 */
	@JsonProperty("top_p") private Float topP;

	/**
	 * Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 */
	@JsonProperty("tfs_z") private Float tfsZ;

	/**
	 * (Default: 1.0)
	 */
	@JsonProperty("typical_p") private Float typicalP;

	/**
	 * Sets how far back for the model to look back to prevent
	 * repetition. (Default: 64, 0 = disabled, -1 = num_ctx)
	 */
	@JsonProperty("repeat_last_n") private Integer repeatLastN;

	/**
	 * The temperature of the model. Increasing the temperature will
	 * make the model answer more creatively. (Default: 0.8)
	 */
	@JsonProperty("temperature") private Float temperature;

	/**
	 * Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.5) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 0.9) will be more lenient. (Default: 1.1)
	 */
	@JsonProperty("repeat_penalty") private Float repeatPenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("presence_penalty") private Float presencePenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("frequency_penalty") private Float frequencyPenalty;

	/**
	 * Enable Mirostat sampling for controlling perplexity. (default: 0, 0
	 * = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
	 */
	@JsonProperty("mirostat") private Integer mirostat;

	/**
	 * Controls the balance between coherence and diversity of the output.
	 * A lower value will result in more focused and coherent text. (Default: 5.0)
	 */
	@JsonProperty("mirostat_tau") private Float mirostatTau;

	/**
	 * Influences how quickly the algorithm responds to feedback from the generated text.
	 * A lower learning rate will result in slower adjustments, while a higher learning rate
	 * will make the algorithm more responsive. (Default: 0.1)
	 */
	@JsonProperty("mirostat_eta") private Float mirostatEta;

	/**
	 * (Default: true)
	 */
	@JsonProperty("penalize_newline") private Boolean penalizeNewline;

	/**
	 * Sets the stop sequences to use. When this pattern is encountered the
	 * LLM will stop generating text and return. Multiple stop patterns may be set by
	 * specifying multiple separate stop parameters in a modelfile.
	 */
	@JsonProperty("stop") private List<String> stop;


	// Following fields are not part of the Ollama Options API but part of the Request.

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
	 */
	@JsonProperty("model") private String model;

	/**
	 * Sets the desired format of output from the LLM. The only valid values are null or "json".
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("format") private String format;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("keep_alive") private String keepAlive;
	
	
	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded. 
	 * Defaults to true.
	 */
	@JsonProperty("truncate") private Boolean truncate;

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the functionCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the functionCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	@NestedConfigurationProperty
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the functionCallbacks registry.
	 * The {@link #functionCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@NestedConfigurationProperty
	@JsonIgnore
	private Set<String> functions = new HashSet<>();


	public static OllamaOptions builder() {
		return new OllamaOptions();
	}

	public OllamaOptions build() {
		return this;
	}
	
	/**
	 * @param model The ollama model names to use. See the {@link OllamaModel} for the common models.
	 */
	public OllamaOptions withModel(String model) {
		this.model = model;
		return this;
	}

	public OllamaOptions withModel(OllamaModel model) {
		this.model = model.getName();
		return this;
	}

	public OllamaOptions withFormat(String format) {
		this.format = format;
		return this;
	}

	public OllamaOptions withKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	public OllamaOptions withTruncate(Boolean truncate) {
		this.truncate = truncate;
		return this;
	}

	public OllamaOptions withUseNUMA(Boolean useNUMA) {
		this.useNUMA = useNUMA;
		return this;
	}

	public OllamaOptions withNumCtx(Integer numCtx) {
		this.numCtx = numCtx;
		return this;
	}

	public OllamaOptions withNumBatch(Integer numBatch) {
		this.numBatch = numBatch;
		return this;
	}

	public OllamaOptions withNumGPU(Integer numGPU) {
		this.numGPU = numGPU;
		return this;
	}

	public OllamaOptions withMainGPU(Integer mainGPU) {
		this.mainGPU = mainGPU;
		return this;
	}

	public OllamaOptions withLowVRAM(Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
		return this;
	}

	public OllamaOptions withF16KV(Boolean f16KV) {
		this.f16KV = f16KV;
		return this;
	}

	public OllamaOptions withLogitsAll(Boolean logitsAll) {
		this.logitsAll = logitsAll;
		return this;
	}

	public OllamaOptions withVocabOnly(Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
		return this;
	}

	public OllamaOptions withUseMMap(Boolean useMMap) {
		this.useMMap = useMMap;
		return this;
	}

	public OllamaOptions withUseMLock(Boolean useMLock) {
		this.useMLock = useMLock;
		return this;
	}

	public OllamaOptions withNumThread(Integer numThread) {
		this.numThread = numThread;
		return this;
	}

	public OllamaOptions withNumKeep(Integer numKeep) {
		this.numKeep = numKeep;
		return this;
	}

	public OllamaOptions withSeed(Integer seed) {
		this.seed = seed;
		return this;
	}

	public OllamaOptions withNumPredict(Integer numPredict) {
		this.numPredict = numPredict;
		return this;
	}

	public OllamaOptions withTopK(Integer topK) {
		this.topK = topK;
		return this;
	}

	public OllamaOptions withTopP(Float topP) {
		this.topP = topP;
		return this;
	}

	public OllamaOptions withTfsZ(Float tfsZ) {
		this.tfsZ = tfsZ;
		return this;
	}

	public OllamaOptions withTypicalP(Float typicalP) {
		this.typicalP = typicalP;
		return this;
	}

	public OllamaOptions withRepeatLastN(Integer repeatLastN) {
		this.repeatLastN = repeatLastN;
		return this;
	}

	public OllamaOptions withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public OllamaOptions withRepeatPenalty(Float repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
		return this;
	}

	public OllamaOptions withPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
		return this;
	}

	public OllamaOptions withFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
		return this;
	}

	public OllamaOptions withMirostat(Integer mirostat) {
		this.mirostat = mirostat;
		return this;
	}

	public OllamaOptions withMirostatTau(Float mirostatTau) {
		this.mirostatTau = mirostatTau;
		return this;
	}

	public OllamaOptions withMirostatEta(Float mirostatEta) {
		this.mirostatEta = mirostatEta;
		return this;
	}

	public OllamaOptions withPenalizeNewline(Boolean penalizeNewline) {
		this.penalizeNewline = penalizeNewline;
		return this;
	}

	public OllamaOptions withStop(List<String> stop) {
		this.stop = stop;
		return this;
	}

	public OllamaOptions withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
		return this;
	}

	public OllamaOptions withFunctions(Set<String> functions) {
		this.functions = functions;
		return this;
	}

	public OllamaOptions withFunction(String functionName) {
		Assert.hasText(functionName, "Function name must not be empty");
		this.functions.add(functionName);
		return this;
	}

	// -------------------
	// Getters and Setters
	// -------------------
	@Override
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getFormat() {
		return this.format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public Boolean getUseNUMA() {
		return this.useNUMA;
	}

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

	public Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	public void setLowVRAM(Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
	}

	public Boolean getF16KV() {
		return this.f16KV;
	}

	public void setF16KV(Boolean f16kv) {
		this.f16KV = f16kv;
	}

	public Boolean getLogitsAll() {
		return this.logitsAll;
	}

	public void setLogitsAll(Boolean logitsAll) {
		this.logitsAll = logitsAll;
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
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public Float getTfsZ() {
		return this.tfsZ;
	}

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
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Float getRepeatPenalty() {
		return this.repeatPenalty;
	}

	public void setRepeatPenalty(Float repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
	}

	@Override
	public Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public Integer getMirostat() {
		return this.mirostat;
	}

	public void setMirostat(Integer mirostat) {
		this.mirostat = mirostat;
	}

	public Float getMirostatTau() {
		return this.mirostatTau;
	}

	public void setMirostatTau(Float mirostatTau) {
		this.mirostatTau = mirostatTau;
	}

	public Float getMirostatEta() {
		return this.mirostatEta;
	}

	public void setMirostatEta(Float mirostatEta) {
		this.mirostatEta = mirostatEta;
	}

	public Boolean getPenalizeNewline() {
		return this.penalizeNewline;
	}

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
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;	
	}

	@Override
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
	}

	@Override
	public Set<String> getFunctions() {
		return this.functions;
	}

	@Override
	public void setFunctions(Set<String> functions) {
		this.functions = functions;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

	/**
	 * Convert the {@link OllamaOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	/**
	 * Helper factory method to create a new {@link OllamaOptions} instance.
	 * @return A new {@link OllamaOptions} instance.
	 */
	public static OllamaOptions create() {
		return new OllamaOptions();
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

	@Override
	public OllamaOptions copy() {
		return fromOptions(this);
	}

	public static OllamaOptions fromOptions(OllamaOptions fromOptions) {
		return new OllamaOptions()
			.withModel(fromOptions.getModel())
			.withFormat(fromOptions.getFormat())
			.withKeepAlive(fromOptions.getKeepAlive())
			.withTruncate(fromOptions.getTruncate())
			.withUseNUMA(fromOptions.getUseNUMA())
			.withNumCtx(fromOptions.getNumCtx())
			.withNumBatch(fromOptions.getNumBatch())
			.withNumGPU(fromOptions.getNumGPU())
			.withMainGPU(fromOptions.getMainGPU())
			.withLowVRAM(fromOptions.getLowVRAM())
			.withF16KV(fromOptions.getF16KV())
			.withLogitsAll(fromOptions.getLogitsAll())
			.withVocabOnly(fromOptions.getVocabOnly())
			.withUseMMap(fromOptions.getUseMMap())
			.withUseMLock(fromOptions.getUseMLock())
			.withNumThread(fromOptions.getNumThread())
			.withNumKeep(fromOptions.getNumKeep())
			.withSeed(fromOptions.getSeed())
			.withNumPredict(fromOptions.getNumPredict())
			.withTopK(fromOptions.getTopK())
			.withTopP(fromOptions.getTopP())
			.withTfsZ(fromOptions.getTfsZ())
			.withTypicalP(fromOptions.getTypicalP())
			.withRepeatLastN(fromOptions.getRepeatLastN())
			.withTemperature(fromOptions.getTemperature())
			.withRepeatPenalty(fromOptions.getRepeatPenalty())
			.withPresencePenalty(fromOptions.getPresencePenalty())
			.withFrequencyPenalty(fromOptions.getFrequencyPenalty())
			.withMirostat(fromOptions.getMirostat())
			.withMirostatTau(fromOptions.getMirostatTau())
			.withMirostatEta(fromOptions.getMirostatEta())
			.withPenalizeNewline(fromOptions.getPenalizeNewline())
			.withStop(fromOptions.getStop())
			.withFunctions(fromOptions.getFunctions())
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks());
	}
	// @formatter:on

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		OllamaOptions that = (OllamaOptions) o;
		return Objects.equals(model, that.model) && Objects.equals(format, that.format)
				&& Objects.equals(keepAlive, that.keepAlive) && Objects.equals(truncate, that.truncate)
				&& Objects.equals(useNUMA, that.useNUMA) && Objects.equals(numCtx, that.numCtx)
				&& Objects.equals(numBatch, that.numBatch) && Objects.equals(numGPU, that.numGPU)
				&& Objects.equals(mainGPU, that.mainGPU) && Objects.equals(lowVRAM, that.lowVRAM)
				&& Objects.equals(f16KV, that.f16KV) && Objects.equals(logitsAll, that.logitsAll)
				&& Objects.equals(vocabOnly, that.vocabOnly) && Objects.equals(useMMap, that.useMMap)
				&& Objects.equals(useMLock, that.useMLock) && Objects.equals(numThread, that.numThread)
				&& Objects.equals(numKeep, that.numKeep) && Objects.equals(seed, that.seed)
				&& Objects.equals(numPredict, that.numPredict) && Objects.equals(topK, that.topK)
				&& Objects.equals(topP, that.topP) && Objects.equals(tfsZ, that.tfsZ)
				&& Objects.equals(typicalP, that.typicalP) && Objects.equals(repeatLastN, that.repeatLastN)
				&& Objects.equals(temperature, that.temperature) && Objects.equals(repeatPenalty, that.repeatPenalty)
				&& Objects.equals(presencePenalty, that.presencePenalty)
				&& Objects.equals(frequencyPenalty, that.frequencyPenalty) && Objects.equals(mirostat, that.mirostat)
				&& Objects.equals(mirostatTau, that.mirostatTau) && Objects.equals(mirostatEta, that.mirostatEta)
				&& Objects.equals(penalizeNewline, that.penalizeNewline) && Objects.equals(stop, that.stop)
				&& Objects.equals(functionCallbacks, that.functionCallbacks)
				&& Objects.equals(functions, that.functions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.format, this.keepAlive, this.truncate, this.useNUMA, this.numCtx,
				this.numBatch, this.numGPU, this.mainGPU, lowVRAM, this.f16KV, this.logitsAll, this.vocabOnly,
				this.useMMap, this.useMLock, this.numThread, this.numKeep, this.seed, this.numPredict, this.topK,
				this.topP, tfsZ, this.typicalP, this.repeatLastN, this.temperature, this.repeatPenalty,
				this.presencePenalty, this.frequencyPenalty, this.mirostat, this.mirostatTau, this.mirostatEta,
				this.penalizeNewline, this.stop, this.functionCallbacks, this.functions);
	}

}
