/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.zhipuai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.FunctionTool;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ZhiPuAiChatOptions represents the options for the ZhiPuAiChat model.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0.0 M1
 */
@JsonInclude(Include.NON_NULL)
public class ZhiPuAiChatOptions implements FunctionCallingOptions, ChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	/**
	 * The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * The model will stop generating characters specified by stop, and currently only supports a single stop word in the format of ["stop_word1"].
	 */
	@NestedConfigurationProperty
	private @JsonProperty("stop") List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic. We generally recommend
	 * altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Float temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the model considers the
	 * results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Float topP;
	/**
	 * A list of tools the model may call. Currently, only functions are supported as a tool. Use this to
	 * provide a list of functions the model may generate JSON inputs for.
	 */
	@NestedConfigurationProperty
	private @JsonProperty("tools") List<FunctionTool> tools;
	/**
	 * Controls which (if any) function is called by the model. none means the model will not call a
	 * function and instead generates a message. auto means the model can pick between generating a message or calling a
	 * function. Specifying a particular function via {"type: "function", "function": {"name": "my_function"}} forces
	 * the model to call that function. none is the default when no functions are present. auto is the default if
	 * functions are present. Use the {@link ZhiPuAiApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice object.
	 */
	private @JsonProperty("tool_choice") String toolChoice;
	/**
	 * A unique identifier representing your end-user, which can help ZhiPuAI to monitor and detect abuse.
	 * ID length requirement: minimum of 6 characters, maximum of 128 characters.
	 */
	private @JsonProperty("user_id") String user;
	/**
	 * The parameter is passed by the client and must ensure uniqueness.
	 * It is used to distinguish the unique identifier for each request.
	 * If the client does not provide it, the platform will generate it by default.
	 */
	private @JsonProperty("request_id") String requestId;
	/**
	 * When do_sample is set to true, the sampling strategy is enabled.
	 * If do_sample is false, the sampling strategy parameters temperature and top_p will not take effect.
	 * The default value is true.
	 */
	private @JsonProperty("do_sample") Boolean doSample;

	/**
	 * ZhiPuAI Tool Function Callbacks to register with the ChatModel.
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
	 *
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@NestedConfigurationProperty
	@JsonIgnore
	private Set<String> functions = new HashSet<>();
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected ZhiPuAiChatOptions options;

		public Builder() {
			this.options = new ZhiPuAiChatOptions();
		}

		public Builder(ZhiPuAiChatOptions options) {
			this.options = options;
		}

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder withStop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withTools(List<FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder withToolChoice(String toolChoice) {
			this.options.toolChoice = toolChoice;
			return this;
		}

		public Builder withUser(String user) {
			this.options.user = user;
			return this;
		}

		public Builder withRequestId(String requestId) {
			this.options.requestId = requestId;
			return this;
		}

		public Builder withDoSample(Boolean doSample) {
			this.options.doSample = doSample;
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

		public ZhiPuAiChatOptions build() {
			return this.options;
		}

	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
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
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public List<FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(List<FunctionTool> tools) {
		this.tools = tools;
	}

	public String getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public Boolean getDoSample() {
		return doSample;
	}

	public void setDoSample(Boolean doSample) {
		this.doSample = doSample;
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
		return functions;
	}

	public void setFunctions(Set<String> functionNames) {
		this.functions = functionNames;
	}

	@Override
	@JsonIgnore
	public Float getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Float getPresencePenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((maxTokens == null) ? 0 : maxTokens.hashCode());
		result = prime * result + ((stop == null) ? 0 : stop.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
		result = prime * result + ((tools == null) ? 0 : tools.hashCode());
		result = prime * result + ((toolChoice == null) ? 0 : toolChoice.hashCode());
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ZhiPuAiChatOptions other = (ZhiPuAiChatOptions) obj;
		if (this.model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (this.maxTokens == null) {
			if (other.maxTokens != null)
				return false;
		}
		else if (!this.maxTokens.equals(other.maxTokens))
			return false;
		if (this.stop == null) {
			if (other.stop != null)
				return false;
		}
		else if (!stop.equals(other.stop))
			return false;
		if (this.temperature == null) {
			if (other.temperature != null)
				return false;
		}
		else if (!this.temperature.equals(other.temperature))
			return false;
		if (this.topP == null) {
			if (other.topP != null)
				return false;
		}
		else if (!topP.equals(other.topP))
			return false;
		if (this.tools == null) {
			if (other.tools != null)
				return false;
		}
		else if (!tools.equals(other.tools))
			return false;
		if (this.toolChoice == null) {
			if (other.toolChoice != null)
				return false;
		}
		else if (!toolChoice.equals(other.toolChoice))
			return false;
		if (this.user == null) {
			if (other.user != null)
				return false;
		}
		else if (!this.user.equals(other.user))
			return false;
		if (this.requestId == null) {
			if (other.requestId != null)
				return false;
		}
		else if (!this.requestId.equals(other.requestId))
			return false;
		if (this.doSample == null) {
			if (other.doSample != null)
				return false;
		}
		else if (!this.doSample.equals(other.doSample))
			return false;
		return true;
	}

	@Override
	public ZhiPuAiChatOptions copy() {
		return fromOptions(this);
	}

	public static ZhiPuAiChatOptions fromOptions(ZhiPuAiChatOptions fromOptions) {
		return ZhiPuAiChatOptions.builder()
			.withModel(fromOptions.getModel())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withStop(fromOptions.getStop())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTools(fromOptions.getTools())
			.withToolChoice(fromOptions.getToolChoice())
			.withUser(fromOptions.getUser())
			.withRequestId(fromOptions.getRequestId())
			.withDoSample(fromOptions.getDoSample())
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
			.withFunctions(fromOptions.getFunctions())
			.build();
	}

}
