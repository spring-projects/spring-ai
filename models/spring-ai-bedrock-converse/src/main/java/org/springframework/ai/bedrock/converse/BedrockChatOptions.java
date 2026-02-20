/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.bedrock.converse;

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
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * The options to be used when sending a chat request to the Bedrock API.
 *
 * @author Sun Yuhan
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BedrockChatOptions implements ToolCallingChatOptions {

	@JsonProperty("model")
	private String model;

	@JsonProperty("frequencyPenalty")
	private Double frequencyPenalty;

	@JsonProperty("maxTokens")
	private Integer maxTokens;

	@JsonProperty("presencePenalty")
	private Double presencePenalty;

	@JsonIgnore
	private Map<String, String> requestParameters = new HashMap<>();

	@JsonProperty("stopSequences")
	private List<String> stopSequences;

	@JsonProperty("temperature")
	private Double temperature;

	@JsonProperty("topK")
	private Integer topK;

	@JsonProperty("topP")
	private Double topP;

	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	@JsonIgnore
	private BedrockCacheOptions cacheOptions;

	@JsonIgnore
	private ToolChoice toolChoice;

	public static Builder builder() {
		return new Builder();
	}

	public static BedrockChatOptions fromOptions(BedrockChatOptions fromOptions) {
		fromOptions.getToolNames();
		return builder().model(fromOptions.getModel())
			.frequencyPenalty(fromOptions.getFrequencyPenalty())
			.maxTokens(fromOptions.getMaxTokens())
			.presencePenalty(fromOptions.getPresencePenalty())
			.requestParameters(new HashMap<>(fromOptions.getRequestParameters()))
			.stopSequences(
					fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null)
			.temperature(fromOptions.getTemperature())
			.topK(fromOptions.getTopK())
			.topP(fromOptions.getTopP())
			.toolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()))
			.toolNames(new HashSet<>(fromOptions.getToolNames()))
			.toolContext(new HashMap<>(fromOptions.getToolContext()))
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
			.cacheOptions(fromOptions.getCacheOptions())
			.toolChoice(fromOptions.getToolChoice())
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

	public Map<String, String> getRequestParameters() {
		return this.requestParameters;
	}

	public void setRequestParameters(Map<String, String> requestParameters) {
		this.requestParameters = requestParameters;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
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
		return Set.copyOf(this.toolNames);
	}

	@Override
	@JsonIgnore
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(toolName -> Assert.hasText(toolName, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
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
	@Nullable
	public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@JsonIgnore
	public BedrockCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	@JsonIgnore
	public void setCacheOptions(BedrockCacheOptions cacheOptions) {
		this.cacheOptions = cacheOptions;
	}

	@JsonIgnore
	public ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	@JsonIgnore
	public void setToolChoice(ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
	}

	@Override
	@SuppressWarnings("unchecked")
	public BedrockChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof BedrockChatOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.requestParameters, that.requestParameters)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames) && Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.cacheOptions, that.cacheOptions)
				&& Objects.equals(this.toolChoice, that.toolChoice);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
				this.requestParameters, this.stopSequences, this.temperature, this.topK, this.topP, this.toolCallbacks,
				this.toolNames, this.toolContext, this.internalToolExecutionEnabled, this.cacheOptions,
				this.toolChoice);
	}

	public static final class Builder {

		private final BedrockChatOptions options = new BedrockChatOptions();

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

		public Builder presencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder requestParameters(Map<String, String> requestParameters) {
			this.options.requestParameters = requestParameters;
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
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

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder cacheOptions(BedrockCacheOptions cacheOptions) {
			this.options.setCacheOptions(cacheOptions);
			return this;
		}

		public Builder toolChoice(ToolChoice toolChoice) {
			this.options.setToolChoice(toolChoice);
			return this;
		}

		public BedrockChatOptions build() {
			return this.options;
		}

	}

}
