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

package org.springframework.ai.mistralai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.FunctionTool;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Options for the Mistral AI FIM API. <a href=
 * "https://docs.mistral.ai/api/#tag/fim/operation/fim_completion_v1_fim_completions_post">Docs</a>
 *
 * @author Yurii Kostyukov
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MistralAiFIMOptions implements ModelOptions {

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("model") String model;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Nucleus sampling, where the model considers the results of the tokens with top_p
	 * probability mass. So 0.1 means only the tokens comprising the top 10% probability
	 * mass are considered. We generally recommend altering this or temperature but not
	 * both.
	 */
	private @JsonProperty("top_p") Double topP;

	/**
	 * The maximum number of tokens to generate in the completion. The token count of your
	 * prompt plus max_tokens cannot exceed the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * Stop generation if this token is detected. Or if one of these tokens is detected
	 * when providing an array.
	 */
	private @JsonProperty("stop") List<String> stop;

	/**
	 * The seed to use for random sampling. If set, different calls will generate
	 * deterministic results.
	 */
	private @JsonProperty("random_seed") Integer randomSeed;

	/**
	 * Min Tokens (integer) or Min Tokens (null) (Min Tokens) The minimum number of tokens
	 * to generate in the completion.
	 */
	private @JsonProperty("min_tokens") Integer minTokens;

	public static Builder builder() {
		return new Builder();
	}

	public static MistralAiFIMOptions fromOptions(MistralAiFIMOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.maxTokens(fromOptions.getMaxTokens())
			.stop(fromOptions.getStop())
			.randomSeed(fromOptions.getRandomSeed())
			.minTokens(fromOptions.getMinTokens())
			.build();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public Integer getMinTokens() {
		return this.minTokens;
	}

	public void setMinTokens(Integer maxTokens) {
		this.minTokens = maxTokens;
	}

	public Integer getRandomSeed() {
		return this.randomSeed;
	}

	public void setRandomSeed(Integer randomSeed) {
		this.randomSeed = randomSeed;
	}

	@JsonIgnore
	public List<String> getStopSequences() {
		return getStop();
	}

	@JsonIgnore
	public void setStopSequences(List<String> stopSequences) {
		setStop(stopSequences);
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public MistralAiFIMOptions copy() {
		return fromOptions(this);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.temperature, this.topP, this.maxTokens, this.stop, this.randomSeed,
				this.minTokens);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		MistralAiFIMOptions other = (MistralAiFIMOptions) obj;

		return Objects.equals(this.model, other.model) && Objects.equals(this.temperature, other.temperature)
				&& Objects.equals(this.topP, other.topP) && Objects.equals(this.maxTokens, other.maxTokens)
				&& Objects.equals(this.stop, other.stop) && Objects.equals(this.minTokens, other.minTokens)
				&& Objects.equals(this.randomSeed, other.randomSeed);
	}

	public static class Builder {

		private final MistralAiFIMOptions options = new MistralAiFIMOptions();

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder model(MistralAiApi.ChatModel chatModel) {
			this.options.setModel(chatModel.getName());
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder minTokens(Integer minTokens) {
			this.options.setMinTokens(minTokens);
			return this;
		}

		public Builder randomSeed(Integer randomSeed) {
			this.options.setRandomSeed(randomSeed);
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.setStop(stop);
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

		public MistralAiFIMOptions build() {
			return this.options;
		}

	}

}
