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

package org.springframework.ai.huggingface;

import java.util.ArrayList;
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

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

/**
 * Chat options for HuggingFace chat model.
 *
 * @author Myeongdeok Kang
 */
@JsonInclude(Include.NON_NULL)
public class HuggingfaceChatOptions implements ToolCallingChatOptions {

	/**
	 * The model name to use for chat.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * Controls the randomness of the output. Higher values (e.g., 1.0) make the output
	 * more random, while lower values (e.g., 0.1) make it more focused and deterministic.
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * The maximum number of tokens to generate in the chat completion.
	 */
	@JsonProperty("max_tokens")
	private Integer maxTokens;

	/**
	 * Nucleus sampling parameter. The model considers the results of the tokens with
	 * top_p probability mass.
	 */
	@JsonProperty("top_p")
	private Double topP;

	/**
	 * The number of highest probability vocabulary tokens to keep for top-k sampling.
	 */
	@JsonProperty("top_k")
	private Integer topK;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
	 * existing frequency in the text so far.
	 */
	@JsonProperty("frequency_penalty")
	private Double frequencyPenalty;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
	 * they appear in the text so far.
	 */
	@JsonProperty("presence_penalty")
	private Double presencePenalty;

	/**
	 * Tool callbacks to be registered with the ChatModel.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Names of the tools to register with the ChatModel.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether the ChatModel is responsible for executing the tools requested by the model
	 * or if the tools should be executed directly by the caller.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	/**
	 * Tool context values as map.
	 */
	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

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

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
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
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public List<String> getStopSequences() {
		return null;
	}

	public void setStopSequences(List<String> stopSequences) {
		// No-op: HuggingfaceChatOptions does not support stop sequences
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		this.toolCallbacks = toolCallbacks != null ? toolCallbacks : new ArrayList<>();
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		this.toolNames = toolNames != null ? toolNames : new HashSet<>();
	}

	@Override
	@Nullable
	public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext != null ? toolContext : new HashMap<>();
	}

	@Override
	public HuggingfaceChatOptions copy() {
		return fromOptions(this);
	}

	/**
	 * Create a new {@link HuggingfaceChatOptions} instance from the given options.
	 * @param fromOptions the options to copy from
	 * @return a new {@link HuggingfaceChatOptions} instance
	 */
	public static HuggingfaceChatOptions fromOptions(HuggingfaceChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.temperature(fromOptions.getTemperature())
			.maxTokens(fromOptions.getMaxTokens())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.frequencyPenalty(fromOptions.getFrequencyPenalty())
			.presencePenalty(fromOptions.getPresencePenalty())
			.toolCallbacks(fromOptions.getToolCallbacks())
			.toolNames(fromOptions.getToolNames())
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
			.toolContext(fromOptions.getToolContext())
			.build();
	}

	/**
	 * Convert the {@link HuggingfaceChatOptions} object to a {@link Map} of key/value
	 * pairs.
	 * @return the {@link Map} of key/value pairs
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		HuggingfaceChatOptions that = (HuggingfaceChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.maxTokens, that.maxTokens) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.temperature, this.maxTokens, this.topP, this.topK, this.frequencyPenalty,
				this.presencePenalty, this.toolCallbacks, this.toolNames, this.internalToolExecutionEnabled,
				this.toolContext);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final HuggingfaceChatOptions options = new HuggingfaceChatOptions();

		private Builder() {
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder topP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			this.options.setToolContext(toolContext);
			return this;
		}

		public HuggingfaceChatOptions build() {
			return this.options;
		}

	}

}
