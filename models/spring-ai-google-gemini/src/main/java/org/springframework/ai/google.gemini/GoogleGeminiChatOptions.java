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
package org.springframework.ai.google.gemini;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.*;

/**
 * Chat completions options for the Google Gemini chat API.
 * <a href="https://ai.google.dev/api/generate-content#v1beta.GenerationConfig">Google
 * Gemini chat completion</a>
 */
@JsonInclude(Include.NON_NULL)
public class GoogleGeminiChatOptions implements ToolCallingChatOptions {

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
	 * Optional context map for tool execution.
	 */
	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("stopSequences") List<String> stopSequences;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
	 * existing frequency in the text so far, decreasing the model's likelihood to repeat
	 * the same line verbatim.
	 */
	private @JsonProperty("candidateCount") Integer candidateCount;

	/**
	 * The maximum number of tokens that can be generated in the chat completion. The
	 * total length of input tokens and generated tokens is limited by the model's context
	 * length.
	 */
	private @JsonProperty("maxOutputTokens") Integer maxOutputTokens;

	/**
	 * What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make
	 * the output more random, while lower values like 0.2 will make it more focused and
	 * deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * The number of thoughts tokens that the model should generate.
	 */
	private @JsonProperty("thinkingBudget") Integer thinkingBudget;

	/**
	 * Optional. Output schema of the generated candidate text. Schemas must be a subset
	 * of the OpenAPI schema and can be objects, primitives or arrays. If set, a
	 * compatible responseMimeType must also be set. Compatible MIME types:
	 * application/json: Schema for JSON response. Refer to the JSON text generation guide
	 * for more details.
	 */
	private @JsonProperty("responseSchema") ResponseSchema responseSchema;

	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the
	 * model considers the results of the tokens with top_p probability mass. So 0.1 means
	 * only the tokens comprising the top 10% probability mass are considered. We
	 * generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("topP") Double topP;

	private @JsonProperty("topK") Integer topK;

	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks != null ? this.toolCallbacks : List.of();
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
		return this.toolNames != null ? this.toolNames : Set.of();
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

	@JsonIgnore
	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@JsonIgnore
	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext != null ? this.toolContext : Map.of();
	}

	@JsonIgnore
	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected GoogleGeminiChatOptions options;

		public Builder() {
			this.options = new GoogleGeminiChatOptions();
		}

		public Builder(GoogleGeminiChatOptions options) {
			this.options = options;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder withResponseSchema(ResponseSchema responseSchema) {
			this.options.responseSchema = responseSchema;
			return this;
		}

		public Builder withCandidateCount(Integer candidateCount) {
			this.options.candidateCount = candidateCount;
			return this;
		}

		public Builder withMaxOutputTokens(Integer maxOutputTokens) {
			this.options.maxOutputTokens = maxOutputTokens;
			return this;
		}

		public Builder withTemperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withThinkingBudget(Integer thinkingBudget) {
			this.options.thinkingBudget = thinkingBudget;
			return this;
		}

		public Builder withTopP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withTopK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder withToolNames(String... toolNames) {
			this.options.setToolNames(Set.of(toolNames));
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public GoogleGeminiChatOptions build() {
			return this.options;
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stopSequences == null) ? 0 : stopSequences.hashCode());
		result = prime * result + ((responseSchema == null) ? 0 : responseSchema.hashCode());
		result = prime * result + ((candidateCount == null) ? 0 : candidateCount.hashCode());
		result = prime * result + ((maxOutputTokens == null) ? 0 : maxOutputTokens.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((thinkingBudget == null) ? 0 : thinkingBudget.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
		result = prime * result + ((topK == null) ? 0 : topK.hashCode());
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
		GoogleGeminiChatOptions other = (GoogleGeminiChatOptions) obj;
		if (this.temperature == null) {
			if (other.temperature != null)
				return false;
		}
		else if (!this.temperature.equals(other.temperature))
			return false;
		if (this.thinkingBudget == null) {
			if (other.thinkingBudget != null)
				return false;
		}
		else if (!this.thinkingBudget.equals(other.thinkingBudget))
			return false;
		if (this.topP == null) {
			if (other.topP != null)
				return false;
		}
		else if (!this.topP.equals(other.topP))
			return false;
		if (this.topK == null) {
			if (other.topK != null)
				return false;
		}
		else if (!this.topK.equals(other.topK))
			return false;
		if (this.maxOutputTokens == null) {
			if (other.maxOutputTokens != null)
				return false;
		}
		else if (!this.maxOutputTokens.equals(other.maxOutputTokens))
			return false;
		if (this.candidateCount == null) {
			if (other.candidateCount != null)
				return false;
		}
		else if (!this.candidateCount.equals(other.candidateCount))
			return false;
		if (this.responseSchema == null) {
			if (other.responseSchema != null)
				return false;
		}
		else if (!this.responseSchema.equals(other.responseSchema))
			return false;
		if (this.stopSequences == null) {
			return other.stopSequences == null;
		}
		return this.stopSequences.equals(other.stopSequences);
	}

	@Override
	public String getModel() {
		return null;
	}

	@Override
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxOutputTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return null;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public Integer getThinkingBudget() {
		return this.thinkingBudget;
	}

	public ResponseSchema getResponseSchema() {
		return this.responseSchema;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public GoogleGeminiChatOptions copy() {
		return GoogleGeminiChatOptions.fromOptions(this);
	}

	public static GoogleGeminiChatOptions fromOptions(GoogleGeminiChatOptions fromOptions) {
		return builder().withStopSequences(fromOptions.stopSequences)
			.withCandidateCount(fromOptions.candidateCount)
			.withMaxOutputTokens(fromOptions.maxOutputTokens)
			.withTemperature(fromOptions.temperature)
			.withThinkingBudget(fromOptions.thinkingBudget)
			.withTopP(fromOptions.topP)
			.withTopK(fromOptions.topK)
			.withResponseSchema(fromOptions.responseSchema)
			.build();
	}

}
