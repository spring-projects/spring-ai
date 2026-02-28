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

package org.springframework.ai.anthropicsdk;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Chat options for {@link AnthropicSdkChatModel}. Supports model selection, sampling
 * parameters (temperature, topP, topK), output control (maxTokens, stopSequences), and
 * tool calling configuration.
 *
 * <p>
 * Options can be set as defaults during model construction or overridden per-request via
 * the {@link org.springframework.ai.chat.prompt.Prompt}.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see AnthropicSdkChatModel
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API</a>
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicSdkChatOptions extends AbstractAnthropicSdkOptions
		implements ToolCallingChatOptions, StructuredOutputChatOptions {

	/**
	 * Default model to use for chat completions.
	 */
	public static final String DEFAULT_MODEL = Model.CLAUDE_SONNET_4_20250514.asString();

	/**
	 * Default max tokens for chat completions.
	 */
	public static final Integer DEFAULT_MAX_TOKENS = 4096;

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
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable internal tool execution in the chat model.
	 */
	@JsonIgnore
	private @Nullable Boolean internalToolExecutionEnabled;

	/**
	 * Context to be passed to tools during execution.
	 */
	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Citation documents to include in the request for citation-enabled responses.
	 */
	@JsonIgnore
	private List<AnthropicSdkCitationDocument> citationDocuments = new ArrayList<>();

	/**
	 * Cache options for configuring prompt caching behavior.
	 */
	@JsonIgnore
	private AnthropicSdkCacheOptions cacheOptions = AnthropicSdkCacheOptions.DISABLED;

	/**
	 * Output configuration for controlling response format and effort level. Includes
	 * structured output (JSON schema) and effort control (LOW, MEDIUM, HIGH, MAX).
	 */
	@JsonIgnore
	private @Nullable OutputConfig outputConfig;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Creates a new builder for AnthropicSdkChatOptions.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(@Nullable Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(@Nullable ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
	}

	public @Nullable ThinkingConfigParam getThinking() {
		return this.thinking;
	}

	public void setThinking(@Nullable ThinkingConfigParam thinking) {
		this.thinking = thinking;
	}

	public @Nullable Boolean getDisableParallelToolUse() {
		return this.disableParallelToolUse;
	}

	public void setDisableParallelToolUse(@Nullable Boolean disableParallelToolUse) {
		this.disableParallelToolUse = disableParallelToolUse;
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
	public @Nullable Boolean getInternalToolExecutionEnabled() {
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
		this.toolContext = toolContext;
	}

	public List<AnthropicSdkCitationDocument> getCitationDocuments() {
		return this.citationDocuments;
	}

	public void setCitationDocuments(List<AnthropicSdkCitationDocument> citationDocuments) {
		Assert.notNull(citationDocuments, "citationDocuments cannot be null");
		this.citationDocuments = citationDocuments;
	}

	public AnthropicSdkCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	public void setCacheOptions(AnthropicSdkCacheOptions cacheOptions) {
		Assert.notNull(cacheOptions, "cacheOptions cannot be null");
		this.cacheOptions = cacheOptions;
	}

	@JsonIgnore
	public @Nullable OutputConfig getOutputConfig() {
		return this.outputConfig;
	}

	public void setOutputConfig(@Nullable OutputConfig outputConfig) {
		this.outputConfig = outputConfig;
	}

	@Override
	@JsonIgnore
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
			try {
				return OBJECT_MAPPER.writeValueAsString(nativeMap);
			}
			catch (JsonProcessingException ex) {
				throw new RuntimeException("Failed to convert output schema to JSON string", ex);
			}
		}).orElse(null);
	}

	@Override
	@JsonIgnore
	public void setOutputSchema(@Nullable String outputSchema) {
		if (outputSchema == null) {
			this.outputConfig = null;
			return;
		}
		try {
			Map<String, Object> schemaMap = OBJECT_MAPPER.readValue(outputSchema,
					new TypeReference<Map<String, Object>>() {
					});
			JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
			for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
				schemaBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
			}
			JsonOutputFormat jsonOutputFormat = JsonOutputFormat.builder().schema(schemaBuilder.build()).build();
			OutputConfig.Builder configBuilder = OutputConfig.builder().format(jsonOutputFormat);
			if (this.outputConfig != null) {
				this.outputConfig.effort().ifPresent(configBuilder::effort);
			}
			this.outputConfig = configBuilder.build();
		}
		catch (JsonProcessingException ex) {
			throw new RuntimeException("Failed to parse output schema JSON: " + outputSchema, ex);
		}
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
		// Not supported by Anthropic API
		return null;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		// Not supported by Anthropic API
		return null;
	}

	@Override
	public AnthropicSdkChatOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicSdkChatOptions that)) {
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
				&& Objects.equals(this.outputConfig, that.outputConfig);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getModel(), this.maxTokens, this.metadata, this.stopSequences, this.temperature,
				this.topP, this.topK, this.toolChoice, this.thinking, this.disableParallelToolUse, this.toolCallbacks,
				this.toolNames, this.internalToolExecutionEnabled, this.toolContext, this.citationDocuments,
				this.cacheOptions, this.outputConfig);
	}

	@Override
	public String toString() {
		return "AnthropicSdkChatOptions{" + "model='" + this.getModel() + '\'' + ", maxTokens=" + this.maxTokens
				+ ", metadata=" + this.metadata + ", stopSequences=" + this.stopSequences + ", temperature="
				+ this.temperature + ", topP=" + this.topP + ", topK=" + this.topK + ", toolChoice=" + this.toolChoice
				+ ", thinking=" + this.thinking + ", disableParallelToolUse=" + this.disableParallelToolUse
				+ ", toolCallbacks=" + this.toolCallbacks + ", toolNames=" + this.toolNames
				+ ", internalToolExecutionEnabled=" + this.internalToolExecutionEnabled + ", toolContext="
				+ this.toolContext + ", citationDocuments=" + this.citationDocuments + ", cacheOptions="
				+ this.cacheOptions + ", outputConfig=" + this.outputConfig + '}';
	}

	/**
	 * Builder for creating {@link AnthropicSdkChatOptions} instances.
	 */
	public static final class Builder {

		private final AnthropicSdkChatOptions options = new AnthropicSdkChatOptions();

		private Builder() {
		}

		/**
		 * Copies all settings from an existing options instance.
		 * @param fromOptions the options to copy from
		 * @return this builder
		 */
		public Builder from(AnthropicSdkChatOptions fromOptions) {
			// Parent class fields
			this.options.setBaseUrl(fromOptions.getBaseUrl());
			this.options.setApiKey(fromOptions.getApiKey());
			this.options.setModel(fromOptions.getModel());
			this.options.setTimeout(fromOptions.getTimeout());
			this.options.setMaxRetries(fromOptions.getMaxRetries());
			this.options.setProxy(fromOptions.getProxy());
			this.options.setCustomHeaders(fromOptions.getCustomHeaders() != null
					? new HashMap<>(fromOptions.getCustomHeaders()) : new HashMap<>());
			// Child class fields
			this.options.setMaxTokens(fromOptions.getMaxTokens());
			this.options.setMetadata(fromOptions.getMetadata());
			this.options.setStopSequences(
					fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null);
			this.options.setTemperature(fromOptions.getTemperature());
			this.options.setTopP(fromOptions.getTopP());
			this.options.setTopK(fromOptions.getTopK());
			this.options.setToolChoice(fromOptions.getToolChoice());
			this.options.setThinking(fromOptions.getThinking());
			this.options.setDisableParallelToolUse(fromOptions.getDisableParallelToolUse());
			this.options.setToolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()));
			this.options.setToolNames(new HashSet<>(fromOptions.getToolNames()));
			this.options.setInternalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled());
			this.options.setToolContext(new HashMap<>(fromOptions.getToolContext()));
			this.options.setCitationDocuments(new ArrayList<>(fromOptions.getCitationDocuments()));
			this.options.setCacheOptions(fromOptions.getCacheOptions());
			this.options.setOutputConfig(fromOptions.getOutputConfig());
			return this;
		}

		/**
		 * Merges non-null settings from another options instance.
		 * @param from the options to merge from
		 * @return this builder
		 */
		public Builder merge(AnthropicSdkChatOptions from) {
			// Parent class fields
			if (from.getBaseUrl() != null) {
				this.options.setBaseUrl(from.getBaseUrl());
			}
			if (from.getApiKey() != null) {
				this.options.setApiKey(from.getApiKey());
			}
			if (from.getModel() != null) {
				this.options.setModel(from.getModel());
			}
			if (from.getTimeout() != null) {
				this.options.setTimeout(from.getTimeout());
			}
			if (from.getMaxRetries() != null) {
				this.options.setMaxRetries(from.getMaxRetries());
			}
			if (from.getProxy() != null) {
				this.options.setProxy(from.getProxy());
			}
			if (from.getCustomHeaders() != null && !from.getCustomHeaders().isEmpty()) {
				this.options.setCustomHeaders(new HashMap<>(from.getCustomHeaders()));
			}
			// Child class fields
			if (from.getMaxTokens() != null) {
				this.options.setMaxTokens(from.getMaxTokens());
			}
			if (from.getMetadata() != null) {
				this.options.setMetadata(from.getMetadata());
			}
			if (from.getStopSequences() != null) {
				this.options.setStopSequences(new ArrayList<>(from.getStopSequences()));
			}
			if (from.getTemperature() != null) {
				this.options.setTemperature(from.getTemperature());
			}
			if (from.getTopP() != null) {
				this.options.setTopP(from.getTopP());
			}
			if (from.getTopK() != null) {
				this.options.setTopK(from.getTopK());
			}
			if (from.getToolChoice() != null) {
				this.options.setToolChoice(from.getToolChoice());
			}
			if (from.getThinking() != null) {
				this.options.setThinking(from.getThinking());
			}
			if (from.getDisableParallelToolUse() != null) {
				this.options.setDisableParallelToolUse(from.getDisableParallelToolUse());
			}
			if (!from.getToolCallbacks().isEmpty()) {
				this.options.setToolCallbacks(new ArrayList<>(from.getToolCallbacks()));
			}
			if (!from.getToolNames().isEmpty()) {
				this.options.setToolNames(new HashSet<>(from.getToolNames()));
			}
			if (from.getInternalToolExecutionEnabled() != null) {
				this.options.setInternalToolExecutionEnabled(from.getInternalToolExecutionEnabled());
			}
			if (!from.getToolContext().isEmpty()) {
				this.options.setToolContext(new HashMap<>(from.getToolContext()));
			}
			if (!from.getCitationDocuments().isEmpty()) {
				this.options.setCitationDocuments(new ArrayList<>(from.getCitationDocuments()));
			}
			if (from.getCacheOptions() != null
					&& from.getCacheOptions().getStrategy() != AnthropicSdkCacheStrategy.NONE) {
				this.options.setCacheOptions(from.getCacheOptions());
			}
			if (from.getOutputConfig() != null) {
				this.options.setOutputConfig(from.getOutputConfig());
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder model(Model model) {
			this.options.setModel(model.asString());
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder metadata(Metadata metadata) {
			this.options.setMetadata(metadata);
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		public Builder stopSequences(String... stopSequences) {
			this.options.setStopSequences(Arrays.asList(stopSequences));
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

		public Builder toolChoice(ToolChoice toolChoice) {
			this.options.setToolChoice(toolChoice);
			return this;
		}

		public Builder thinking(ThinkingConfigParam thinking) {
			this.options.setThinking(thinking);
			return this;
		}

		/**
		 * Convenience method to enable thinking with a specific budget in tokens.
		 * @param budgetTokens the thinking budget (must be >= 1024 and < maxTokens)
		 */
		public Builder thinkingEnabled(long budgetTokens) {
			return thinking(
					ThinkingConfigParam.ofEnabled(ThinkingConfigEnabled.builder().budgetTokens(budgetTokens).build()));
		}

		/**
		 * Convenience method to let Claude adaptively decide whether to think.
		 */
		public Builder thinkingAdaptive() {
			return thinking(ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder().build()));
		}

		/**
		 * Convenience method to explicitly disable thinking.
		 */
		public Builder thinkingDisabled() {
			return thinking(ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build()));
		}

		public Builder disableParallelToolUse(Boolean disableParallelToolUse) {
			this.options.setDisableParallelToolUse(disableParallelToolUse);
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

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public Builder citationDocuments(List<AnthropicSdkCitationDocument> citationDocuments) {
			Assert.notNull(citationDocuments, "citationDocuments cannot be null");
			this.options.setCitationDocuments(new ArrayList<>(citationDocuments));
			return this;
		}

		public Builder citationDocuments(AnthropicSdkCitationDocument... citationDocuments) {
			Assert.notNull(citationDocuments, "citationDocuments cannot be null");
			this.options.citationDocuments.addAll(Arrays.asList(citationDocuments));
			return this;
		}

		public Builder addCitationDocument(AnthropicSdkCitationDocument citationDocument) {
			Assert.notNull(citationDocument, "citationDocument cannot be null");
			this.options.citationDocuments.add(citationDocument);
			return this;
		}

		public Builder cacheOptions(AnthropicSdkCacheOptions cacheOptions) {
			Assert.notNull(cacheOptions, "cacheOptions cannot be null");
			this.options.setCacheOptions(cacheOptions);
			return this;
		}

		/**
		 * Sets the output configuration for controlling response format and effort.
		 * @param outputConfig the output configuration
		 * @return this builder
		 */
		public Builder outputConfig(@Nullable OutputConfig outputConfig) {
			this.options.setOutputConfig(outputConfig);
			return this;
		}

		/**
		 * Convenience method to set structured output via JSON schema string.
		 * @param outputSchema the JSON schema string
		 * @return this builder
		 */
		public Builder outputSchema(@Nullable String outputSchema) {
			this.options.setOutputSchema(outputSchema);
			return this;
		}

		/**
		 * Convenience method to set the effort level for the model's response.
		 * @param effort the desired effort level (LOW, MEDIUM, HIGH, MAX)
		 * @return this builder
		 */
		public Builder effort(OutputConfig.Effort effort) {
			OutputConfig.Builder configBuilder = OutputConfig.builder().effort(effort);
			if (this.options.getOutputConfig() != null) {
				this.options.getOutputConfig().format().ifPresent(configBuilder::format);
			}
			this.options.setOutputConfig(configBuilder.build());
			return this;
		}

		public Builder baseUrl(String baseUrl) {
			this.options.setBaseUrl(baseUrl);
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.options.setApiKey(apiKey);
			return this;
		}

		public Builder timeout(java.time.Duration timeout) {
			this.options.setTimeout(timeout);
			return this;
		}

		public Builder maxRetries(Integer maxRetries) {
			this.options.setMaxRetries(maxRetries);
			return this;
		}

		public Builder proxy(java.net.Proxy proxy) {
			this.options.setProxy(proxy);
			return this;
		}

		public Builder customHeaders(Map<String, String> customHeaders) {
			this.options.setCustomHeaders(customHeaders);
			return this;
		}

		public AnthropicSdkChatOptions build() {
			// Create a new instance to ensure builder reusability
			AnthropicSdkChatOptions result = new AnthropicSdkChatOptions();
			// Parent class fields (AbstractAnthropicSdkOptions)
			result.setBaseUrl(this.options.getBaseUrl());
			result.setApiKey(this.options.getApiKey());
			result.setModel(this.options.getModel());
			result.setTimeout(this.options.getTimeout());
			result.setMaxRetries(this.options.getMaxRetries());
			result.setProxy(this.options.getProxy());
			result.setCustomHeaders(this.options.getCustomHeaders() != null
					? new HashMap<>(this.options.getCustomHeaders()) : new HashMap<>());
			// Child class fields (AnthropicSdkChatOptions)
			result.setMaxTokens(this.options.getMaxTokens());
			result.setMetadata(this.options.getMetadata());
			result.setStopSequences(
					this.options.getStopSequences() != null ? new ArrayList<>(this.options.getStopSequences()) : null);
			result.setTemperature(this.options.getTemperature());
			result.setTopP(this.options.getTopP());
			result.setTopK(this.options.getTopK());
			result.setToolChoice(this.options.getToolChoice());
			result.setThinking(this.options.getThinking());
			result.setDisableParallelToolUse(this.options.getDisableParallelToolUse());
			result.setToolCallbacks(new ArrayList<>(this.options.getToolCallbacks()));
			result.setToolNames(new HashSet<>(this.options.getToolNames()));
			result.setInternalToolExecutionEnabled(this.options.getInternalToolExecutionEnabled());
			result.setToolContext(new HashMap<>(this.options.getToolContext()));
			result.setCitationDocuments(new ArrayList<>(this.options.getCitationDocuments()));
			result.setCacheOptions(this.options.getCacheOptions());
			result.setOutputConfig(this.options.getOutputConfig());
			return result;
		}

	}

}
