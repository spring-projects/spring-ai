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
package org.springframework.ai.vertexai.gemini;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.ChatModel;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.1
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiGeminiChatOptions implements FunctionCallingOptions, ChatOptions {

	// https://cloud.google.com/vertex-ai/docs/reference/rest/v1/GenerationConfig

	public enum TransportType {

		GRPC, REST

	}

	// @formatter:off
	/**
	 * Optional. Stop sequences.
	 */
	private @JsonProperty("stopSequences") List<String> stopSequences;
	/**
	 * Optional. Controls the randomness of predictions.
	 */
	private @JsonProperty("temperature") Float temperature;
	/**
	 * Optional. If specified, nucleus sampling will be used.
	 */
	private @JsonProperty("topP") Float topP;
	/**
	 * Optional. If specified, top k sampling will be used.
	 */
	private @JsonProperty("topK") Float topK;
	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private @JsonProperty("candidateCount") Integer candidateCount;
	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private @JsonProperty("maxOutputTokens") Integer maxOutputTokens;
	/**
	 * Gemini model name.
	 */
	private @JsonProperty("modelName") String model;

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

		private VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder withTopK(Float topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder withCandidateCount(Integer candidateCount) {
			this.options.setCandidateCount(candidateCount);
			return this;
		}

		public Builder withMaxOutputTokens(Integer maxOutputTokens) {
			this.options.setMaxOutputTokens(maxOutputTokens);
			return this;
		}

		public Builder withModel(String modelName) {
			this.options.setModel(modelName);
			return this;
		}

		public Builder withModel(ChatModel model) {
			this.options.setModel(model.getValue());
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

		public VertexAiGeminiChatOptions build() {
			return this.options;
		}

	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
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

	@Override
	public Integer getTopK() {
		return (this.topK != null) ? this.topK.intValue() : null;
	}

	public void setTopK(Float topK) {
		this.topK = topK;
	}

	@JsonIgnore
	public void setTopK(Integer topK) {
		this.topK = (topK != null) ? topK.floatValue() : null;
	}

	public Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	@Override
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxOutputTokens();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxOutputTokens(maxTokens);
	}

	public Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String modelName) {
		this.model = modelName;
	}

	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;
	}

	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
	}

	public Set<String> getFunctions() {
		return this.functions;
	}

	public void setFunctions(Set<String> functions) {
		this.functions = functions;
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stopSequences == null) ? 0 : stopSequences.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
		result = prime * result + ((topK == null) ? 0 : topK.hashCode());
		result = prime * result + ((candidateCount == null) ? 0 : candidateCount.hashCode());
		result = prime * result + ((maxOutputTokens == null) ? 0 : maxOutputTokens.hashCode());
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((functionCallbacks == null) ? 0 : functionCallbacks.hashCode());
		result = prime * result + ((functions == null) ? 0 : functions.hashCode());
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
		VertexAiGeminiChatOptions other = (VertexAiGeminiChatOptions) obj;
		if (stopSequences == null) {
			if (other.stopSequences != null)
				return false;
		}
		else if (!stopSequences.equals(other.stopSequences))
			return false;
		if (temperature == null) {
			if (other.temperature != null)
				return false;
		}
		else if (!temperature.equals(other.temperature))
			return false;
		if (topP == null) {
			if (other.topP != null)
				return false;
		}
		else if (!topP.equals(other.topP))
			return false;
		if (topK == null) {
			if (other.topK != null)
				return false;
		}
		else if (!topK.equals(other.topK))
			return false;
		if (candidateCount == null) {
			if (other.candidateCount != null)
				return false;
		}
		else if (!candidateCount.equals(other.candidateCount))
			return false;
		if (maxOutputTokens == null) {
			if (other.maxOutputTokens != null)
				return false;
		}
		else if (!maxOutputTokens.equals(other.maxOutputTokens))
			return false;
		if (model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (functionCallbacks == null) {
			if (other.functionCallbacks != null)
				return false;
		}
		else if (!functionCallbacks.equals(other.functionCallbacks))
			return false;
		if (functions == null) {
			if (other.functions != null)
				return false;
		}
		else if (!functions.equals(other.functions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "VertexAiGeminiChatOptions [stopSequences=" + stopSequences + ", temperature=" + temperature + ", topP="
				+ topP + ", topK=" + topK + ", candidateCount=" + candidateCount + ", maxOutputTokens="
				+ maxOutputTokens + ", model=" + model + ", functionCallbacks=" + functionCallbacks + ", functions="
				+ functions + ", getClass()=" + getClass() + ", getStopSequences()=" + getStopSequences()
				+ ", getTemperature()=" + getTemperature() + ", getTopP()=" + getTopP() + ", getTopK()=" + getTopK()
				+ ", getCandidateCount()=" + getCandidateCount() + ", getMaxOutputTokens()=" + getMaxOutputTokens()
				+ ", getModel()=" + getModel() + ", getFunctionCallbacks()=" + getFunctionCallbacks()
				+ ", getFunctions()=" + getFunctions() + ", hashCode()=" + hashCode() + ", toString()="
				+ super.toString() + "]";
	}

	@Override
	public VertexAiGeminiChatOptions copy() {
		return fromOptions(this);
	}

	public static VertexAiGeminiChatOptions fromOptions(VertexAiGeminiChatOptions fromOptions) {
		VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();
		options.setStopSequences(fromOptions.getStopSequences());
		options.setTemperature(fromOptions.getTemperature());
		options.setTopP(fromOptions.getTopP());
		options.setTopK(fromOptions.getTopK());
		options.setCandidateCount(fromOptions.getCandidateCount());
		options.setMaxOutputTokens(fromOptions.getMaxOutputTokens());
		options.setModel(fromOptions.getModel());
		options.setFunctionCallbacks(fromOptions.getFunctionCallbacks());
		options.setFunctions(fromOptions.getFunctions());
		return options;
	}

}
