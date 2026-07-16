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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ReasoningEffort;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.FunctionTool;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Mistral AI chat.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiChatProperties.CONFIG_PREFIX)
public class MistralAiChatProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.chat";

	private @Nullable String model;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Integer maxTokens;

	private @Nullable Boolean safePrompt;

	private @Nullable Integer randomSeed;

	private @Nullable ResponseFormat responseFormat;

	private @Nullable List<String> stop;

	private @Nullable Double frequencyPenalty;

	private @Nullable Double presencePenalty;

	private @Nullable Integer n;

	private @Nullable List<FunctionTool> tools;

	private @Nullable ToolChoice toolChoice;

	private @Nullable ReasoningEffort reasoningEffort;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
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

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Boolean getSafePrompt() {
		return this.safePrompt;
	}

	public void setSafePrompt(@Nullable Boolean safePrompt) {
		this.safePrompt = safePrompt;
	}

	public @Nullable Integer getRandomSeed() {
		return this.randomSeed;
	}

	public void setRandomSeed(@Nullable Integer randomSeed) {
		this.randomSeed = randomSeed;
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

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public @Nullable List<FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(@Nullable List<FunctionTool> tools) {
		this.tools = tools;
	}

	public @Nullable ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(@Nullable ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
	}

	public @Nullable ReasoningEffort getReasoningEffort() {
		return this.reasoningEffort;
	}

	public void setReasoningEffort(@Nullable ReasoningEffort reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	public MistralAiChatOptions toOptions() {
		return MistralAiChatOptions.builder()
			.model(this.model)
			.temperature(this.temperature)
			.topP(this.topP)
			.maxTokens(this.maxTokens)
			.safePrompt(this.safePrompt)
			.randomSeed(this.randomSeed)
			.responseFormat(this.responseFormat)
			.stop(this.stop)
			.frequencyPenalty(this.frequencyPenalty)
			.presencePenalty(this.presencePenalty)
			.n(this.n)
			.tools(this.tools)
			.toolChoice(this.toolChoice)
			.reasoningEffort(this.reasoningEffort)
			.build();
	}

	public MistralAiChatProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return MistralAiChatProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			MistralAiChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return MistralAiChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			MistralAiChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return MistralAiChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			MistralAiChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return MistralAiChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			MistralAiChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.safe-prompt")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getSafePrompt() {
			return MistralAiChatProperties.this.getSafePrompt();
		}

		public void setSafePrompt(@Nullable Boolean safePrompt) {
			MistralAiChatProperties.this.setSafePrompt(safePrompt);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.random-seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getRandomSeed() {
			return MistralAiChatProperties.this.getRandomSeed();
		}

		public void setRandomSeed(@Nullable Integer randomSeed) {
			MistralAiChatProperties.this.setRandomSeed(randomSeed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ResponseFormat getResponseFormat() {
			return MistralAiChatProperties.this.getResponseFormat();
		}

		public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
			MistralAiChatProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.stop")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStop() {
			return MistralAiChatProperties.this.getStop();
		}

		public void setStop(@Nullable List<String> stop) {
			MistralAiChatProperties.this.setStop(stop);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return MistralAiChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			MistralAiChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return MistralAiChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			MistralAiChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getN() {
			return MistralAiChatProperties.this.getN();
		}

		public void setN(@Nullable Integer n) {
			MistralAiChatProperties.this.setN(n);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.tools")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<FunctionTool> getTools() {
			return MistralAiChatProperties.this.getTools();
		}

		public void setTools(@Nullable List<FunctionTool> tools) {
			MistralAiChatProperties.this.setTools(tools);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.chat.tool-choice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ToolChoice getToolChoice() {
			return MistralAiChatProperties.this.getToolChoice();
		}

		public void setToolChoice(@Nullable ToolChoice toolChoice) {
			MistralAiChatProperties.this.setToolChoice(toolChoice);
		}

	}

}
