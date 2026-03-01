/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.cohere.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.MediaContent.DetailLevel;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Options for the Cohere API.
 *
 * @author Ricken Bazolo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CohereChatOptions implements ToolCallingChatOptions {

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("model") String model;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Ensures that only the most likely tokens, with total probability mass of p, are
	 * considered for generation at each step. If both k and p are enabled, p acts after
	 * k. Defaults to 0.75. min value of 0.01, max value of 0.99.
	 */
	private @JsonProperty("p") Double p;

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * Min value of 0.0, max value of 1.0. Used to reduce repetitiveness of generated
	 * tokens. Similar to frequency_penalty, except that this penalty is applied equally
	 * to all tokens that have already appeared, regardless of their exact frequencies.
	 */
	private @JsonProperty("presence_penalty") Double presencePenalty;

	/**
	 * Min value of 0.0, max value of 1.0. Used to reduce repetitiveness of generated
	 * tokens. Similar to frequency_penalty, except that this penalty is applied equally
	 * to all tokens that have already appeared, regardless of their exact frequencies.
	 */
	private @JsonProperty("frequency_penalty") Double frequencyPenalty;

	/**
	 * Ensures that only the top k most likely tokens are considered for generation at
	 * each step. When k is set to 0, k-sampling is disabled. Defaults to 0, min value of
	 * 0, max value of 500.
	 */
	private @JsonProperty("k") Integer k;

	/**
	 * A list of tools the model may call. Currently, only functions are supported as a
	 * tool. Use this to provide a list of functions the model may generate JSON inputs
	 * for.
	 */
	private @JsonProperty("tools") List<CohereApi.FunctionTool> tools;

	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates
	 * is valid JSON.
	 */
	private @JsonProperty("response_format") ResponseFormat responseFormat;

	/**
	 * Used to select the safety instruction inserted into the prompt. Defaults to
	 * CONTEXTUAL. When OFF is specified, the safety instruction will be omitted.
	 */
	private @JsonProperty("safety_mode") CohereApi.SafetyMode safetyMode;

	/**
	 * A list of up to 5 strings that the model will use to stop generation. If the model
	 * generates a string that matches any of the strings in the list, it will stop
	 * generating tokens and return the generated text up to that point not including the
	 * stop sequence.
	 */
	private @JsonProperty("stop_sequences") List<String> stopSequences;

	/**
	 * If specified, the backend will make a best effort to sample tokens
	 * deterministically, such that repeated requests with the same seed and parameters
	 * should return the same result. However, determinism cannot be totally guaranteed.
	 */
	private @JsonProperty("seed") Integer seed;

	/**
	 * Defaults to false. When set to true, the log probabilities of the generated tokens
	 * will be included in the response.
	 */
	private @JsonProperty("logprobs") Boolean logprobs;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function. Specifying a particular
	 * function via {"type: "function", "function": {"name": "my_function"}} forces the
	 * model to call that function. none is the default when no functions are present.
	 * auto is the default if functions are present. Use the
	 * {@link CohereApi.ToolChoiceBuilder} to create a tool choice object.
	 */
	private @JsonProperty("tool_choice") ToolChoice toolChoice;

	private @JsonProperty("strict_tools") Boolean strictTools;

	/**
	 * The level of detail for processing images. Can be "low", "high", or "auto".
	 * Defaults to "auto" if not specified. This controls the resolution at which the
	 * model views image.
	 */
	@JsonIgnore
	private DetailLevel imageDetail;

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

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	public CohereApi.SafetyMode getSafetyMode() {
		return this.safetyMode;
	}

	public void setSafetyMode(CohereApi.SafetyMode safetyMode) {
		this.safetyMode = safetyMode;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public Boolean getStrictTools() {
		return this.strictTools;
	}

	public void setStrictTools(Boolean strictTools) {
		this.strictTools = strictTools;
	}

	public DetailLevel getImageDetail() {
		return this.imageDetail;
	}

	public void setImageDetail(DetailLevel imageDetail) {
		this.imageDetail = imageDetail;
	}

	public Double getP() {
		return this.p;
	}

	public void setP(Double p) {
		this.p = p;
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

	public ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
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
		return this.stopSequences;
	}

	public void setStop(List<String> stop) {
		this.stopSequences = stop;
	}

	public List<FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(List<FunctionTool> tools) {
		this.tools = tools;
	}

	public ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
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
		return getP();
	}

	public void setTopP(Double topP) {
		setP(topP);
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public CohereChatOptions copy() {
		return fromOptions(this);
	}

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
	@JsonIgnore
	public Integer getTopK() {
		return this.k;
	}

	public void setTopK(Integer k) {
		this.k = k;
	}

	@Override
	@JsonIgnore
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	@JsonIgnore
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CohereChatOptions that = (CohereChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.p, that.p) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty) && Objects.equals(this.k, that.k)
				&& Objects.equals(this.tools, that.tools) && Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.safetyMode, that.safetyMode)
				&& Objects.equals(this.stopSequences, that.stopSequences) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.logprobs, that.logprobs) && Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.strictTools, that.strictTools)
				&& Objects.equals(this.imageDetail, that.imageDetail)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.temperature, this.p, this.maxTokens, this.presencePenalty,
				this.frequencyPenalty, this.k, this.tools, this.responseFormat, this.safetyMode, this.stopSequences,
				this.seed, this.logprobs, this.toolChoice, this.strictTools, this.imageDetail, this.toolCallbacks,
				this.toolNames, this.internalToolExecutionEnabled, this.toolContext);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static CohereChatOptions fromOptions(CohereChatOptions fromOptions) {
		Builder builder = builder().model(fromOptions.getModel())
			.temperature(fromOptions.getTemperature())
			.maxTokens(fromOptions.getMaxTokens())
			.topP(fromOptions.getTopP())
			.frequencyPenalty(fromOptions.getFrequencyPenalty())
			.presencePenalty(fromOptions.getPresencePenalty())
			.topK(fromOptions.getTopK())
			.responseFormat(fromOptions.getResponseFormat())
			.safetyMode(fromOptions.getSafetyMode())
			.seed(fromOptions.getSeed())
			.logprobs(fromOptions.getLogprobs())
			.toolChoice(fromOptions.getToolChoice())
			.strictTools(fromOptions.getStrictTools())
			.imageDetail(fromOptions.getImageDetail())
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled());

		// Create defensive copies of collections
		if (fromOptions.getTools() != null) {
			builder.tools(new ArrayList<>(fromOptions.getTools()));
		}
		if (fromOptions.getStopSequences() != null) {
			builder.stop(new ArrayList<>(fromOptions.getStopSequences()));
		}
		if (fromOptions.getToolCallbacks() != null) {
			builder.toolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()));
		}
		if (fromOptions.getToolNames() != null) {
			builder.toolNames(new HashSet<>(fromOptions.getToolNames()));
		}
		if (fromOptions.getToolContext() != null) {
			builder.toolContext(new HashMap<>(fromOptions.getToolContext()));
		}

		return builder.build();
	}

	public static CohereChatOptions fromOptions2(CohereChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.temperature(fromOptions.getTemperature())
			.maxTokens(fromOptions.getMaxTokens())
			.topP(fromOptions.getTopP())
			.frequencyPenalty(fromOptions.getFrequencyPenalty())
			.presencePenalty(fromOptions.getPresencePenalty())
			.topK(fromOptions.getTopK())
			.tools(null)
			.responseFormat(fromOptions.getResponseFormat())
			.safetyMode(fromOptions.getSafetyMode())
			.stop(fromOptions.getStopSequences())
			.seed(fromOptions.getSeed())
			.logprobs(fromOptions.getLogprobs())
			.toolChoice(null)
			.strictTools(null)
			.toolCallbacks()
			.toolNames()
			.internalToolExecutionEnabled(null)
			.build();
	}

	public static class Builder {

		private final CohereChatOptions options = new CohereChatOptions();

		public CohereChatOptions build() {
			return this.options;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder model(CohereApi.ChatModel chatModel) {
			this.options.setModel(chatModel.getName());
			return this;
		}

		public Builder safetyMode(CohereApi.SafetyMode safetyMode) {
			this.options.setSafetyMode(safetyMode);
			return this;
		}

		public Builder logprobs(Boolean logprobs) {
			this.options.setLogprobs(logprobs);
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

		public Builder maxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.setStop(stop);
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder topP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder topK(Integer k) {
			this.options.setTopK(k);
			return this;
		}

		public Builder responseFormat(ResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder tools(List<FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder strictTools(Boolean strictTools) {
			this.options.setStrictTools(strictTools);
			return this;
		}

		public Builder toolChoice(ToolChoice toolChoice) {
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

		public Builder imageDetail(DetailLevel imageDetail) {
			this.options.setImageDetail(imageDetail);
			return this;
		}

	}

}
