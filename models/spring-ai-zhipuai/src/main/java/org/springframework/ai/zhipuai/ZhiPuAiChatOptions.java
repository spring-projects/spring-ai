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

package org.springframework.ai.zhipuai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * ZhiPuAiChatOptions represents the options for the ZhiPuAiChat model.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0 M1
 */
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

	public static Builder builder() {
		return new Builder();
	}

	public static ZhiPuAiChatOptions fromOptions(ZhiPuAiChatOptions fromOptions) {
		return ZhiPuAiChatOptions.builder()
			.model(fromOptions.getModel())
			.maxTokens(fromOptions.getMaxTokens())
			.stop(fromOptions.getStop())
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.tools(fromOptions.getTools())
			.toolChoice(fromOptions.getToolChoice())
			.user(fromOptions.getUser())
			.requestId(fromOptions.getRequestId())
			.doSample(fromOptions.getDoSample())
			.toolCallbacks(fromOptions.getToolCallbacks())
			.toolNames(fromOptions.getToolNames())
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
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
	@Nullable
	@JsonIgnore
	public Boolean getInternalToolExecutionEnabled() {
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
		this.toolContext = toolContext;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.maxTokens == null) ? 0 : this.maxTokens.hashCode());
		result = prime * result + ((this.stop == null) ? 0 : this.stop.hashCode());
		result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
		result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
		result = prime * result + ((this.tools == null) ? 0 : this.tools.hashCode());
		result = prime * result + ((this.toolChoice == null) ? 0 : this.toolChoice.hashCode());
		result = prime * result + ((this.user == null) ? 0 : this.user.hashCode());
		result = prime * result
				+ ((this.internalToolExecutionEnabled == null) ? 0 : this.internalToolExecutionEnabled.hashCode());
		result = prime * result + ((this.toolCallbacks == null) ? 0 : this.toolCallbacks.hashCode());
		result = prime * result + ((this.toolNames == null) ? 0 : this.toolNames.hashCode());
		result = prime * result + ((this.toolContext == null) ? 0 : this.toolContext.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ZhiPuAiChatOptions other = (ZhiPuAiChatOptions) obj;
		if (this.model == null) {
			if (other.model != null) {
				return false;
			}
		}
		else if (!this.model.equals(other.model)) {
			return false;
		}
		if (this.maxTokens == null) {
			if (other.maxTokens != null) {
				return false;
			}
		}
		else if (!this.maxTokens.equals(other.maxTokens)) {
			return false;
		}
		if (this.stop == null) {
			if (other.stop != null) {
				return false;
			}
		}
		else if (!this.stop.equals(other.stop)) {
			return false;
		}
		if (this.temperature == null) {
			if (other.temperature != null) {
				return false;
			}
		}
		else if (!this.temperature.equals(other.temperature)) {
			return false;
		}
		if (this.topP == null) {
			if (other.topP != null) {
				return false;
			}
		}
		else if (!this.topP.equals(other.topP)) {
			return false;
		}
		if (this.tools == null) {
			if (other.tools != null) {
				return false;
			}
		}
		else if (!this.tools.equals(other.tools)) {
			return false;
		}
		if (this.toolChoice == null) {
			if (other.toolChoice != null) {
				return false;
			}
		}
		else if (!this.toolChoice.equals(other.toolChoice)) {
			return false;
		}
		if (this.user == null) {
			if (other.user != null) {
				return false;
			}
		}
		else if (!this.user.equals(other.user)) {
			return false;
		}
		if (this.requestId == null) {
			if (other.requestId != null) {
				return false;
			}
		}
		else if (!this.requestId.equals(other.requestId)) {
			return false;
		}
		if (this.doSample == null) {
			if (other.doSample != null) {
				return false;
			}
		}
		else if (!this.doSample.equals(other.doSample)) {
			return false;
		}
		if (this.internalToolExecutionEnabled == null) {
			if (other.internalToolExecutionEnabled != null) {
				return false;
			}
		}
		else if (!this.internalToolExecutionEnabled.equals(other.internalToolExecutionEnabled)) {
			return false;
		}
		if (this.toolContext == null) {
			if (other.toolContext != null) {
				return false;
			}
		}
		else if (!this.toolContext.equals(other.toolContext)) {
			return false;
		}
		return true;
	}

	@Override
	public ZhiPuAiChatOptions copy() {
		return fromOptions(this);
	}

	public ToolCallingChatOptions merge(ChatOptions options) {
		ZhiPuAiChatOptions.Builder builder = ZhiPuAiChatOptions.builder();

		// Merge chat-specific options
		builder.model(options.getModel() != null ? options.getModel() : this.getModel())
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxTokens())
			.stop(options.getStopSequences() != null ? options.getStopSequences() : this.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature())
			.topP(options.getTopP() != null ? options.getTopP() : this.getTopP());

		// Try to get tool-specific properties if options is a ToolCallingChatOptions
		if (options instanceof ToolCallingChatOptions toolCallingChatOptions) {
			builder.internalToolExecutionEnabled(toolCallingChatOptions.getInternalToolExecutionEnabled() != null
					? (toolCallingChatOptions).getInternalToolExecutionEnabled()
					: this.getInternalToolExecutionEnabled());

			Set<String> toolNames = new HashSet<>();
			if (this.toolNames != null) {
				toolNames.addAll(this.toolNames);
			}
			if (toolCallingChatOptions.getToolNames() != null) {
				toolNames.addAll(toolCallingChatOptions.getToolNames());
			}
			builder.toolNames(toolNames);

			List<ToolCallback> toolCallbacks = new ArrayList<>();
			if (this.toolCallbacks != null) {
				toolCallbacks.addAll(this.toolCallbacks);
			}
			if (toolCallingChatOptions.getToolCallbacks() != null) {
				toolCallbacks.addAll(toolCallingChatOptions.getToolCallbacks());
			}
			builder.toolCallbacks(toolCallbacks);

			Map<String, Object> context = new HashMap<>();
			if (this.toolContext != null) {
				context.putAll(this.toolContext);
			}
			if (toolCallingChatOptions.getToolContext() != null) {
				context.putAll(toolCallingChatOptions.getToolContext());
			}
			builder.toolContext(context);
		}
		else {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
			builder.toolNames(this.toolNames != null ? new HashSet<>(this.toolNames) : null);
			builder.toolCallbacks(this.toolCallbacks != null ? new ArrayList<>(this.toolCallbacks) : null);
			builder.toolContext(this.toolContext != null ? new HashMap<>(this.toolContext) : null);
		}

		// Preserve ZhiPuAi-specific properties
		builder.tools(this.tools)
			.toolChoice(this.toolChoice)
			.user(this.user)
			.requestId(this.requestId)
			.doSample(this.doSample);

		return builder.build();
	}

	public static class Builder {

		protected ZhiPuAiChatOptions options;

		public Builder() {
			this.options = new ZhiPuAiChatOptions();
		}

		public Builder(ZhiPuAiChatOptions options) {
			this.options = options;
		}

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.stop = stop;
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

		public Builder tools(List<ZhiPuAiApi.FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder toolChoice(String toolChoice) {
			this.options.toolChoice = toolChoice;
			return this;
		}

		public Builder user(String user) {
			this.options.user = user;
			return this;
		}

		public Builder requestId(String requestId) {
			this.options.requestId = requestId;
			return this;
		}

		public Builder doSample(Boolean doSample) {
			this.options.doSample = doSample;
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
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

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public ZhiPuAiChatOptions build() {
			return this.options;
		}

	}

}
