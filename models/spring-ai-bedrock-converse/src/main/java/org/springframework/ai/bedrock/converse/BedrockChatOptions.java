/*
 * Copyright 2023-present the original author or authors.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * The options to be used when sending a chat request to the Bedrock API.
 *
 * @author Sun Yuhan
 */
public class BedrockChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	private String model;

	private Double frequencyPenalty;

	private Integer maxTokens;

	private Double presencePenalty;

	private Map<String, String> requestParameters = new HashMap<>();

	private List<String> stopSequences;

	private Double temperature;

	private Integer topK;

	private Double topP;

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private Map<String, Object> toolContext = new HashMap<>();

	private Boolean internalToolExecutionEnabled;

	private BedrockCacheOptions cacheOptions;

	private String outputSchema;

	// TODO: left here for ModelOptionUtils.merge*()
	public BedrockChatOptions() {
	}

	protected BedrockChatOptions(String model, Double frequencyPenalty, Integer maxTokens, Double presencePenalty,
			Map<String, String> requestParameters, List<String> stopSequences, Double temperature, Integer topK,
			Double topP, Boolean internalToolExecutionEnabled, @Nullable List<ToolCallback> toolCallbacks,
			@Nullable Set<String> toolNames, @Nullable Map<String, Object> toolContext,
			BedrockCacheOptions cacheOptions, String outputSchema) {
		this.model = model;
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.presencePenalty = presencePenalty;
		this.requestParameters = requestParameters;
		this.stopSequences = stopSequences;
		this.temperature = temperature;
		this.topK = topK;
		this.topP = topP;
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		this.toolCallbacks = toolCallbacks == null ? new ArrayList<>() : new ArrayList<>(toolCallbacks);
		this.toolNames = toolNames == null ? new HashSet<>() : new HashSet<>(toolNames);
		this.toolContext = toolContext == null ? new HashMap<>() : new HashMap<>(toolContext);
		this.cacheOptions = cacheOptions;
		this.outputSchema = outputSchema;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static BedrockChatOptions fromOptions(BedrockChatOptions fromOptions) {
		return fromOptions.mutate().build();
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
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return Set.copyOf(this.toolNames);
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(toolName -> Assert.hasText(toolName, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
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
	@Nullable public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public BedrockCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	public void setCacheOptions(BedrockCacheOptions cacheOptions) {
		this.cacheOptions = cacheOptions;
	}

	@Override
	public @Nullable String getOutputSchema() {
		return this.outputSchema;
	}

	@Override
	public void setOutputSchema(String outputSchema) {
		this.outputSchema = outputSchema;
	}

	@Override
	public void setOutputSchemaResource(Resource outputSchemaResource) {
		this.setOutputSchema(StructuredOutputChatOptions.extractOutputSchema(outputSchemaResource));
	}

	@Override
	public BedrockChatOptions copy() {
		return mutate().build();
	}

	@Override
	public Builder mutate() {
		return BedrockChatOptions.builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolNames(this.getToolNames())
			.toolContext(this.getToolContext())
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// Bedrock Specific
			.requestParameters(this.requestParameters)
			.cacheOptions(this.cacheOptions)
			.outputSchema(this.outputSchema);
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
				&& Objects.equals(this.outputSchema, that.outputSchema);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
				this.requestParameters, this.stopSequences, this.temperature, this.topK, this.topP, this.toolCallbacks,
				this.toolNames, this.toolContext, this.internalToolExecutionEnabled, this.cacheOptions);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> implements StructuredOutputChatOptions.Builder<B> {

		@Override
		public B clone() {
			B copy = super.clone();
			copy.requestParameters = this.requestParameters == null ? null : new HashMap<>(this.requestParameters);
			return copy;
		}

		protected Map<String, String> requestParameters = new HashMap<>();

		protected @Nullable BedrockCacheOptions cacheOptions;

		private @Nullable String outputSchema;

		public B requestParameters(Map<String, String> requestParameters) {
			this.requestParameters = requestParameters;
			return self();
		}

		public B cacheOptions(@Nullable BedrockCacheOptions cacheOptions) {
			this.cacheOptions = cacheOptions;
			return self();
		}

		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.requestParameters != null) {
					this.requestParameters = that.requestParameters;
				}
				if (that.cacheOptions != null) {
					this.cacheOptions = that.cacheOptions;
				}
			}
			return self();
		}

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			this.outputSchema = outputSchema;
			return self();
		}

		@Override
		public BedrockChatOptions build() {
			return new BedrockChatOptions(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
					this.requestParameters, this.stopSequences, this.temperature, this.topK, this.topP,
					this.internalToolExecutionEnabled, this.toolCallbacks, this.toolNames, this.toolContext,
					this.cacheOptions, this.outputSchema);
		}

	}

}
