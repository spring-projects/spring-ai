/*
 * Copyright 2023-2024 the original author or authors.
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
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
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
public class OllamaOptions implements FunctionCallingOptions, EmbeddingOptions {

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "format", "keep_alive", "truncate");

	// Following fields are options which must be set when the model is loaded into
	// memory.
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	@JsonProperty("numa")
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
	 */
	@JsonProperty("low_vram")
	private Boolean lowVRAM;

	/**
	 * (Default: true)
	 */
	@JsonProperty("f16_kv")
	private Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 */
	@JsonProperty("logits_all")
	private Boolean logitsAll;

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
	 * Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 */
	@JsonProperty("tfs_z")
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
	 */
	@JsonProperty("mirostat")
	private Integer mirostat;

	/**
	 * Controls the balance between coherence and diversity of the output.
	 * A lower value will result in more focused and coherent text. (Default: 5.0)
	 */
	@JsonProperty("mirostat_tau")
	private Float mirostatTau;

	/**
	 * Influences how quickly the algorithm responds to feedback from the generated text.
	 * A lower learning rate will result in slower adjustments, while a higher learning rate
	 * will make the algorithm more responsive. (Default: 0.1)
	 */
	@JsonProperty("mirostat_eta")
	private Float mirostatEta;

	/**
	 * (Default: true)
	 */
	@JsonProperty("penalize_newline")
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

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the functionCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the functionCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
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
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	@JsonIgnore
	private Boolean proxyToolCalls;

	@JsonIgnore
	private Map<String, Object> toolContext;

	public static Builder builder() {
		return new Builder();
	}


	/**
	 * Helper factory method to create a new {@link OllamaOptions} instance.
	 * @return A new {@link OllamaOptions} instance.
	 * @deprecated Use {@link OllamaOptions#builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
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
				.functions(fromOptions.getFunctions())
				.proxyToolCalls(fromOptions.getProxyToolCalls())
				.functionCallbacks(fromOptions.getFunctionCallbacks())
				.toolContext(fromOptions.getToolContext()).build();
	}

	/**
	 * @deprecated Use {@link Builder#build()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions build() {
		return this;
	}





	/**
	 * @deprecated use {@link Builder#model( String)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withModel(String model) {
		this.model = model;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#model( OllamaModel)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withModel(OllamaModel model) {
		this.model = model.getName();
		return this;
	}

	/**
	 * @deprecated use {@link Builder#format} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withFormat(Object format) {
		this.format = format;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#keepAlive} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#truncate( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTruncate(Boolean truncate) {
		this.truncate = truncate;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#useNUMA( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withUseNUMA(Boolean useNUMA) {
		this.useNUMA = useNUMA;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numCtx( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumCtx(Integer numCtx) {
		this.numCtx = numCtx;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numBatch( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumBatch(Integer numBatch) {
		this.numBatch = numBatch;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numGPU( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumGPU(Integer numGPU) {
		this.numGPU = numGPU;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#mainGPU( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withMainGPU(Integer mainGPU) {
		this.mainGPU = mainGPU;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#lowVRAM( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withLowVRAM(Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#f16KV( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withF16KV(Boolean f16KV) {
		this.f16KV = f16KV;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#logitsAll( Boolean)}  instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withLogitsAll(Boolean logitsAll) {
		this.logitsAll = logitsAll;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#vocabOnly( Boolean)}  instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withVocabOnly(Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#useMMap( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withUseMMap(Boolean useMMap) {
		this.useMMap = useMMap;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#useMLock( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withUseMLock(Boolean useMLock) {
		this.useMLock = useMLock;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numThread( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumThread(Integer numThread) {
		this.numThread = numThread;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numKeep( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumKeep(Integer numKeep) {
		this.numKeep = numKeep;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#seed( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withSeed(Integer seed) {
		this.seed = seed;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#numPredict( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withNumPredict(Integer numPredict) {
		this.numPredict = numPredict;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#topK( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTopK(Integer topK) {
		this.topK = topK;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#topP( Double)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTopP(Double topP) {
		this.topP = topP;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#tfsZ( Float)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTfsZ(Float tfsZ) {
		this.tfsZ = tfsZ;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#typicalP( Float)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTypicalP(Float typicalP) {
		this.typicalP = typicalP;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#repeatLastN( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withRepeatLastN(Integer repeatLastN) {
		this.repeatLastN = repeatLastN;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#temperature( Double)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withTemperature(Double temperature) {
		this.temperature = temperature;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#repeatPenalty( Double)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withRepeatPenalty(Double repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#presencePenalty( Double)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#frequencyPenalty( Double)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#mirostat( Integer)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withMirostat(Integer mirostat) {
		this.mirostat = mirostat;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#mirostatTau( Float)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withMirostatTau(Float mirostatTau) {
		this.mirostatTau = mirostatTau;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#mirostatEta( Float)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withMirostatEta(Float mirostatEta) {
		this.mirostatEta = mirostatEta;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#penalizeNewline( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withPenalizeNewline(Boolean penalizeNewline) {
		this.penalizeNewline = penalizeNewline;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#stop( List)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withStop(List<String> stop) {
		this.stop = stop;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#functionCallbacks( List)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#functions( Set)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withFunctions(Set<String> functions) {
		this.functions = functions;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#function( String)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withFunction(String functionName) {
		Assert.hasText(functionName, "Function name must not be empty");
		this.functions.add(functionName);
		return this;
	}

	/**
	 * @deprecated use {@link Builder#proxyToolCalls( Boolean)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withProxyToolCalls(Boolean proxyToolCalls) {
		this.proxyToolCalls = proxyToolCalls;
		return this;
	}

	/**
	 * @deprecated use {@link Builder#toolContext( Map)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public OllamaOptions withToolContext(Map<String, Object> toolContext) {
		if (this.toolContext == null) {
			this.toolContext = toolContext;
		}
		else {
			this.toolContext.putAll(toolContext);
		}
		return this;
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
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
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

	@Override
	public Boolean getProxyToolCalls() {
		return this.proxyToolCalls;
	}

	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.proxyToolCalls = proxyToolCalls;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
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
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.tfsZ, that.tfsZ)
				&& Objects.equals(this.typicalP, that.typicalP) && Objects.equals(this.repeatLastN, that.repeatLastN)
				&& Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.repeatPenalty, that.repeatPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.mirostat, that.mirostat) && Objects.equals(this.mirostatTau, that.mirostatTau)
				&& Objects.equals(this.mirostatEta, that.mirostatEta)
				&& Objects.equals(this.penalizeNewline, that.penalizeNewline) && Objects.equals(this.stop, that.stop)
				&& Objects.equals(this.functionCallbacks, that.functionCallbacks)
				&& Objects.equals(this.proxyToolCalls, that.proxyToolCalls)
				&& Objects.equals(this.functions, that.functions) && Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.format, this.keepAlive, this.truncate, this.useNUMA, this.numCtx,
				this.numBatch, this.numGPU, this.mainGPU, this.lowVRAM, this.f16KV, this.logitsAll, this.vocabOnly,
				this.useMMap, this.useMLock, this.numThread, this.numKeep, this.seed, this.numPredict, this.topK,
				this.topP, this.tfsZ, this.typicalP, this.repeatLastN, this.temperature, this.repeatPenalty,
				this.presencePenalty, this.frequencyPenalty, this.mirostat, this.mirostatTau, this.mirostatEta,
				this.penalizeNewline, this.stop, this.functionCallbacks, this.functions, this.proxyToolCalls,
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

		public Builder lowVRAM(Boolean lowVRAM) {
			this.options.lowVRAM = lowVRAM;
			return this;
		}

		public Builder f16KV(Boolean f16KV) {
			this.options.f16KV = f16KV;
			return this;
		}

		public Builder logitsAll(Boolean logitsAll) {
			this.options.logitsAll = logitsAll;
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

		public Builder mirostat(Integer mirostat) {
			this.options.mirostat = mirostat;
			return this;
		}

		public Builder mirostatTau(Float mirostatTau) {
			this.options.mirostatTau = mirostatTau;
			return this;
		}

		public Builder mirostatEta(Float mirostatEta) {
			this.options.mirostatEta = mirostatEta;
			return this;
		}

		public Builder penalizeNewline(Boolean penalizeNewline) {
			this.options.penalizeNewline = penalizeNewline;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.options.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder functions(Set<String> functions) {
			Assert.notNull(functions, "Function names must not be null");
			this.options.functions = functions;
			return this;
		}

		public Builder function(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			this.options.functions.add(functionName);
			return this;
		}

		public Builder proxyToolCalls(Boolean proxyToolCalls) {
			this.options.proxyToolCalls = proxyToolCalls;
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

		/**
		 * @deprecated use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(String model) {
			return model(model);
		}

		/**
		 * @deprecated use {@link #model(OllamaModel)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(OllamaModel model) {
			return model(model);
		}

		/**
		 * @deprecated use {@link #format(Object)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFormat(Object format) {
			return format(format);
		}

		/**
		 * @deprecated use {@link #keepAlive(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withKeepAlive(String keepAlive) {
			return keepAlive(keepAlive);
		}

		/**
		 * @deprecated use {@link #truncate(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTruncate(Boolean truncate) {
			return truncate(truncate);
		}

		/**
		 * @deprecated use {@link #useNUMA(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withUseNUMA(Boolean useNUMA) {
			return useNUMA(useNUMA);
		}

		/**
		 * @deprecated use {@link #numCtx(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumCtx(Integer numCtx) {
			return numCtx(numCtx);
		}

		/**
		 * @deprecated use {@link #numBatch(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumBatch(Integer numBatch) {
			return numBatch(numBatch);
		}

		/**
		 * @deprecated use {@link #numGPU(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumGPU(Integer numGPU) {
			return numGPU(numGPU);
		}

		/**
		 * @deprecated use {@link #mainGPU(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMainGPU(Integer mainGPU) {
			return mainGPU(mainGPU);
		}

		/**
		 * @deprecated use {@link #lowVRAM(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withLowVRAM(Boolean lowVRAM) {
			return lowVRAM(lowVRAM);
		}

		/**
		 * @deprecated use {@link #f16KV(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withF16KV(Boolean f16KV) {
			return f16KV(f16KV);
		}

		/**
		 * @deprecated use {@link #logitsAll(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withLogitsAll(Boolean logitsAll) {
			return logitsAll(logitsAll);
		}

		/**
		 * @deprecated use {@link #vocabOnly(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withVocabOnly(Boolean vocabOnly) {
			return vocabOnly(vocabOnly);
		}

		/**
		 * @deprecated use {@link #useMMap(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withUseMMap(Boolean useMMap) {
			return useMMap(useMMap);
		}

		/**
		 * @deprecated use {@link #useMLock(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withUseMLock(Boolean useMLock) {
			return useMLock(useMLock);
		}

		/**
		 * @deprecated use {@link #numThread(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumThread(Integer numThread) {
			return numThread(numThread);
		}

		/**
		 * @deprecated use {@link #numKeep(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumKeep(Integer numKeep) {
			return numKeep(numKeep);
		}

		/**
		 * @deprecated use {@link #seed(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withSeed(Integer seed) {
			return seed(seed);
		}

		/**
		 * @deprecated use {@link #numPredict(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withNumPredict(Integer numPredict) {
			return numPredict(numPredict);
		}

		/**
		 * @deprecated use {@link #topK(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTopK(Integer topK) {
			return topK(topK);
		}

		/**
		 * @deprecated use {@link #topP(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTopP(Double topP) {
			return topP(topP);
		}

		/**
		 * @deprecated use {@link #tfsZ(Float)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTfsZ(Float tfsZ) {
			return tfsZ(tfsZ);
		}

		/**
		 * @deprecated use {@link #typicalP(Float)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTypicalP(Float typicalP) {
			return typicalP(typicalP);
		}

		/**
		 * @deprecated use {@link #repeatLastN(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withRepeatLastN(Integer repeatLastN) {
			return repeatLastN(repeatLastN);
		}

		/**
		 * @deprecated use {@link #temperature(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTemperature(Double temperature) {
			return temperature(temperature);
		}

		/**
		 * @deprecated use {@link #repeatPenalty(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withRepeatPenalty(Double repeatPenalty) {
			return repeatPenalty(repeatPenalty);
		}

		/**
		 * @deprecated use {@link #presencePenalty(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withPresencePenalty(Double presencePenalty) {
			return presencePenalty(presencePenalty);
		}

		/**
		 * @deprecated use {@link #frequencyPenalty(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFrequencyPenalty(Double frequencyPenalty) {
			return frequencyPenalty(frequencyPenalty);
		}

		/**
		 * @deprecated use {@link #mirostat(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMirostat(Integer mirostat) {
			return mirostat(mirostat);
		}

		/**
		 * @deprecated use {@link #mirostatTau(Float)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMirostatTau(Float mirostatTau) {
			return mirostatTau(mirostatTau);
		}

		/**
		 * @deprecated use {@link #mirostatEta(Float)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMirostatEta(Float mirostatEta) {
			return mirostatEta(mirostatEta);
		}

		/**
		 * @deprecated use {@link #penalizeNewline(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withPenalizeNewline(Boolean penalizeNewline) {
			return penalizeNewline(penalizeNewline);
		}

		/**
		 * @deprecated use {@link #stop(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withStop(List<String> stop) {
			return stop(stop);
		}

		/**
		 * @deprecated use {@link #functionCallbacks(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			return functionCallbacks(functionCallbacks);
		}

		/**
		 * @deprecated use {@link #functions(Set)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunctions(Set<String> functions) {
			return functions(functions);
		}

		/**
		 * @deprecated use {@link #function(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunction(String functionName) {
			return function(functionName);
		}

		/**
		 * @deprecated use {@link #proxyToolCalls(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withProxyToolCalls(Boolean proxyToolCalls) {
			return proxyToolCalls(proxyToolCalls);
		}

		/**
		 * @deprecated use {@link #toolContext(Map)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withToolContext(Map<String, Object> toolContext) {
			return toolContext(toolContext);
		}

		public OllamaOptions build() {
			return this.options;
		}

	}

}
