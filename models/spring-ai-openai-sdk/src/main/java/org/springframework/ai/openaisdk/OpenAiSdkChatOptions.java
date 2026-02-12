/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.openaisdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionAudioParam;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel.ResponseFormat.Type;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Configuration information for the Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 */
public class OpenAiSdkChatOptions extends AbstractOpenAiSdkOptions
		implements ToolCallingChatOptions, StructuredOutputChatOptions {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GPT_5_MINI.asString();

	private static final Logger logger = LoggerFactory.getLogger(OpenAiSdkChatOptions.class);

	private @Nullable Double frequencyPenalty;

	private @Nullable Map<String, Integer> logitBias;

	private @Nullable Boolean logprobs;

	private @Nullable Integer topLogprobs;

	private @Nullable Integer maxTokens;

	private @Nullable Integer maxCompletionTokens;

	private @Nullable Integer n;

	private @Nullable List<String> outputModalities;

	private @Nullable AudioParameters outputAudio;

	private @Nullable Double presencePenalty;

	private OpenAiSdkChatModel.@Nullable ResponseFormat responseFormat;

	private @Nullable StreamOptions streamOptions;

	private @Nullable Integer seed;

	private @Nullable List<String> stop;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Object toolChoice;

	private @Nullable String user;

	private @Nullable Boolean parallelToolCalls;

	private @Nullable Boolean store;

	private @Nullable Map<String, String> metadata;

	private @Nullable String reasoningEffort;

	private @Nullable String verbosity;

	private @Nullable String serviceTier;

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private @Nullable Boolean internalToolExecutionEnabled;

	private Map<String, Object> toolContext = new HashMap<>();

	/**
	 * Gets the frequency penalty parameter.
	 * @return the frequency penalty
	 */
	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	/**
	 * Sets the frequency penalty parameter.
	 * @param frequencyPenalty the frequency penalty to set
	 */
	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	/**
	 * Gets the logit bias map.
	 * @return the logit bias map
	 */
	public @Nullable Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	/**
	 * Sets the logit bias map.
	 * @param logitBias the logit bias map to set
	 */
	public void setLogitBias(@Nullable Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
	}

	/**
	 * Gets whether to return log probabilities.
	 * @return true if log probabilities should be returned
	 */
	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	/**
	 * Sets whether to return log probabilities.
	 * @param logprobs whether to return log probabilities
	 */
	public void setLogprobs(@Nullable Boolean logprobs) {
		this.logprobs = logprobs;
	}

	/**
	 * Gets the number of top log probabilities to return.
	 * @return the number of top log probabilities
	 */
	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	/**
	 * Sets the number of top log probabilities to return.
	 * @param topLogprobs the number of top log probabilities
	 */
	public void setTopLogprobs(@Nullable Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	/**
	 * Sets the maximum number of tokens to generate.
	 * @param maxTokens the maximum number of tokens
	 */
	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	/**
	 * Gets the maximum number of completion tokens.
	 * @return the maximum number of completion tokens
	 */
	public @Nullable Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}

	/**
	 * Sets the maximum number of completion tokens.
	 * @param maxCompletionTokens the maximum number of completion tokens
	 */
	public void setMaxCompletionTokens(@Nullable Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}

	/**
	 * Gets the number of completions to generate.
	 * @return the number of completions
	 */
	public @Nullable Integer getN() {
		return this.n;
	}

	/**
	 * Sets the number of completions to generate.
	 * @param n the number of completions
	 */
	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	/**
	 * Gets the output modalities.
	 * @return the output modalities
	 */
	public @Nullable List<String> getOutputModalities() {
		return this.outputModalities;
	}

	/**
	 * Sets the output modalities.
	 * @param outputModalities the output modalities
	 */
	public void setOutputModalities(@Nullable List<String> outputModalities) {
		this.outputModalities = outputModalities;
	}

	/**
	 * Gets the output audio parameters.
	 * @return the output audio parameters
	 */
	public @Nullable AudioParameters getOutputAudio() {
		return this.outputAudio;
	}

	/**
	 * Sets the output audio parameters.
	 * @param outputAudio the output audio parameters
	 */
	public void setOutputAudio(@Nullable AudioParameters outputAudio) {
		this.outputAudio = outputAudio;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	/**
	 * Sets the presence penalty parameter.
	 * @param presencePenalty the presence penalty to set
	 */
	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	/**
	 * Gets the response format configuration.
	 * @return the response format
	 */
	public OpenAiSdkChatModel.@Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	/**
	 * Sets the response format configuration.
	 * @param responseFormat the response format to set
	 */
	public void setResponseFormat(OpenAiSdkChatModel.@Nullable ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	/**
	 * Gets the stream options.
	 * @return the stream options
	 */
	public @Nullable StreamOptions getStreamOptions() {
		return this.streamOptions;
	}

	/**
	 * Sets the stream options.
	 * @param streamOptions the stream options to set
	 */
	public void setStreamOptions(@Nullable StreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}

	/**
	 * Gets the random seed for deterministic generation.
	 * @return the random seed
	 */
	public @Nullable Integer getSeed() {
		return this.seed;
	}

	/**
	 * Sets the random seed for deterministic generation.
	 * @param seed the random seed
	 */
	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	/**
	 * Gets the stop sequences.
	 * @return the list of stop sequences
	 */
	public @Nullable List<String> getStop() {
		return this.stop;
	}

	/**
	 * Sets the stop sequences.
	 * @param stop the list of stop sequences
	 */
	public void setStop(@Nullable List<String> stop) {
		this.stop = stop;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return getStop();
	}

	/**
	 * Sets the stop sequences.
	 * @param stopSequences the list of stop sequences
	 */
	public void setStopSequences(@Nullable List<String> stopSequences) {
		setStop(stopSequences);
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	/**
	 * Sets the temperature for sampling.
	 * @param temperature the temperature value
	 */
	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	/**
	 * Sets the top-p nucleus sampling parameter.
	 * @param topP the top-p value
	 */
	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	/**
	 * Gets the tool choice configuration.
	 * @return the tool choice option
	 */
	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}

	/**
	 * Sets the tool choice configuration.
	 * @param toolChoice the tool choice option
	 */
	public void setToolChoice(@Nullable Object toolChoice) {
		this.toolChoice = toolChoice;
	}

	/**
	 * Gets the user identifier.
	 * @return the user identifier
	 */
	public @Nullable String getUser() {
		return this.user;
	}

	/**
	 * Sets the user identifier.
	 * @param user the user identifier
	 */
	public void setUser(@Nullable String user) {
		this.user = user;
	}

	/**
	 * Gets whether to enable parallel tool calls.
	 * @return true if parallel tool calls are enabled
	 */
	public @Nullable Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	/**
	 * Sets whether to enable parallel tool calls.
	 * @param parallelToolCalls whether to enable parallel tool calls
	 */
	public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	/**
	 * Gets whether to store the conversation.
	 * @return true if the conversation should be stored
	 */
	public @Nullable Boolean getStore() {
		return this.store;
	}

	/**
	 * Sets whether to store the conversation.
	 * @param store whether to store the conversation
	 */
	public void setStore(@Nullable Boolean store) {
		this.store = store;
	}

	/**
	 * Gets the metadata map.
	 * @return the metadata map
	 */
	public @Nullable Map<String, String> getMetadata() {
		return this.metadata;
	}

	/**
	 * Sets the metadata map.
	 * @param metadata the metadata map
	 */
	public void setMetadata(@Nullable Map<String, String> metadata) {
		this.metadata = metadata;
	}

	/**
	 * Gets the reasoning effort level.
	 * @return the reasoning effort level
	 */
	public @Nullable String getReasoningEffort() {
		return this.reasoningEffort;
	}

	/**
	 * Sets the reasoning effort level.
	 * @param reasoningEffort the reasoning effort level
	 */
	public void setReasoningEffort(@Nullable String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	/**
	 * Gets the verbosity level.
	 * @return the verbosity level
	 */
	public @Nullable String getVerbosity() {
		return this.verbosity;
	}

	/**
	 * Sets the verbosity level.
	 * @param verbosity the verbosity level
	 */
	public void setVerbosity(@Nullable String verbosity) {
		this.verbosity = verbosity;
	}

	/**
	 * Gets the service tier.
	 * @return the service tier
	 */
	public @Nullable String getServiceTier() {
		return this.serviceTier;
	}

	/**
	 * Sets the service tier.
	 * @param serviceTier the service tier
	 */
	public void setServiceTier(@Nullable String serviceTier) {
		this.serviceTier = serviceTier;
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
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
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
	public @Nullable Integer getTopK() {
		return null;
	}

	@Override
	@JsonIgnore
	public @Nullable String getOutputSchema() {
		OpenAiSdkChatModel.ResponseFormat format = this.getResponseFormat();
		return format != null ? format.getJsonSchema() : null;
	}

	@Override
	@JsonIgnore
	public void setOutputSchema(@Nullable String outputSchema) {
		if (outputSchema != null) {
			this.setResponseFormat(OpenAiSdkChatModel.ResponseFormat.builder()
				.type(Type.JSON_SCHEMA)
				.jsonSchema(outputSchema)
				.build());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public OpenAiSdkChatOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiSdkChatOptions options = (OpenAiSdkChatOptions) o;
		return Objects.equals(this.getModel(), options.getModel())
				&& Objects.equals(this.frequencyPenalty, options.frequencyPenalty)
				&& Objects.equals(this.logitBias, options.logitBias) && Objects.equals(this.logprobs, options.logprobs)
				&& Objects.equals(this.temperature, options.temperature)
				&& Objects.equals(this.maxTokens, options.maxTokens)
				&& Objects.equals(this.maxCompletionTokens, options.maxCompletionTokens)
				&& Objects.equals(this.n, options.n) && Objects.equals(this.outputModalities, options.outputModalities)
				&& Objects.equals(this.outputAudio, options.outputAudio)
				&& Objects.equals(this.presencePenalty, options.presencePenalty)
				&& Objects.equals(this.responseFormat, options.responseFormat)
				&& Objects.equals(this.streamOptions, options.streamOptions) && Objects.equals(this.seed, options.seed)
				&& Objects.equals(this.stop, options.stop) && Objects.equals(this.topP, options.topP)
				&& Objects.equals(this.toolChoice, options.toolChoice) && Objects.equals(this.user, options.user)
				&& Objects.equals(this.parallelToolCalls, options.parallelToolCalls)
				&& Objects.equals(this.store, options.store) && Objects.equals(this.metadata, options.metadata)
				&& Objects.equals(this.reasoningEffort, options.reasoningEffort)
				&& Objects.equals(this.verbosity, options.verbosity)
				&& Objects.equals(this.serviceTier, options.serviceTier)
				&& Objects.equals(this.toolCallbacks, options.toolCallbacks)
				&& Objects.equals(this.toolNames, options.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, options.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, options.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getModel(), this.frequencyPenalty, this.logitBias, this.logprobs, this.topLogprobs,
				this.maxTokens, this.maxCompletionTokens, this.n, this.outputModalities, this.outputAudio,
				this.presencePenalty, this.responseFormat, this.streamOptions, this.seed, this.stop, this.temperature,
				this.topP, this.toolChoice, this.user, this.parallelToolCalls, this.store, this.metadata,
				this.reasoningEffort, this.verbosity, this.serviceTier, this.toolCallbacks, this.toolNames,
				this.internalToolExecutionEnabled, this.toolContext);
	}

	@Override
	public String toString() {
		return "OpenAiSdkChatOptions{" + "model='" + this.getModel() + ", frequencyPenalty=" + this.frequencyPenalty
				+ ", logitBias=" + this.logitBias + ", logprobs=" + this.logprobs + ", topLogprobs=" + this.topLogprobs
				+ ", maxTokens=" + this.maxTokens + ", maxCompletionTokens=" + this.maxCompletionTokens + ", n="
				+ this.n + ", outputModalities=" + this.outputModalities + ", outputAudio=" + this.outputAudio
				+ ", presencePenalty=" + this.presencePenalty + ", responseFormat=" + this.responseFormat
				+ ", streamOptions=" + this.streamOptions + ", streamUsage=" + ", seed=" + this.seed + ", stop="
				+ this.stop + ", temperature=" + this.temperature + ", topP=" + this.topP + ", toolChoice="
				+ this.toolChoice + ", user='" + this.user + '\'' + ", parallelToolCalls=" + this.parallelToolCalls
				+ ", store=" + this.store + ", metadata=" + this.metadata + ", reasoningEffort='" + this.reasoningEffort
				+ '\'' + ", verbosity='" + this.verbosity + '\'' + ", serviceTier='" + this.serviceTier + '\''
				+ ", toolCallbacks=" + this.toolCallbacks + ", toolNames=" + this.toolNames
				+ ", internalToolExecutionEnabled=" + this.internalToolExecutionEnabled + ", toolContext="
				+ this.toolContext + '}';
	}

	public record AudioParameters(@Nullable Voice voice, @Nullable AudioResponseFormat format) {

		/**
		 * Specifies the voice type.
		 */
		public enum Voice {

			ALLOY, ASH, BALLAD, CORAL, ECHO, FABLE, ONYX, NOVA, SAGE, SHIMMER

		}

		/**
		 * Specifies the output audio format.
		 */
		public enum AudioResponseFormat {

			MP3, FLAC, OPUS, PCM16, WAV, AAC

		}

		public ChatCompletionAudioParam toChatCompletionAudioParam() {
			ChatCompletionAudioParam.Builder builder = ChatCompletionAudioParam.builder();
			if (this.voice() != null) {
				builder.voice(voice().name().toLowerCase());
			}
			if (this.format() != null) {
				builder.format(ChatCompletionAudioParam.Format.of(this.format().name().toLowerCase()));
			}
			return builder.build();
		}
	}

	public record StreamOptions(@Nullable Boolean includeObfuscation, @Nullable Boolean includeUsage,
			@Nullable Map<String, Object> additionalProperties) {

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private @Nullable Boolean includeObfuscation;

			private @Nullable Boolean includeUsage;

			private @Nullable Map<String, Object> additionalProperties = new HashMap<>();

			public Builder from(@Nullable StreamOptions fromOptions) {
				if (fromOptions != null) {
					this.includeObfuscation = fromOptions.includeObfuscation();
					this.includeUsage = fromOptions.includeUsage();
					this.additionalProperties = fromOptions.additionalProperties() != null
							? new HashMap<>(fromOptions.additionalProperties()) : new HashMap<>();
				}
				return this;
			}

			public Builder includeObfuscation(@Nullable Boolean includeObfuscation) {
				this.includeObfuscation = includeObfuscation;
				return this;
			}

			public Builder includeUsage(@Nullable Boolean includeUsage) {
				this.includeUsage = includeUsage;
				return this;
			}

			public Builder additionalProperties(@Nullable Map<String, Object> additionalProperties) {
				this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties)
						: new HashMap<>();
				return this;
			}

			public Builder additionalProperty(String key, Object value) {
				if (this.additionalProperties == null) {
					this.additionalProperties = new HashMap<>();
				}
				this.additionalProperties.put(key, value);
				return this;
			}

			public StreamOptions build() {
				return new StreamOptions(this.includeObfuscation, this.includeUsage, this.additionalProperties);
			}

		}

	}

	public static final class Builder {

		private final OpenAiSdkChatOptions options = new OpenAiSdkChatOptions();

		public Builder from(OpenAiSdkChatOptions fromOptions) {
			// Parent class fields
			this.options.setBaseUrl(fromOptions.getBaseUrl());
			this.options.setApiKey(fromOptions.getApiKey());
			this.options.setCredential(fromOptions.getCredential());
			this.options.setModel(fromOptions.getModel());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setMicrosoftFoundryServiceVersion(fromOptions.getMicrosoftFoundryServiceVersion());
			this.options.setOrganizationId(fromOptions.getOrganizationId());
			this.options.setMicrosoftFoundry(fromOptions.isMicrosoftFoundry());
			this.options.setGitHubModels(fromOptions.isGitHubModels());
			this.options.setTimeout(fromOptions.getTimeout());
			this.options.setMaxRetries(fromOptions.getMaxRetries());
			this.options.setProxy(fromOptions.getProxy());
			this.options.setCustomHeaders(new HashMap<>(fromOptions.getCustomHeaders()));
			// Child class fields
			this.options.setFrequencyPenalty(fromOptions.getFrequencyPenalty());
			this.options.setLogitBias(fromOptions.getLogitBias());
			this.options.setLogprobs(fromOptions.getLogprobs());
			this.options.setTopLogprobs(fromOptions.getTopLogprobs());
			this.options.setMaxTokens(fromOptions.getMaxTokens());
			this.options.setMaxCompletionTokens(fromOptions.getMaxCompletionTokens());
			this.options.setN(fromOptions.getN());
			this.options.setOutputModalities(fromOptions.getOutputModalities());
			this.options.setOutputAudio(fromOptions.getOutputAudio());
			this.options.setPresencePenalty(fromOptions.getPresencePenalty());
			this.options.setResponseFormat(fromOptions.getResponseFormat());
			this.options.setStreamOptions(fromOptions.getStreamOptions());
			this.options.setSeed(fromOptions.getSeed());
			this.options.setStop(fromOptions.getStop() != null ? new ArrayList<>(fromOptions.getStop()) : null);
			this.options.setTemperature(fromOptions.getTemperature());
			this.options.setTopP(fromOptions.getTopP());
			this.options.setToolChoice(fromOptions.getToolChoice());
			this.options.setUser(fromOptions.getUser());
			this.options.setParallelToolCalls(fromOptions.getParallelToolCalls());
			this.options.setToolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()));
			this.options.setToolNames(new HashSet<>(fromOptions.getToolNames()));
			this.options.setInternalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled());
			this.options.setToolContext(new HashMap<>(fromOptions.getToolContext()));
			this.options.setStore(fromOptions.getStore());
			this.options.setMetadata(fromOptions.getMetadata());
			this.options.setReasoningEffort(fromOptions.getReasoningEffort());
			this.options.setVerbosity(fromOptions.getVerbosity());
			this.options.setServiceTier(fromOptions.getServiceTier());
			return this;
		}

		public Builder merge(@Nullable OpenAiSdkChatOptions from) {
			if (from == null) {
				return this;
			}
			// Parent class fields
			if (from.getBaseUrl() != null) {
				this.options.setBaseUrl(from.getBaseUrl());
			}
			if (from.getApiKey() != null) {
				this.options.setApiKey(from.getApiKey());
			}
			if (from.getCredential() != null) {
				this.options.setCredential(from.getCredential());
			}
			if (from.getModel() != null) {
				this.options.setModel(from.getModel());
			}
			if (from.getDeploymentName() != null) {
				this.options.setDeploymentName(from.getDeploymentName());
			}
			if (from.getMicrosoftFoundryServiceVersion() != null) {
				this.options.setMicrosoftFoundryServiceVersion(from.getMicrosoftFoundryServiceVersion());
			}
			if (from.getOrganizationId() != null) {
				this.options.setOrganizationId(from.getOrganizationId());
			}
			this.options.setMicrosoftFoundry(from.isMicrosoftFoundry());
			this.options.setGitHubModels(from.isGitHubModels());
			this.options.setCustomHeaders(from.getCustomHeaders());
			if (from.getTimeout() != null) {
				this.options.setTimeout(from.getTimeout());
			}
			if (from.getMaxRetries() != null) {
				this.options.setMaxRetries(from.getMaxRetries());
			}
			if (from.getProxy() != null) {
				this.options.setProxy(from.getProxy());
			}
			// Child class fields
			if (from.getFrequencyPenalty() != null) {
				this.options.setFrequencyPenalty(from.getFrequencyPenalty());
			}
			if (from.getLogitBias() != null) {
				this.options.setLogitBias(from.getLogitBias());
			}
			if (from.getLogprobs() != null) {
				this.options.setLogprobs(from.getLogprobs());
			}
			if (from.getTopLogprobs() != null) {
				this.options.setTopLogprobs(from.getTopLogprobs());
			}
			if (from.getMaxTokens() != null) {
				this.options.setMaxTokens(from.getMaxTokens());
			}
			if (from.getMaxCompletionTokens() != null) {
				this.options.setMaxCompletionTokens(from.getMaxCompletionTokens());
			}
			if (from.getN() != null) {
				this.options.setN(from.getN());
			}
			if (from.getOutputModalities() != null) {
				this.options.setOutputModalities(new ArrayList<>(from.getOutputModalities()));
			}
			if (from.getOutputAudio() != null) {
				this.options.setOutputAudio(from.getOutputAudio());
			}
			if (from.getPresencePenalty() != null) {
				this.options.setPresencePenalty(from.getPresencePenalty());
			}
			if (from.getResponseFormat() != null) {
				this.options.setResponseFormat(from.getResponseFormat());
			}
			if (from.getStreamOptions() != null) {
				this.options.setStreamOptions(from.getStreamOptions());
			}
			if (from.getSeed() != null) {
				this.options.setSeed(from.getSeed());
			}
			if (from.getStop() != null) {
				this.options.setStop(new ArrayList<>(from.getStop()));
			}
			if (from.getTemperature() != null) {
				this.options.setTemperature(from.getTemperature());
			}
			if (from.getTopP() != null) {
				this.options.setTopP(from.getTopP());
			}
			if (from.getToolChoice() != null) {
				this.options.setToolChoice(from.getToolChoice());
			}
			if (from.getUser() != null) {
				this.options.setUser(from.getUser());
			}
			if (from.getParallelToolCalls() != null) {
				this.options.setParallelToolCalls(from.getParallelToolCalls());
			}
			if (!from.getToolCallbacks().isEmpty()) {
				this.options.setToolCallbacks(new ArrayList<>(from.getToolCallbacks()));
			}
			if (!from.getToolNames().isEmpty()) {
				this.options.setToolNames(new HashSet<>(from.getToolNames()));
			}
			if (from.getInternalToolExecutionEnabled() != null) {
				this.options.setInternalToolExecutionEnabled(from.getInternalToolExecutionEnabled());
			}
			if (!from.getToolContext().isEmpty()) {
				this.options.setToolContext(new HashMap<>(from.getToolContext()));
			}
			if (from.getStore() != null) {
				this.options.setStore(from.getStore());
			}
			if (from.getMetadata() != null) {
				this.options.setMetadata(from.getMetadata());
			}
			if (from.getReasoningEffort() != null) {
				this.options.setReasoningEffort(from.getReasoningEffort());
			}
			if (from.getVerbosity() != null) {
				this.options.setVerbosity(from.getVerbosity());
			}
			if (from.getServiceTier() != null) {
				this.options.setServiceTier(from.getServiceTier());
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder baseUrl(String baseUrl) {
			this.options.setBaseUrl(baseUrl);
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.options.setApiKey(apiKey);
			return this;
		}

		public Builder credential(com.openai.credential.Credential credential) {
			this.options.setCredential(credential);
			return this;
		}

		public Builder azureOpenAIServiceVersion(com.openai.azure.AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
			this.options.setMicrosoftFoundryServiceVersion(azureOpenAIServiceVersion);
			return this;
		}

		public Builder organizationId(String organizationId) {
			this.options.setOrganizationId(organizationId);
			return this;
		}

		public Builder azure(boolean azure) {
			this.options.setMicrosoftFoundry(azure);
			return this;
		}

		public Builder gitHubModels(boolean gitHubModels) {
			this.options.setGitHubModels(gitHubModels);
			return this;
		}

		public Builder timeout(java.time.Duration timeout) {
			this.options.setTimeout(timeout);
			return this;
		}

		public Builder maxRetries(Integer maxRetries) {
			this.options.setMaxRetries(maxRetries);
			return this;
		}

		public Builder proxy(java.net.Proxy proxy) {
			this.options.setProxy(proxy);
			return this;
		}

		public Builder customHeaders(Map<String, String> customHeaders) {
			this.options.setCustomHeaders(customHeaders);
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder logitBias(Map<String, Integer> logitBias) {
			this.options.setLogitBias(logitBias);
			return this;
		}

		public Builder logprobs(Boolean logprobs) {
			this.options.setLogprobs(logprobs);
			return this;
		}

		public Builder topLogprobs(Integer topLogprobs) {
			this.options.setTopLogprobs(topLogprobs);
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			if (this.options.getMaxCompletionTokens() != null) {
				logger.warn(
						"Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
								+ "As maxToken is deprecated, we will ignore it and use maxCompletionToken ({}).",
						this.options.getMaxCompletionTokens());
			}
			else {
				this.options.setMaxTokens(maxTokens);
			}
			return this;
		}

		public Builder maxCompletionTokens(Integer maxCompletionTokens) {
			if (maxCompletionTokens != null && this.options.getMaxTokens() != null) {
				logger.warn(
						"Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
								+ "As maxToken is deprecated, we will use maxCompletionToken ({}).",
						maxCompletionTokens);

				this.options.setMaxTokens(null);
			}
			this.options.setMaxCompletionTokens(maxCompletionTokens);
			return this;
		}

		public Builder N(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder outputModalities(List<String> outputModalities) {
			this.options.setOutputModalities(outputModalities);
			return this;
		}

		public Builder outputAudio(AudioParameters audio) {
			this.options.setOutputAudio(audio);
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder responseFormat(OpenAiSdkChatModel.ResponseFormat responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder streamOptions(StreamOptions streamOptions) {
			this.options.setStreamOptions(streamOptions);
			return this;
		}

		// helper shortcut methods for StreamOptions with included stream usage
		public Builder streamUsage(boolean streamUsage) {
			this.options.setStreamOptions(
					StreamOptions.builder().from(this.options.getStreamOptions()).includeUsage(streamUsage).build());
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.setSeed(seed);
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

		public Builder toolChoice(Object toolChoice) {
			this.options.setToolChoice(toolChoice);
			return this;
		}

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder parallelToolCalls(Boolean parallelToolCalls) {
			this.options.setParallelToolCalls(parallelToolCalls);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			this.options.setToolCallbacks(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(new HashSet<>(Arrays.asList(toolNames)));
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			this.options.setToolContext(toolContext);
			return this;
		}

		public Builder store(Boolean store) {
			this.options.setStore(store);
			return this;
		}

		public Builder metadata(Map<String, String> metadata) {
			this.options.setMetadata(metadata);
			return this;
		}

		public Builder reasoningEffort(String reasoningEffort) {
			this.options.setReasoningEffort(reasoningEffort);
			return this;
		}

		public Builder verbosity(String verbosity) {
			this.options.setVerbosity(verbosity);
			return this;
		}

		public Builder serviceTier(String serviceTier) {
			this.options.setServiceTier(serviceTier);
			return this;
		}

		public OpenAiSdkChatOptions build() {
			return this.options;
		}

	}

}
