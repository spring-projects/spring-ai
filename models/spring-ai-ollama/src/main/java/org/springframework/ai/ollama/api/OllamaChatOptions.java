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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Nicolas Krier
 * @since 0.8.0
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.mdx#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 */
@JsonInclude(Include.NON_NULL)
public class OllamaChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "format", "keep_alive", "truncate",
			"think");

	// Following fields are options which must be set when the model is loaded into
	// memory.
	// See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

	// @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	@JsonProperty("numa")
	private @Nullable Boolean useNUMA;

	/**
	 * Sets the size of the context window used to generate the next token. (Default: 2048)
	 */
	@JsonProperty("num_ctx")
	private @Nullable Integer numCtx;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	@JsonProperty("num_batch")
	private @Nullable Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	 */
	@JsonProperty("num_gpu")
	private @Nullable Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	@JsonProperty("main_gpu")
	private @Nullable Integer mainGPU;

	/**
	 * (Default: false)
	 */
	@JsonProperty("low_vram")
	private @Nullable Boolean lowVRAM;

	/**
	 * (Default: true)
	 */
	@JsonProperty("f16_kv")
	private @Nullable Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 */
	@JsonProperty("logits_all")
	private @Nullable Boolean logitsAll;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	@JsonProperty("vocab_only")
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
	@JsonProperty("use_mmap")
	private @Nullable Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	@JsonProperty("use_mlock")
	private @Nullable Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	@JsonProperty("num_thread")
	private @Nullable Integer numThread;

	// Following fields are predict options used at runtime.

	/**
	 * (Default: 4)
	 */
	@JsonProperty("num_keep")
	private @Nullable Integer numKeep;

	/**
	 * Sets the random number seed to use for generation. Setting this to a
	 * specific number will make the model generate the same text for the same prompt.
	 * (Default: -1)
	 */
	@JsonProperty("seed")
	private @Nullable Integer seed;

	/**
	 * Maximum number of tokens to predict when generating text.
	 * (Default: 128, -1 = infinite generation, -2 = fill context)
	 */
	@JsonProperty("num_predict")
	private @Nullable Integer numPredict;

	/**
	 * Reduces the probability of generating nonsense. A higher value (e.g.
	 * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
	 * conservative. (Default: 40)
	 */
	@JsonProperty("top_k")
	private @Nullable Integer topK;

	/**
	 * Works together with top-k. A higher value (e.g., 0.95) will lead to
	 * more diverse text, while a lower value (e.g., 0.5) will generate more focused and
	 * conservative text. (Default: 0.9)
	 */
	@JsonProperty("top_p")
	private @Nullable Double topP;

	/**
	 * Alternative to the top_p, and aims to ensure a balance of quality and variety.
	 * The parameter p represents the minimum probability for a token to be considered,
	 * relative to the probability of the most likely token. For example, with p=0.05 and
	 * the most likely token having a probability of 0.9, logits with a value
	 * less than 0.045 are filtered out. (Default: 0.0)
	 */
	@JsonProperty("min_p")
	private @Nullable Double minP;

	/**
	 * Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 */
	@JsonProperty("tfs_z")
	private @Nullable Float tfsZ;

	/**
	 * (Default: 1.0)
	 */
	@JsonProperty("typical_p")
	private @Nullable Float typicalP;

	/**
	 * Sets how far back for the model to look back to prevent
	 * repetition. (Default: 64, 0 = disabled, -1 = num_ctx)
	 */
	@JsonProperty("repeat_last_n")
	private @Nullable Integer repeatLastN;

	/**
	 * The temperature of the model. Increasing the temperature will
	 * make the model answer more creatively. (Default: 0.8)
	 */
	@JsonProperty("temperature")
	private @Nullable Double temperature;

	/**
	 * Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.5) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 0.9) will be more lenient. (Default: 1.1)
	 */
	@JsonProperty("repeat_penalty")
	private @Nullable Double repeatPenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("presence_penalty")
	private @Nullable Double presencePenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("frequency_penalty")
	private @Nullable Double frequencyPenalty;

	/**
	 * Enable Mirostat sampling for controlling perplexity. (default: 0, 0
	 * = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
	 */
	@JsonProperty("mirostat")
	private @Nullable Integer mirostat;

	/**
	 * Controls the balance between coherence and diversity of the output.
	 * A lower value will result in more focused and coherent text. (Default: 5.0)
	 */
	@JsonProperty("mirostat_tau")
	private @Nullable Float mirostatTau;

	/**
	 * Influences how quickly the algorithm responds to feedback from the generated text.
	 * A lower learning rate will result in slower adjustments, while a higher learning rate
	 * will make the algorithm more responsive. (Default: 0.1)
	 */
	@JsonProperty("mirostat_eta")
	private @Nullable Float mirostatEta;

	/**
	 * (Default: true)
	 */
	@JsonProperty("penalize_newline")
	private @Nullable Boolean penalizeNewline;

	/**
	 * Sets the stop sequences to use. When this pattern is encountered the
	 * LLM will stop generating text and return. Multiple stop patterns may be set by
	 * specifying multiple separate stop parameters in a modelfile.
	 */
	@JsonProperty("stop")
	private @Nullable List<String> stop;


	// Following fields are not part of the Ollama Options API but part of the Request.

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
	 */
	@JsonProperty("model")
	private @Nullable String model;

	/**
	 * Sets the desired format of output from the LLM. The only valid values are null or "json".
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("format")
	private @Nullable Object format;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("keep_alive")
	private @Nullable String keepAlive;

	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	@JsonProperty("truncate")
	private @Nullable Boolean truncate;

	/**
	 * The model should think before responding, if supported.
	 * <p>
	 * Most models (Qwen 3, DeepSeek-v3.1, DeepSeek R1) use boolean enable/disable.
	 * The GPT-OSS model requires string levels: "low", "medium", or "high".
	 * <p>
	 * <strong>Default Behavior (Ollama 0.12+):</strong>
	 * <ul>
	 * <li>Thinking-capable models (e.g., qwen3:*-thinking, deepseek-r1, deepseek-v3.1)
	 * <strong>auto-enable thinking by default</strong> when this field is not set.</li>
	 * <li>Standard models (e.g., qwen2.5:*, llama3.2) do not enable thinking by default.</li>
	 * <li>To explicitly control behavior, use {@link Builder#enableThinking()} or
	 * {@link Builder#disableThinking()}.</li>
	 * </ul>
	 * <p>
	 * Use {@link Builder#enableThinking()}, {@link Builder#disableThinking()}, or
	 * {@link Builder#thinkHigh()} to configure this option.
	 *
	 * @see ThinkOption
	 * @see ThinkOption.ThinkBoolean
	 * @see ThinkOption.ThinkLevel
	 */
	@JsonProperty("think")
	private @Nullable ThinkOption thinkOption;

	@JsonIgnore
	private @Nullable Boolean internalToolExecutionEnabled;

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

	public static OllamaChatOptions fromOptions(OllamaChatOptions fromOptions) {
		return builder()
				.model(fromOptions.getModel())
				.format(fromOptions.getFormat())
				.keepAlive(fromOptions.getKeepAlive())
				.truncate(fromOptions.getTruncate())
				.thinkOption(fromOptions.getThinkOption())
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
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Object getFormat() {
		return this.format;
	}

	public void setFormat(@Nullable Object format) {
		this.format = format;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(@Nullable String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public @Nullable Boolean getUseNUMA() {
		return this.useNUMA;
	}

	public void setUseNUMA(@Nullable Boolean useNUMA) {
		this.useNUMA = useNUMA;
	}

	public @Nullable Integer getNumCtx() {
		return this.numCtx;
	}

	public void setNumCtx(@Nullable Integer numCtx) {
		this.numCtx = numCtx;
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

	public @Nullable Boolean getF16KV() {
		return this.f16KV;
	}

	public void setF16KV(@Nullable Boolean f16kv) {
		this.f16KV = f16kv;
	}

	public @Nullable Boolean getLogitsAll() {
		return this.logitsAll;
	}

	public void setLogitsAll(@Nullable Boolean logitsAll) {
		this.logitsAll = logitsAll;
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

	public @Nullable Integer getNumKeep() {
		return this.numKeep;
	}

	public void setNumKeep(@Nullable Integer numKeep) {
		this.numKeep = numKeep;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	@Override
	@JsonIgnore
	public @Nullable Integer getMaxTokens() {
		return getNumPredict();
	}

	@JsonIgnore
	public void setMaxTokens(@Nullable Integer maxTokens) {
		setNumPredict(maxTokens);
	}

	public @Nullable Integer getNumPredict() {
		return this.numPredict;
	}

	public void setNumPredict(@Nullable Integer numPredict) {
		this.numPredict = numPredict;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Double getMinP() {
		return this.minP;
	}

	public void setMinP(@Nullable Double minP) {
		this.minP = minP;
	}

	public @Nullable Float getTfsZ() {
		return this.tfsZ;
	}

	public void setTfsZ(@Nullable Float tfsZ) {
		this.tfsZ = tfsZ;
	}

	public @Nullable Float getTypicalP() {
		return this.typicalP;
	}

	public void setTypicalP(@Nullable Float typicalP) {
		this.typicalP = typicalP;
	}

	public @Nullable Integer getRepeatLastN() {
		return this.repeatLastN;
	}

	public void setRepeatLastN(@Nullable Integer repeatLastN) {
		this.repeatLastN = repeatLastN;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getRepeatPenalty() {
		return this.repeatPenalty;
	}

	public void setRepeatPenalty(@Nullable Double repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Integer getMirostat() {
		return this.mirostat;
	}

	public void setMirostat(@Nullable Integer mirostat) {
		this.mirostat = mirostat;
	}

	public @Nullable Float getMirostatTau() {
		return this.mirostatTau;
	}

	public void setMirostatTau(@Nullable Float mirostatTau) {
		this.mirostatTau = mirostatTau;
	}

	public @Nullable Float getMirostatEta() {
		return this.mirostatEta;
	}

	public void setMirostatEta(@Nullable Float mirostatEta) {
		this.mirostatEta = mirostatEta;
	}

	public @Nullable Boolean getPenalizeNewline() {
		return this.penalizeNewline;
	}

	public void setPenalizeNewline(@Nullable Boolean penalizeNewline) {
		this.penalizeNewline = penalizeNewline;
	}

	@Override
	@JsonIgnore
	public @Nullable List<String> getStopSequences() {
		return getStop();
	}

	@JsonIgnore
	public void setStopSequences(@Nullable List<String> stopSequences) {
		setStop(stopSequences);
	}

	public @Nullable List<String> getStop() {
		return this.stop;
	}

	public void setStop(@Nullable List<String> stop) {
		this.stop = stop;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(@Nullable Boolean truncate) {
		this.truncate = truncate;
	}

	public @Nullable ThinkOption getThinkOption() {
		return this.thinkOption;
	}

	@JsonIgnore
	public void setThinkOption(@Nullable ThinkOption thinkOption) {
		this.thinkOption = thinkOption;
	}

	/**
	 * Supports property binding from values such as true/false/high/medium/low.
	 * @param think the think property value
	 */
	@JsonProperty("think")
	public void setThink(@Nullable Object think) {
		if (think == null) {
			this.thinkOption = null;
			return;
		}
		if (think instanceof ThinkOption parsedThinkOption) {
			this.thinkOption = parsedThinkOption;
			return;
		}
		if (think instanceof Boolean enabled) {
			this.thinkOption = enabled ? ThinkOption.ThinkBoolean.ENABLED : ThinkOption.ThinkBoolean.DISABLED;
			return;
		}

		String normalized = think.toString().trim().toLowerCase(Locale.ROOT);
		if (!StringUtils.hasText(normalized)) {
			this.thinkOption = null;
		}
		else if ("true".equals(normalized)) {
			this.thinkOption = ThinkOption.ThinkBoolean.ENABLED;
		}
		else if ("false".equals(normalized)) {
			this.thinkOption = ThinkOption.ThinkBoolean.DISABLED;
		}
		else {
			this.thinkOption = new ThinkOption.ThinkLevel(normalized);
		}
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
	@JsonIgnore
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
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

	@Override
	@JsonIgnore
	public String getOutputSchema() {
		Assert.state(this.format != null, "format must not be null");
		// If format is a simple string (e.g., "json"), return it as-is
		if (this.format instanceof String) {
			return (String) this.format;
		}
		// Otherwise, serialize the Map/Object to JSON string (JSON Schema case)
		return ModelOptionsUtils.toJsonString(this.format);
	}

	@Override
	@JsonIgnore
	public void setOutputSchema(String outputSchema) {
		this.format = ModelOptionsUtils.jsonToMap(outputSchema);
	}

	/**
	 * Convert the {@link OllamaChatOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	@Override
	public OllamaChatOptions copy() {
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
		OllamaChatOptions that = (OllamaChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.format, that.format)
				&& Objects.equals(this.keepAlive, that.keepAlive) && Objects.equals(this.truncate, that.truncate)
				&& Objects.equals(this.thinkOption, that.thinkOption) && Objects.equals(this.useNUMA, that.useNUMA)
				&& Objects.equals(this.numCtx, that.numCtx) && Objects.equals(this.numBatch, that.numBatch)
				&& Objects.equals(this.numGPU, that.numGPU) && Objects.equals(this.mainGPU, that.mainGPU)
				&& Objects.equals(this.lowVRAM, that.lowVRAM) && Objects.equals(this.f16KV, that.f16KV)
				&& Objects.equals(this.logitsAll, that.logitsAll) && Objects.equals(this.vocabOnly, that.vocabOnly)
				&& Objects.equals(this.useMMap, that.useMMap) && Objects.equals(this.useMLock, that.useMLock)
				&& Objects.equals(this.numThread, that.numThread) && Objects.equals(this.numKeep, that.numKeep)
				&& Objects.equals(this.seed, that.seed) && Objects.equals(this.numPredict, that.numPredict)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.minP, that.minP) && Objects.equals(this.tfsZ, that.tfsZ)
				&& Objects.equals(this.typicalP, that.typicalP) && Objects.equals(this.repeatLastN, that.repeatLastN)
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
		return Objects.hash(this.model, this.format, this.keepAlive, this.truncate, this.thinkOption, this.useNUMA,
				this.numCtx, this.numBatch, this.numGPU, this.mainGPU, this.lowVRAM, this.f16KV, this.logitsAll,
				this.vocabOnly, this.useMMap, this.useMLock, this.numThread, this.numKeep, this.seed, this.numPredict,
				this.topK, this.topP, this.minP, this.tfsZ, this.typicalP, this.repeatLastN, this.temperature,
				this.repeatPenalty, this.presencePenalty, this.frequencyPenalty, this.mirostat, this.mirostatTau,
				this.mirostatEta, this.penalizeNewline, this.stop, this.toolCallbacks, this.toolNames,
				this.internalToolExecutionEnabled, this.toolContext);
	}

	public static final class Builder {

		private final OllamaChatOptions options = new OllamaChatOptions();

		public Builder model(@Nullable String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(OllamaModel model) {
			this.options.model = model.getName();
			return this;
		}

		public Builder format(@Nullable Object format) {
			this.options.format = format;
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

		public Builder numCtx(@Nullable Integer numCtx) {
			this.options.numCtx = numCtx;
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

		public Builder f16KV(@Nullable Boolean f16KV) {
			this.options.f16KV = f16KV;
			return this;
		}

		public Builder logitsAll(@Nullable Boolean logitsAll) {
			this.options.logitsAll = logitsAll;
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

		public Builder numKeep(@Nullable Integer numKeep) {
			this.options.numKeep = numKeep;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.options.seed = seed;
			return this;
		}

		public Builder numPredict(@Nullable Integer numPredict) {
			this.options.numPredict = numPredict;
			return this;
		}

		public Builder topK(@Nullable Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder topP(@Nullable Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder minP(@Nullable Double minP) {
			this.options.minP = minP;
			return this;
		}

		public Builder tfsZ(@Nullable Float tfsZ) {
			this.options.tfsZ = tfsZ;
			return this;
		}

		public Builder typicalP(@Nullable Float typicalP) {
			this.options.typicalP = typicalP;
			return this;
		}

		public Builder repeatLastN(@Nullable Integer repeatLastN) {
			this.options.repeatLastN = repeatLastN;
			return this;
		}

		public Builder temperature(@Nullable Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder repeatPenalty(@Nullable Double repeatPenalty) {
			this.options.repeatPenalty = repeatPenalty;
			return this;
		}

		public Builder presencePenalty(@Nullable Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder mirostat(@Nullable Integer mirostat) {
			this.options.mirostat = mirostat;
			return this;
		}

		public Builder mirostatTau(@Nullable Float mirostatTau) {
			this.options.mirostatTau = mirostatTau;
			return this;
		}

		public Builder mirostatEta(@Nullable Float mirostatEta) {
			this.options.mirostatEta = mirostatEta;
			return this;
		}

		public Builder penalizeNewline(@Nullable Boolean penalizeNewline) {
			this.options.penalizeNewline = penalizeNewline;
			return this;
		}

		public Builder stop(@Nullable List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		/**
		 * Enable thinking mode for the model. The model will include its reasoning
		 * process in the response's thinking field.
		 * <p>
		 * Supported by models: Qwen 3, DeepSeek-v3.1, DeepSeek R1
		 * @return this builder
		 * @see #disableThinking()
		 * @see #thinkLow()
		 */
		public Builder enableThinking() {
			this.options.thinkOption = ThinkOption.ThinkBoolean.ENABLED;
			return this;
		}

		/**
		 * Disable thinking mode for the model.
		 * @return this builder
		 * @see #enableThinking()
		 */
		public Builder disableThinking() {
			this.options.thinkOption = ThinkOption.ThinkBoolean.DISABLED;
			return this;
		}

		/**
		 * Set thinking level to "low" (for GPT-OSS model).
		 * <p>
		 * GPT-OSS requires one of: low, medium, high. Boolean enable/disable is not
		 * supported for this model.
		 * @return this builder
		 * @see #thinkMedium()
		 * @see #thinkHigh()
		 */
		public Builder thinkLow() {
			this.options.thinkOption = ThinkOption.ThinkLevel.LOW;
			return this;
		}

		/**
		 * Set thinking level to "medium" (for GPT-OSS model).
		 * @return this builder
		 * @see #thinkLow()
		 * @see #thinkHigh()
		 */
		public Builder thinkMedium() {
			this.options.thinkOption = ThinkOption.ThinkLevel.MEDIUM;
			return this;
		}

		/**
		 * Set thinking level to "high" (for GPT-OSS model).
		 * @return this builder
		 * @see #thinkLow()
		 * @see #thinkMedium()
		 */
		public Builder thinkHigh() {
			this.options.thinkOption = ThinkOption.ThinkLevel.HIGH;
			return this;
		}

		/**
		 * Set the think option explicitly. Use {@link #enableThinking()},
		 * {@link #disableThinking()}, {@link #thinkLow()}, {@link #thinkMedium()}, or
		 * {@link #thinkHigh()} for more convenient alternatives.
		 * @param thinkOption the think option
		 * @return this builder
		 */
		public Builder thinkOption(@Nullable ThinkOption thinkOption) {
			this.options.setThinkOption(thinkOption);
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

		public Builder outputSchema(String outputSchema) {
			this.options.setOutputSchema(outputSchema);
			return this;
		}

		public OllamaChatOptions build() {
			return this.options;
		}

	}

}
