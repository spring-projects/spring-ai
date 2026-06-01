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

package org.springframework.ai.google.genai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel.ChatModel;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiServiceTier;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Options for the Google GenAI Chat API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Grogdunn
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Dan Dobrin
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public class GoogleGenAiChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	// https://cloud.google.com/vertex-ai/docs/reference/rest/v1/GenerationConfig

	/**
	 * Optional. Stop sequences.
	 */
	private final @Nullable List<String> stopSequences;

	// @formatter:off

	/**
	 * Optional. Controls the randomness of predictions.
	 */
	private final Double temperature;

	/**
	 * Optional. If specified, nucleus sampling will be used.
	 */
	private final Double topP;

	/**
	 * Optional. If specified, top k sampling will be used.
	 */
	private final @Nullable Integer topK;

	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private final @Nullable Integer candidateCount;

	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private final @Nullable Integer maxOutputTokens;

	/**
	 * Gemini model name.
	 */
	private final String model;

	/**
	 * Optional. Output response mimetype of the generated candidate text.
	 * - text/plain: (default) Text output.
	 * - application/json: JSON response in the candidates.
	 */
	private final @Nullable String responseMimeType;

	/**
	 * Optional. Gemini response schema.
	 */
	private final @Nullable String responseSchema;

	/**
	 * Optional. Frequency penalties.
	 */
	private final @Nullable Double frequencyPenalty;

	/**
	 * Optional. Positive penalties.
	 */
	private final @Nullable Double presencePenalty;

	/**
	 * Optional. Thinking budget for the thinking process.
	 * This is part of the thinkingConfig in GenerationConfig.
	 */
	private final @Nullable Integer thinkingBudget;

	/**
	 * Optional. Whether to include thoughts in the response.
	 * When true, thoughts are returned if the model supports them and thoughts are available.
	 *
	 * <p><strong>IMPORTANT:</strong> For Gemini 3 Pro with function calling,
	 * this MUST be set to true to avoid validation errors. Thought signatures
	 * are automatically propagated in multi-turn conversations to maintain context.
	 *
	 * <p>Note: Enabling thoughts increases token usage and API costs.
	 * This is part of the thinkingConfig in GenerationConfig.
	 */
	private final @Nullable Boolean includeThoughts;

	/**
	 * Optional. The level of thinking tokens the model should generate.
	 * LOW = minimal thinking, HIGH = extensive thinking.
	 * This is part of the thinkingConfig in GenerationConfig.
	 */
	private final @Nullable GoogleGenAiThinkingLevel thinkingLevel;

	/**
	 * Optional. Whether to include extended usage metadata in responses.
	 * When true, includes thinking tokens, cached content, tool-use tokens, and modality details.
	 * Defaults to true for full metadata access.
	 */
	private final @Nullable Boolean includeExtendedUsageMetadata;

	/**
	 * Optional. The name of cached content to use for this request.
	 * When set, the cached content will be used as context for the request.
	 */
	private final @Nullable String cachedContentName;

	/**
	 * Optional. Whether to use cached content if available.
	 * When true and cachedContentName is set, the system will use the cached content.
	 */
	private final @Nullable Boolean useCachedContent;

	/**
	 * Optional. Automatically cache prompts that exceed this token threshold.
	 * When set, prompts larger than this value will be automatically cached for reuse.
	 * Set to null to disable auto-caching.
	 */
	private final @Nullable Integer autoCacheThreshold;

	/**
	 * Optional. Time-to-live for auto-cached content.
	 * Used when auto-caching is enabled. Defaults to 1 hour if not specified.
	 */
	private final @Nullable Duration autoCacheTtl;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	private final @Nullable List<ToolCallback> toolCallbacks;

	/**
     * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	private final @Nullable Set<String> toolNames;

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	private final @Nullable Boolean internalToolExecutionEnabled;

	private final @Nullable Map<String, Object> toolContext;

	/**
	 * Use Google search Grounding feature
	 */
	private final Boolean googleSearchRetrieval;

	/**
	 * Optional. When true, the API response will include server-side tool calls and
	 * responses (e.g., Google Search invocations) within Content message parts.
	 * This allows clients to observe the server's tool invocations without executing them.
	 * Only supported with MLDev (Google AI) API, not Vertex AI.
	 */
	private final Boolean includeServerSideToolInvocations;

	private final @Nullable List<GoogleGenAiSafetySetting> safetySettings;

	private final @Nullable Map<String, String> labels;

	/**
	 * Optional. The service tier to use for the request.
	 */
	private final @Nullable GoogleGenAiServiceTier serviceTier;
	// @formatter:on

	protected GoogleGenAiChatOptions(@Nullable String model, @Nullable Double frequencyPenalty,
			@Nullable Integer maxOutputTokens, @Nullable Double presencePenalty, @Nullable List<String> stopSequences,
			@Nullable Double temperature, @Nullable Integer topK, @Nullable Double topP,
			@Nullable Boolean internalToolExecutionEnabled, @Nullable List<ToolCallback> toolCallbacks,
			@Nullable Set<String> toolNames, @Nullable Map<String, Object> toolContext,
			@Nullable Integer candidateCount, @Nullable String responseMimeType, @Nullable String responseSchema,
			@Nullable Integer thinkingBudget, @Nullable Boolean includeThoughts,
			@Nullable GoogleGenAiThinkingLevel thinkingLevel, @Nullable Boolean includeExtendedUsageMetadata,
			@Nullable String cachedContentName, @Nullable Boolean useCachedContent,
			@Nullable Integer autoCacheThreshold, @Nullable Duration autoCacheTtl,
			@Nullable Boolean googleSearchRetrieval, @Nullable Boolean includeServerSideToolInvocations,
			@Nullable List<GoogleGenAiSafetySetting> safetySettings, @Nullable Map<String, String> labels,
			@Nullable GoogleGenAiServiceTier serviceTier) {
		this.model = model != null ? model : ChatModel.GEMINI_2_5_FLASH.getValue();
		this.frequencyPenalty = frequencyPenalty;
		this.maxOutputTokens = maxOutputTokens;
		this.presencePenalty = presencePenalty;
		this.stopSequences = (stopSequences != null ? List.copyOf(stopSequences) : null);
		this.temperature = temperature != null ? temperature : 0.7;
		this.topK = topK;
		this.topP = topP != null ? topP : 1.0;
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		this.toolCallbacks = (toolCallbacks != null ? List.copyOf(toolCallbacks) : null);
		this.toolNames = (toolNames != null ? Set.copyOf(toolNames) : null);
		this.toolContext = (toolContext != null ? Map.copyOf(toolContext) : null);
		this.candidateCount = candidateCount;
		this.responseMimeType = responseMimeType;
		this.responseSchema = responseSchema;
		this.thinkingBudget = thinkingBudget;
		this.includeThoughts = includeThoughts;
		this.thinkingLevel = thinkingLevel;
		this.includeExtendedUsageMetadata = includeExtendedUsageMetadata;
		this.cachedContentName = cachedContentName;
		this.useCachedContent = useCachedContent;
		this.autoCacheThreshold = autoCacheThreshold;
		this.autoCacheTtl = autoCacheTtl;
		this.googleSearchRetrieval = Boolean.TRUE.equals(googleSearchRetrieval);
		this.includeServerSideToolInvocations = Boolean.TRUE.equals(includeServerSideToolInvocations);
		this.safetySettings = (safetySettings != null ? List.copyOf(safetySettings) : null);
		this.labels = (labels != null ? Map.copyOf(labels) : null);
		this.serviceTier = serviceTier;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static GoogleGenAiChatOptions fromOptions(GoogleGenAiChatOptions fromOptions) {
		return fromOptions.mutate().build();
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public @Nullable Integer getCandidateCount() {
		return this.candidateCount;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return getMaxOutputTokens();
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public @Nullable String getResponseMimeType() {
		return this.responseMimeType;
	}

	public @Nullable String getResponseSchema() {
		return this.responseSchema;
	}

	@Override
	public @Nullable List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public @Nullable Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public @Nullable Integer getThinkingBudget() {
		return this.thinkingBudget;
	}

	public @Nullable Boolean getIncludeThoughts() {
		return this.includeThoughts;
	}

	public @Nullable GoogleGenAiThinkingLevel getThinkingLevel() {
		return this.thinkingLevel;
	}

	public @Nullable Boolean getIncludeExtendedUsageMetadata() {
		return this.includeExtendedUsageMetadata;
	}

	public @Nullable String getCachedContentName() {
		return this.cachedContentName;
	}

	public @Nullable Boolean getUseCachedContent() {
		return this.useCachedContent;
	}

	public @Nullable Integer getAutoCacheThreshold() {
		return this.autoCacheThreshold;
	}

	public @Nullable Duration getAutoCacheTtl() {
		return this.autoCacheTtl;
	}

	public @Nullable Boolean getGoogleSearchRetrieval() {
		return this.googleSearchRetrieval;
	}

	public @Nullable Boolean getIncludeServerSideToolInvocations() {
		return this.includeServerSideToolInvocations;
	}

	public @Nullable List<GoogleGenAiSafetySetting> getSafetySettings() {
		return this.safetySettings;
	}

	public @Nullable Map<String, String> getLabels() {
		return this.labels;
	}

	/**
	 * @since 2.0.0
	 */
	public @Nullable GoogleGenAiServiceTier getServiceTier() {
		return this.serviceTier;
	}

	@Override
	public @Nullable Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public @Nullable String getOutputSchema() {
		return this.getResponseSchema();
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GoogleGenAiChatOptions that)) {
			return false;
		}
		return Objects.equals(this.googleSearchRetrieval, that.googleSearchRetrieval)
				&& Objects.equals(this.includeServerSideToolInvocations, that.includeServerSideToolInvocations)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.candidateCount, that.candidateCount)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.thinkingBudget, that.thinkingBudget)
				&& Objects.equals(this.includeThoughts, that.includeThoughts)
				&& this.thinkingLevel == that.thinkingLevel
				&& Objects.equals(this.maxOutputTokens, that.maxOutputTokens) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.responseMimeType, that.responseMimeType)
				&& Objects.equals(this.responseSchema, that.responseSchema)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.safetySettings, that.safetySettings)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext) && Objects.equals(this.labels, that.labels)
				&& Objects.equals(this.serviceTier, that.serviceTier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.stopSequences, this.temperature, this.topP, this.topK, this.candidateCount,
				this.frequencyPenalty, this.presencePenalty, this.thinkingBudget, this.includeThoughts,
				this.thinkingLevel, this.maxOutputTokens, this.model, this.responseMimeType, this.responseSchema,
				this.toolCallbacks, this.toolNames, this.googleSearchRetrieval, this.includeServerSideToolInvocations,
				this.safetySettings, this.internalToolExecutionEnabled, this.toolContext, this.labels,
				this.serviceTier);
	}

	@Override
	public Builder mutate() {
		return GoogleGenAiChatOptions.builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxOutputTokens(this.maxOutputTokens) // alias for maxTokens
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
			// StructuredOutputChatOptions
			.responseMimeType(this.responseMimeType)
			.outputSchema(this.getOutputSchema())
			// GoogleGenAi Specific
			.candidateCount(this.candidateCount)
			.thinkingBudget(this.thinkingBudget)
			.includeThoughts(this.includeThoughts)
			.thinkingLevel(this.thinkingLevel)
			.includeExtendedUsageMetadata(this.includeExtendedUsageMetadata)
			.cachedContentName(this.cachedContentName)
			.useCachedContent(this.useCachedContent)
			.autoCacheThreshold(this.autoCacheThreshold)
			.autoCacheTtl(this.autoCacheTtl)
			.googleSearchRetrieval(this.googleSearchRetrieval)
			.includeServerSideToolInvocations(this.includeServerSideToolInvocations)
			.safetySettings(this.safetySettings)
			.labels(this.labels)
			.serviceTier(this.serviceTier)
			.responseMimeType(this.responseMimeType);
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
			copy.safetySettings = this.safetySettings;
			copy.labels = this.labels;
			return copy;
		}

		protected @Nullable Integer candidateCount;

		protected @Nullable String responseMimeType;

		protected @Nullable String responseSchema;

		protected @Nullable Integer thinkingBudget;

		protected @Nullable Boolean includeThoughts;

		protected @Nullable GoogleGenAiThinkingLevel thinkingLevel;

		protected @Nullable Boolean includeExtendedUsageMetadata;

		protected @Nullable String cachedContentName;

		protected @Nullable Boolean useCachedContent;

		protected @Nullable Integer autoCacheThreshold;

		protected @Nullable Duration autoCacheTtl;

		protected @Nullable Boolean googleSearchRetrieval;

		protected @Nullable Boolean includeServerSideToolInvocations;

		protected @Nullable List<GoogleGenAiSafetySetting> safetySettings;

		protected @Nullable Map<String, String> labels;

		protected @Nullable GoogleGenAiServiceTier serviceTier;

		public B candidateCount(@Nullable Integer candidateCount) {
			this.candidateCount = candidateCount;
			return self();
		}

		public B maxOutputTokens(@Nullable Integer maxOutputTokens) {
			return this.maxTokens(maxOutputTokens);
		}

		public B model(@Nullable ChatModel model) {
			if (model == null) {
				return this.model((String) null);
			}
			else {
				return this.model(model.getValue());
			}
		}

		public B responseMimeType(@Nullable String mimeType) {
			this.responseMimeType = mimeType;
			return self();
		}

		public B responseSchema(@Nullable String responseSchema) {
			this.responseSchema = responseSchema;
			return self();
		}

		public B outputSchema(@Nullable String jsonSchema) {
			this.responseSchema = jsonSchema;
			if (jsonSchema != null) {
				this.responseMimeType = "application/json";
			}
			else {
				this.responseMimeType = null;
			}
			return self();
		}

		public B googleSearchRetrieval(@Nullable Boolean googleSearch) {
			this.googleSearchRetrieval = googleSearch;
			return self();
		}

		public B includeServerSideToolInvocations(@Nullable Boolean includeServerSideToolInvocations) {
			this.includeServerSideToolInvocations = includeServerSideToolInvocations;
			return self();
		}

		public B safetySettings(@Nullable List<GoogleGenAiSafetySetting> safetySettings) {
			this.safetySettings = safetySettings;
			return self();
		}

		public B thinkingBudget(@Nullable Integer thinkingBudget) {
			this.thinkingBudget = thinkingBudget;
			return self();
		}

		public B includeThoughts(@Nullable Boolean includeThoughts) {
			this.includeThoughts = includeThoughts;
			return self();
		}

		public B thinkingLevel(@Nullable GoogleGenAiThinkingLevel thinkingLevel) {
			this.thinkingLevel = thinkingLevel;
			return self();
		}

		public B includeExtendedUsageMetadata(@Nullable Boolean includeExtendedUsageMetadata) {
			this.includeExtendedUsageMetadata = includeExtendedUsageMetadata;
			return self();
		}

		public B labels(@Nullable Map<String, String> labels) {
			this.labels = labels;
			return self();
		}

		public B cachedContentName(@Nullable String cachedContentName) {
			this.cachedContentName = cachedContentName;
			return self();
		}

		public B useCachedContent(@Nullable Boolean useCachedContent) {
			this.useCachedContent = useCachedContent;
			return self();
		}

		public B autoCacheThreshold(@Nullable Integer autoCacheThreshold) {
			this.autoCacheThreshold = autoCacheThreshold;
			return self();
		}

		public B autoCacheTtl(@Nullable Duration autoCacheTtl) {
			this.autoCacheTtl = autoCacheTtl;
			return self();
		}

		/**
		 * @since 2.0.0
		 */
		public B serviceTier(@Nullable GoogleGenAiServiceTier serviceTier) {
			this.serviceTier = serviceTier;
			return self();
		}

		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.candidateCount != null) {
					this.candidateCount = that.candidateCount;
				}
				if (that.responseMimeType != null) {
					this.responseMimeType = that.responseMimeType;
				}
				if (that.responseSchema != null) {
					this.responseSchema = that.responseSchema;
				}
				if (that.thinkingBudget != null) {
					this.thinkingBudget = that.thinkingBudget;
				}
				if (that.includeThoughts != null) {
					this.includeThoughts = that.includeThoughts;
				}
				if (that.thinkingLevel != null) {
					this.thinkingLevel = that.thinkingLevel;
				}
				if (that.includeExtendedUsageMetadata != null) {
					this.includeExtendedUsageMetadata = that.includeExtendedUsageMetadata;
				}
				if (that.cachedContentName != null) {
					this.cachedContentName = that.cachedContentName;
				}
				if (that.useCachedContent != null) {
					this.useCachedContent = that.useCachedContent;
				}
				if (that.autoCacheThreshold != null) {
					this.autoCacheThreshold = that.autoCacheThreshold;
				}
				if (that.autoCacheTtl != null) {
					this.autoCacheTtl = that.autoCacheTtl;
				}
				if (that.googleSearchRetrieval != null) {
					this.googleSearchRetrieval = that.googleSearchRetrieval;
				}
				if (that.includeServerSideToolInvocations != null) {
					this.includeServerSideToolInvocations = that.includeServerSideToolInvocations;
				}
				if (that.safetySettings != null) {
					if (this.safetySettings == null) {
						this.safetySettings = new ArrayList<>(that.safetySettings);
					}
					else {
						List<GoogleGenAiSafetySetting> merged = new ArrayList<>(this.safetySettings);
						merged.addAll(that.safetySettings);
						this.safetySettings = merged;
					}
				}
				if (that.labels != null) {
					if (this.labels == null) {
						this.labels = new HashMap<>(that.labels);
					}
					else {
						Map<String, String> merged = new HashMap<>(this.labels);
						merged.putAll(that.labels);
						this.labels = merged;
					}
				}
				if (that.serviceTier != null) {
					this.serviceTier = that.serviceTier;
				}
			}
			return self();
		}

		@Override
		public GoogleGenAiChatOptions build() {
			return new GoogleGenAiChatOptions(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
					this.stopSequences, this.temperature, this.topK, this.topP, this.internalToolExecutionEnabled,
					this.toolCallbacks, this.toolNames, this.toolContext, this.candidateCount, this.responseMimeType,
					this.responseSchema, this.thinkingBudget, this.includeThoughts, this.thinkingLevel,
					this.includeExtendedUsageMetadata, this.cachedContentName, this.useCachedContent,
					this.autoCacheThreshold, this.autoCacheTtl, this.googleSearchRetrieval,
					this.includeServerSideToolInvocations, this.safetySettings, this.labels, this.serviceTier);
		}

	}

}
