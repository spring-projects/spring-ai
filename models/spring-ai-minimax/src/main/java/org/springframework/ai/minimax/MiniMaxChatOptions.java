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

package org.springframework.ai.minimax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * MiniMaxChatOptions represents the options for performing chat completion using the
 * MiniMax API. It provides methods to set and retrieve various options like model,
 * frequency penalty, max tokens, etc.
 *
 * @see ChatOptions
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @since 1.0.0 M1
 */
@JsonInclude(Include.NON_NULL)
public class MiniMaxChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 */
	private @JsonProperty("frequency_penalty") Double frequencyPenalty;
	/**
	 * The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * How many chat completion choices to generate for each input message. Note that you will be charged based
	 * on the number of generated tokens across all of the choices. Keep n as 1 to minimize costs.
	 */
	private @JsonProperty("n") Integer n;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 */
	private @JsonProperty("presence_penalty") Double presencePenalty;
	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
	 */
	private @JsonProperty("response_format") MiniMaxApi.ChatCompletionRequest.ResponseFormat responseFormat;
	/**
	 * This feature is in Beta. If specified, our system will make a best effort to sample
	 * deterministically, such that repeated requests with the same seed and parameters should return the same result.
	 * Determinism is not guaranteed, and you should refer to the system_fingerprint response parameter to monitor
	 * changes in the backend.
	 */
	private @JsonProperty("seed") Integer seed;
	/**
	 * Up to 4 sequences where the API will stop generating further tokens.
	 */
	private @JsonProperty("stop") List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic. We generally recommend
	 * altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the model considers the
	 * results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Double topP;
	/**
	 * Mask the text information in the output that is easy to involve privacy issues,
	 * including but not limited to email, domain name, link, ID number, home address, etc.
	 * The default is true, which means enabling masking.
	 */
	private @JsonProperty("mask_sensitive_info") Boolean maskSensitiveInfo;
	/**
	 * A list of tools the model may call. Currently, only functions are supported as a tool. Use this to
	 * provide a list of functions the model may generate JSON inputs for.
	 */
	private @JsonProperty("tools") List<MiniMaxApi.FunctionTool> tools;
	/**
	 * Controls which (if any) function is called by the model. none means the model will not call a
	 * function and instead generates a message. auto means the model can pick between generating a message or calling a
	 * function. Specifying a particular function via {"type: "function", "function": {"name": "my_function"}} forces
	 * the model to call that function. none is the default when no functions are present. auto is the default if
	 * functions are present. Use the {@link MiniMaxApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice object.
	 */
	private @JsonProperty("tool_choice") String toolChoice;

	/**
	 * MiniMax Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the functionCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the functionCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the functionCallbacks registry.
	 * The {@link #toolCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static MiniMaxChatOptions fromOptions(MiniMaxChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.frequencyPenalty(fromOptions.getFrequencyPenalty())
			.maxTokens(fromOptions.getMaxTokens())
			.N(fromOptions.getN())
			.presencePenalty(fromOptions.getPresencePenalty())
			.responseFormat(fromOptions.getResponseFormat())
			.seed(fromOptions.getSeed())
			.stop(fromOptions.getStop() != null ? new ArrayList<>(fromOptions.getStop()) : null)
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.maskSensitiveInfo(fromOptions.getMaskSensitiveInfo())
			.tools(fromOptions.getTools() != null ? new ArrayList<>(fromOptions.getTools()) : null)
			.toolChoice(fromOptions.getToolChoice())
			.toolCallbacks(fromOptions.getToolCallbacks())
			.toolNames(fromOptions.getToolNames())
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
			.toolContext(fromOptions.getToolContext())
			.build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public MiniMaxApi.ChatCompletionRequest.ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(MiniMaxApi.ChatCompletionRequest.ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
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
		return (this.stop != null) ? Collections.unmodifiableList(this.stop) : null;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
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

	public Boolean getMaskSensitiveInfo() {
		return this.maskSensitiveInfo;
	}

	public void setMaskSensitiveInfo(Boolean maskSensitiveInfo) {
		this.maskSensitiveInfo = maskSensitiveInfo;
	}

	public List<MiniMaxApi.FunctionTool> getTools() {
		return (this.tools != null) ? Collections.unmodifiableList(this.tools) : null;
	}

	public void setTools(List<MiniMaxApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public String getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return Collections.unmodifiableList(this.toolCallbacks);
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
		return Collections.unmodifiableSet(this.toolNames);
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
	public Map<String, Object> getToolContext() {
		return (this.toolContext != null) ? Collections.unmodifiableMap(this.toolContext) : null;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.n, this.presencePenalty,
				this.responseFormat, this.seed, this.stop, this.temperature, this.topP, this.maskSensitiveInfo,
				this.tools, this.toolChoice, this.toolCallbacks, this.toolNames, this.toolContext,
				this.internalToolExecutionEnabled);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MiniMaxChatOptions that = (MiniMaxChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens) && Objects.equals(this.n, that.n)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.stop, that.stop) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.maskSensitiveInfo, that.maskSensitiveInfo)
				&& Objects.equals(this.tools, that.tools) && Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames) && Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled);
	}

	@Override
	public MiniMaxChatOptions copy() {
		return fromOptions(this);
	}

	public static class Builder {

		protected MiniMaxChatOptions options;

		public Builder() {
			this.options = new MiniMaxChatOptions();
		}

		public Builder(MiniMaxChatOptions options) {
			this.options = options;
		}

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
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

		public Builder responseFormat(MiniMaxApi.ChatCompletionRequest.ResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.seed = seed;
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

		public Builder maskSensitiveInfo(Boolean maskSensitiveInfo) {
			this.options.maskSensitiveInfo = maskSensitiveInfo;
			return this;
		}

		public Builder tools(List<MiniMaxApi.FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder toolChoice(String toolChoice) {
			this.options.toolChoice = toolChoice;
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

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public MiniMaxChatOptions build() {
			return this.options;
		}

	}

}
