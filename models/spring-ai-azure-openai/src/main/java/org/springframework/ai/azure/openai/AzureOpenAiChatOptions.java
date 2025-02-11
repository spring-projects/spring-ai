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

package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.ChatCompletionStreamOptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
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
 */
@JsonInclude(Include.NON_NULL)
public class AzureOpenAiChatOptions implements ToolCallingChatOptions {

	/**
	 * The maximum number of tokens to generate.
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
	private List<String> stop;

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
	private List<FunctionCallback> toolCallbacks = new ArrayList<>();

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

	@Override
	@JsonIgnore
	public List<FunctionCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<FunctionCallback> toolCallbacks) {
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
	public Boolean isInternalToolExecutionEnabled() {
		return internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AzureOpenAiChatOptions fromOptions(AzureOpenAiChatOptions fromOptions) {
		return builder().deploymentName(fromOptions.getDeploymentName())
			.frequencyPenalty(fromOptions.getFrequencyPenalty() != null ? fromOptions.getFrequencyPenalty() : null)
			.logitBias(fromOptions.getLogitBias())
			.maxTokens(fromOptions.getMaxTokens())
			.N(fromOptions.getN())
			.presencePenalty(fromOptions.getPresencePenalty() != null ? fromOptions.getPresencePenalty() : null)
			.stop(fromOptions.getStop())
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.user(fromOptions.getUser())
			.functionCallbacks(fromOptions.getFunctionCallbacks())
			.functions(fromOptions.getFunctions())
			.responseFormat(fromOptions.getResponseFormat())
			.seed(fromOptions.getSeed())
			.logprobs(fromOptions.isLogprobs())
			.topLogprobs(fromOptions.getTopLogProbs())
			.enhancements(fromOptions.getEnhancements())
			.toolContext(fromOptions.getToolContext())
			.internalToolExecutionEnabled(fromOptions.isInternalToolExecutionEnabled())
			.streamOptions(fromOptions.getStreamOptions())
			.toolCallbacks(fromOptions.getToolCallbacks())
			.toolNames(fromOptions.getToolNames())
			.build();
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
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

	@Override
	@Deprecated
	@JsonIgnore
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.getToolCallbacks();
	}

	@Override
	@Deprecated
	@JsonIgnore
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.setToolCallbacks(functionCallbacks);
	}

	@Override
	@Deprecated
	@JsonIgnore
	public Set<String> getFunctions() {
		return this.getToolNames();
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
	@Deprecated
	@JsonIgnore
	public Boolean getProxyToolCalls() {
		return this.internalToolExecutionEnabled != null ? !this.internalToolExecutionEnabled : null;
	}

	@Deprecated
	@JsonIgnore
	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.internalToolExecutionEnabled = proxyToolCalls != null ? !proxyToolCalls : null;
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
		return fromOptions(this);
	}

	public static class Builder {

		protected AzureOpenAiChatOptions options;

		public Builder() {
			this.options = new AzureOpenAiChatOptions();
		}

		public Builder(AzureOpenAiChatOptions options) {
			this.options = options;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.deploymentName = deploymentName;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder logitBias(Map<String, Integer> logitBias) {
			this.options.logitBias = logitBias;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder N(Integer n) {
			this.options.n = n;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder user(String user) {
			this.options.user = user;
			return this;
		}

		@Deprecated
		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			return toolCallbacks(functionCallbacks);
		}

		@Deprecated
		public Builder functions(Set<String> functionNames) {
			return toolNames(functionNames);
		}

		@Deprecated
		public Builder function(String functionName) {
			return toolNames(functionName);
		}

		public Builder responseFormat(AzureOpenAiResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		@Deprecated
		public Builder proxyToolCalls(Boolean proxyToolCalls) {
			if (proxyToolCalls != null) {
				this.options.setInternalToolExecutionEnabled(!proxyToolCalls);
			}
			return this;
		}

		public Builder seed(Long seed) {
			this.options.seed = seed;
			return this;
		}

		public Builder logprobs(Boolean logprobs) {
			this.options.logprobs = logprobs;
			return this;
		}

		public Builder topLogprobs(Integer topLogprobs) {
			this.options.topLogProbs = topLogprobs;
			return this;
		}

		public Builder enhancements(AzureChatEnhancementConfiguration enhancements) {
			this.options.enhancements = enhancements;
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

		public Builder streamOptions(ChatCompletionStreamOptions streamOptions) {
			this.options.streamOptions = streamOptions;
			return this;
		}

		public Builder toolCallbacks(List<FunctionCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(FunctionCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
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

		public AzureOpenAiChatOptions build() {
			return this.options;
		}

	}

}
