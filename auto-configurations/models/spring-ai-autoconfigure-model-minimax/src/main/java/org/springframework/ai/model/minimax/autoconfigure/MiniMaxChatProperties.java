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

package org.springframework.ai.model.minimax.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for MiniMax chat model.
 *
 * @author Geng Rong
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(MiniMaxChatProperties.CONFIG_PREFIX)
public class MiniMaxChatProperties extends MiniMaxParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.minimax.chat";

	public static final String DEFAULT_CHAT_MODEL = MiniMaxApi.ChatModel.ABAB_5_5_Chat.value;

	private String model = DEFAULT_CHAT_MODEL;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer maxTokens;

	private @Nullable Integer n;

	private @Nullable Double presencePenalty;

	private MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat;

	private @Nullable Integer seed;

	private @Nullable List<String> stop;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Boolean maskSensitiveInfo;

	private @Nullable List<MiniMaxApi.FunctionTool> tools;

	private @Nullable String toolChoice;

	private @Nullable Boolean internalToolExecutionEnabled;

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

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
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

	public @Nullable Boolean getMaskSensitiveInfo() {
		return this.maskSensitiveInfo;
	}

	public void setMaskSensitiveInfo(@Nullable Boolean maskSensitiveInfo) {
		this.maskSensitiveInfo = maskSensitiveInfo;
	}

	public @Nullable List<MiniMaxApi.FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(@Nullable List<MiniMaxApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public @Nullable String getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(@Nullable String toolChoice) {
		this.toolChoice = toolChoice;
	}

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public MiniMaxChatOptions toOptions() {
		MiniMaxChatOptions.Builder builder = MiniMaxChatOptions.builder();
		builder.model(this.model);
		if (this.frequencyPenalty != null) {
			builder.frequencyPenalty(this.frequencyPenalty);
		}
		if (this.maxTokens != null) {
			builder.maxTokens(this.maxTokens);
		}
		if (this.n != null) {
			builder.N(this.n);
		}
		if (this.presencePenalty != null) {
			builder.presencePenalty(this.presencePenalty);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.seed != null) {
			builder.seed(this.seed);
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
		if (this.maskSensitiveInfo != null) {
			builder.maskSensitiveInfo(this.maskSensitiveInfo);
		}
		if (this.tools != null) {
			builder.tools(this.tools);
		}
		if (this.toolChoice != null) {
			builder.toolChoice(this.toolChoice);
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getModel() {
			return MiniMaxChatProperties.this.getModel();
		}

		public void setModel(String model) {
			MiniMaxChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return MiniMaxChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			MiniMaxChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return MiniMaxChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			MiniMaxChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getN() {
			return MiniMaxChatProperties.this.getN();
		}

		public void setN(@Nullable Integer n) {
			MiniMaxChatProperties.this.setN(n);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return MiniMaxChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			MiniMaxChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat getResponseFormat() {
			return MiniMaxChatProperties.this.getResponseFormat();
		}

		public void setResponseFormat(MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat) {
			MiniMaxChatProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getSeed() {
			return MiniMaxChatProperties.this.getSeed();
		}

		public void setSeed(@Nullable Integer seed) {
			MiniMaxChatProperties.this.setSeed(seed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.stop")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStop() {
			return MiniMaxChatProperties.this.getStop();
		}

		public void setStop(@Nullable List<String> stop) {
			MiniMaxChatProperties.this.setStop(stop);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return MiniMaxChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			MiniMaxChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return MiniMaxChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			MiniMaxChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.mask-sensitive-info")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getMaskSensitiveInfo() {
			return MiniMaxChatProperties.this.getMaskSensitiveInfo();
		}

		public void setMaskSensitiveInfo(@Nullable Boolean maskSensitiveInfo) {
			MiniMaxChatProperties.this.setMaskSensitiveInfo(maskSensitiveInfo);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.tools")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<MiniMaxApi.FunctionTool> getTools() {
			return MiniMaxChatProperties.this.getTools();
		}

		public void setTools(@Nullable List<MiniMaxApi.FunctionTool> tools) {
			MiniMaxChatProperties.this.setTools(tools);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.tool-choice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getToolChoice() {
			return MiniMaxChatProperties.this.getToolChoice();
		}

		public void setToolChoice(@Nullable String toolChoice) {
			MiniMaxChatProperties.this.setToolChoice(toolChoice);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.minimax.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return MiniMaxChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			MiniMaxChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

	}

}
