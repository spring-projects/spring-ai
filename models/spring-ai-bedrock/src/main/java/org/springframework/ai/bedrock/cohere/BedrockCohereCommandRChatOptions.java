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
package org.springframework.ai.bedrock.cohere;

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
 * Java {@link ChatOptions} for the Bedrock Cohere Command R chat generative model chat
 * options.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere-command-r-plus.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockCohereCommandRChatOptions implements ChatOptions, FunctionCallingOptions {

	// @formatter:off
	/**
	 * (optional) When enabled, it will only generate potential search queries without performing
	 * searches or providing a response.
	 */
	@JsonProperty("search_queries_only") Boolean searchQueriesOnly;
	/**
	 * (optional) Overrides the default preamble for search query generation.
	 */
	@JsonProperty("preamble") String preamble;
	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	@JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * (optional) Use a lower value to decrease randomness in the response.
	 */
	@JsonProperty("temperature") Float temperature;
	/**
	 * Top P. Use a lower value to ignore less probable options. Set to 0 or 1.0 to disable.
	 */
	@JsonProperty("p") Float topP;
	/**
	 * Top K. Specify the number of token choices the model uses to generate the next token.
	 */
	@JsonProperty("k") Integer topK;
	/**
	 * (optional) Dictates how the prompt is constructed.
	 */
	@JsonProperty("prompt_truncation") PromptTruncation promptTruncation;
	/**
	 * (optional) Used to reduce repetitiveness of generated tokens.
	 */
	@JsonProperty("frequency_penalty") Float frequencyPenalty;
	/**
	 * (optional) Used to reduce repetitiveness of generated tokens.
	 */
	@JsonProperty("presence_penalty") Float presencePenalty;
	/**
	 * (optional) Specify the best effort to sample tokens deterministically.
	 */
	@JsonProperty("seed") Integer seed;
	/**
	 * (optional) Specify true to return the full prompt that was sent to the model.
	 */
	@JsonProperty("return_prompt") Boolean returnPrompt;
	/**
	 * (optional) A list of stop sequences.
	 */
	@JsonProperty("stop_sequences") List<String> stopSequences;
	/**
	 * (optional) Specify true, to send the user’s message to the model without any preprocessing.
	 */
	@JsonProperty("raw_prompting") Boolean rawPrompting;

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
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final BedrockCohereCommandRChatOptions options = new BedrockCohereCommandRChatOptions();

		public Builder withSearchQueriesOnly(Boolean searchQueriesOnly) {
			options.setSearchQueriesOnly(searchQueriesOnly);
			return this;
		}

		public Builder withPreamble(String preamble) {
			options.setPreamble(preamble);
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder withTemperature(Float temperature) {
			options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			options.setTopP(topP);
			return this;
		}

		public Builder withTopK(Integer topK) {
			options.setTopK(topK);
			return this;
		}

		public Builder withPromptTruncation(PromptTruncation promptTruncation) {
			options.setPromptTruncation(promptTruncation);
			return this;
		}

		public Builder withFrequencyPenalty(Float frequencyPenalty) {
			options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder withPresencePenalty(Float presencePenalty) {
			options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder withSeed(Integer seed) {
			options.setSeed(seed);
			return this;
		}

		public Builder withReturnPrompt(Boolean returnPrompt) {
			options.setReturnPrompt(returnPrompt);
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			options.setStopSequences(stopSequences);
			return this;
		}

		public Builder withRawPrompting(Boolean rawPrompting) {
			options.setRawPrompting(rawPrompting);
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

		public BedrockCohereCommandRChatOptions build() {
			return this.options;
		}

	}

	public Boolean getSearchQueriesOnly() {
		return searchQueriesOnly;
	}

	public void setSearchQueriesOnly(Boolean searchQueriesOnly) {
		this.searchQueriesOnly = searchQueriesOnly;
	}

	public String getPreamble() {
		return preamble;
	}

	public void setPreamble(String preamble) {
		this.preamble = preamble;
	}

	public Integer getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Float getTemperature() {
		return temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public PromptTruncation getPromptTruncation() {
		return promptTruncation;
	}

	public void setPromptTruncation(PromptTruncation promptTruncation) {
		this.promptTruncation = promptTruncation;
	}

	public Float getFrequencyPenalty() {
		return frequencyPenalty;
	}

	public void setFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public Float getPresencePenalty() {
		return presencePenalty;
	}

	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public Integer getSeed() {
		return seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public Boolean getReturnPrompt() {
		return returnPrompt;
	}

	public void setReturnPrompt(Boolean returnPrompt) {
		this.returnPrompt = returnPrompt;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public Boolean getRawPrompting() {
		return rawPrompting;
	}

	public void setRawPrompting(Boolean rawPrompting) {
		this.rawPrompting = rawPrompting;
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

	/**
	 * Specifies how the prompt is constructed.
	 */
	public enum PromptTruncation {

		/**
		 * Some elements from chat_history and documents will be dropped to construct a
		 * prompt that fits within the model's context length limit.
		 */
		AUTO_PRESERVE_ORDER,
		/**
		 * (Default) No elements will be dropped.
		 */
		OFF

	}

	public static BedrockCohereCommandRChatOptions fromOptions(BedrockCohereCommandRChatOptions fromOptions) {
		return builder().withSearchQueriesOnly(fromOptions.getSearchQueriesOnly())
			.withPreamble(fromOptions.getPreamble())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withPromptTruncation(fromOptions.getPromptTruncation())
			.withFrequencyPenalty(fromOptions.getFrequencyPenalty())
			.withPresencePenalty(fromOptions.getPresencePenalty())
			.withSeed(fromOptions.getSeed())
			.withReturnPrompt(fromOptions.getReturnPrompt())
			.withStopSequences(fromOptions.getStopSequences())
			.withRawPrompting(fromOptions.getRawPrompting())
			.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
			.withFunctions(fromOptions.getFunctions())
			.build();
	}

}
