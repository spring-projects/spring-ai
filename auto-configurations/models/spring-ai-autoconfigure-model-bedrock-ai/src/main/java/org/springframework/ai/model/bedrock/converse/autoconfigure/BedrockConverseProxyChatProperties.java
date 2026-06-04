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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Bedrock Converse.
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Sébastien Deleuze
 * @since 1.0.0
 */
@ConfigurationProperties(BedrockConverseProxyChatProperties.CONFIG_PREFIX)
public class BedrockConverseProxyChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.converse.chat";

	/**
	 * whether Bedrock functionality should be enabled.
	 */
	private boolean enabled;

	private @Nullable String model;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer maxTokens;

	private @Nullable Double presencePenalty;

	private Map<String, String> requestParameters = new HashMap<>();

	private @Nullable List<String> stopSequences;

	private @Nullable Double temperature;

	private @Nullable Integer topK;

	private @Nullable Double topP;

	private @Nullable BedrockCacheProperties cacheOptions;

	private Options options = new Options();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public Map<String, String> getRequestParameters() {
		return this.requestParameters;
	}

	public void setRequestParameters(Map<String, String> requestParameters) {
		this.requestParameters = requestParameters;
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

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable BedrockCacheProperties getCacheOptions() {
		return this.cacheOptions;
	}

	public void setCacheOptions(@Nullable BedrockCacheProperties cacheOptions) {
		this.cacheOptions = cacheOptions;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public BedrockChatOptions toOptions() {
		return BedrockChatOptions.builder()
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.requestParameters(this.requestParameters)
			.stopSequences(this.stopSequences)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			.cacheOptions(this.cacheOptions != null ? this.cacheOptions.toOptions() : null)
			.build();
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return BedrockConverseProxyChatProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			BedrockConverseProxyChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return BedrockConverseProxyChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			BedrockConverseProxyChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return BedrockConverseProxyChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			BedrockConverseProxyChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return BedrockConverseProxyChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			BedrockConverseProxyChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.request-parameters")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Map<String, String> getRequestParameters() {
			return BedrockConverseProxyChatProperties.this.getRequestParameters();
		}

		public void setRequestParameters(Map<String, String> requestParameters) {
			BedrockConverseProxyChatProperties.this.setRequestParameters(requestParameters);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.stop-sequences")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStopSequences() {
			return BedrockConverseProxyChatProperties.this.getStopSequences();
		}

		public void setStopSequences(@Nullable List<String> stopSequences) {
			BedrockConverseProxyChatProperties.this.setStopSequences(stopSequences);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return BedrockConverseProxyChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			BedrockConverseProxyChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.top-k")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopK() {
			return BedrockConverseProxyChatProperties.this.getTopK();
		}

		public void setTopK(@Nullable Integer topK) {
			BedrockConverseProxyChatProperties.this.setTopK(topK);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return BedrockConverseProxyChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			BedrockConverseProxyChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.bedrock.converse.chat.cache-options")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable BedrockCacheProperties getCacheOptions() {
			return BedrockConverseProxyChatProperties.this.getCacheOptions();
		}

		public void setCacheOptions(@Nullable BedrockCacheProperties cacheOptions) {
			BedrockConverseProxyChatProperties.this.setCacheOptions(cacheOptions);
		}

	}

}
