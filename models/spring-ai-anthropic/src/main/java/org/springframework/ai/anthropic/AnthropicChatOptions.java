/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;

/**
 * The options to be used when sending a chat request to the Anthropic API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicChatOptions implements FunctionCallingOptions {

	// @formatter:off
	private @JsonProperty("model") String model;
	private @JsonProperty("max_tokens") Integer maxTokens;
	private @JsonProperty("metadata") ChatCompletionRequest.Metadata metadata;
	private @JsonProperty("stop_sequences") List<String> stopSequences;
	private @JsonProperty("temperature") Double temperature;
	private @JsonProperty("top_p") Double topP;
	private @JsonProperty("top_k") Integer topK;

	/**
	 * Tool Function Callbacks to register with the ChatModel. For Prompt
	 * Options the functionCallbacks are automatically enabled for the duration of the
	 * prompt execution. For Default Options the functionCallbacks are registered but
	 * disabled by default. Use the enableFunctions to set the functions from the registry
	 * to be used by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests. Functions with those names must exist in the
	 * functionCallbacks registry. The {@link #functionCallbacks} from the PromptOptions
	 * are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat
	 * completion requests. This could impact the token count and the billing. If the
	 * functions is set in a prompt options, then the enabled functions are only active
	 * for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	@JsonIgnore
	private Boolean proxyToolCalls;

	@JsonIgnore
	private Map<String, Object> toolContext;

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static AnthropicChatOptions fromOptions(AnthropicChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.maxTokens(fromOptions.getMaxTokens())
			.metadata(fromOptions.getMetadata())
			.stopSequences(fromOptions.getStopSequences())
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.functionCallbacks(fromOptions.getFunctionCallbacks())
			.functions(fromOptions.getFunctions())
			.proxyToolCalls(fromOptions.getProxyToolCalls())
			.toolContext(fromOptions.getToolContext())
			.build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public ChatCompletionRequest.Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(ChatCompletionRequest.Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;
	}

	@Override
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.functionCallbacks = functionCallbacks;
	}

	@Override
	public Set<String> getFunctions() {
		return this.functions;
	}

	@Override
	public void setFunctions(Set<String> functions) {
		Assert.notNull(functions, "Function must not be null");
		this.functions = functions;
	}

	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return null;
	}

	@Override
	public Boolean getProxyToolCalls() {
		return this.proxyToolCalls;
	}

	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.proxyToolCalls = proxyToolCalls;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@Override
	public AnthropicChatOptions copy() {
		return fromOptions(this);
	}

	public static class Builder {

		private final AnthropicChatOptions options = new AnthropicChatOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(AnthropicApi.ChatModel model) {
			this.options.model = model.getValue();
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder metadata(ChatCompletionRequest.Metadata metadata) {
			this.options.metadata = metadata;
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.options.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder functions(Set<String> functionNames) {
			Assert.notNull(functionNames, "Function names must not be null");
			this.options.functions = functionNames;
			return this;
		}

		public Builder function(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			this.options.functions.add(functionName);
			return this;
		}

		public Builder proxyToolCalls(Boolean proxyToolCalls) {
			this.options.proxyToolCalls = proxyToolCalls;
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

		/**
		 * @deprecated use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(String model) {
			return model(model);
		}

		/**
		 * @deprecated use {@link #model(AnthropicApi.ChatModel)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(AnthropicApi.ChatModel model) {
			return model(model);
		}

		/**
		 * @deprecated use {@link #maxTokens(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMaxTokens(Integer maxTokens) {
			return maxTokens(maxTokens);
		}

		/**
		 * @deprecated use {@link #metadata(ChatCompletionRequest.Metadata)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMetadata(ChatCompletionRequest.Metadata metadata) {
			return metadata(metadata);
		}

		/**
		 * @deprecated use {@link #stopSequences(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withStopSequences(List<String> stopSequences) {
			return stopSequences(stopSequences);
		}

		/**
		 * @deprecated use {@link #temperature(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTemperature(Double temperature) {
			return temperature(temperature);
		}

		/**
		 * @deprecated use {@link #topP(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTopP(Double topP) {
			return topP(topP);
		}

		/**
		 * @deprecated use {@link #topK(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withTopK(Integer topK) {
			return topK(topK);
		}

		/**
		 * @deprecated use {@link #functionCallbacks(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			return functionCallbacks(functionCallbacks);
		}

		/**
		 * @deprecated use {@link #functions(Set)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunctions(Set<String> functionNames) {
			return functions(functionNames);
		}

		/**
		 * @deprecated use {@link #function(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withFunction(String functionName) {
			return function(functionName);
		}

		/**
		 * @deprecated use {@link #proxyToolCalls(Boolean)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withProxyToolCalls(Boolean proxyToolCalls) {
			return proxyToolCalls(proxyToolCalls);
		}

		/**
		 * @deprecated use {@link #toolContext(Map)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withToolContext(Map<String, Object> toolContext) {
			return toolContext(toolContext);
		}

		public AnthropicChatOptions build() {
			return this.options;
		}

	}

}
