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

package org.springframework.ai.model.deepseek.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for DeepSeek chat client.
 *
 * @author Geng Rong
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(DeepSeekChatProperties.CONFIG_PREFIX)
public class DeepSeekChatProperties extends DeepSeekParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.deepseek.chat";

	public static final String DEFAULT_CHAT_MODEL = DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue();

	public static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

	public static final String DEFAULT_BETA_PREFIX_PATH = "/beta";

	/**
	 * Enable DeepSeek chat client.
	 */
	private boolean enabled = true;

	private String completionsPath = DEFAULT_COMPLETIONS_PATH;

	private String betaPrefixPath = DEFAULT_BETA_PREFIX_PATH;

	private String model = DEFAULT_CHAT_MODEL;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer maxTokens;

	private @Nullable Double presencePenalty;

	private @Nullable ResponseFormat responseFormat;

	private @Nullable List<String> stop;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Boolean logprobs;

	private @Nullable Integer topLogprobs;

	private @Nullable Boolean internalToolExecutionEnabled;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getCompletionsPath() {
		return this.completionsPath;
	}

	public void setCompletionsPath(String completionsPath) {
		this.completionsPath = completionsPath;
	}

	public String getBetaPrefixPath() {
		return this.betaPrefixPath;
	}

	public void setBetaPrefixPath(String betaPrefixPath) {
		this.betaPrefixPath = betaPrefixPath;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
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

	public @Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable List<String> getStop() {
		return this.stop;
	}

	public void setStop(@Nullable List<String> stop) {
		this.stop = stop;
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

	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(@Nullable Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(@Nullable Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public DeepSeekChatOptions toOptions() {
		DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder();
		builder.model(this.model);
		if (this.frequencyPenalty != null) {
			builder.frequencyPenalty(this.frequencyPenalty);
		}
		if (this.maxTokens != null) {
			builder.maxTokens(this.maxTokens);
		}
		if (this.presencePenalty != null) {
			builder.presencePenalty(this.presencePenalty);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.stop != null) {
			builder.stop(this.stop);
		}
		if (this.temperature != null) {
			builder.temperature(this.temperature);
		}
		if (this.topP != null) {
			builder.topP(this.topP);
		}
		if (this.logprobs != null) {
			builder.logprobs(this.logprobs);
		}
		if (this.topLogprobs != null) {
			builder.topLogprobs(this.topLogprobs);
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getModel() {
			return DeepSeekChatProperties.this.getModel();
		}

		public void setModel(String model) {
			DeepSeekChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return DeepSeekChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			DeepSeekChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return DeepSeekChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			DeepSeekChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return DeepSeekChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			DeepSeekChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ResponseFormat getResponseFormat() {
			return DeepSeekChatProperties.this.getResponseFormat();
		}

		public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
			DeepSeekChatProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.stop")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStop() {
			return DeepSeekChatProperties.this.getStop();
		}

		public void setStop(@Nullable List<String> stop) {
			DeepSeekChatProperties.this.setStop(stop);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return DeepSeekChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			DeepSeekChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return DeepSeekChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			DeepSeekChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.logprobs")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getLogprobs() {
			return DeepSeekChatProperties.this.getLogprobs();
		}

		public void setLogprobs(@Nullable Boolean logprobs) {
			DeepSeekChatProperties.this.setLogprobs(logprobs);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.top-logprobs")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopLogprobs() {
			return DeepSeekChatProperties.this.getTopLogprobs();
		}

		public void setTopLogprobs(@Nullable Integer topLogprobs) {
			DeepSeekChatProperties.this.setTopLogprobs(topLogprobs);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.deepseek.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return DeepSeekChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			DeepSeekChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

	}

}
