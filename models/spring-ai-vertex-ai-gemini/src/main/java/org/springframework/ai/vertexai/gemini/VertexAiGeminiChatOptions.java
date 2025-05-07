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

package org.springframework.ai.vertexai.gemini;

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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.ChatModel;
import org.springframework.ai.vertexai.gemini.common.VertexAiGeminiSafetySetting;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Options for the Vertex AI Gemini Chat API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Grogdunn
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiGeminiChatOptions implements ToolCallingChatOptions {

	// https://cloud.google.com/vertex-ai/docs/reference/rest/v1/GenerationConfig

	/**
	 * Optional. Stop sequences.
	 */
	private @JsonProperty("stopSequences") List<String> stopSequences;

	// @formatter:off

	/**
	 * Optional. Controls the randomness of predictions.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Optional. If specified, nucleus sampling will be used.
	 */
	private @JsonProperty("topP") Double topP;

	/**
	 * Optional. If specified, top k sampling will be used.
	 */
	private @JsonProperty("topK") Integer topK;

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
	 * Optional. Output response mimetype of the generated candidate text.
	 * - text/plain: (default) Text output.
	 * - application/json: JSON response in the candidates.
	 */
	private @JsonProperty("responseMimeType") String responseMimeType;

	/**
	 * Optional. Frequency penalties.
	 */
	private @JsonProperty("frequencyPenalty") Double frequencyPenalty;

	/**
	 * Optional. Positive penalties.
	 */
	private @JsonProperty("presencePenalty") Double presencePenalty;

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

	/**
	 * Use Google search Grounding feature
	 */
	@JsonIgnore
	private Boolean googleSearchRetrieval = false;

	@JsonIgnore
	private List<VertexAiGeminiSafetySetting> safetySettings = new ArrayList<>();
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static VertexAiGeminiChatOptions fromOptions(VertexAiGeminiChatOptions fromOptions) {
		VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();
		options.setStopSequences(fromOptions.getStopSequences());
		options.setTemperature(fromOptions.getTemperature());
		options.setTopP(fromOptions.getTopP());
		options.setTopK(fromOptions.getTopK());
		options.setFrequencyPenalty(fromOptions.getFrequencyPenalty());
		options.setPresencePenalty(fromOptions.getPresencePenalty());
		options.setCandidateCount(fromOptions.getCandidateCount());
		options.setMaxOutputTokens(fromOptions.getMaxOutputTokens());
		options.setModel(fromOptions.getModel());
		options.setToolCallbacks(fromOptions.getToolCallbacks());
		options.setResponseMimeType(fromOptions.getResponseMimeType());
		options.setToolNames(fromOptions.getToolNames());
		options.setResponseMimeType(fromOptions.getResponseMimeType());
		options.setGoogleSearchRetrieval(fromOptions.getGoogleSearchRetrieval());
		options.setSafetySettings(fromOptions.getSafetySettings());
		options.setInternalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled());
		options.setToolContext(fromOptions.getToolContext());
		return options;
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

	public String getResponseMimeType() {
		return this.responseMimeType;
	}

	public void setResponseMimeType(String mimeType) {
		this.responseMimeType = mimeType;
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
		return this.toolNames;
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
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
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public Boolean getGoogleSearchRetrieval() {
		return this.googleSearchRetrieval;
	}

	public void setGoogleSearchRetrieval(Boolean googleSearchRetrieval) {
		this.googleSearchRetrieval = googleSearchRetrieval;
	}

	public List<VertexAiGeminiSafetySetting> getSafetySettings() {
		return this.safetySettings;
	}

	public void setSafetySettings(List<VertexAiGeminiSafetySetting> safetySettings) {
		Assert.notNull(safetySettings, "safetySettings must not be null");
		this.safetySettings = safetySettings;
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
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof VertexAiGeminiChatOptions that)) {
			return false;
		}
		return this.googleSearchRetrieval == that.googleSearchRetrieval
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.candidateCount, that.candidateCount)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.maxOutputTokens, that.maxOutputTokens) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.responseMimeType, that.responseMimeType)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.safetySettings, that.safetySettings)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.stopSequences, this.temperature, this.topP, this.topK, this.candidateCount,
				this.frequencyPenalty, this.presencePenalty, this.maxOutputTokens, this.model, this.responseMimeType,
				this.toolCallbacks, this.toolNames, this.googleSearchRetrieval, this.safetySettings,
				this.internalToolExecutionEnabled, this.toolContext);
	}

	@Override
	public String toString() {
		return "VertexAiGeminiChatOptions{" + "stopSequences=" + this.stopSequences + ", temperature="
				+ this.temperature + ", topP=" + this.topP + ", topK=" + this.topK + ", frequencyPenalty="
				+ this.frequencyPenalty + ", presencePenalty=" + this.presencePenalty + ", candidateCount="
				+ this.candidateCount + ", maxOutputTokens=" + this.maxOutputTokens + ", model='" + this.model + '\''
				+ ", responseMimeType='" + this.responseMimeType + '\'' + ", toolCallbacks=" + this.toolCallbacks
				+ ", toolNames=" + this.toolNames + ", googleSearchRetrieval=" + this.googleSearchRetrieval
				+ ", safetySettings=" + this.safetySettings + '}';
	}

	@Override
	public VertexAiGeminiChatOptions copy() {
		return fromOptions(this);
	}

	public enum TransportType {

		GRPC, REST

	}

	public static class Builder {

		private VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();

		public Builder stopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
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

		public Builder topK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder frequencePenalty(Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder candidateCount(Integer candidateCount) {
			this.options.setCandidateCount(candidateCount);
			return this;
		}

		public Builder maxOutputTokens(Integer maxOutputTokens) {
			this.options.setMaxOutputTokens(maxOutputTokens);
			return this;
		}

		public Builder model(String modelName) {
			this.options.setModel(modelName);
			return this;
		}

		public Builder model(ChatModel model) {
			this.options.setModel(model.getValue());
			return this;
		}

		public Builder responseMimeType(String mimeType) {
			Assert.notNull(mimeType, "mimeType must not be null");
			this.options.setResponseMimeType(mimeType);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.toolCallbacks = toolCallbacks;
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "Tool names must not be null");
			this.options.toolNames = toolNames;
			return this;
		}

		public Builder toolName(String toolName) {
			Assert.hasText(toolName, "Tool name must not be empty");
			this.options.toolNames.add(toolName);
			return this;
		}

		public Builder googleSearchRetrieval(boolean googleSearch) {
			this.options.googleSearchRetrieval = googleSearch;
			return this;
		}

		public Builder safetySettings(List<VertexAiGeminiSafetySetting> safetySettings) {
			Assert.notNull(safetySettings, "safetySettings must not be null");
			this.options.safetySettings = safetySettings;
			return this;
		}

		public Builder internalToolExecutionEnabled(boolean internalToolExecutionEnabled) {
			this.options.internalToolExecutionEnabled = internalToolExecutionEnabled;
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

		public VertexAiGeminiChatOptions build() {
			return this.options;
		}

	}

}
