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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;

/**
 * MiniMaxChatOptions represents the options for performing chat completion using the
 * MiniMax API. It provides methods to set and retrieve various options like model,
 * frequency penalty, max tokens, etc.
 *
 * @see FunctionCallingOptions
 * @see ChatOptions
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0.0 M1
 */
@JsonInclude(Include.NON_NULL)
public class MiniMaxChatOptions implements FunctionCallingOptions {

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
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the functionCallbacks registry.
	 * The {@link #functionCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	@JsonIgnore
	private Boolean proxyToolCalls;

	@JsonIgnore
	private Map<String, Object> toolContext;

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static MiniMaxChatOptions fromOptions(MiniMaxChatOptions fromOptions) {
		return builder().withModel(fromOptions.getModel())
			.withFrequencyPenalty(fromOptions.getFrequencyPenalty())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withN(fromOptions.getN())
			.withPresencePenalty(fromOptions.getPresencePenalty())
			.withResponseFormat(fromOptions.getResponseFormat())
			.withSeed(fromOptions.getSeed())
			.withStop(fromOptions.getStop())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withMaskSensitiveInfo(fromOptions.getMaskSensitiveInfo())
			.withTools(fromOptions.getTools())
			.withToolChoice(fromOptions.getToolChoice())
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
			.withFunctions(fromOptions.getFunctions())
			.withProxyToolCalls(fromOptions.getProxyToolCalls())
			.withToolContext(fromOptions.getToolContext())
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
		return this.stop;
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
		return this.tools;
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

	public void setFunctions(Set<String> functionNames) {
		this.functions = functionNames;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.frequencyPenalty == null) ? 0 : this.frequencyPenalty.hashCode());
		result = prime * result + ((this.maxTokens == null) ? 0 : this.maxTokens.hashCode());
		result = prime * result + ((this.n == null) ? 0 : this.n.hashCode());
		result = prime * result + ((this.presencePenalty == null) ? 0 : this.presencePenalty.hashCode());
		result = prime * result + ((this.responseFormat == null) ? 0 : this.responseFormat.hashCode());
		result = prime * result + ((this.seed == null) ? 0 : this.seed.hashCode());
		result = prime * result + ((this.stop == null) ? 0 : this.stop.hashCode());
		result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
		result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
		result = prime * result + ((this.maskSensitiveInfo == null) ? 0 : this.maskSensitiveInfo.hashCode());
		result = prime * result + ((this.tools == null) ? 0 : this.tools.hashCode());
		result = prime * result + ((this.toolChoice == null) ? 0 : this.toolChoice.hashCode());
		result = prime * result + ((this.proxyToolCalls == null) ? 0 : this.proxyToolCalls.hashCode());
		result = prime * result + ((this.toolContext == null) ? 0 : this.toolContext.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		MiniMaxChatOptions other = (MiniMaxChatOptions) obj;
		if (this.model == null) {
			if (other.model != null) {
				return false;
			}
		}
		else if (!this.model.equals(other.model)) {
			return false;
		}
		if (this.frequencyPenalty == null) {
			if (other.frequencyPenalty != null) {
				return false;
			}
		}
		else if (!this.frequencyPenalty.equals(other.frequencyPenalty)) {
			return false;
		}
		if (this.maxTokens == null) {
			if (other.maxTokens != null) {
				return false;
			}
		}
		else if (!this.maxTokens.equals(other.maxTokens)) {
			return false;
		}
		if (this.n == null) {
			if (other.n != null) {
				return false;
			}
		}
		else if (!this.n.equals(other.n)) {
			return false;
		}
		if (this.presencePenalty == null) {
			if (other.presencePenalty != null) {
				return false;
			}
		}
		else if (!this.presencePenalty.equals(other.presencePenalty)) {
			return false;
		}
		if (this.responseFormat == null) {
			if (other.responseFormat != null) {
				return false;
			}
		}
		else if (!this.responseFormat.equals(other.responseFormat)) {
			return false;
		}
		if (this.seed == null) {
			if (other.seed != null) {
				return false;
			}
		}
		else if (!this.seed.equals(other.seed)) {
			return false;
		}
		if (this.stop == null) {
			if (other.stop != null) {
				return false;
			}
		}
		else if (!this.stop.equals(other.stop)) {
			return false;
		}
		if (this.temperature == null) {
			if (other.temperature != null) {
				return false;
			}
		}
		else if (!this.temperature.equals(other.temperature)) {
			return false;
		}
		if (this.topP == null) {
			if (other.topP != null) {
				return false;
			}
		}
		else if (!this.topP.equals(other.topP)) {
			return false;
		}
		if (this.maskSensitiveInfo == null) {
			if (other.maskSensitiveInfo != null) {
				return false;
			}
		}
		else if (!this.maskSensitiveInfo.equals(other.maskSensitiveInfo)) {
			return false;
		}
		if (this.tools == null) {
			if (other.tools != null) {
				return false;
			}
		}
		else if (!this.tools.equals(other.tools)) {
			return false;
		}
		if (this.toolChoice == null) {
			if (other.toolChoice != null) {
				return false;
			}
		}
		else if (!this.toolChoice.equals(other.toolChoice)) {
			return false;
		}
		if (this.proxyToolCalls == null) {
			if (other.proxyToolCalls != null) {
				return false;
			}
		}
		else if (!this.proxyToolCalls.equals(other.proxyToolCalls)) {
			return false;
		}

		if (this.toolContext == null) {
			if (other.toolContext != null) {
				return false;
			}
		}
		else if (!this.toolContext.equals(other.toolContext)) {
			return false;
		}

		return true;
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

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withFrequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder withN(Integer n) {
			this.options.n = n;
			return this;
		}

		public Builder withPresencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder withResponseFormat(MiniMaxApi.ChatCompletionRequest.ResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder withSeed(Integer seed) {
			this.options.seed = seed;
			return this;
		}

		public Builder withStop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder withTemperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withMaskSensitiveInfo(Boolean maskSensitiveInfo) {
			this.options.maskSensitiveInfo = maskSensitiveInfo;
			return this;
		}

		public Builder withTools(List<MiniMaxApi.FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder withToolChoice(String toolChoice) {
			this.options.toolChoice = toolChoice;
			return this;
		}

		public Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.options.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder withFunctions(Set<String> functionNames) {
			Assert.notNull(functionNames, "Function names must not be null");
			this.options.functions = functionNames;
			return this;
		}

		public Builder withFunction(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			this.options.functions.add(functionName);
			return this;
		}

		public Builder withProxyToolCalls(Boolean proxyToolCalls) {
			this.options.proxyToolCalls = proxyToolCalls;
			return this;
		}

		public Builder withToolContext(Map<String, Object> toolContext) {
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
