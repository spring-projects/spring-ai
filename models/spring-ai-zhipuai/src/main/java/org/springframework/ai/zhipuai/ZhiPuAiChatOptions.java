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

package org.springframework.ai.zhipuai;

import java.util.ArrayList;
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
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;
import org.springframework.util.Assert;

/**
 * ZhiPuAiChatOptions represents the options for the ZhiPuAiChat model.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author YunKui Lu
 * @since 1.0.0 M1
 * @deprecated will be moved to <a href="https://github.com/spring-ai-community">Spring AI
 * Community</a> with new package and dependency coordinates if a maintainer is found
 */
@Deprecated(since = "2.0.0-M4", forRemoval = true)
@JsonInclude(Include.NON_NULL)
public class ZhiPuAiChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	/**
	 * The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * The model will stop generating characters specified by stop, and currently only supports a single stop word in the format of ["stop_word1"].
	 */
	private @JsonProperty("stop") List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic. We generally recommend
	 * altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the model considers the
	 * results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Double topP;
	/**
	 * A list of tools the model may call. Currently, only functions are supported as a tool. Use this to
	 * provide a list of functions the model may generate JSON inputs for.
	 */
	private @JsonProperty("tools") List<ZhiPuAiApi.FunctionTool> tools;
	/**
	 * Controls which (if any) function is called by the model. none means the model will not call a
	 * function and instead generates a message. auto means the model can pick between generating a message or calling a
	 * function. Specifying a particular function via {"type: "function", "function": {"name": "my_function"}} forces
	 * the model to call that function. none is the default when no functions are present. auto is the default if
	 * functions are present. Use the {@link ZhiPuAiApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice object.
	 */
	private @JsonProperty("tool_choice") String toolChoice;
	/**
	 * A unique identifier representing your end-user, which can help ZhiPuAI to monitor and detect abuse.
	 * ID length requirement: minimum of 6 characters, maximum of 128 characters.
	 */
	private @JsonProperty("user_id") String user;
	/**
	 * The parameter is passed by the client and must ensure uniqueness.
	 * It is used to distinguish the unique identifier for each request.
	 * If the client does not provide it, the platform will generate it by default.
	 */
	private @JsonProperty("request_id") String requestId;
	/**
	 * When do_sample is set to true, the sampling strategy is enabled.
	 * If do_sample is false, the sampling strategy parameters temperature and top_p will not take effect.
	 * The default value is true.
	 */
	private @JsonProperty("do_sample") Boolean doSample;

	/**
	 * Control the format of the model output. Set to `json_object` to ensure the message is a valid JSON object.
	 */
	private @JsonProperty("response_format") ChatCompletionRequest.ResponseFormat responseFormat;

	/**
	 * Control whether to enable the large model's chain of thought. Available options: (default) enabled, disabled.
	 */
	private @JsonProperty("thinking") ChatCompletionRequest.Thinking thinking;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the chat completion requests.
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
	// @formatter:on

	// TODO: left here for ModelOptionUtils.merge*()
	public ZhiPuAiChatOptions() {
	}

	protected ZhiPuAiChatOptions(String model, Integer maxTokens, List<String> stop, Double temperature, Double topP,
			List<ZhiPuAiApi.FunctionTool> tools, String toolChoice, String user, String requestId, Boolean doSample,
			ChatCompletionRequest.ResponseFormat responseFormat, ChatCompletionRequest.Thinking thinking,
			Boolean internalToolExecutionEnabled, @Nullable List<ToolCallback> toolCallbacks,
			@Nullable Set<String> toolNames, @Nullable Map<String, Object> toolContext) {
		this.model = model;
		this.maxTokens = maxTokens;
		this.stop = stop;
		this.temperature = temperature;
		this.topP = topP;
		this.tools = tools;
		this.toolChoice = toolChoice;
		this.user = user;
		this.requestId = requestId;
		this.doSample = doSample;
		this.responseFormat = responseFormat;
		this.thinking = thinking;
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		this.toolCallbacks = toolCallbacks == null ? new ArrayList<>() : new ArrayList<>(toolCallbacks);
		this.toolNames = toolNames == null ? new HashSet<>() : new HashSet<>(toolNames);
		this.toolContext = toolContext == null ? new HashMap<>() : new HashMap<>(toolContext);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static ZhiPuAiChatOptions fromOptions(ZhiPuAiChatOptions fromOptions) {
		return fromOptions.mutate().build();
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

	@Override
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

	public List<ZhiPuAiApi.FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(List<ZhiPuAiApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public String getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getRequestId() {
		return this.requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public Boolean getDoSample() {
		return this.doSample;
	}

	public void setDoSample(Boolean doSample) {
		this.doSample = doSample;
	}

	public ChatCompletionRequest.ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public ZhiPuAiChatOptions setResponseFormat(ChatCompletionRequest.ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
		return this;
	}

	public ChatCompletionRequest.Thinking getThinking() {
		return this.thinking;
	}

	public ZhiPuAiChatOptions setThinking(ChatCompletionRequest.Thinking thinking) {
		this.thinking = thinking;
		return this;
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
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
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
	@JsonIgnore
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		Assert.notNull(toolContext, "toolContext cannot be null");
		this.toolContext = toolContext;
	}

	@Override
	public final boolean equals(Object o) {
		if (!(o instanceof ZhiPuAiChatOptions that)) {
			return false;
		}

		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.stop, that.stop) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.tools, that.tools)
				&& Objects.equals(this.toolChoice, that.toolChoice) && Objects.equals(this.user, that.user)
				&& Objects.equals(this.requestId, that.requestId) && Objects.equals(this.doSample, that.doSample)
				&& Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.thinking, that.thinking)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(this.model);
		result = 31 * result + Objects.hashCode(this.maxTokens);
		result = 31 * result + Objects.hashCode(this.stop);
		result = 31 * result + Objects.hashCode(this.temperature);
		result = 31 * result + Objects.hashCode(this.topP);
		result = 31 * result + Objects.hashCode(this.tools);
		result = 31 * result + Objects.hashCode(this.toolChoice);
		result = 31 * result + Objects.hashCode(this.user);
		result = 31 * result + Objects.hashCode(this.requestId);
		result = 31 * result + Objects.hashCode(this.doSample);
		result = 31 * result + Objects.hashCode(this.responseFormat);
		result = 31 * result + Objects.hashCode(this.thinking);
		result = 31 * result + Objects.hashCode(this.toolCallbacks);
		result = 31 * result + Objects.hashCode(this.toolNames);
		result = 31 * result + Objects.hashCode(this.internalToolExecutionEnabled);
		result = 31 * result + Objects.hashCode(this.toolContext);
		return result;
	}

	@Override
	public String toString() {
		return "ZhiPuAiChatOptions: " + ModelOptionsUtils.toJsonString(this);
	}

	@Override
	public ZhiPuAiChatOptions copy() {
		return mutate().build();
	}

	@Override
	public Builder mutate() {
		return ZhiPuAiChatOptions.builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.getFrequencyPenalty()) // unused in this model
			.maxTokens(this.maxTokens)
			.presencePenalty(this.getPresencePenalty()) // unused in this model
			.stopSequences(this.getStopSequences())
			.temperature(this.temperature)
			.topK(this.getTopK()) // unused in this model
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolNames(this.getToolNames())
			.toolContext(this.getToolContext())
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// ZhiPuAi Specific
			.tools(this.tools)
			.toolChoice(this.toolChoice)
			.user(this.user)
			.requestId(this.requestId)
			.doSample(this.doSample)
			.responseFormat(this.responseFormat)
			.thinking(this.thinking);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> {

		protected @Nullable List<ZhiPuAiApi.FunctionTool> tools;

		protected @Nullable String toolChoice;

		protected @Nullable String user;

		protected @Nullable String requestId;

		protected @Nullable Boolean doSample;

		protected ChatCompletionRequest.@Nullable ResponseFormat responseFormat;

		protected ChatCompletionRequest.@Nullable Thinking thinking;

		public B stop(@Nullable List<String> stop) {
			return this.stopSequences(stop);
		}

		public B tools(@Nullable List<ZhiPuAiApi.FunctionTool> tools) {
			this.tools = tools;
			return self();
		}

		public B toolChoice(@Nullable String toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B user(@Nullable String user) {
			this.user = user;
			return self();
		}

		public B requestId(@Nullable String requestId) {
			this.requestId = requestId;
			return self();
		}

		public B doSample(@Nullable Boolean doSample) {
			this.doSample = doSample;
			return self();
		}

		public B responseFormat(ChatCompletionRequest.@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B thinking(ChatCompletionRequest.@Nullable Thinking thinking) {
			this.thinking = thinking;
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.tools != null) {
					this.tools = that.tools;
				}
				if (that.toolChoice != null) {
					this.toolChoice = that.toolChoice;
				}
				if (that.user != null) {
					this.user = that.user;
				}
				if (that.requestId != null) {
					this.requestId = that.requestId;
				}
				if (that.doSample != null) {
					this.doSample = that.doSample;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.thinking != null) {
					this.thinking = that.thinking;
				}
			}
			return self();
		}

		@Override
		public ZhiPuAiChatOptions build() {
			return new ZhiPuAiChatOptions(this.model, this.maxTokens, this.stopSequences, this.temperature, this.topP,
					this.tools, this.toolChoice, this.user, this.requestId, this.doSample, this.responseFormat,
					this.thinking, this.internalToolExecutionEnabled, this.toolCallbacks, this.toolNames,
					this.toolContext);
		}

	}

}
