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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.ToolChoice;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicServiceTier;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Anthropic Chat autoconfiguration properties.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 2.0.0
 */
@ConfigurationProperties(AnthropicChatProperties.CONFIG_PREFIX)
public class AnthropicChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic.chat";

	private @Nullable String model;

	private @Nullable Integer maxTokens = AnthropicChatOptions.DEFAULT_MAX_TOKENS;

	private @Nullable Metadata metadata;

	private @Nullable List<String> stopSequences;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Integer topK;

	private @Nullable ToolChoice toolChoice;

	private @Nullable ThinkingConfigParam thinking;

	private @Nullable Boolean disableParallelToolUse;

	private @Nullable Boolean internalToolExecutionEnabled;

	private @Nullable OutputConfig outputConfig;

	private @Nullable AnthropicWebSearchTool webSearchTool;

	private @Nullable AnthropicServiceTier serviceTier;

	private @Nullable String inferenceGeo;

	private @Nullable AnthropicCacheOptions cacheOptions;

	private Map<String, String> httpHeaders = new HashMap<>();

	private Options options = new Options();

	public AnthropicChatProperties() {
		this.model = AnthropicChatOptions.DEFAULT_MODEL;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

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

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public @Nullable OutputConfig getOutputConfig() {
		return this.outputConfig;
	}

	public void setOutputConfig(@Nullable OutputConfig outputConfig) {
		this.outputConfig = outputConfig;
	}

	public @Nullable AnthropicWebSearchTool getWebSearchTool() {
		return this.webSearchTool;
	}

	public void setWebSearchTool(@Nullable AnthropicWebSearchTool webSearchTool) {
		this.webSearchTool = webSearchTool;
	}

	public @Nullable AnthropicServiceTier getServiceTier() {
		return this.serviceTier;
	}

	public void setServiceTier(@Nullable AnthropicServiceTier serviceTier) {
		this.serviceTier = serviceTier;
	}

	public @Nullable String getInferenceGeo() {
		return this.inferenceGeo;
	}

	public void setInferenceGeo(@Nullable String inferenceGeo) {
		this.inferenceGeo = inferenceGeo;
	}

	public @Nullable AnthropicCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	public void setCacheOptions(@Nullable AnthropicCacheOptions cacheOptions) {
		this.cacheOptions = cacheOptions;
	}

	public Map<String, String> getHttpHeaders() {
		return this.httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	public AnthropicChatOptions toOptions() {
		AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder();
		builder.model(this.getModel());
		if (this.maxTokens != null) {
			builder.maxTokens(this.maxTokens);
		}
		if (this.metadata != null) {
			builder.metadata(this.metadata);
		}
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
		if (this.toolChoice != null) {
			builder.toolChoice(this.toolChoice);
		}
		if (this.thinking != null) {
			builder.thinking(this.thinking);
		}
		if (this.disableParallelToolUse != null) {
			builder.disableParallelToolUse(this.disableParallelToolUse);
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		if (this.outputConfig != null) {
			builder.outputConfig(this.outputConfig);
		}
		if (this.webSearchTool != null) {
			builder.webSearchTool(this.webSearchTool);
		}
		if (this.serviceTier != null) {
			builder.serviceTier(this.serviceTier);
		}
		if (this.inferenceGeo != null) {
			builder.inferenceGeo(this.inferenceGeo);
		}
		if (this.cacheOptions != null) {
			builder.cacheOptions(this.cacheOptions);
		}
		if (this.httpHeaders != null && !this.httpHeaders.isEmpty()) {
			builder.httpHeaders(this.httpHeaders);
		}
		return builder.build();
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return AnthropicChatProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			AnthropicChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return AnthropicChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			AnthropicChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.metadata")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Metadata getMetadata() {
			return AnthropicChatProperties.this.getMetadata();
		}

		public void setMetadata(@Nullable Metadata metadata) {
			AnthropicChatProperties.this.setMetadata(metadata);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.stop-sequences")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStopSequences() {
			return AnthropicChatProperties.this.getStopSequences();
		}

		public void setStopSequences(@Nullable List<String> stopSequences) {
			AnthropicChatProperties.this.setStopSequences(stopSequences);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return AnthropicChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			AnthropicChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return AnthropicChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			AnthropicChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.top-k")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopK() {
			return AnthropicChatProperties.this.getTopK();
		}

		public void setTopK(@Nullable Integer topK) {
			AnthropicChatProperties.this.setTopK(topK);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.tool-choice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ToolChoice getToolChoice() {
			return AnthropicChatProperties.this.getToolChoice();
		}

		public void setToolChoice(@Nullable ToolChoice toolChoice) {
			AnthropicChatProperties.this.setToolChoice(toolChoice);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.thinking")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ThinkingConfigParam getThinking() {
			return AnthropicChatProperties.this.getThinking();
		}

		public void setThinking(@Nullable ThinkingConfigParam thinking) {
			AnthropicChatProperties.this.setThinking(thinking);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.disable-parallel-tool-use")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getDisableParallelToolUse() {
			return AnthropicChatProperties.this.getDisableParallelToolUse();
		}

		public void setDisableParallelToolUse(@Nullable Boolean disableParallelToolUse) {
			AnthropicChatProperties.this.setDisableParallelToolUse(disableParallelToolUse);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return AnthropicChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			AnthropicChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.output-config")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable OutputConfig getOutputConfig() {
			return AnthropicChatProperties.this.getOutputConfig();
		}

		public void setOutputConfig(@Nullable OutputConfig outputConfig) {
			AnthropicChatProperties.this.setOutputConfig(outputConfig);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.web-search-tool")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable AnthropicWebSearchTool getWebSearchTool() {
			return AnthropicChatProperties.this.getWebSearchTool();
		}

		public void setWebSearchTool(@Nullable AnthropicWebSearchTool webSearchTool) {
			AnthropicChatProperties.this.setWebSearchTool(webSearchTool);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.service-tier")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable AnthropicServiceTier getServiceTier() {
			return AnthropicChatProperties.this.getServiceTier();
		}

		public void setServiceTier(@Nullable AnthropicServiceTier serviceTier) {
			AnthropicChatProperties.this.setServiceTier(serviceTier);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.inference-geo")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getInferenceGeo() {
			return AnthropicChatProperties.this.getInferenceGeo();
		}

		public void setInferenceGeo(@Nullable String inferenceGeo) {
			AnthropicChatProperties.this.setInferenceGeo(inferenceGeo);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.cache-options")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable AnthropicCacheOptions getCacheOptions() {
			return AnthropicChatProperties.this.getCacheOptions();
		}

		public void setCacheOptions(@Nullable AnthropicCacheOptions cacheOptions) {
			AnthropicChatProperties.this.setCacheOptions(cacheOptions);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.anthropic.chat.http-headers")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Map<String, String> getHttpHeaders() {
			return AnthropicChatProperties.this.getHttpHeaders();
		}

		public void setHttpHeaders(Map<String, String> httpHeaders) {
			AnthropicChatProperties.this.setHttpHeaders(httpHeaders);
		}

	}

}
