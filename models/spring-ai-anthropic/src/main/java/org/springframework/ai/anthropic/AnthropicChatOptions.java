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

package org.springframework.ai.anthropic;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.ToolChoice;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Chat options for {@link AnthropicChatModel}. Supports model selection, sampling
 * parameters (temperature, topP, topK), output control (maxTokens, stopSequences), and
 * tool calling configuration.
 *
 * <p>
 * Options can be set as defaults during model construction or overridden per-request via
 * the {@link org.springframework.ai.chat.prompt.Prompt}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Austin Dase
 * @author Sebastien Deleuze
 * @since 1.0.0
 * @see AnthropicChatModel
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API</a>
 */
public class AnthropicChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	/**
	 * Default model to use for chat completions.
	 */
	public static final String DEFAULT_MODEL = Model.CLAUDE_HAIKU_4_5.asString();

	/**
	 * Default max tokens for chat completions.
	 */
	public static final Integer DEFAULT_MAX_TOKENS = 4096;

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	/**
	 * The base URL to connect to the Anthropic API. Defaults to
	 * "https://api.anthropic.com" if not specified.
	 */
	private @Nullable String baseUrl;

	/**
	 * The API key to authenticate with the Anthropic API. Can also be set via the
	 * ANTHROPIC_API_KEY environment variable.
	 */
	private @Nullable String apiKey;

	/**
	 * The model name to use for requests.
	 */
	private @Nullable String model;

	/**
	 * Request timeout for the Anthropic client. Defaults to 60 seconds if not specified.
	 */
	private @Nullable Duration timeout;

	/**
	 * Maximum number of retries for failed requests. Defaults to 2 if not specified.
	 */
	private @Nullable Integer maxRetries;

	/**
	 * Proxy settings for the Anthropic client.
	 */
	private @Nullable Proxy proxy;

	/**
	 * Custom HTTP headers to add to Anthropic client requests.
	 */
	private Map<String, String> customHeaders = new HashMap<>();

	/**
	 * Maximum number of tokens to generate in the response.
	 */
	private @Nullable Integer maxTokens;

	/**
	 * Request metadata containing user ID for abuse detection.
	 */
	private @Nullable Metadata metadata;

	/**
	 * Sequences that will cause the model to stop generating.
	 */
	private @Nullable List<String> stopSequences;

	/**
	 * Sampling temperature between 0 and 1. Higher values make output more random.
	 */
	private @Nullable Double temperature;

	/**
	 * Nucleus sampling parameter. The model considers tokens with top_p probability mass.
	 */
	private @Nullable Double topP;

	/**
	 * Only sample from the top K options for each subsequent token.
	 */
	private @Nullable Integer topK;

	/**
	 * Tool choice configuration for controlling tool usage behavior.
	 */
	private @Nullable ToolChoice toolChoice;

	/**
	 * Extended thinking configuration for Claude's reasoning capabilities.
	 */
	private @Nullable ThinkingConfigParam thinking;

	/**
	 * Whether to disable parallel tool use. When true, the model will use at most one
	 * tool per response.
	 */
	private @Nullable Boolean disableParallelToolUse;

	/**
	 * Collection of tool callbacks for tool calling.
	 */
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime.
	 */
	private Set<String> toolNames = new java.util.HashSet<>();

	/**
	 * Whether to enable internal tool execution in the chat model.
	 */
	private @Nullable Boolean internalToolExecutionEnabled;

	/**
	 * Context to be passed to tools during execution.
	 */
	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Citation documents to include in the request for citation-enabled responses.
	 */
	private List<AnthropicCitationDocument> citationDocuments = new ArrayList<>();

	/**
	 * Cache options for configuring prompt caching behavior.
	 */
	private AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.disabled();

	/**
	 * Output configuration for controlling response format and effort level. Includes
	 * structured output (JSON schema) and effort control (LOW, MEDIUM, HIGH, MAX).
	 */
	private @Nullable OutputConfig outputConfig;

	/**
	 * Per-request HTTP headers to include in the API call. Merged with model-level
	 * defaults (runtime headers take precedence). Used for beta feature headers, custom
	 * tracking, etc.
	 */
	private Map<String, String> httpHeaders = new HashMap<>();

	/**
	 * Skills container for configuring Claude Skills in the request.
	 */
	private @Nullable AnthropicSkillContainer skillContainer;

	/**
	 * Controls the geographic region for inference processing. Supported values: "us",
	 * "eu". Used for data residency compliance.
	 */
	private @Nullable String inferenceGeo;

	/**
	 * Configuration for Anthropic's built-in web search tool. When set, Claude can search
	 * the web during the conversation.
	 */
	private @Nullable AnthropicWebSearchTool webSearchTool;

	/**
	 * Determines whether to use priority capacity (if available) or standard capacity for
	 * this request. See <a href="https://docs.claude.com/en/api/service-tiers">Service
	 * Tiers</a>.
	 */
	private @Nullable AnthropicServiceTier serviceTier;

	/**
	 * Creates a new builder for AnthropicChatOptions.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public @Nullable Integer getMaxRetries() {
		return this.maxRetries;
	}

	public @Nullable Proxy getProxy() {
		return this.proxy;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public @Nullable Metadata getMetadata() {
		return this.metadata;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public @Nullable ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	public @Nullable ThinkingConfigParam getThinking() {
		return this.thinking;
	}

	public @Nullable Boolean getDisableParallelToolUse() {
		return this.disableParallelToolUse;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	public List<AnthropicCitationDocument> getCitationDocuments() {
		return this.citationDocuments;
	}

	/**
	 * Validate that all citation documents have consistent citation settings. Anthropic
	 * requires all documents to have citations enabled if any do.
	 */
	public void validateCitationConsistency() {
		if (this.citationDocuments.isEmpty()) {
			return;
		}

		boolean hasEnabledCitations = this.citationDocuments.stream()
			.anyMatch(AnthropicCitationDocument::isCitationsEnabled);
		boolean hasDisabledCitations = this.citationDocuments.stream().anyMatch(doc -> !doc.isCitationsEnabled());

		if (hasEnabledCitations && hasDisabledCitations) {
			throw new IllegalArgumentException(
					"Anthropic Citations API requires all documents to have consistent citation settings. "
							+ "Either enable citations for all documents or disable for all documents.");
		}
	}

	public AnthropicCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	public @Nullable OutputConfig getOutputConfig() {
		return this.outputConfig;
	}

	public Map<String, String> getHttpHeaders() {
		return this.httpHeaders;
	}

	public @Nullable AnthropicSkillContainer getSkillContainer() {
		return this.skillContainer;
	}

	public @Nullable String getInferenceGeo() {
		return this.inferenceGeo;
	}

	public @Nullable AnthropicWebSearchTool getWebSearchTool() {
		return this.webSearchTool;
	}

	public @Nullable AnthropicServiceTier getServiceTier() {
		return this.serviceTier;
	}

	@Override
	public @Nullable String getOutputSchema() {
		if (this.outputConfig == null) {
			return null;
		}
		return this.outputConfig.format().map(format -> {
			Map<String, JsonValue> schemaProps = format.schema()._additionalProperties();
			Map<String, Object> nativeMap = new LinkedHashMap<>();
			for (Map.Entry<String, JsonValue> entry : schemaProps.entrySet()) {
				nativeMap.put(entry.getKey(), convertJsonValueToNative(entry.getValue()));
			}
			return JSON_MAPPER.writeValueAsString(nativeMap);
		}).orElse(null);
	}

	/**
	 * Converts a {@link JsonValue} to a native Java object using the visitor pattern.
	 * Maps to null, Boolean, Number, String, List, or Map recursively.
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return the equivalent native Java object, or null for JSON null
	 */
	private static @Nullable Object convertJsonValueToNative(JsonValue jsonValue) {
		return jsonValue.accept(new JsonValue.Visitor<@Nullable Object>() {
			@Override
			public @Nullable Object visitNull() {
				return null;
			}

			@Override
			public @Nullable Object visitMissing() {
				return null;
			}

			@Override
			public Object visitBoolean(boolean value) {
				return value;
			}

			@Override
			public Object visitNumber(Number value) {
				return value;
			}

			@Override
			public Object visitString(String value) {
				return value;
			}

			@Override
			public Object visitArray(List<? extends JsonValue> values) {
				return values.stream().map(v -> convertJsonValueToNative(v)).toList();
			}

			@Override
			public Object visitObject(Map<String, ? extends JsonValue> values) {
				Map<String, Object> result = new LinkedHashMap<>();
				for (Map.Entry<String, ? extends JsonValue> entry : values.entrySet()) {
					result.put(entry.getKey(), convertJsonValueToNative(entry.getValue()));
				}
				return result;
			}
		});
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return null;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return null;
	}

	@Override
	public AnthropicChatOptions copy() {
		return mutate().build();
	}

	@Override
	public Builder mutate() {
		return builder()
			// AbstractAnthropicOptions
			.model(this.getModel())
			.baseUrl(this.getBaseUrl())
			.apiKey(this.getApiKey())
			.timeout(this.getTimeout())
			.maxRetries(this.getMaxRetries())
			.proxy(this.getProxy())
			.customHeaders(this.getCustomHeaders())
			// ChatOptions
			.frequencyPenalty(this.getFrequencyPenalty())
			.maxTokens(this.maxTokens)
			.presencePenalty(this.getPresencePenalty())
			.stopSequences(this.stopSequences)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolNames(this.getToolNames())
			.toolContext(this.getToolContext())
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// Anthropic Specific
			.metadata(this.metadata)
			.toolChoice(this.toolChoice)
			.thinking(this.thinking)
			.disableParallelToolUse(this.disableParallelToolUse)
			.citationDocuments(this.getCitationDocuments())
			.cacheOptions(this.getCacheOptions())
			.outputConfig(this.outputConfig)
			.httpHeaders(this.getHttpHeaders())
			.skillContainer(this.getSkillContainer())
			.inferenceGeo(this.inferenceGeo)
			.webSearchTool(this.webSearchTool)
			.serviceTier(this.serviceTier);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicChatOptions that)) {
			return false;
		}
		return Objects.equals(this.getModel(), that.getModel()) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.metadata, that.metadata)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.thinking, that.thinking)
				&& Objects.equals(this.disableParallelToolUse, that.disableParallelToolUse)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.citationDocuments, that.citationDocuments)
				&& Objects.equals(this.cacheOptions, that.cacheOptions)
				&& Objects.equals(this.outputConfig, that.outputConfig)
				&& Objects.equals(this.httpHeaders, that.httpHeaders)
				&& Objects.equals(this.skillContainer, that.skillContainer)
				&& Objects.equals(this.inferenceGeo, that.inferenceGeo)
				&& Objects.equals(this.webSearchTool, that.webSearchTool)
				&& Objects.equals(this.serviceTier, that.serviceTier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getModel(), this.maxTokens, this.metadata, this.stopSequences, this.temperature,
				this.topP, this.topK, this.toolChoice, this.thinking, this.disableParallelToolUse, this.toolCallbacks,
				this.toolNames, this.internalToolExecutionEnabled, this.toolContext, this.citationDocuments,
				this.cacheOptions, this.outputConfig, this.httpHeaders, this.skillContainer, this.inferenceGeo,
				this.webSearchTool, this.serviceTier);
	}

	@Override
	public String toString() {
		return "AnthropicChatOptions{" + "model='" + this.getModel() + '\'' + ", maxTokens=" + this.maxTokens
				+ ", metadata=" + this.metadata + ", stopSequences=" + this.stopSequences + ", temperature="
				+ this.temperature + ", topP=" + this.topP + ", topK=" + this.topK + ", toolChoice=" + this.toolChoice
				+ ", thinking=" + this.thinking + ", disableParallelToolUse=" + this.disableParallelToolUse
				+ ", toolCallbacks=" + this.toolCallbacks + ", toolNames=" + this.toolNames
				+ ", internalToolExecutionEnabled=" + this.internalToolExecutionEnabled + ", toolContext="
				+ this.toolContext + ", citationDocuments=" + this.citationDocuments + ", cacheOptions="
				+ this.cacheOptions + ", outputConfig=" + this.outputConfig + ", httpHeaders=" + this.httpHeaders
				+ ", skillContainer=" + this.skillContainer + ", inferenceGeo=" + this.inferenceGeo + ", webSearchTool="
				+ this.webSearchTool + ", serviceTier=" + this.serviceTier + '}';
	}

	/**
	 * Builder for creating {@link AnthropicChatOptions} instances.
	 */
	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> implements StructuredOutputChatOptions.Builder<B> {

		@Override
		public B clone() {
			AbstractBuilder<B> copy = super.clone();
			if (!this.customHeaders.isEmpty()) {
				copy.customHeaders = new HashMap<>(this.customHeaders);
			}
			if (!this.citationDocuments.isEmpty()) {
				copy.citationDocuments = new ArrayList<>(this.citationDocuments);
			}
			if (!this.httpHeaders.isEmpty()) {
				copy.httpHeaders = new HashMap<>(this.httpHeaders);
			}
			return (B) copy;
		}

		// AbstractAnthropicOptions fields
		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable Duration timeout;

		private @Nullable Integer maxRetries;

		private @Nullable Proxy proxy;

		private Map<String, String> customHeaders = new HashMap<>();

		// Anthropic-specific fields
		private @Nullable Metadata metadata;

		private @Nullable ToolChoice toolChoice;

		private @Nullable ThinkingConfigParam thinking;

		private @Nullable Boolean disableParallelToolUse;

		private List<AnthropicCitationDocument> citationDocuments = new ArrayList<>();

		private AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.disabled();

		private @Nullable OutputConfig outputConfig;

		private Map<String, String> httpHeaders = new HashMap<>();

		private @Nullable AnthropicSkillContainer skillContainer;

		private @Nullable String inferenceGeo;

		private @Nullable AnthropicWebSearchTool webSearchTool;

		private @Nullable AnthropicServiceTier serviceTier;

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			if (outputSchema != null) {
				Map<String, Object> schemaMap = JSON_MAPPER.readValue(outputSchema,
						new TypeReference<Map<String, Object>>() {
						});
				JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
				for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
					// Strip JSON Schema meta-fields not supported by the Anthropic
					// API
					if ("$schema".equals(entry.getKey()) || "$defs".equals(entry.getKey())) {
						continue;
					}
					schemaBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
				}
				JsonOutputFormat jsonOutputFormat = JsonOutputFormat.builder().schema(schemaBuilder.build()).build();
				OutputConfig.Builder configBuilder = OutputConfig.builder().format(jsonOutputFormat);
				if (this.outputConfig != null) {
					this.outputConfig.effort().ifPresent(configBuilder::effort);
				}
				this.outputConfig = configBuilder.build();
			}
			else {
				this.outputConfig = null;
			}
			return self();
		}

		public B baseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
			return self();
		}

		public B apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return self();
		}

		public B timeout(@Nullable Duration timeout) {
			this.timeout = timeout;
			return self();
		}

		public B maxRetries(@Nullable Integer maxRetries) {
			this.maxRetries = maxRetries;
			return self();
		}

		public B proxy(@Nullable Proxy proxy) {
			this.proxy = proxy;
			return self();
		}

		public B customHeaders(Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
			return self();
		}

		public B model(@Nullable Model model) {
			if (model != null) {
				this.model(model.asString());
			}
			else {
				this.model((String) null);
			}
			return self();
		}

		public B metadata(@Nullable Metadata metadata) {
			this.metadata = metadata;
			return self();
		}

		public B toolChoice(@Nullable ToolChoice toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B thinking(@Nullable ThinkingConfigParam thinking) {
			this.thinking = thinking;
			return self();
		}

		/**
		 * Convenience method to enable thinking with a specific budget in tokens.
		 * @param budgetTokens the thinking budget (must be >= 1024 and < maxTokens)
		 */
		public B thinkingEnabled(long budgetTokens) {
			return thinking(
					ThinkingConfigParam.ofEnabled(ThinkingConfigEnabled.builder().budgetTokens(budgetTokens).build()));
		}

		/**
		 * Convenience method to enable thinking with a specific budget and display
		 * setting.
		 * @param budgetTokens the thinking budget (must be >= 1024 and < maxTokens)
		 * @param display controls how thinking content appears in the response
		 * (SUMMARIZED or OMITTED)
		 */
		public B thinkingEnabled(long budgetTokens, ThinkingConfigEnabled.Display display) {
			return thinking(ThinkingConfigParam
				.ofEnabled(ThinkingConfigEnabled.builder().budgetTokens(budgetTokens).display(display).build()));
		}

		/**
		 * Convenience method to let Claude adaptively decide whether to think.
		 */
		public B thinkingAdaptive() {
			return thinking(ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder().build()));
		}

		/**
		 * Convenience method to let Claude adaptively decide whether to think, with a
		 * display setting.
		 * @param display controls how thinking content appears in the response
		 * (SUMMARIZED or OMITTED)
		 */
		public B thinkingAdaptive(ThinkingConfigAdaptive.Display display) {
			return thinking(ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder().display(display).build()));
		}

		/**
		 * Convenience method to explicitly disable thinking.
		 */
		public B thinkingDisabled() {
			return thinking(ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build()));
		}

		public B disableParallelToolUse(@Nullable Boolean disableParallelToolUse) {
			this.disableParallelToolUse = disableParallelToolUse;
			return self();
		}

		public B citationDocuments(List<AnthropicCitationDocument> citationDocuments) {
			Assert.notNull(citationDocuments, "citationDocuments cannot be null");
			this.citationDocuments = new ArrayList<>(citationDocuments);
			return self();
		}

		public B citationDocuments(AnthropicCitationDocument... citationDocuments) {
			Assert.notNull(citationDocuments, "citationDocuments cannot be null");
			this.citationDocuments.addAll(java.util.Arrays.asList(citationDocuments));
			return self();
		}

		public B addCitationDocument(AnthropicCitationDocument citationDocument) {
			Assert.notNull(citationDocument, "citationDocument cannot be null");
			this.citationDocuments.add(citationDocument);
			return self();
		}

		public B cacheOptions(AnthropicCacheOptions cacheOptions) {
			Assert.notNull(cacheOptions, "cacheOptions cannot be null");
			this.cacheOptions = cacheOptions;
			return self();
		}

		/**
		 * Sets the output configuration for controlling response format and effort.
		 * @param outputConfig the output configuration
		 * @return this builder
		 */
		public B outputConfig(@Nullable OutputConfig outputConfig) {
			this.outputConfig = outputConfig;
			return self();
		}

		/**
		 * Convenience method to set the effort level for the model's response.
		 * @param effort the desired effort level (LOW, MEDIUM, HIGH, MAX)
		 * @return this builder
		 */
		public B effort(OutputConfig.Effort effort) {
			OutputConfig.Builder configBuilder = OutputConfig.builder().effort(effort);
			if (this.outputConfig != null) {
				this.outputConfig.format().ifPresent(configBuilder::format);
			}
			this.outputConfig = configBuilder.build();
			return self();
		}

		public B httpHeaders(Map<String, String> httpHeaders) {
			this.httpHeaders = new HashMap<>(httpHeaders);
			return self();
		}

		public B skillContainer(@Nullable AnthropicSkillContainer skillContainer) {
			this.skillContainer = skillContainer;
			return self();
		}

		/**
		 * Enables Anthropic's built-in web search tool with the given configuration.
		 * @param webSearchTool the web search configuration
		 * @return this builder
		 */
		public B webSearchTool(@Nullable AnthropicWebSearchTool webSearchTool) {
			this.webSearchTool = webSearchTool;
			return self();
		}

		/**
		 * Sets the service tier for capacity routing.
		 * @param serviceTier the service tier (AUTO or STANDARD_ONLY)
		 * @return this builder
		 */
		public B serviceTier(@Nullable AnthropicServiceTier serviceTier) {
			this.serviceTier = serviceTier;
			return self();
		}

		public B skill(String skillIdOrName) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			AnthropicSkill prebuilt = AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill());
			}
			return this.skill(new AnthropicSkillRecord(AnthropicSkillType.CUSTOM, skillIdOrName));
		}

		public B skill(String skillIdOrName, String version) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			Assert.hasText(version, "Version cannot be empty");
			AnthropicSkill prebuilt = AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill(version));
			}
			return this.skill(new AnthropicSkillRecord(AnthropicSkillType.CUSTOM, skillIdOrName, version));
		}

		public B skill(AnthropicSkill anthropicSkill) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			return this.skill(anthropicSkill.toSkill());
		}

		public B skill(AnthropicSkill anthropicSkill, String version) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			Assert.hasText(version, "Version cannot be empty");
			return this.skill(anthropicSkill.toSkill(version));
		}

		public B skill(AnthropicSkillRecord skill) {
			Assert.notNull(skill, "Skill cannot be null");
			if (this.skillContainer == null) {
				this.skillContainer = AnthropicSkillContainer.builder().skill(skill).build();
			}
			else {
				List<AnthropicSkillRecord> existingSkills = new ArrayList<>(this.skillContainer.getSkills());
				existingSkills.add(skill);
				this.skillContainer = new AnthropicSkillContainer(existingSkills);
			}
			return self();
		}

		public B skills(String... skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			for (String skillId : skillIds) {
				this.skill(skillId);
			}
			return self();
		}

		public B skills(List<String> skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			skillIds.forEach(this::skill);
			return self();
		}

		/**
		 * Sets the geographic region for inference processing.
		 * @param inferenceGeo the region identifier ("us" or "eu")
		 * @return this builder
		 */
		public B inferenceGeo(@Nullable String inferenceGeo) {
			this.inferenceGeo = inferenceGeo;
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> options) {
				if (options.baseUrl != null) {
					this.baseUrl = options.baseUrl;
				}
				if (options.apiKey != null) {
					this.apiKey = options.apiKey;
				}
				if (options.timeout != null) {
					this.timeout = options.timeout;
				}
				if (options.maxRetries != null) {
					this.maxRetries = options.maxRetries;
				}
				if (options.proxy != null) {
					this.proxy = options.proxy;
				}
				if (!options.customHeaders.isEmpty()) {
					this.customHeaders = options.customHeaders;
				}
				if (options.metadata != null) {
					this.metadata = options.metadata;
				}
				if (options.toolChoice != null) {
					this.toolChoice = options.toolChoice;
				}
				if (options.thinking != null) {
					this.thinking = options.thinking;
				}
				if (options.disableParallelToolUse != null) {
					this.disableParallelToolUse = options.disableParallelToolUse;
				}
				if (!options.citationDocuments.isEmpty()) {
					this.citationDocuments = options.citationDocuments;
				}
				if (options.cacheOptions != null && options.cacheOptions.getStrategy() != AnthropicCacheStrategy.NONE) {
					this.cacheOptions = options.cacheOptions;
				}
				if (options.outputConfig != null) {
					this.outputConfig = options.outputConfig;
				}
				if (!options.httpHeaders.isEmpty()) {
					this.httpHeaders = options.httpHeaders;
				}
				if (options.skillContainer != null) {
					this.skillContainer = options.skillContainer;
				}
				if (options.inferenceGeo != null) {
					this.inferenceGeo = options.inferenceGeo;
				}
				if (options.webSearchTool != null) {
					this.webSearchTool = options.webSearchTool;
				}
				if (options.serviceTier != null) {
					this.serviceTier = options.serviceTier;
				}
			}
			return self();
		}

		@SuppressWarnings("NullAway")
		public AnthropicChatOptions build() {
			AnthropicChatOptions options = new AnthropicChatOptions();
			// AbstractAnthropicOptions fields
			options.model = this.model;
			options.baseUrl = this.baseUrl;
			options.apiKey = this.apiKey;
			options.timeout = this.timeout;
			options.maxRetries = this.maxRetries;
			options.proxy = this.proxy;
			options.customHeaders = this.customHeaders;
			// ChatOptions fields
			options.maxTokens = this.maxTokens;
			options.stopSequences = this.stopSequences;
			options.temperature = this.temperature;
			options.topP = this.topP;
			options.topK = this.topK;
			// ToolCallingChatOptions fields
			options.toolCallbacks = this.toolCallbacks == null ? new ArrayList<>()
					: new ArrayList<>(this.toolCallbacks);
			options.toolNames = this.toolNames == null ? new HashSet<>() : new HashSet<>(this.toolNames);
			options.internalToolExecutionEnabled = this.internalToolExecutionEnabled;
			options.toolContext = this.toolContext == null ? new HashMap<>() : new HashMap<>(this.toolContext);
			// Anthropic-specific fields
			options.metadata = this.metadata;
			options.toolChoice = this.toolChoice;
			options.thinking = this.thinking;
			options.disableParallelToolUse = this.disableParallelToolUse;
			options.citationDocuments = this.citationDocuments;
			options.cacheOptions = this.cacheOptions;
			options.outputConfig = this.outputConfig;
			options.httpHeaders = this.httpHeaders;
			options.skillContainer = this.skillContainer;
			options.inferenceGeo = this.inferenceGeo;
			options.webSearchTool = this.webSearchTool;
			options.serviceTier = this.serviceTier;
			options.validateCitationConsistency();
			return options;
		}

	}

}
