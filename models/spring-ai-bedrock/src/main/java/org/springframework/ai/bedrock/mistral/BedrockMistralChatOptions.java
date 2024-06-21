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
package org.springframework.ai.bedrock.mistral;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Java {@link ChatOptions} for the Bedrock Mistral chat generative model chat options.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-mistral-text-completion.html
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-mistral-chat-completion.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockMistralChatOptions implements ChatOptions, FunctionCallingOptions {

	/**
	 * The temperature value controls the randomness of the generated text. Use a lower
	 * value to decrease randomness in the response.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * (optional) The maximum cumulative probability of tokens to consider when sampling.
	 * The generative uses combined Top-k and nucleus sampling. Nucleus sampling considers
	 * the smallest set of tokens whose probability sum is at least topP.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * (optional) Specify the number of token choices the generative uses to generate the
	 * next token.
	 */
	private @JsonProperty("top_k") Integer topK;

	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * (optional) Configure up to four sequences that the generative recognizes. After a
	 * stop sequence, the generative stops generating further tokens. The returned text
	 * doesn't contain the stop sequence.
	 */
	private @JsonProperty("stop") List<String> stopSequences;

	/**
	 * (optional) Specifies how functions are called. If set to none the model won't call
	 * a function and will generate a message instead. If set to auto the model can choose
	 * to either generate a message or call a function. If set to any the model is forced
	 * to call a function.
	 */
	private @JsonProperty("tool_choice") String toolChoice;

	/**
	 * Tool Function Callbacks to register with the ChatModel. For Prompt Options the
	 * functionCallbacks are automatically enabled for the duration of the prompt
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

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final BedrockMistralChatOptions options = new BedrockMistralChatOptions();

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

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
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

		public BedrockMistralChatOptions build() {
			return this.options;
		}

	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public Integer getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public String getToolChoice() {
		return toolChoice;
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
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.functionCallbacks = functionCallbacks;
	}

	@Override
	public Set<String> getFunctions() {
		return this.functions;
	}

	@Override
	public void setFunctions(Set<String> functions) {
		Assert.notNull(functions, "Function must not be null");
		this.functions = functions;
	}

	public static BedrockMistralChatOptions fromOptions(BedrockMistralChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withStopSequences(fromOptions.getStopSequences())
			.withToolChoice(fromOptions.toolChoice)
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
			.withFunctions(fromOptions.getFunctions())
			.build();
	}

}
