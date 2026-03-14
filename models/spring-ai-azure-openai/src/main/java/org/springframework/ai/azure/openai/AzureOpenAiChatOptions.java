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

package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.ChatCompletionStreamOptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * The configuration information for a chat completions request. Completions support a
 * wide variety of tasks and generate text that continues from or "completes" provided
 * prompt data.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @author Andres da Silva Santos
 */
@JsonInclude(Include.NON_NULL)
public class AzureOpenAiChatOptions implements ToolCallingChatOptions {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatOptions.class);

	// Temporary constructor to maintain compat with ModelOptionUtils
	public AzureOpenAiChatOptions() {
	}

	protected AzureOpenAiChatOptions(@Nullable Integer maxTokens, @Nullable Double temperature, @Nullable Double topP,
			@Nullable Map<String, Integer> logitBias, @Nullable String user, @Nullable Integer n,
			@Nullable List<String> stop, @Nullable Double presencePenalty, @Nullable Double frequencyPenalty,
			@Nullable String deploymentName, @Nullable AzureOpenAiResponseFormat responseFormat, @Nullable Long seed,
			@Nullable Boolean logprobs, @Nullable Integer topLogProbs, @Nullable Integer maxCompletionTokens,
			@Nullable AzureChatEnhancementConfiguration enhancements,
			@Nullable ChatCompletionStreamOptions streamOptions, @Nullable Boolean internalToolExecutionEnabled,
			@Nullable List<ToolCallback> toolCallbacks, @Nullable Set<String> toolNames,
			@Nullable Map<String, Object> toolContext, @Nullable Boolean enableStreamUsage,
			@Nullable String reasoningEffort) {
		this.maxTokens = maxTokens;
		this.temperature = temperature;
		this.topP = topP;
		this.logitBias = logitBias;
		this.user = user;
		this.n = n;
		this.stop = stop;
		this.presencePenalty = presencePenalty;
		this.frequencyPenalty = frequencyPenalty;
		this.deploymentName = deploymentName;
		this.responseFormat = responseFormat;
		this.seed = seed;
		this.logprobs = logprobs;
		this.topLogProbs = topLogProbs;
		this.maxCompletionTokens = maxCompletionTokens;
		this.enhancements = enhancements;
		this.streamOptions = streamOptions;
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		this.toolCallbacks = toolCallbacks != null ? new ArrayList<>(toolCallbacks) : new ArrayList<>();
		this.toolNames = toolNames != null ? new HashSet<>(toolNames) : new HashSet<>();
		this.toolContext = toolContext != null ? new HashMap<>(toolContext) : new HashMap<>();
		this.enableStreamUsage = enableStreamUsage;
		this.reasoningEffort = reasoningEffort;
	}

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 *
	 * <p>
	 * <strong>Model-specific usage:</strong>
	 * </p>
	 * <ul>
	 * <li><strong>Use for non-reasoning models</strong> (e.g., gpt-4o,
	 * gpt-3.5-turbo)</li>
	 * <li><strong>Cannot be used with reasoning models</strong> (e.g., o1, o3, o4-mini
	 * series)</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Mutual exclusivity:</strong> This parameter cannot be used together with
	 * {@link #maxCompletionTokens}. Setting both will result in an API error.
	 * </p>
	 */
	@JsonProperty("max_tokens")
	private Integer maxTokens;

	/**
	 * The sampling temperature to use that controls the apparent creativity of generated
	 * completions. Higher values will make output more random while lower values will
	 * make results more focused and deterministic. It is not recommended to modify
	 * temperature and top_p for the same completions request as the interaction of these
	 * two settings is difficult to predict.
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * An alternative to sampling with temperature called nucleus sampling. This value
	 * causes the model to consider the results of tokens with the provided probability
	 * mass. As an example, a value of 0.15 will cause only the tokens comprising the top
	 * 15% of probability mass to be considered. It is not recommended to modify
	 * temperature and top_p for the same completions request as the interaction of these
	 * two settings is difficult to predict.
	 */
	@JsonProperty("top_p")
	private Double topP;

	/**
	 * A map between GPT token IDs and bias scores that influences the probability of
	 * specific tokens appearing in a completions response. Token IDs are computed via
	 * external tokenizer tools, while bias scores reside in the range of -100 to 100 with
	 * minimum and maximum values corresponding to a full ban or exclusive selection of a
	 * token, respectively. The exact behavior of a given bias score varies by model.
	 */
	@JsonProperty("logit_bias")
	private Map<String, Integer> logitBias;

	/**
	 * An identifier for the caller or end user of the operation. This may be used for
	 * tracking or rate-limiting purposes.
	 */
	@JsonProperty("user")
	private String user;

	/**
	 * The number of chat completions choices that should be generated for a chat
	 * completions response. Because this setting can generate many completions, it may
	 * quickly consume your token quota. Use carefully and ensure reasonable settings for
	 * max_tokens and stop.
	 */
	@JsonProperty("n")
	private Integer n;

	/**
	 * A collection of textual sequences that will end completions generation.
	 */
	@JsonProperty("stop")
	private List<String> stop = new ArrayList<>();

	/**
	 * A value that influences the probability of generated tokens appearing based on
	 * their existing presence in generated text. Positive values will make tokens less
	 * likely to appear when they already exist and increase the model's likelihood to
	 * output new topics.
	 */
	@JsonProperty("presence_penalty")
	private Double presencePenalty;

	/**
	 * A value that influences the probability of generated tokens appearing based on
	 * their cumulative frequency in generated text. Positive values will make tokens less
	 * likely to appear as their frequency increases and decrease the likelihood of the
	 * model repeating the same statements verbatim.
	 */
	@JsonProperty("frequency_penalty")
	private Double frequencyPenalty;

	/**
	 * The deployment name as defined in Azure Open AI Studio when creating a deployment
	 * backed by an Azure OpenAI base model.
	 */
	@JsonProperty("deployment_name")
	private String deploymentName;

	/**
	 * The response format expected from the Azure OpenAI model
	 * @see org.springframework.ai.azure.openai.AzureOpenAiResponseFormat for supported
	 * formats
	 */
	@JsonProperty("response_format")
	private AzureOpenAiResponseFormat responseFormat;

	/**
	 * Seed value for deterministic sampling such that the same seed and parameters return
	 * the same result.
	 */
	@JsonProperty("seed")
	private Long seed;

	/**
	 * Whether to return log probabilities of the output tokens or not. If true, returns
	 * the log probabilities of each output token returned in the `content` of `message`.
	 * This option is currently not available on the `gpt-4-vision-preview` model.
	 */
	@JsonProperty("log_probs")
	private Boolean logprobs;

	/*
	 * An integer between 0 and 5 specifying the number of most likely tokens to return at
	 * each token position, each with an associated log probability. `logprobs` must be
	 * set to `true` if this parameter is used.
	 */
	@JsonProperty("top_log_probs")
	private Integer topLogProbs;

	/**
	 * An upper bound for the number of tokens that can be generated for a completion,
	 * including visible output tokens and reasoning tokens.
	 *
	 * <p>
	 * <strong>Model-specific usage:</strong>
	 * </p>
	 * <ul>
	 * <li><strong>Required for reasoning models</strong> (e.g., o1, o3, o4-mini
	 * series)</li>
	 * <li><strong>Cannot be used with non-reasoning models</strong> (e.g., gpt-4o,
	 * gpt-3.5-turbo)</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Mutual exclusivity:</strong> This parameter cannot be used together with
	 * {@link #maxTokens}. Setting both will result in an API error.
	 * </p>
	 */
	@JsonProperty("max_completion_tokens")
	private Integer maxCompletionTokens;

	/*
	 * If provided, the configuration options for available Azure OpenAI chat
	 * enhancements.
	 */
	@JsonIgnore
	private AzureChatEnhancementConfiguration enhancements;

	@JsonProperty("stream_options")
	private ChatCompletionStreamOptions streamOptions;

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	/**
	 * Whether to include token usage information in streaming chat completion responses.
	 * Only applies to streaming responses.
	 */
	@JsonIgnore
	private Boolean enableStreamUsage;

	/**
	 * Constrains effort on reasoning for reasoning models. Currently supported values are
	 * low, medium, and high. Reducing reasoning effort can result in faster responses and
	 * fewer tokens used on reasoning in a response. Optional. Defaults to medium. Only
	 * for reasoning models.
	 */
	@JsonProperty("reasoning_effort")
	private String reasoningEffort;

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

	public static Builder<?> builder() {
		return new Builder<>();
	}

	public static AzureOpenAiChatOptions fromOptions(AzureOpenAiChatOptions fromOptions) {
		return fromOptions.mutate().build();
	}

	@Override
	public AzureOpenAiChatOptions.Builder<?> mutate() {
		return AzureOpenAiChatOptions.builder()
			// ChatOptions
			.deploymentName(getDeploymentName())// alias for model in azure
			.frequencyPenalty(getFrequencyPenalty())
			.maxTokens(getMaxTokens())
			.presencePenalty(getPresencePenalty())
			.stop(this.getStop() == null ? null : new ArrayList<>(this.getStop()))
			.temperature(getTemperature())
			.topP(getTopP())
			// ToolCallingChatOptions
			.toolCallbacks(new ArrayList<>(getToolCallbacks()))
			.toolNames(new HashSet<>(getToolNames()))
			.toolContext(new HashMap<>(getToolContext()))
			.internalToolExecutionEnabled(getInternalToolExecutionEnabled())
			// Azure Specific
			.logitBias(getLogitBias())
			.maxCompletionTokens(getMaxCompletionTokens())
			.N(getN())
			.user(getUser())
			.responseFormat(getResponseFormat())
			.streamUsage(getStreamUsage())
			.reasoningEffort(getReasoningEffort())
			.seed(getSeed())
			.logprobs(isLogprobs())
			.topLogprobs(getTopLogProbs())
			.enhancements(getEnhancements())
			.streamOptions(getStreamOptions());
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}

	public void setMaxCompletionTokens(Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}

	public Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	public void setLogitBias(Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
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

	@Override
	@JsonIgnore
	public String getModel() {
		return getDeploymentName();
	}

	@JsonIgnore
	public void setModel(String model) {
		setDeploymentName(model);
	}

	public String getDeploymentName() {
		return this.deploymentName;
	}

	public void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public void setFunctions(Set<String> functions) {
		this.setToolNames(functions);
	}

	public AzureOpenAiResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(AzureOpenAiResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Boolean getStreamUsage() {
		return this.enableStreamUsage;
	}

	public void setStreamUsage(Boolean enableStreamUsage) {
		this.enableStreamUsage = enableStreamUsage;
	}

	public String getReasoningEffort() {
		return this.reasoningEffort;
	}

	public void setReasoningEffort(String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	public Long getSeed() {
		return this.seed;
	}

	public void setSeed(Long seed) {
		this.seed = seed;
	}

	public Boolean isLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public Integer getTopLogProbs() {
		return this.topLogProbs;
	}

	public void setTopLogProbs(Integer topLogProbs) {
		this.topLogProbs = topLogProbs;
	}

	public AzureChatEnhancementConfiguration getEnhancements() {
		return this.enhancements;
	}

	public void setEnhancements(AzureChatEnhancementConfiguration enhancements) {
		this.enhancements = enhancements;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	public ChatCompletionStreamOptions getStreamOptions() {
		return this.streamOptions;
	}

	public void setStreamOptions(ChatCompletionStreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}

	@Override
	public AzureOpenAiChatOptions copy() {
		return mutate().build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AzureOpenAiChatOptions that)) {
			return false;
		}
		return Objects.equals(this.logitBias, that.logitBias) && Objects.equals(this.user, that.user)
				&& Objects.equals(this.n, that.n) && Objects.equals(this.stop, that.stop)
				&& Objects.equals(this.deploymentName, that.deploymentName)
				&& Objects.equals(this.responseFormat, that.responseFormat)

				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.logprobs, that.logprobs) && Objects.equals(this.topLogProbs, that.topLogProbs)
				&& Objects.equals(this.enhancements, that.enhancements)
				&& Objects.equals(this.streamOptions, that.streamOptions)
				&& Objects.equals(this.enableStreamUsage, that.enableStreamUsage)
				&& Objects.equals(this.reasoningEffort, that.reasoningEffort)
				&& Objects.equals(this.toolContext, that.toolContext) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.maxCompletionTokens, that.maxCompletionTokens)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.logitBias, this.user, this.n, this.stop, this.deploymentName, this.responseFormat,
				this.toolCallbacks, this.toolNames, this.internalToolExecutionEnabled, this.seed, this.logprobs,
				this.topLogProbs, this.enhancements, this.streamOptions, this.reasoningEffort, this.enableStreamUsage,
				this.toolContext, this.maxTokens, this.maxCompletionTokens, this.frequencyPenalty, this.presencePenalty,
				this.temperature, this.topP);
	}

	public static class Builder<B extends Builder<B>> extends DefaultToolCallingChatOptions.Builder<B> {

		protected @Nullable Map<String, Integer> logitBias;

		protected @Nullable String user;

		protected @Nullable Integer n;

		protected @Nullable AzureOpenAiResponseFormat responseFormat;

		protected @Nullable Long seed;

		protected @Nullable Boolean logprobs;

		protected @Nullable Integer topLogProbs;

		protected @Nullable Integer maxCompletionTokens;

		protected @Nullable AzureChatEnhancementConfiguration enhancements;

		protected @Nullable ChatCompletionStreamOptions streamOptions;

		protected @Nullable Boolean enableStreamUsage;

		protected @Nullable String reasoningEffort;

		public Builder() {
		}

		public B deploymentName(@Nullable String deploymentName) {
			return this.model(deploymentName);
		}

		public B logitBias(@Nullable Map<String, Integer> logitBias) {
			this.logitBias = logitBias;
			return self();
		}

		/**
		 * Sets the maximum number of tokens to generate in the chat completion. The total
		 * length of input tokens and generated tokens is limited by the model's context
		 * length.
		 *
		 * <p>
		 * <strong>Model-specific usage:</strong>
		 * </p>
		 * <ul>
		 * <li><strong>Use for non-reasoning models</strong> (e.g., gpt-4o,
		 * gpt-3.5-turbo)</li>
		 * <li><strong>Cannot be used with reasoning models</strong> (e.g., o1, o3,
		 * o4-mini series)</li>
		 * </ul>
		 *
		 * <p>
		 * <strong>Mutual exclusivity:</strong> This parameter cannot be used together
		 * with {@link #maxCompletionTokens(Integer)}. If both are set, the last one set
		 * will be used and the other will be cleared with a warning.
		 * </p>
		 * @param maxTokens the maximum number of tokens to generate, or null to unset
		 * @return this builder instance
		 */
		@Override
		public B maxTokens(@Nullable Integer maxTokens) {
			if (maxTokens != null && this.maxCompletionTokens != null) {
				logger
					.warn("Both maxTokens and maxCompletionTokens are set. Azure OpenAI API does not support setting both parameters simultaneously. "
							+ "The previously set maxCompletionTokens ({}) will be cleared and maxTokens ({}) will be used.",
							this.maxCompletionTokens, maxTokens);
				this.maxCompletionTokens = null;
			}
			super.maxTokens(maxTokens);
			return self();
		}

		/**
		 * Sets an upper bound for the number of tokens that can be generated for a
		 * completion, including visible output tokens and reasoning tokens.
		 *
		 * <p>
		 * <strong>Model-specific usage:</strong>
		 * </p>
		 * <ul>
		 * <li><strong>Required for reasoning models</strong> (e.g., o1, o3, o4-mini
		 * series)</li>
		 * <li><strong>Cannot be used with non-reasoning models</strong> (e.g., gpt-4o,
		 * gpt-3.5-turbo)</li>
		 * </ul>
		 *
		 * <p>
		 * <strong>Mutual exclusivity:</strong> This parameter cannot be used together
		 * with {@link #maxTokens(Integer)}. If both are set, the last one set will be
		 * used and the other will be cleared with a warning.
		 * </p>
		 * @param maxCompletionTokens the maximum number of completion tokens to generate,
		 * or null to unset
		 * @return this builder instance
		 */
		public B maxCompletionTokens(@Nullable Integer maxCompletionTokens) {
			if (maxCompletionTokens != null && this.maxTokens != null) {
				logger
					.warn("Both maxTokens and maxCompletionTokens are set. Azure OpenAI API does not support setting both parameters simultaneously. "
							+ "The previously set maxTokens ({}) will be cleared and maxCompletionTokens ({}) will be used.",
							this.maxTokens, maxCompletionTokens);
				super.maxTokens(null);
			}
			this.maxCompletionTokens = maxCompletionTokens;
			return self();
		}

		public B N(@Nullable Integer n) {
			this.n = n;
			return self();
		}

		public B stop(@Nullable List<String> stop) {
			super.stopSequences(stop);
			return self();
		}

		public B user(@Nullable String user) {
			this.user = user;
			return self();
		}

		public B responseFormat(@Nullable AzureOpenAiResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B streamUsage(@Nullable Boolean enableStreamUsage) {
			this.enableStreamUsage = enableStreamUsage;
			return self();
		}

		public B reasoningEffort(@Nullable String reasoningEffort) {
			this.reasoningEffort = reasoningEffort;
			return self();
		}

		public B seed(@Nullable Long seed) {
			this.seed = seed;
			return self();
		}

		public B logprobs(@Nullable Boolean logprobs) {
			this.logprobs = logprobs;
			return self();
		}

		public B topLogprobs(@Nullable Integer topLogprobs) {
			this.topLogProbs = topLogprobs;
			return self();
		}

		public B enhancements(@Nullable AzureChatEnhancementConfiguration enhancements) {
			this.enhancements = enhancements;
			return self();
		}

		public B streamOptions(@Nullable ChatCompletionStreamOptions streamOptions) {
			this.streamOptions = streamOptions;
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof Builder<?> that) {
				if (that.logitBias != null) {
					this.logitBias = that.logitBias;
				}
				if (that.user != null) {
					this.user = that.user;
				}
				if (that.n != null) {
					this.n = that.n;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.seed != null) {
					this.seed = that.seed;
				}
				if (that.logprobs != null) {
					this.logprobs = that.logprobs;
				}
				if (that.topLogProbs != null) {
					this.topLogProbs = that.topLogProbs;
				}
				if (that.maxCompletionTokens != null) {
					this.maxCompletionTokens = that.maxCompletionTokens;
				}
				if (that.enhancements != null) {
					this.enhancements = that.enhancements;
				}
				if (that.streamOptions != null) {
					this.streamOptions = that.streamOptions;
				}
				if (that.enableStreamUsage != null) {
					this.enableStreamUsage = that.enableStreamUsage;
				}
				if (that.reasoningEffort != null) {
					this.reasoningEffort = that.reasoningEffort;
				}
			}
			return self();
		}

		@Override
		public AzureOpenAiChatOptions build() {
			return new AzureOpenAiChatOptions(this.maxTokens, this.temperature, this.topP, this.logitBias, this.user,
					this.n, this.stopSequences, this.presencePenalty, this.frequencyPenalty, this.model,
					this.responseFormat, this.seed, this.logprobs, this.topLogProbs, this.maxCompletionTokens,
					this.enhancements, this.streamOptions, this.internalToolExecutionEnabled,
					new ArrayList<>(this.toolCallbacks), new HashSet<>(this.toolNames), new HashMap<>(this.toolContext),
					this.enableStreamUsage, this.reasoningEffort);
		}

	}

}
