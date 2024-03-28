/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool;

public class OpenAiChatOptionsBuilder {

	private final FunctionCallingOptionsBuilder functionCallingOptionsBuilder = FunctionCallingOptionsBuilder.builder();

	private final ChatOptionsBuilder chatOptionsBuilder = ChatOptionsBuilder.builder();

	private String model;

	private Float frequencyPenalty;

	private ResponseFormat responseFormat;

	private Integer maxTokens;

	private Integer n;

	private Float presencePenalty;

	private Integer seed;

	private String toolChoice;

	private String user;

	private final List<String> stop = new ArrayList<>();

	private final List<FunctionTool> tools = new ArrayList<>();

	private final Map<String, Integer> logitBias = new HashMap<>();

	private OpenAiChatOptionsBuilder() {
	}

	public static OpenAiChatOptionsBuilder builder() {
		return new OpenAiChatOptionsBuilder();
	}

	/**
	 * Copy Constructor for {@link OpenAiChatOptionsBuilder}
	 * @param options Existing {@link OpenAiChatOptions}
	 * @return new OpenAiChatOptionsBuilder
	 */
	public static OpenAiChatOptionsBuilder builder(final OpenAiChatOptions options) {
		return builder().withFunctionCallingOptions(options)
			.withChatOptions(options)
			.withFrequencyPenalty(options.getFrequencyPenalty())
			.withPresencePenalty(options.getPresencePenalty())
			.withMaxTokens(options.getMaxTokens())
			.withLogitBias(options.getLogitBias())
			.withUser(options.getUser())
			.withModel(options.getModel())
			.withToolChoice(options.getToolChoice())
			.withTools(options.getTools())
			.withN(options.getN())
			.withSeed(options.getSeed())
			.withStop(options.getStop())
			.withResponseFormat(options.getResponseFormat());
	}

	public OpenAiChatOptions build() {
		return new OpenAiChatOptionsImpl(this);
	}

	public OpenAiChatOptionsBuilder withFunctionCallingOptions(final FunctionCallingOptions options) {
		if (options == null)
			return this;
		withFunctionCallbacks(options.getFunctionCallbacks());
		withFunctions(options.getFunctions());
		return this;
	}

	public OpenAiChatOptionsBuilder withChatOptions(final ChatOptions options) {
		if (options == null)
			return this;
		withTopP(options.getTopP());
		withTemperature(options.getTemperature());
		return this;
	}

	public OpenAiChatOptionsBuilder withModel(final String model) {
		if (model == null)
			return this;
		this.model = model;
		return this;
	}

	public OpenAiChatOptionsBuilder withFrequencyPenalty(final Float frequencyPenalty) {
		if (frequencyPenalty == null)
			return this;
		this.frequencyPenalty = frequencyPenalty;
		return this;
	}

	public OpenAiChatOptionsBuilder withLogitBias(final Map<String, Integer> logitBias) {
		if (logitBias == null)
			return this;
		this.logitBias.putAll(logitBias);
		return this;
	}

	public OpenAiChatOptionsBuilder withMaxTokens(final Integer maxTokens) {
		if (maxTokens == null)
			return this;
		this.maxTokens = maxTokens;
		return this;
	}

	public OpenAiChatOptionsBuilder withN(final Integer n) {
		if (n == null)
			return this;
		this.n = n;
		return this;
	}

	public OpenAiChatOptionsBuilder withPresencePenalty(final Float presencePenalty) {
		if (presencePenalty == null)
			return this;
		this.presencePenalty = presencePenalty;
		return this;
	}

	public OpenAiChatOptionsBuilder withResponseFormat(final ResponseFormat responseFormat) {
		if (responseFormat == null)
			return this;
		this.responseFormat = responseFormat;
		return this;
	}

	public OpenAiChatOptionsBuilder withSeed(final Integer seed) {
		if (seed == null)
			return this;
		this.seed = seed;
		return this;
	}

	public OpenAiChatOptionsBuilder withStop(final List<String> stop) {
		if (stop == null)
			return this;
		this.stop.addAll(stop);
		return this;
	}

	public OpenAiChatOptionsBuilder withTemperature(final Float temperature) {
		if (temperature == null)
			return this;
		this.chatOptionsBuilder.withTemperature(temperature);
		return this;
	}

	public OpenAiChatOptionsBuilder withTopP(final Float topP) {
		if (topP == null)
			return this;
		this.chatOptionsBuilder.withTopP(topP);
		return this;
	}

	public OpenAiChatOptionsBuilder withTools(final List<FunctionTool> tools) {
		if (tools == null)
			return this;
		this.tools.addAll(tools);
		return this;
	}

	public OpenAiChatOptionsBuilder withToolChoice(final String toolChoice) {
		if (toolChoice == null)
			return this;
		this.toolChoice = toolChoice;
		return this;
	}

	public OpenAiChatOptionsBuilder withUser(final String user) {
		if (user == null)
			return this;
		this.user = user;
		return this;
	}

	public OpenAiChatOptionsBuilder withFunctionCallbacks(final List<FunctionCallback> functionCallbacks) {
		if (functionCallbacks == null)
			return this;
		this.functionCallingOptionsBuilder.withFunctionCallbacks(functionCallbacks);
		return this;
	}

	public OpenAiChatOptionsBuilder withFunctionCallback(final FunctionCallback functionCallback) {
		if (functionCallback == null)
			return this;
		this.functionCallingOptionsBuilder.withFunctionCallback(functionCallback);
		return this;
	}

	public OpenAiChatOptionsBuilder withFunctions(final Set<String> functionNames) {
		if (functionNames == null)
			return this;
		this.functionCallingOptionsBuilder.withFunctions(functionNames);
		return this;
	}

	public OpenAiChatOptionsBuilder withFunction(final String functionName) {
		if (functionName != null)
			this.functionCallingOptionsBuilder.withFunction(functionName);
		return this;
	}

	private static class OpenAiChatOptionsImpl implements OpenAiChatOptions {

		private final ChatOptions chatOptions;

		private final FunctionCallingOptions functionCallingOptions;

		private final String model;

		private final Float frequencyPenalty;

		private final ResponseFormat responseFormat;

		private final Map<String, Integer> logitBias;

		private final Integer maxTokens;

		private final Integer n;

		private final Float presencePenalty;

		private final Integer seed;

		private final String toolChoice;

		private final String user;

		private final List<String> stop;

		private final List<FunctionTool> tools;

		private OpenAiChatOptionsImpl(final OpenAiChatOptionsBuilder builder) {
			this.chatOptions = builder.chatOptionsBuilder.build();
			this.functionCallingOptions = builder.functionCallingOptionsBuilder.build();
			this.frequencyPenalty = builder.frequencyPenalty;
			this.presencePenalty = builder.presencePenalty;
			this.logitBias = builder.logitBias;
			this.maxTokens = builder.maxTokens;
			this.model = builder.model;
			this.responseFormat = builder.responseFormat;
			this.stop = builder.stop;
			this.tools = builder.tools;
			this.user = builder.user;
			this.n = builder.n;
			this.toolChoice = builder.toolChoice;
			this.seed = builder.seed;
		}

		@Override
		public String getModel() {
			return this.model;
		}

		@Override
		public Float getFrequencyPenalty() {
			return this.frequencyPenalty;
		}

		@Override
		public Map<String, Integer> getLogitBias() {
			return this.logitBias;
		}

		@Override
		public Integer getMaxTokens() {
			return this.maxTokens;
		}

		@Override
		public Integer getN() {
			return this.n;
		}

		@Override
		public Float getPresencePenalty() {
			return this.presencePenalty;
		}

		@Override
		public ResponseFormat getResponseFormat() {
			return this.responseFormat;
		}

		@Override
		public Integer getSeed() {
			return this.seed;
		}

		@Override
		public List<String> getStop() {
			return this.stop;
		}

		@Override
		public List<FunctionTool> getTools() {
			return this.tools;
		}

		@Override
		public String getToolChoice() {
			return this.toolChoice;
		}

		@Override
		public String getUser() {
			return this.user;
		}

		/**
		 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will
		 * make the output more random, while lower values like 0.2 will make it more
		 * focused and deterministic. We generally recommend altering this or top_p but
		 * not both.
		 */
		@Override
		@JsonProperty("temperature")
		public Float getTemperature() {
			return this.chatOptions.getTemperature();
		}

		/**
		 * An alternative to sampling with temperature, called nucleus sampling, where the
		 * model considers the results of the tokens with top_p probability mass. So 0.1
		 * means only the tokens comprising the top 10% probability mass are considered.
		 * We generally recommend altering this or temperature but not both.
		 */
		@Override
		@JsonProperty("top_p")
		public Float getTopP() {
			return this.chatOptions.getTopP();
		}

		@Override
		@JsonIgnore
		public Integer getTopK() {
			throw new UnsupportedOperationException("Unimplemented method 'getTopK'");
		}

		@Override
		public List<FunctionCallback> getFunctionCallbacks() {
			return this.functionCallingOptions.getFunctionCallbacks();
		}

		@Override
		public Set<String> getFunctions() {
			return this.functionCallingOptions.getFunctions();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((model == null) ? 0 : model.hashCode());
			result = prime * result + ((frequencyPenalty == null) ? 0 : frequencyPenalty.hashCode());
			result = prime * result + ((logitBias == null) ? 0 : logitBias.hashCode());
			result = prime * result + ((maxTokens == null) ? 0 : maxTokens.hashCode());
			result = prime * result + ((n == null) ? 0 : n.hashCode());
			result = prime * result + ((presencePenalty == null) ? 0 : presencePenalty.hashCode());
			result = prime * result + ((responseFormat == null) ? 0 : responseFormat.hashCode());
			result = prime * result + ((seed == null) ? 0 : seed.hashCode());
			result = prime * result + ((stop == null) ? 0 : stop.hashCode());
			result = prime * result + ((tools == null) ? 0 : tools.hashCode());
			result = prime * result + ((toolChoice == null) ? 0 : toolChoice.hashCode());
			result = prime * result + ((user == null) ? 0 : user.hashCode());
			result = prime * result + ((functionCallingOptions == null) ? 0 : functionCallingOptions.hashCode());
			result = prime * result + ((chatOptions == null) ? 0 : chatOptions.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OpenAiChatOptionsImpl other = (OpenAiChatOptionsImpl) obj;
			if (this.model == null) {
				if (other.model != null)
					return false;
			}
			else if (!model.equals(other.model))
				return false;
			if (this.frequencyPenalty == null) {
				if (other.frequencyPenalty != null)
					return false;
			}
			else if (!this.frequencyPenalty.equals(other.frequencyPenalty))
				return false;
			if (this.logitBias == null) {
				if (other.logitBias != null)
					return false;
			}
			else if (!this.logitBias.equals(other.logitBias))
				return false;
			if (this.maxTokens == null) {
				if (other.maxTokens != null)
					return false;
			}
			else if (!this.maxTokens.equals(other.maxTokens))
				return false;
			if (this.n == null) {
				if (other.n != null)
					return false;
			}
			else if (!this.n.equals(other.n))
				return false;
			if (this.presencePenalty == null) {
				if (other.presencePenalty != null)
					return false;
			}
			else if (!this.presencePenalty.equals(other.presencePenalty))
				return false;
			if (this.responseFormat == null) {
				if (other.responseFormat != null)
					return false;
			}
			else if (!this.responseFormat.equals(other.responseFormat))
				return false;
			if (this.seed == null) {
				if (other.seed != null)
					return false;
			}
			else if (!this.seed.equals(other.seed))
				return false;
			if (this.stop == null) {
				if (other.stop != null)
					return false;
			}
			else if (!stop.equals(other.stop))
				return false;
			if ((this.chatOptions == null) != (other.chatOptions == null))
				return false;
			else if (!this.chatOptions.equals(other.chatOptions))
				return false;
			if ((this.tools == null) != (other.tools == null))
				return false;
			else if (!tools.equals(other.tools))
				return false;
			if ((this.toolChoice == null) != (other.toolChoice == null))
				return false;
			else if (!toolChoice.equals(other.toolChoice))
				return false;
			if ((this.user == null) != (other.user == null))
				return false;
			else
				return this.user.equals(other.user);
		}

	}

}
