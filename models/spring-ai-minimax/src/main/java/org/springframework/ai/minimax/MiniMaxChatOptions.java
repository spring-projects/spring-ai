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

package org.springframework.ai.minimax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * MiniMaxChatOptions represents the options for performing chat completion using the
 * MiniMax API. It provides methods to set and retrieve various options like model,
 * frequency penalty, max tokens, etc.
 *
 * @see ChatOptions
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 * @since 1.0.0 M1
 */
public class MiniMaxChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	@SuppressWarnings("NullAway.Init")
	private String model;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 */
	private @Nullable Double frequencyPenalty;
	/**
	 * The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
	 */
	private @Nullable Integer maxTokens;
	/**
	 * How many chat completion choices to generate for each input message. Note that you will be charged based
	 * on the number of generated tokens across all of the choices. Keep n as 1 to minimize costs.
	 */
	private @Nullable Integer n;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 */
	private @Nullable Double presencePenalty;
	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
	 */
	private MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat;
	/**
	 * This feature is in Beta. If specified, our system will make a best effort to sample
	 * deterministically, such that repeated requests with the same seed and parameters should return the same result.
	 * Determinism is not guaranteed, and you should refer to the system_fingerprint response parameter to monitor
	 * changes in the backend.
	 */
	private @Nullable Integer seed;
	/**
	 * Up to 4 sequences where the API will stop generating further tokens.
	 */
	private @Nullable List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic. We generally recommend
	 * altering this or top_p but not both.
	 */
	private @Nullable Double temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the model considers the
	 * results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or temperature but not both.
	 */
	private @Nullable Double topP;
	/**
	 * Mask the text information in the output that is easy to involve privacy issues,
	 * including but not limited to email, domain name, link, ID number, home address, etc.
	 * The default is true, which means enabling masking.
	 */
	private @Nullable Boolean maskSensitiveInfo;
	/**
	 * A list of tools the model may call. Currently, only functions are supported as a tool. Use this to
	 * provide a list of functions the model may generate JSON inputs for.
	 */
	private @Nullable List<MiniMaxApi.FunctionTool> tools;
	/**
	 * Controls which (if any) function is called by the model. none means the model will not call a
	 * function and instead generates a message. auto means the model can pick between generating a message or calling a
	 * function. Specifying a particular function via {"type: "function", "function": {"name": "my_function"}} forces
	 * the model to call that function. none is the default when no functions are present. auto is the default if
	 * functions are present. Use the {@link MiniMaxApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice object.
	 */
	private @Nullable String toolChoice;

	/**
	 * MiniMax Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the functionCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the functionCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the functionCallbacks registry.
	 * The {@link #toolCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	private Set<String> toolNames = new HashSet<>();

	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	private @Nullable Boolean internalToolExecutionEnabled;

	// @formatter:on

	// TODO: left here for ModelOptionUtils.merge*()
	public MiniMaxChatOptions() {
	}

	protected MiniMaxChatOptions(String model, @Nullable Double frequencyPenalty, @Nullable Integer maxTokens,
			@Nullable Integer n, @Nullable Double presencePenalty,
			MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat, @Nullable Integer seed,
			@Nullable List<String> stop, @Nullable Double temperature, @Nullable Double topP,
			@Nullable Boolean maskSensitiveInfo, @Nullable List<MiniMaxApi.FunctionTool> tools,
			@Nullable String toolChoice, @Nullable List<ToolCallback> toolCallbacks, @Nullable Set<String> toolNames,
			@Nullable Map<String, Object> toolContext, @Nullable Boolean internalToolExecutionEnabled) {
		this.model = model;
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.n = n;
		this.presencePenalty = presencePenalty;
		this.responseFormat = responseFormat;
		this.seed = seed;
		this.stop = stop;
		this.temperature = temperature;
		this.topP = topP;
		this.maskSensitiveInfo = maskSensitiveInfo;
		this.tools = tools;
		this.toolChoice = toolChoice;
		this.toolCallbacks = toolCallbacks == null ? new ArrayList<>() : new ArrayList<>(toolCallbacks);
		this.toolNames = toolNames == null ? new HashSet<>() : new HashSet<>(toolNames);
		this.toolContext = toolContext == null ? new HashMap<>() : new HashMap<>(toolContext);
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static MiniMaxChatOptions fromOptions(MiniMaxChatOptions fromOptions) {
		return fromOptions.mutate().build();
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

	public @Nullable Integer getN() {
		return this.n;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return getStop();
	}

	public @Nullable List<String> getStop() {
		return (this.stop != null) ? Collections.unmodifiableList(this.stop) : null;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public @Nullable Boolean getMaskSensitiveInfo() {
		return this.maskSensitiveInfo;
	}

	public @Nullable List<MiniMaxApi.FunctionTool> getTools() {
		return (this.tools != null) ? Collections.unmodifiableList(this.tools) : null;
	}

	public @Nullable String getToolChoice() {
		return this.toolChoice;
	}

	@Override
	public @Nullable Integer getTopK() {
		return null;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return Collections.unmodifiableList(this.toolCallbacks);
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return Collections.unmodifiableSet(this.toolNames);
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return Collections.unmodifiableMap(this.toolContext);
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		Assert.notNull(toolContext, "toolContext cannot be null");
		this.toolContext = toolContext;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.n, this.presencePenalty,
				this.responseFormat, this.seed, this.stop, this.temperature, this.topP, this.maskSensitiveInfo,
				this.tools, this.toolChoice, this.toolCallbacks, this.toolNames, this.toolContext,
				this.internalToolExecutionEnabled);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MiniMaxChatOptions that = (MiniMaxChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens) && Objects.equals(this.n, that.n)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.stop, that.stop) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.maskSensitiveInfo, that.maskSensitiveInfo)
				&& Objects.equals(this.tools, that.tools) && Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames) && Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled);
	}

	@Override
	public MiniMaxChatOptions copy() {
		return mutate().build();
	}

	@Override
	public Builder mutate() {
		return MiniMaxChatOptions.builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stop)
			.temperature(this.temperature)
			.topK(this.getTopK()) // unused in this model
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolNames(this.getToolNames())
			.toolContext(this.getToolContext())
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// MiniMax Specific
			.N(this.n)
			.responseFormat(this.responseFormat)
			.seed(this.seed)
			.maskSensitiveInfo(this.maskSensitiveInfo)
			.tools(this.tools)
			.toolChoice(this.toolChoice);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> {

		@Override
		public B clone() {
			B copy = super.clone();
			copy.tools = this.tools == null ? null : new ArrayList<>(this.tools);
			return copy;
		}

		protected @Nullable Integer n;

		protected MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat;

		protected @Nullable Integer seed;

		protected @Nullable Boolean maskSensitiveInfo;

		protected @Nullable List<MiniMaxApi.FunctionTool> tools;

		protected @Nullable String toolChoice;

		public B N(@Nullable Integer n) {
			this.n = n;
			return self();
		}

		public B responseFormat(MiniMaxApi.ChatCompletionRequest.@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B seed(@Nullable Integer seed) {
			this.seed = seed;
			return self();
		}

		public B stop(@Nullable List<String> stop) {
			return this.stopSequences(stop);
		}

		public B maskSensitiveInfo(@Nullable Boolean maskSensitiveInfo) {
			this.maskSensitiveInfo = maskSensitiveInfo;
			return self();
		}

		public B tools(@Nullable List<MiniMaxApi.FunctionTool> tools) {
			this.tools = tools;
			return self();
		}

		public B toolChoice(@Nullable String toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.n != null) {
					this.n = that.n;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.seed != null) {
					this.seed = that.seed;
				}
				if (that.maskSensitiveInfo != null) {
					this.maskSensitiveInfo = that.maskSensitiveInfo;
				}
				if (that.tools != null) {
					this.tools = that.tools;
				}
				if (that.toolChoice != null) {
					this.toolChoice = that.toolChoice;
				}
			}
			return self();
		}

		@Override
		@SuppressWarnings("NullAway")
		public MiniMaxChatOptions build() {
			// TODO: add assertions, remove SuppressWarnings
			// Assert.state(this.model != null, "model must be set");
			return new MiniMaxChatOptions(this.model, this.frequencyPenalty, this.maxTokens, this.n,
					this.presencePenalty, this.responseFormat, this.seed, this.stopSequences, this.temperature,
					this.topP, this.maskSensitiveInfo, this.tools, this.toolChoice, this.toolCallbacks, this.toolNames,
					this.toolContext, this.internalToolExecutionEnabled);
		}

	}

}
