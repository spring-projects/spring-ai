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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Google GenAI Chat.
 *
 * @author Christian Tzolov
 * @author Hyunsang Han
 * @author Sebastien Deleuze
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiChatProperties.CONFIG_PREFIX)
public class GoogleGenAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.chat";

	public static final String DEFAULT_MODEL = GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue();

	private @Nullable List<String> stopSequences;

	private @Nullable Double temperature = 0.7;

	private @Nullable Double topP;

	private @Nullable Integer topK;

	private @Nullable Integer candidateCount = 1;

	private @Nullable Integer maxOutputTokens;

	private @Nullable String model = DEFAULT_MODEL;

	private @Nullable String responseMimeType;

	private @Nullable String responseSchema;

	private @Nullable Double frequencyPenalty;

	private @Nullable Double presencePenalty;

	private @Nullable Integer thinkingBudget;

	private @Nullable Boolean includeThoughts;

	private @Nullable GoogleGenAiThinkingLevel thinkingLevel;

	private @Nullable Boolean includeExtendedUsageMetadata;

	private @Nullable String cachedContentName;

	private @Nullable Boolean useCachedContent;

	private @Nullable Integer autoCacheThreshold;

	private @Nullable Duration autoCacheTtl;

	private @Nullable List<String> toolNames;

	private @Nullable Boolean internalToolExecutionEnabled;

	private @Nullable Boolean googleSearchRetrieval = false;

	private @Nullable Boolean includeServerSideToolInvocations = false;

	private @Nullable List<GoogleGenAiSafetySetting> safetySettings;

	private @Nullable Map<String, String> labels;

	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(@Nullable Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getResponseMimeType() {
		return this.responseMimeType;
	}

	public void setResponseMimeType(@Nullable String responseMimeType) {
		this.responseMimeType = responseMimeType;
	}

	public @Nullable String getResponseSchema() {
		return this.responseSchema;
	}

	public void setResponseSchema(@Nullable String responseSchema) {
		this.responseSchema = responseSchema;
	}

	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public @Nullable Integer getThinkingBudget() {
		return this.thinkingBudget;
	}

	public void setThinkingBudget(@Nullable Integer thinkingBudget) {
		this.thinkingBudget = thinkingBudget;
	}

	public @Nullable Boolean getIncludeThoughts() {
		return this.includeThoughts;
	}

	public void setIncludeThoughts(@Nullable Boolean includeThoughts) {
		this.includeThoughts = includeThoughts;
	}

	public @Nullable GoogleGenAiThinkingLevel getThinkingLevel() {
		return this.thinkingLevel;
	}

	public void setThinkingLevel(@Nullable GoogleGenAiThinkingLevel thinkingLevel) {
		this.thinkingLevel = thinkingLevel;
	}

	public @Nullable Boolean getIncludeExtendedUsageMetadata() {
		return this.includeExtendedUsageMetadata;
	}

	public void setIncludeExtendedUsageMetadata(@Nullable Boolean includeExtendedUsageMetadata) {
		this.includeExtendedUsageMetadata = includeExtendedUsageMetadata;
	}

	public @Nullable String getCachedContentName() {
		return this.cachedContentName;
	}

	public void setCachedContentName(@Nullable String cachedContentName) {
		this.cachedContentName = cachedContentName;
	}

	public @Nullable Boolean getUseCachedContent() {
		return this.useCachedContent;
	}

	public void setUseCachedContent(@Nullable Boolean useCachedContent) {
		this.useCachedContent = useCachedContent;
	}

	public @Nullable Integer getAutoCacheThreshold() {
		return this.autoCacheThreshold;
	}

	public void setAutoCacheThreshold(@Nullable Integer autoCacheThreshold) {
		this.autoCacheThreshold = autoCacheThreshold;
	}

	public @Nullable Duration getAutoCacheTtl() {
		return this.autoCacheTtl;
	}

	public void setAutoCacheTtl(@Nullable Duration autoCacheTtl) {
		this.autoCacheTtl = autoCacheTtl;
	}

	public @Nullable List<String> getToolNames() {
		return this.toolNames;
	}

	public void setToolNames(@Nullable List<String> toolNames) {
		this.toolNames = toolNames;
	}

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public @Nullable Boolean getGoogleSearchRetrieval() {
		return this.googleSearchRetrieval;
	}

	public void setGoogleSearchRetrieval(@Nullable Boolean googleSearchRetrieval) {
		this.googleSearchRetrieval = googleSearchRetrieval;
	}

	public @Nullable Boolean getIncludeServerSideToolInvocations() {
		return this.includeServerSideToolInvocations;
	}

	public void setIncludeServerSideToolInvocations(@Nullable Boolean includeServerSideToolInvocations) {
		this.includeServerSideToolInvocations = includeServerSideToolInvocations;
	}

	public @Nullable List<GoogleGenAiSafetySetting> getSafetySettings() {
		return this.safetySettings;
	}

	public void setSafetySettings(@Nullable List<GoogleGenAiSafetySetting> safetySettings) {
		this.safetySettings = safetySettings;
	}

	public @Nullable Map<String, String> getLabels() {
		return this.labels;
	}

	public void setLabels(@Nullable Map<String, String> labels) {
		this.labels = labels;
	}

	public GoogleGenAiChatOptions toOptions() {
		GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder();
		builder.model(this.model);
		if (this.stopSequences != null) {
			builder.stopSequences(this.stopSequences);
		}
		if (this.temperature != null) {
			builder.temperature(this.temperature);
		}
		if (this.topP != null) {
			builder.topP(this.topP);
		}
		if (this.topK != null) {
			builder.topK(this.topK);
		}
		if (this.candidateCount != null) {
			builder.candidateCount(this.candidateCount);
		}
		if (this.maxOutputTokens != null) {
			builder.maxOutputTokens(this.maxOutputTokens);
		}
		if (this.responseMimeType != null) {
			builder.responseMimeType(this.responseMimeType);
		}
		if (this.responseSchema != null) {
			builder.responseSchema(this.responseSchema);
		}
		if (this.frequencyPenalty != null) {
			builder.frequencyPenalty(this.frequencyPenalty);
		}
		if (this.presencePenalty != null) {
			builder.presencePenalty(this.presencePenalty);
		}
		if (this.thinkingBudget != null) {
			builder.thinkingBudget(this.thinkingBudget);
		}
		if (this.includeThoughts != null) {
			builder.includeThoughts(this.includeThoughts);
		}
		if (this.thinkingLevel != null) {
			builder.thinkingLevel(this.thinkingLevel);
		}
		if (this.includeExtendedUsageMetadata != null) {
			builder.includeExtendedUsageMetadata(this.includeExtendedUsageMetadata);
		}
		if (this.cachedContentName != null) {
			builder.cachedContentName(this.cachedContentName);
		}
		if (this.useCachedContent != null) {
			builder.useCachedContent(this.useCachedContent);
		}
		if (this.autoCacheThreshold != null) {
			builder.autoCacheThreshold(this.autoCacheThreshold);
		}
		if (this.autoCacheTtl != null) {
			builder.autoCacheTtl(this.autoCacheTtl);
		}
		if (this.toolNames != null) {
			builder.toolNames(Set.copyOf(this.toolNames));
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		if (this.googleSearchRetrieval != null) {
			builder.googleSearchRetrieval(this.googleSearchRetrieval);
		}
		if (this.includeServerSideToolInvocations != null) {
			builder.includeServerSideToolInvocations(this.includeServerSideToolInvocations);
		}
		if (this.safetySettings != null) {
			builder.safetySettings(this.safetySettings);
		}
		if (this.labels != null) {
			builder.labels(this.labels);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return GoogleGenAiChatProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			GoogleGenAiChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.stop-sequences")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStopSequences() {
			return GoogleGenAiChatProperties.this.getStopSequences();
		}

		public void setStopSequences(@Nullable List<String> stopSequences) {
			GoogleGenAiChatProperties.this.setStopSequences(stopSequences);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return GoogleGenAiChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			GoogleGenAiChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return GoogleGenAiChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			GoogleGenAiChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.top-k")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopK() {
			return GoogleGenAiChatProperties.this.getTopK();
		}

		public void setTopK(@Nullable Integer topK) {
			GoogleGenAiChatProperties.this.setTopK(topK);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.candidate-count")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getCandidateCount() {
			return GoogleGenAiChatProperties.this.getCandidateCount();
		}

		public void setCandidateCount(@Nullable Integer candidateCount) {
			GoogleGenAiChatProperties.this.setCandidateCount(candidateCount);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.max-output-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxOutputTokens() {
			return GoogleGenAiChatProperties.this.getMaxOutputTokens();
		}

		public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
			GoogleGenAiChatProperties.this.setMaxOutputTokens(maxOutputTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.response-mime-type")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getResponseMimeType() {
			return GoogleGenAiChatProperties.this.getResponseMimeType();
		}

		public void setResponseMimeType(@Nullable String responseMimeType) {
			GoogleGenAiChatProperties.this.setResponseMimeType(responseMimeType);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.response-schema")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getResponseSchema() {
			return GoogleGenAiChatProperties.this.getResponseSchema();
		}

		public void setResponseSchema(@Nullable String responseSchema) {
			GoogleGenAiChatProperties.this.setResponseSchema(responseSchema);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return GoogleGenAiChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			GoogleGenAiChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return GoogleGenAiChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			GoogleGenAiChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.thinking-budget")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getThinkingBudget() {
			return GoogleGenAiChatProperties.this.getThinkingBudget();
		}

		public void setThinkingBudget(@Nullable Integer thinkingBudget) {
			GoogleGenAiChatProperties.this.setThinkingBudget(thinkingBudget);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.include-thoughts")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getIncludeThoughts() {
			return GoogleGenAiChatProperties.this.getIncludeThoughts();
		}

		public void setIncludeThoughts(@Nullable Boolean includeThoughts) {
			GoogleGenAiChatProperties.this.setIncludeThoughts(includeThoughts);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.thinking-level")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable GoogleGenAiThinkingLevel getThinkingLevel() {
			return GoogleGenAiChatProperties.this.getThinkingLevel();
		}

		public void setThinkingLevel(@Nullable GoogleGenAiThinkingLevel thinkingLevel) {
			GoogleGenAiChatProperties.this.setThinkingLevel(thinkingLevel);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.include-extended-usage-metadata")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getIncludeExtendedUsageMetadata() {
			return GoogleGenAiChatProperties.this.getIncludeExtendedUsageMetadata();
		}

		public void setIncludeExtendedUsageMetadata(@Nullable Boolean includeExtendedUsageMetadata) {
			GoogleGenAiChatProperties.this.setIncludeExtendedUsageMetadata(includeExtendedUsageMetadata);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.cached-content-name")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getCachedContentName() {
			return GoogleGenAiChatProperties.this.getCachedContentName();
		}

		public void setCachedContentName(@Nullable String cachedContentName) {
			GoogleGenAiChatProperties.this.setCachedContentName(cachedContentName);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.use-cached-content")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getUseCachedContent() {
			return GoogleGenAiChatProperties.this.getUseCachedContent();
		}

		public void setUseCachedContent(@Nullable Boolean useCachedContent) {
			GoogleGenAiChatProperties.this.setUseCachedContent(useCachedContent);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.auto-cache-threshold")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getAutoCacheThreshold() {
			return GoogleGenAiChatProperties.this.getAutoCacheThreshold();
		}

		public void setAutoCacheThreshold(@Nullable Integer autoCacheThreshold) {
			GoogleGenAiChatProperties.this.setAutoCacheThreshold(autoCacheThreshold);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.auto-cache-ttl")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Duration getAutoCacheTtl() {
			return GoogleGenAiChatProperties.this.getAutoCacheTtl();
		}

		public void setAutoCacheTtl(@Nullable Duration autoCacheTtl) {
			GoogleGenAiChatProperties.this.setAutoCacheTtl(autoCacheTtl);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.tool-names")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getToolNames() {
			return GoogleGenAiChatProperties.this.getToolNames();
		}

		public void setToolNames(@Nullable List<String> toolNames) {
			GoogleGenAiChatProperties.this.setToolNames(toolNames);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return GoogleGenAiChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			GoogleGenAiChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.google-search-retrieval")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getGoogleSearchRetrieval() {
			return GoogleGenAiChatProperties.this.getGoogleSearchRetrieval();
		}

		public void setGoogleSearchRetrieval(@Nullable Boolean googleSearchRetrieval) {
			GoogleGenAiChatProperties.this.setGoogleSearchRetrieval(googleSearchRetrieval);
		}

		@DeprecatedConfigurationProperty(
				replacement = "spring.ai.google.genai.chat.include-server-side-tool-invocations")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getIncludeServerSideToolInvocations() {
			return GoogleGenAiChatProperties.this.getIncludeServerSideToolInvocations();
		}

		public void setIncludeServerSideToolInvocations(@Nullable Boolean includeServerSideToolInvocations) {
			GoogleGenAiChatProperties.this.setIncludeServerSideToolInvocations(includeServerSideToolInvocations);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.safety-settings")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<GoogleGenAiSafetySetting> getSafetySettings() {
			return GoogleGenAiChatProperties.this.getSafetySettings();
		}

		public void setSafetySettings(@Nullable List<GoogleGenAiSafetySetting> safetySettings) {
			GoogleGenAiChatProperties.this.setSafetySettings(safetySettings);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.google.genai.chat.labels")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, String> getLabels() {
			return GoogleGenAiChatProperties.this.getLabels();
		}

		public void setLabels(@Nullable Map<String, String> labels) {
			GoogleGenAiChatProperties.this.setLabels(labels);
		}

	}

}
