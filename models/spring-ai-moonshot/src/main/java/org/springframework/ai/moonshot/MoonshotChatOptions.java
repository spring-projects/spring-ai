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
package org.springframework.ai.moonshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Geng Rong
 * @author Thomas Vitale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoonshotChatOptions implements ChatOptions {

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("model") String model;

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the
	 * model considers the results of the tokens with top_p probability mass. So 0.1 means
	 * only the tokens comprising the top 10% probability mass are considered. We
	 * generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * How many chat completion choices to generate for each input message. Note that you
	 * will be charged based on the number of generated tokens across all the choices.
	 * Keep n as 1 to minimize costs.
	 */
	private @JsonProperty("n") Integer n;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
	 * they appear in the text so far, increasing the model's likelihood to talk about new
	 * topics.
	 */
	private @JsonProperty("presence_penalty") Float presencePenalty;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
	 * existing frequency in the text so far, decreasing the model's likelihood to repeat
	 * the same line verbatim.
	 */
	private @JsonProperty("frequency_penalty") Float frequencyPenalty;

	/**
	 * Up to 5 sequences where the API will stop generating further tokens.
	 */
	@NestedConfigurationProperty
	private @JsonProperty("stop") List<String> stop;

	@NestedConfigurationProperty
	private @JsonProperty("tools") List<MoonshotApi.FunctionTool> tools;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function. Specifying a particular
	 * function via {"type: "function", "function": {"name": "my_function"}} forces the
	 * model to call that function. none is the default when no functions are present.
	 * auto is the default if functions are present. Use the
	 * {@link MoonshotApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice
	 * object.
	 */
	private @JsonProperty("tool_choice") String toolChoice;

	/**
	 * Moonshot Tool Function Callbacks to register with the ChatModel. For Prompt Options
	 * the functionCallbacks are automatically enabled for the duration of the prompt
	 * execution. For Default Options the functionCallbacks are registered but disabled by
	 * default. Use the enableFunctions to set the functions from the registry to be used
	 * by the ChatModel chat completion requests.
	 */
	@NestedConfigurationProperty
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests. Functions with those names must exist in the
	 * functionCallbacks registry. The {@link #functionCallbacks} from the PromptOptions
	 * are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat
	 * completion requests. This could impact the token count and the billing. If the
	 * functions is set in a prompt options, then the enabled functions are only active
	 * for the duration of this prompt execution.
	 */
	@NestedConfigurationProperty
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	/**
	 * A unique identifier representing your end-user, which can help Moonshot to monitor
	 * and detect abuse.
	 */
	private @JsonProperty("user") String user;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected MoonshotChatOptions options;

		public Builder() {
			this.options = new MoonshotChatOptions();
		}

		public Builder(MoonshotChatOptions options) {
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

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withN(Integer n) {
			this.options.n = n;
			return this;
		}

		public Builder withPresencePenalty(Float presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder withFrequencyPenalty(Float frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder withStop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder withUser(String user) {
			this.options.user = user;
			return this;
		}

		public Builder withTools(List<MoonshotApi.FunctionTool> tools) {
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

		public MoonshotChatOptions build() {
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
	public Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Float frequencyPenalty) {
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
	public Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
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

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	public MoonshotChatOptions copy() {
		return builder().withModel(this.model)
			.withMaxTokens(this.maxTokens)
			.withTemperature(this.temperature)
			.withTopP(this.topP)
			.withN(this.n)
			.withPresencePenalty(this.presencePenalty)
			.withFrequencyPenalty(this.frequencyPenalty)
			.withStop(this.stop)
			.withUser(this.user)
			.withTools(this.tools)
			.withToolChoice(this.toolChoice)
			.withFunctionCallbacks(this.functionCallbacks)
			.withFunctions(this.functions)
			.build();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((frequencyPenalty == null) ? 0 : frequencyPenalty.hashCode());
		result = prime * result + ((maxTokens == null) ? 0 : maxTokens.hashCode());
		result = prime * result + ((n == null) ? 0 : n.hashCode());
		result = prime * result + ((presencePenalty == null) ? 0 : presencePenalty.hashCode());
		result = prime * result + ((stop == null) ? 0 : stop.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
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
		MoonshotChatOptions other = (MoonshotChatOptions) obj;
		if (this.model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (this.frequencyPenalty == null) {
			if (other.frequencyPenalty != null)
				return false;
		}
		else if (!this.frequencyPenalty.equals(other.frequencyPenalty))
			return false;
		if (this.maxTokens == null) {
			if (other.maxTokens != null)
				return false;
		}
		else if (!this.maxTokens.equals(other.maxTokens))
			return false;
		if (this.n == null) {
			if (other.n != null)
				return false;
		}
		else if (!this.n.equals(other.n))
			return false;
		if (this.presencePenalty == null) {
			if (other.presencePenalty != null)
				return false;
		}
		else if (!this.presencePenalty.equals(other.presencePenalty))
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
		if (this.user == null) {
			return other.user == null;
		}
		else if (!this.user.equals(other.user))
			return false;
		return true;
	}

}
