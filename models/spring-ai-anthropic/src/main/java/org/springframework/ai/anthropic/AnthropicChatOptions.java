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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
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
public class AnthropicChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	private @JsonProperty("model") String model;
	private @JsonProperty("max_tokens") Integer maxTokens;
	private @JsonProperty("metadata") ChatCompletionRequest.Metadata metadata;
	private @JsonProperty("stop_sequences") List<String> stopSequences;
	private @JsonProperty("temperature") Double temperature;
	private @JsonProperty("top_p") Double topP;
	private @JsonProperty("top_k") Integer topK;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	@JsonIgnore
	private List<FunctionCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();


	/**
	 * Optional HTTP headers to be added to the chat completion request.
	 */
	@JsonIgnore
	private Map<String, String> httpHeaders = new HashMap<>();

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static AnthropicChatOptions fromOptions(AnthropicChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.maxTokens(fromOptions.getMaxTokens())
			.metadata(fromOptions.getMetadata())
			.stopSequences(
					fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null)
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.toolCallbacks(
					fromOptions.getToolCallbacks() != null ? new ArrayList<>(fromOptions.getToolCallbacks()) : null)
			.toolNames(fromOptions.getToolNames() != null ? new HashSet<>(fromOptions.getToolNames()) : null)
			.internalToolExecutionEnabled(fromOptions.isInternalToolExecutionEnabled())
			.toolContext(fromOptions.getToolContext() != null ? new HashMap<>(fromOptions.getToolContext()) : null)
			.httpHeaders(fromOptions.getHttpHeaders() != null ? new HashMap<>(fromOptions.getHttpHeaders()) : null)
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
	@JsonIgnore
	public List<FunctionCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<FunctionCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	@JsonIgnore
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	@JsonIgnore
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable
	@JsonIgnore
	public Boolean isInternalToolExecutionEnabled() {
		return internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	@Deprecated
	@JsonIgnore
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.getToolCallbacks();
	}

	@Override
	@Deprecated
	@JsonIgnore
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.setToolCallbacks(functionCallbacks);
	}

	@Override
	@Deprecated
	@JsonIgnore
	public Set<String> getFunctions() {
		return this.getToolNames();
	}

	@Override
	@Deprecated
	@JsonIgnore
	public void setFunctions(Set<String> functionNames) {
		this.setToolNames(functionNames);
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
	@Deprecated
	@JsonIgnore
	public Boolean getProxyToolCalls() {
		return this.internalToolExecutionEnabled != null ? !this.internalToolExecutionEnabled : null;
	}

	@Deprecated
	@JsonIgnore
	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.internalToolExecutionEnabled = proxyToolCalls != null ? !proxyToolCalls : null;
	}

	@Override
	@JsonIgnore
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	@JsonIgnore
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@JsonIgnore
	public Map<String, String> getHttpHeaders() {
		return httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	@Override
	@SuppressWarnings("unchecked")
	public AnthropicChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicChatOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.metadata, that.metadata)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.httpHeaders, that.httpHeaders);
	}

	@Override
	public int hashCode() {
		return Objects.hash(model, maxTokens, metadata, stopSequences, temperature, topP, topK, toolCallbacks,
				toolNames, internalToolExecutionEnabled, toolContext, httpHeaders);
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

		public Builder toolCallbacks(List<FunctionCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(FunctionCallback... toolCallbacks) {
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

		@Deprecated
		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			return toolCallbacks(functionCallbacks);
		}

		@Deprecated
		public Builder functions(Set<String> functionNames) {
			return toolNames(functionNames);
		}

		@Deprecated
		public Builder function(String functionName) {
			return toolNames(functionName);
		}

		@Deprecated
		public Builder proxyToolCalls(Boolean proxyToolCalls) {
			if (proxyToolCalls != null) {
				this.options.setInternalToolExecutionEnabled(!proxyToolCalls);
			}
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

		public Builder httpHeaders(Map<String, String> httpHeaders) {
			this.options.setHttpHeaders(httpHeaders);
			return this;
		}

		public AnthropicChatOptions build() {
			return this.options;
		}

	}

}
