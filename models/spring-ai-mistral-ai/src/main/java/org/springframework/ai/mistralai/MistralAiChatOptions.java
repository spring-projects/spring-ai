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

package org.springframework.ai.mistralai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.FunctionTool;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Options for the Mistral AI Chat API.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Jason Smith
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
public class MistralAiChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	/**
	 * ID of the model to use
	 */
	@SuppressWarnings("NullAway.Init")
	private String model;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @Nullable Double temperature;

	/**
	 * Nucleus sampling, where the model considers the results of the tokens with top_p
	 * probability mass. So 0.1 means only the tokens comprising the top 10% probability
	 * mass are considered. We generally recommend altering this or temperature but not
	 * both.
	 */
	private Double topP = 1.0;

	/**
	 * The maximum number of tokens to generate in the completion. The token count of your
	 * prompt plus max_tokens cannot exceed the model's context length.
	 */
	private @Nullable Integer maxTokens;

	/**
	 * Whether to inject a safety prompt before all conversations.
	 */
	private Boolean safePrompt = false;

	/**
	 * The seed to use for random sampling. If set, different calls will generate
	 * deterministic results.
	 */
	private @Nullable Integer randomSeed;

	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates
	 * is valid JSON.
	 */
	private @Nullable ResponseFormat responseFormat;

	/**
	 * Stop generation if this token is detected. Or if one of these tokens is detected
	 * when providing an array.
	 */
	private @Nullable List<String> stop;

	/**
	 * Number between -2.0 and 2.0. frequency_penalty penalizes the repetition of words
	 * based on their frequency in the generated text. A higher frequency penalty
	 * discourages the model from repeating words that have already appeared frequently in
	 * the output, promoting diversity and reducing repetition.
	 */
	private Double frequencyPenalty = 0.0;

	/**
	 * Number between -2.0 and 2.0. presence_penalty determines how much the model
	 * penalizes the repetition of words or phrases. A higher presence penalty encourages
	 * the model to use a wider variety of words and phrases, making the output more
	 * diverse and creative.
	 */
	private Double presencePenalty = 0.0;

	/**
	 * Number of completions to return for each request, input tokens are only billed
	 * once.
	 */
	private @Nullable Integer n;

	/**
	 * A list of tools the model may call. Currently, only functions are supported as a
	 * tool. Use this to provide a list of functions the model may generate JSON inputs
	 * for.
	 */
	private @Nullable List<FunctionTool> tools;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function.
	 */
	private @Nullable ToolChoice toolChoice;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	private @Nullable Boolean internalToolExecutionEnabled;

	private Map<String, Object> toolContext = new HashMap<>();

	// Temporary constructor to maintain compat with ModelOptionUtils
	public MistralAiChatOptions() {
	}

	protected MistralAiChatOptions(String model, @Nullable Double temperature, @Nullable Double topP,
			@Nullable Integer maxTokens, @Nullable Boolean safePrompt, @Nullable Integer randomSeed,
			@Nullable ResponseFormat responseFormat, @Nullable List<String> stop, @Nullable Double frequencyPenalty,
			@Nullable Double presencePenalty, @Nullable Integer n, @Nullable List<FunctionTool> tools,
			@Nullable ToolChoice toolChoice, @Nullable List<ToolCallback> toolCallbacks,
			@Nullable Set<String> toolNames, @Nullable Boolean internalToolExecutionEnabled,
			@Nullable Map<String, Object> toolContext) {

		this.model = model;
		this.temperature = temperature;
		if (topP != null) {
			this.topP = topP;
		}
		this.maxTokens = maxTokens;
		if (safePrompt != null) {
			this.safePrompt = safePrompt;
		}
		this.randomSeed = randomSeed;
		this.responseFormat = responseFormat;
		this.stop = stop;
		if (frequencyPenalty != null) {
			this.frequencyPenalty = frequencyPenalty;
		}
		if (presencePenalty != null) {
			this.presencePenalty = presencePenalty;
		}
		this.n = n;
		this.tools = tools;
		this.toolChoice = toolChoice;
		if (toolCallbacks != null) {
			this.toolCallbacks = new ArrayList<>(toolCallbacks);
		}
		if (toolNames != null) {
			this.toolNames = new HashSet<>(toolNames);
		}
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		if (toolContext != null) {
			this.toolContext = new HashMap<>(toolContext);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static MistralAiChatOptions fromOptions(MistralAiChatOptions fromOptions) {
		return fromOptions.mutate().build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public Boolean getSafePrompt() {
		return this.safePrompt;
	}

	public @Nullable Integer getRandomSeed() {
		return this.randomSeed;
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

	public @Nullable List<FunctionTool> getTools() {
		return this.tools;
	}

	public @Nullable ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public @Nullable Integer getN() {
		return this.n;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public @Nullable Integer getTopK() {
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
	public @Nullable String getOutputSchema() {
		if (this.responseFormat == null || this.responseFormat.getJsonSchema() == null) {
			return null;
		}
		return ModelOptionsUtils.toJsonString(this.responseFormat.getJsonSchema().getSchema());
	}

	@Override
	public void setOutputSchema(String outputSchema) {
		this.responseFormat = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(outputSchema)
			.build();
	}

	@Override
	public MistralAiChatOptions copy() {
		return mutate().build();
	}

	public Builder mutate() {
		return builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stop(this.stop == null ? null : new ArrayList<>(this.stop))
			.temperature(this.temperature)
			.topP(this.topP)
			.topK(this.getTopK()) // always null but here for consistency
			// ToolCallingChatOptions
			.toolCallbacks(new ArrayList<>(this.getToolCallbacks()))
			.toolNames(new HashSet<>(this.getToolNames()))
			.toolContext(new HashMap<>(this.getToolContext()))
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// Mistral AI specific
			.safePrompt(this.safePrompt)
			.randomSeed(this.randomSeed)
			.responseFormat(this.responseFormat)
			.n(this.n)
			.tools(this.tools != null ? new ArrayList<>(this.tools) : null)
			.toolChoice(this.toolChoice);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.temperature, this.topP, this.maxTokens, this.safePrompt, this.randomSeed,
				this.responseFormat, this.stop, this.frequencyPenalty, this.presencePenalty, this.n, this.tools,
				this.toolChoice, this.toolCallbacks, this.tools, this.internalToolExecutionEnabled, this.toolContext);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		MistralAiChatOptions other = (MistralAiChatOptions) obj;

		return Objects.equals(this.model, other.model) && Objects.equals(this.temperature, other.temperature)
				&& Objects.equals(this.topP, other.topP) && Objects.equals(this.maxTokens, other.maxTokens)
				&& Objects.equals(this.safePrompt, other.safePrompt)
				&& Objects.equals(this.randomSeed, other.randomSeed)
				&& Objects.equals(this.responseFormat, other.responseFormat) && Objects.equals(this.stop, other.stop)
				&& Objects.equals(this.frequencyPenalty, other.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, other.presencePenalty) && Objects.equals(this.n, other.n)
				&& Objects.equals(this.tools, other.tools) && Objects.equals(this.toolChoice, other.toolChoice)
				&& Objects.equals(this.toolCallbacks, other.toolCallbacks)
				&& Objects.equals(this.toolNames, other.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, other.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, other.toolContext);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> implements StructuredOutputChatOptions.Builder<B> {

		@Override
		public B clone() {
			AbstractBuilder<B> copy = super.clone();
			copy.tools = this.tools == null ? null : new ArrayList<>(this.tools);
			return (B) copy;
		}

		private @Nullable Boolean safePrompt;

		private @Nullable Integer randomSeed;

		private @Nullable ResponseFormat responseFormat;

		private @Nullable Integer n;

		private @Nullable List<FunctionTool> tools;

		private @Nullable ToolChoice toolChoice;

		public B model(MistralAiApi.@Nullable ChatModel chatModel) {
			if (chatModel != null) {
				this.model(chatModel.getName());
			}
			else {
				this.model((String) null);
			}
			return self();
		}

		public B safePrompt(@Nullable Boolean safePrompt) {
			this.safePrompt = safePrompt;
			return self();
		}

		public B randomSeed(@Nullable Integer randomSeed) {
			this.randomSeed = randomSeed;
			return self();
		}

		public B stop(@Nullable List<String> stop) {
			super.stopSequences(stop);
			return self();
		}

		public B responseFormat(@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B n(@Nullable Integer n) {
			this.n = n;
			return self();
		}

		public B tools(@Nullable List<FunctionTool> tools) {
			this.tools = tools;
			return self();
		}

		public B toolChoice(@Nullable ToolChoice toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			if (outputSchema != null) {
				this.responseFormat = ResponseFormat.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(outputSchema)
					.build();
			}
			else {
				this.responseFormat = null;
			}
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that) {
				if (that.safePrompt != null) {
					this.safePrompt = that.safePrompt;
				}
				if (that.randomSeed != null) {
					this.randomSeed = that.randomSeed;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.n != null) {
					this.n = that.n;
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
		public MistralAiChatOptions build() {
			// TODO: add assertions, remove SuppressWarnings
			// Assert.state(this.model != null, "model must be set");
			return new MistralAiChatOptions(this.model, this.temperature, this.topP, this.maxTokens, this.safePrompt,
					this.randomSeed, this.responseFormat, this.stopSequences, this.frequencyPenalty,
					this.presencePenalty, this.n, this.tools, this.toolChoice, this.toolCallbacks, this.toolNames,
					this.internalToolExecutionEnabled, this.toolContext);
		}

	}

}
