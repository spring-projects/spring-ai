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
package org.springframework.ai.vertexai.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiAnthropicChatOptions implements FunctionCallingOptions, ChatOptions {

	// @formatter:off
	/**
	 * Optional. Stop sequences.
	 */
	private @JsonProperty("stop_sequences") List<String> stopSequences;

	/**
	 * Optional. Controls the randomness of predictions.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * Optional. If specified, nucleus sampling will be used.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * Optional. If specified, top k sampling will be used.
	 */
	private @JsonProperty("top_k") Integer topK;

	/**
	 * The maximum number of tokens to generate.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * The version of the generative to use. The default value is vertex-2023-10-16.
	 */
	private @JsonProperty("anthropic_version") String anthropicVersion;

	/**
	 * The model to use. The default value is claude-3-5-sonnet@20240620.
	 */
	private @JsonProperty("model") String model;

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

	private VertexAiAnthropicChatOptions() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final VertexAiAnthropicChatOptions options = new VertexAiAnthropicChatOptions();

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

		public Builder withTopK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder withAnthropicVersion(String anthropicVersion) {
			this.options.setAnthropicVersion(anthropicVersion);
			return this;
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
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

		public VertexAiAnthropicChatOptions build() {
			Assert.hasText(this.options.anthropicVersion, "Anthropic version must not be empty");
			Assert.hasText(this.options.model, "Model must not be empty");
			Assert.isTrue(this.options.maxTokens != null && this.options.maxTokens > 0,
					"Max tokens must be greater than 0");

			return this.options;
		}

	}

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
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public String getAnthropicVersion() {
		return this.anthropicVersion;
	}

	public String getModel() {
		return this.model;
	}

	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public void setAnthropicVersion(String anthropicVersion) {
		this.anthropicVersion = anthropicVersion;
	}

	public void setModel(String model) {
		this.model = model;
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
	public VertexAiAnthropicChatOptions copy() {
		return fromOptions(this);
	}

	public static VertexAiAnthropicChatOptions fromOptions(VertexAiAnthropicChatOptions fromOptions) {
		return builder().withStopSequences(fromOptions.getStopSequences())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withAnthropicVersion(fromOptions.getAnthropicVersion())
			.withModel(fromOptions.getModel())
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
			.withFunctions(fromOptions.getFunctions())
			.build();
	}

}