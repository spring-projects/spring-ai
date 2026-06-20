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

package org.springframework.ai.deepseek;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Chat completions options for the DeepSeek chat API.
 * <a href="https://platform.deepseek.com/api-docs/api/create-chat-completion">DeepSeek
 * chat completion</a>
 *
 * @author Geng Rong
 * @author Sebastien Deleuze
 */
public class DeepSeekChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 * You can use the DeepSeek v4 model names (deepseek-v4-flash, deepseek-v4-pro) or the legacy model
	 * names (deepseek-chat, deepseek-reasoner).
	 */
	private final String model;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 */
	private final @Nullable Double frequencyPenalty;
	/**
	 * The maximum number of tokens that can be generated in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's context length.
	 */
	private final @Nullable Integer maxTokens;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 */
	private final @Nullable Double presencePenalty;
	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
	 */
	private final @Nullable ResponseFormat responseFormat;
	/**
	 * A string or a list containing up to 4 strings, upon encountering these words, the API will cease generating more tokens.
	 */
	private final @Nullable List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 2.
	 * Higher values like 0.8 will make the output more random,
	 * while lower values like 0.2 will make it more focused and deterministic.
	 * We generally recommend altering this or top_p but not both.
	 */
	private final Double temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass.
	 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 */
	private final @Nullable Double topP;
	/**
	 * Whether to return log probabilities of the output tokens or not.
	 * If true, returns the log probabilities of each output token returned in the content of message.
	 */
	private final @Nullable Boolean logprobs;
	/**
	 * An integer between 0 and 20 specifying the number of most likely tokens to return at each token position,
	 * each with an associated log probability. logprobs must be set to true if this parameter is used.
	 */
	private final @Nullable Integer topLogprobs;


	private final @Nullable List<DeepSeekApi.FunctionTool> tools;

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
	private final @Nullable Object toolChoice;

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the toolCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the toolCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	private final @Nullable List<ToolCallback> toolCallbacks;

	private final @Nullable Map<String, Object> toolContext;

	protected DeepSeekChatOptions(@Nullable String model, @Nullable Double frequencyPenalty,
			@Nullable Integer maxTokens, @Nullable Double presencePenalty,
			@Nullable ResponseFormat responseFormat, @Nullable List<String> stop,
			@Nullable Double temperature, @Nullable Double topP, @Nullable Boolean logprobs,
			@Nullable Integer topLogprobs, @Nullable List<DeepSeekApi.FunctionTool> tools,
			@Nullable Object toolChoice,
			@Nullable List<ToolCallback> toolCallbacks, @Nullable Map<String, Object> toolContext) {
		this.model = model != null ? model : DeepSeekApi.DEFAULT_CHAT_MODEL.getValue();
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.presencePenalty = presencePenalty;
		this.responseFormat = responseFormat;
		this.stop = (stop != null ? List.copyOf(stop) : null);
		this.temperature = temperature != null ? temperature : 0.7;
		this.topP = topP;
		this.logprobs = logprobs;
		this.topLogprobs = topLogprobs;
		this.tools = tools != null ? List.copyOf(tools) : null;
		this.toolChoice = toolChoice;
		this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : null;
		this.toolContext = toolContext != null ? Map.copyOf(toolContext) : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public @Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return getStop();
	}

	public @Nullable List<String> getStop() {
		return this.stop;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public @Nullable List<DeepSeekApi.FunctionTool> getTools() {
		return this.tools;
	}

	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}


	@Override
	public @Nullable List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	@Override
	public @Nullable Integer getTopK() {
		return null;
	}


	@Override
	public @Nullable Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public Builder mutate() {
		return DeepSeekChatOptions.builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stop)
			.temperature(this.temperature)
			.topP(this.topP)
			.topK(this.getTopK()) // always null but here for consistency
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolContext(this.getToolContext())
			// DeepSeek Specific
			.responseFormat(this.responseFormat)
			.logprobs(this.logprobs)
			.topLogprobs(this.topLogprobs)
			.tools(this.tools)
			.toolChoice(this.toolChoice);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.logprobs, this.topLogprobs,
				this.maxTokens,  this.presencePenalty, this.responseFormat,
				this.stop, this.temperature, this.topP, this.tools, this.toolChoice,
				this.toolCallbacks, this.toolContext);
	}


	@Override
	public boolean equals(@Nullable Object o) {
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
				&& Objects.equals(this.toolContext, other.toolContext);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> {

		@Override
		public B clone() {
			B copy = super.clone();
			copy.tools = this.tools;
			return copy;
		}

		protected @Nullable ResponseFormat responseFormat;

		protected @Nullable Boolean logprobs;

		protected @Nullable Integer topLogprobs;

		protected @Nullable List<DeepSeekApi.FunctionTool> tools;

		protected @Nullable Object toolChoice;

		public B model(DeepSeekApi.@Nullable ChatModel deepseekAiChatModel) {
			if (deepseekAiChatModel == null) {
				this.model = null;
			}
			else {
				this.model = deepseekAiChatModel.getName();
			}
			return self();
		}

		public B responseFormat(@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B stop(@Nullable List<String> stop) {
			return stopSequences(stop);
		}

		public B logprobs(@Nullable Boolean logprobs) {
			this.logprobs = logprobs;
			return self();
		}

		public B topLogprobs(@Nullable Integer topLogprobs) {
			this.topLogprobs = topLogprobs;
			return self();
		}

		public B tools(@Nullable List<DeepSeekApi.FunctionTool> tools) {
			this.tools = tools;
			return self();
		}

		public B toolChoice(@Nullable Object toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.logprobs != null) {
					this.logprobs = that.logprobs;
				}
				if (that.topLogprobs != null) {
					this.topLogprobs = that.topLogprobs;
				}
				if (that.tools != null) {
					if (this.tools == null) {
						this.tools = new ArrayList<>(that.tools);
					}
					else {
						List<DeepSeekApi.FunctionTool> merged = new ArrayList<>(this.tools);
						merged.addAll(that.tools);
						this.tools = merged;
					}
				}
				if (that.toolChoice != null) {
					this.toolChoice = that.toolChoice;
				}
			}
			return self();
		}

		@Override
		public DeepSeekChatOptions build() {
			return new DeepSeekChatOptions(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty,
					this.responseFormat, this.stopSequences, this.temperature, this.topP, this.logprobs,
					this.topLogprobs, this.tools, this.toolChoice,
					this.toolCallbacks, this.toolContext);
		}

	}

}
