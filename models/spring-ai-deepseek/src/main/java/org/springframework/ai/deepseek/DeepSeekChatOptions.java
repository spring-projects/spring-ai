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
package org.springframework.ai.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.*;

/**
 * Chat completions options for the DeepSeek chat API.
 * <a href="https://platform.deepseek.com/api-docs/api/create-chat-completion">DeepSeek
 * chat completion</a>
 *
 * @author Geng Rong
 */
@JsonInclude(Include.NON_NULL)
public class DeepSeekChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
	 */
	private @JsonProperty("model") String model;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 */
	private @JsonProperty("frequency_penalty") Double frequencyPenalty;
	/**
	 * The maximum number of tokens that can be generated in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 */
	private @JsonProperty("presence_penalty") Double presencePenalty;
	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
	 */
	private @JsonProperty("response_format") ResponseFormat responseFormat;
	/**
	 * A string or a list containing up to 4 strings, upon encountering these words, the API will cease generating more tokens.
	 */
	private @JsonProperty("stop") List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 2.
	 * Higher values like 0.8 will make the output more random,
	 * while lower values like 0.2 will make it more focused and deterministic.
	 * We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass.
	 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Double topP;
	/**
	 * Whether to return log probabilities of the output tokens or not.
	 * If true, returns the log probabilities of each output token returned in the content of message.
	 */
	private @JsonProperty("logprobs") Boolean logprobs;
	/**
	 * An integer between 0 and 20 specifying the number of most likely tokens to return at each token position,
	 * each with an associated log probability. logprobs must be set to true if this parameter is used.
	 */
	private @JsonProperty("top_logprobs") Integer topLogprobs;


	private @JsonProperty("tools") List<DeepSeekApi.FunctionTool> tools;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function. Specifying a particular
	 * function via {"type: "function", "function": {"name": "my_function"}} forces the
	 * model to call that function. none is the default when no functions are present.
	 * auto is the default if functions are present. Use the
	 * {@link DeepSeekApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice
	 * object.
	 */
	private @JsonProperty("tool_choice") Object toolChoice;

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the toolCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the toolCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the toolCallbacks registry.
	 * The {@link #toolCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
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

	public List<DeepSeekApi.FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(List<DeepSeekApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public Object getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(Object toolChoice) {
		this.toolChoice = toolChoice;
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

	public Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
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
	public DeepSeekChatOptions copy() {
		return DeepSeekChatOptions.fromOptions(this);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.logprobs, this.topLogprobs,
				this.maxTokens,  this.presencePenalty, this.responseFormat,
				this.stop, this.temperature, this.topP, this.tools, this.toolChoice,
				 this.toolCallbacks, this.toolNames, this.internalToolExecutionEnabled, this.toolContext);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DeepSeekChatOptions other = (DeepSeekChatOptions) o;
		return Objects.equals(this.model, other.model) && Objects.equals(this.frequencyPenalty, other.frequencyPenalty)
				&& Objects.equals(this.logprobs, other.logprobs)
				&& Objects.equals(this.topLogprobs, other.topLogprobs)
				&& Objects.equals(this.maxTokens, other.maxTokens)
				&& Objects.equals(this.presencePenalty, other.presencePenalty)
				&& Objects.equals(this.responseFormat, other.responseFormat)
				&& Objects.equals(this.stop, other.stop) && Objects.equals(this.temperature, other.temperature)
				&& Objects.equals(this.topP, other.topP) && Objects.equals(this.tools, other.tools)
				&& Objects.equals(this.toolChoice, other.toolChoice)
				&& Objects.equals(this.toolCallbacks, other.toolCallbacks)
				&& Objects.equals(this.toolNames, other.toolNames)
				&& Objects.equals(this.toolContext, other.toolContext)
				&& Objects.equals(this.internalToolExecutionEnabled, other.internalToolExecutionEnabled);
	}

	public static class Builder {

		protected DeepSeekChatOptions options;

		public Builder() {
			this.options = new DeepSeekChatOptions();
		}

		public Builder(DeepSeekChatOptions options) {
			this.options = options;
		}

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(DeepSeekApi.ChatModel deepseekAiChatModel) {
			this.options.model = deepseekAiChatModel.getName();
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder logprobs(Boolean logprobs) {
			this.options.logprobs = logprobs;
			return this;
		}

		public Builder topLogprobs(Integer topLogprobs) {
			this.options.topLogprobs = topLogprobs;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder responseFormat(ResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
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

		public Builder tools(List<DeepSeekApi.FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder toolChoice(Object toolChoice) {
			this.options.toolChoice = toolChoice;
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

		public DeepSeekChatOptions build() {
			return this.options;
		}

	}

	public static DeepSeekChatOptions fromOptions(DeepSeekChatOptions fromOptions) {
		return DeepSeekChatOptions.builder()
				.model(fromOptions.getModel())
				.frequencyPenalty(fromOptions.getFrequencyPenalty())
				.logprobs(fromOptions.getLogprobs())
				.topLogprobs(fromOptions.getTopLogprobs())
				.maxTokens(fromOptions.getMaxTokens())
				.presencePenalty(fromOptions.getPresencePenalty())
				.responseFormat(fromOptions.getResponseFormat())
				.stop(fromOptions.getStop() != null ? new ArrayList<>(fromOptions.getStop()) : null)
				.temperature(fromOptions.getTemperature())
				.topP(fromOptions.getTopP())
				.tools(fromOptions.getTools())
				.toolChoice(fromOptions.getToolChoice())
				.toolCallbacks(
						fromOptions.getToolCallbacks() != null ? new ArrayList<>(fromOptions.getToolCallbacks()) : null)
				.toolNames(fromOptions.getToolNames() != null ? new HashSet<>(fromOptions.getToolNames()) : null)
				.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
				.toolContext(fromOptions.getToolContext() != null ? new HashMap<>(fromOptions.getToolContext()) : null)
				.build();
	}

}
