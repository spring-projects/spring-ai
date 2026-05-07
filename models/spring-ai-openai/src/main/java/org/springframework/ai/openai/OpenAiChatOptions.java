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

package org.springframework.ai.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionAudioParam;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Configuration information for the Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Mariusz Bernacki
 * @author lambochen
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 */
public class OpenAiChatOptions extends AbstractOpenAiOptions
		implements ToolCallingChatOptions, StructuredOutputChatOptions {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GPT_5_MINI.asString();

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatOptions.class);

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

	private OpenAiChatModel.@Nullable ResponseFormat responseFormat;

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

	/**
	 * Extra parameters that are not part of the standard OpenAI API. These parameters are
	 * passed as additional body properties to support OpenAI-compatible providers like
	 * vLLM, Ollama, Groq, etc. that support custom parameters such as top_k,
	 * repetition_penalty, etc.
	 */
	private @Nullable Map<String, Object> extraBody;

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private @Nullable Boolean internalToolExecutionEnabled;

	private Map<String, Object> toolContext = new HashMap<>();

	// Temporary constructor to maintain compat with ModelOptionsUtils
	public OpenAiChatOptions() {
	}

	protected OpenAiChatOptions(@Nullable String baseUrl, @Nullable String apiKey, @Nullable Credential credential,
			@Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			boolean isMicrosoftFoundry, boolean isGitHubModels, Duration timeout, int maxRetries, @Nullable Proxy proxy,
			Map<String, String> customHeaders, @Nullable Double frequencyPenalty, @Nullable Integer maxTokens,
			@Nullable Double presencePenalty, @Nullable List<String> stop, @Nullable Double temperature,
			@Nullable Double topP, @Nullable List<ToolCallback> toolCallbacks, @Nullable Set<String> toolNames,
			@Nullable Map<String, Object> toolContext, @Nullable Boolean internalToolExecutionEnabled,
			@Nullable Map<String, Integer> logitBias, @Nullable Boolean logprobs, @Nullable Integer topLogprobs,
			@Nullable Integer maxCompletionTokens, @Nullable Integer n, @Nullable List<String> outputModalities,
			@Nullable AudioParameters outputAudio, OpenAiChatModel.@Nullable ResponseFormat responseFormat,
			@Nullable StreamOptions streamOptions, @Nullable Integer seed, @Nullable Object toolChoice,
			@Nullable String user, @Nullable Boolean parallelToolCalls, @Nullable Boolean store,
			@Nullable Map<String, String> metadata, @Nullable String reasoningEffort, @Nullable String verbosity,
			@Nullable String serviceTier, @Nullable Map<String, Object> extraBody) {
		super(baseUrl, apiKey, credential, model, microsoftDeploymentName, microsoftFoundryServiceVersion,
				organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries, proxy, customHeaders);
		// ChatOptions
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.presencePenalty = presencePenalty;
		this.stop = stop;
		this.temperature = temperature;
		this.topP = topP;
		// ToolCallingChatOptions
		this.toolCallbacks = toolCallbacks != null ? new ArrayList<>(toolCallbacks) : new ArrayList<>();
		this.toolNames = toolNames != null ? new HashSet<>(toolNames) : new HashSet<>();
		this.toolContext = toolContext != null ? new HashMap<>(toolContext) : new HashMap<>();
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
		// OpenAI SDK specific
		this.logitBias = logitBias;
		this.logprobs = logprobs;
		this.topLogprobs = topLogprobs;
		this.maxCompletionTokens = maxCompletionTokens;
		this.n = n;
		this.outputModalities = outputModalities;
		this.outputAudio = outputAudio;
		this.responseFormat = responseFormat;
		this.streamOptions = streamOptions;
		this.seed = seed;
		this.toolChoice = toolChoice;
		this.user = user;
		this.parallelToolCalls = parallelToolCalls;
		this.store = store;
		this.metadata = metadata;
		this.reasoningEffort = reasoningEffort;
		this.verbosity = verbosity;
		this.serviceTier = serviceTier;
		this.extraBody = extraBody;
	}

	/**
	 * Gets the frequency penalty parameter.
	 * @return the frequency penalty
	 */
	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	/**
	 * Gets the logit bias map.
	 * @return the logit bias map
	 */
	public @Nullable Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	/**
	 * Gets whether to return log probabilities.
	 * @return true if log probabilities should be returned
	 */
	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	/**
	 * Gets the number of top log probabilities to return.
	 * @return the number of top log probabilities
	 */
	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	/**
	 * Gets the maximum number of completion tokens.
	 * @return the maximum number of completion tokens
	 */
	public @Nullable Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}

	/**
	 * Gets the number of completions to generate.
	 * @return the number of completions
	 */
	public @Nullable Integer getN() {
		return this.n;
	}

	/**
	 * Gets the output modalities.
	 * @return the output modalities
	 */
	public @Nullable List<String> getOutputModalities() {
		return this.outputModalities;
	}

	/**
	 * Gets the output audio parameters.
	 * @return the output audio parameters
	 */
	public @Nullable AudioParameters getOutputAudio() {
		return this.outputAudio;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	/**
	 * Gets the response format configuration.
	 * @return the response format
	 */
	public OpenAiChatModel.@Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	/**
	 * Gets the stream options.
	 * @return the stream options
	 */
	public @Nullable StreamOptions getStreamOptions() {
		return this.streamOptions;
	}

	/**
	 * Gets the random seed for deterministic generation.
	 * @return the random seed
	 */
	public @Nullable Integer getSeed() {
		return this.seed;
	}

	/**
	 * Gets the stop sequences.
	 * @return the list of stop sequences
	 */
	public @Nullable List<String> getStop() {
		return this.stop;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return getStop();
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	/**
	 * Gets the tool choice configuration.
	 * @return the tool choice option
	 */
	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}

	/**
	 * Gets the user identifier.
	 * @return the user identifier
	 */
	public @Nullable String getUser() {
		return this.user;
	}

	/**
	 * Gets whether to enable parallel tool calls.
	 * @return true if parallel tool calls are enabled
	 */
	public @Nullable Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	/**
	 * Gets whether to store the conversation.
	 * @return true if the conversation should be stored
	 */
	public @Nullable Boolean getStore() {
		return this.store;
	}

	/**
	 * Gets the metadata map.
	 * @return the metadata map
	 */
	public @Nullable Map<String, String> getMetadata() {
		return this.metadata;
	}

	/**
	 * Gets the reasoning effort level.
	 * @return the reasoning effort level
	 */
	public @Nullable String getReasoningEffort() {
		return this.reasoningEffort;
	}

	/**
	 * Gets the verbosity level.
	 * @return the verbosity level
	 */
	public @Nullable String getVerbosity() {
		return this.verbosity;
	}

	/**
	 * Gets the service tier.
	 * @return the service tier
	 */
	public @Nullable String getServiceTier() {
		return this.serviceTier;
	}

	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
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
	public @Nullable String getOutputSchema() {
		OpenAiChatModel.ResponseFormat format = this.getResponseFormat();
		return format != null ? format.getJsonSchema() : null;
	}

	@Override
	public void setOutputSchema(@Nullable String outputSchema) {
		if (outputSchema != null) {
			this.responseFormat = OpenAiChatModel.ResponseFormat.builder()
				.type(Type.JSON_SCHEMA)
				.jsonSchema(outputSchema)
				.build();
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static OpenAiChatOptions fromOptions(OpenAiChatOptions fromOptions) {
		return fromOptions.mutate().build();
	}

	@Override
	public OpenAiChatOptions copy() {
		return mutate().build();
	}

	@Override
	public Builder mutate() {
		return builder()
			// AbstractOpenAiOptions
			.baseUrl(this.getBaseUrl())
			.apiKey(this.getApiKey())
			.credential(this.getCredential())
			.model(this.getModel())
			.deploymentName(this.getDeploymentName())
			.microsoftFoundryServiceVersion(this.getMicrosoftFoundryServiceVersion())
			.organizationId(this.getOrganizationId())
			.microsoftFoundry(this.isMicrosoftFoundry())
			.gitHubModels(this.isGitHubModels())
			.timeout(this.getTimeout())
			.maxRetries(this.getMaxRetries())
			.proxy(this.getProxy())
			.customHeaders(new HashMap<>(this.getCustomHeaders()))
			// ChatOptions
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stop != null ? new ArrayList<>(this.stop) : null)
			.temperature(this.temperature)
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(new ArrayList<>(this.getToolCallbacks()))
			.toolNames(new HashSet<>(this.getToolNames()))
			.toolContext(new HashMap<>(this.getToolContext()))
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// OpenAI SDK specific
			.logitBias(this.logitBias != null ? new HashMap<>(this.logitBias) : null)
			.logprobs(this.logprobs)
			.topLogprobs(this.topLogprobs)
			.maxCompletionTokens(this.maxCompletionTokens)
			.n(this.n)
			.outputModalities(this.outputModalities != null ? new ArrayList<>(this.outputModalities) : null)
			.outputAudio(this.outputAudio)
			.responseFormat(this.responseFormat)
			.streamOptions(this.streamOptions)
			.seed(this.seed)
			.toolChoice(this.toolChoice)
			.user(this.user)
			.parallelToolCalls(this.parallelToolCalls)
			.store(this.store)
			.metadata(this.metadata != null ? new HashMap<>(this.metadata) : null)
			.reasoningEffort(this.reasoningEffort)
			.verbosity(this.verbosity)
			.serviceTier(this.serviceTier)
			.extraBody(this.extraBody != null ? new HashMap<>(this.extraBody) : null);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiChatOptions options = (OpenAiChatOptions) o;
		return Objects.equals(this.getModel(), options.getModel())
				&& Objects.equals(this.frequencyPenalty, options.frequencyPenalty)
				&& Objects.equals(this.logitBias, options.logitBias) && Objects.equals(this.logprobs, options.logprobs)
				&& Objects.equals(this.topLogprobs, options.topLogprobs)
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
				&& Objects.equals(this.extraBody, options.extraBody)
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
				this.reasoningEffort, this.verbosity, this.serviceTier, this.extraBody, this.toolCallbacks,
				this.toolNames, this.internalToolExecutionEnabled, this.toolContext);
	}

	@Override
	public String toString() {
		return "OpenAiChatOptions{" + "model='" + this.getModel() + ", frequencyPenalty=" + this.frequencyPenalty
				+ ", logitBias=" + this.logitBias + ", logprobs=" + this.logprobs + ", topLogprobs=" + this.topLogprobs
				+ ", maxTokens=" + this.maxTokens + ", maxCompletionTokens=" + this.maxCompletionTokens + ", n="
				+ this.n + ", outputModalities=" + this.outputModalities + ", outputAudio=" + this.outputAudio
				+ ", presencePenalty=" + this.presencePenalty + ", responseFormat=" + this.responseFormat
				+ ", streamOptions=" + this.streamOptions + ", streamUsage=" + ", seed=" + this.seed + ", stop="
				+ this.stop + ", temperature=" + this.temperature + ", topP=" + this.topP + ", toolChoice="
				+ this.toolChoice + ", user='" + this.user + '\'' + ", parallelToolCalls=" + this.parallelToolCalls
				+ ", store=" + this.store + ", metadata=" + this.metadata + ", reasoningEffort='" + this.reasoningEffort
				+ '\'' + ", verbosity='" + this.verbosity + '\'' + ", serviceTier='" + this.serviceTier + '\''
				+ ", extraBody=" + this.extraBody + ", toolCallbacks=" + this.toolCallbacks + ", toolNames="
				+ this.toolNames + ", internalToolExecutionEnabled=" + this.internalToolExecutionEnabled
				+ ", toolContext=" + this.toolContext + '}';
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

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends AbstractOpenAiOptions.AbstractBuilder<OpenAiChatOptions, B>
			implements ToolCallingChatOptions.Builder<B>, StructuredOutputChatOptions.Builder<B> {

		@Override
		public B clone() {
			try {
				@SuppressWarnings("unchecked")
				B copy = (B) super.clone();
				if (this.customHeaders != null && !this.customHeaders.isEmpty()) {
					copy.customHeaders = new HashMap<>(this.customHeaders);
				}
				copy.logitBias = this.logitBias == null ? null : new HashMap<>(this.logitBias);
				copy.outputModalities = this.outputModalities == null ? null : new ArrayList<>(this.outputModalities);
				copy.metadata = this.metadata == null ? null : new HashMap<>(this.metadata);
				copy.toolCallbacks = this.toolCallbacks == null ? null : new ArrayList<>(this.toolCallbacks);
				copy.toolNames = this.toolNames == null ? null : new HashSet<>(this.toolNames);
				copy.toolContext = this.toolContext == null ? null : new HashMap<>(this.toolContext);
				return copy;
			}
			catch (CloneNotSupportedException e) {
				throw new RuntimeException(e);
			}
		}

		// ToolCallingChatOptions fields
		protected @Nullable List<ToolCallback> toolCallbacks;

		protected @Nullable Set<String> toolNames;

		protected @Nullable Map<String, Object> toolContext;

		protected @Nullable Boolean internalToolExecutionEnabled;

		protected @Nullable Double frequencyPenalty;

		protected @Nullable Integer maxTokens;

		protected @Nullable Double presencePenalty;

		protected @Nullable List<String> stopSequences;

		protected @Nullable Double temperature;

		protected @Nullable Integer topK;

		protected @Nullable Double topP;

		// OpenAI SDK specific fields
		protected @Nullable Map<String, Integer> logitBias;

		protected @Nullable Boolean logprobs;

		protected @Nullable Integer topLogprobs;

		protected @Nullable Integer maxCompletionTokens;

		protected @Nullable Integer n;

		protected @Nullable List<String> outputModalities;

		protected @Nullable AudioParameters outputAudio;

		protected OpenAiChatModel.@Nullable ResponseFormat responseFormat;

		protected @Nullable StreamOptions streamOptions;

		protected @Nullable Integer seed;

		protected @Nullable Object toolChoice;

		protected @Nullable String user;

		protected @Nullable Boolean parallelToolCalls;

		protected @Nullable Boolean store;

		protected @Nullable Map<String, String> metadata;

		protected @Nullable String reasoningEffort;

		protected @Nullable String verbosity;

		protected @Nullable String serviceTier;

		protected @Nullable Map<String, Object> extraBody;

		@Override
		public B toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks != null ? new ArrayList<>(toolCallbacks) : null;
			return self();
		}

		@Override
		public B toolCallbacks(ToolCallback... toolCallbacks) {
			if (this.toolCallbacks == null) {
				this.toolCallbacks = new ArrayList<>();
			}
			this.toolCallbacks.addAll(java.util.Arrays.asList(toolCallbacks));
			return self();
		}

		@Override
		public B toolNames(@Nullable Set<String> toolNames) {
			this.toolNames = toolNames != null ? new HashSet<>(toolNames) : null;
			return self();
		}

		@Override
		public B toolNames(String... toolNames) {
			if (this.toolNames == null) {
				this.toolNames = new HashSet<>();
			}
			this.toolNames.addAll(Set.of(toolNames));
			return self();
		}

		@Override
		public B toolContext(@Nullable Map<String, Object> context) {
			if (context != null) {
				if (this.toolContext == null) {
					this.toolContext = new HashMap<>();
				}
				this.toolContext.putAll(context);
			}
			else {
				this.toolContext = null;
			}
			return self();
		}

		@Override
		public B toolContext(String key, Object value) {
			if (this.toolContext == null) {
				this.toolContext = new HashMap<>();
			}
			this.toolContext.put(key, value);
			return self();
		}

		@Override
		public B internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.internalToolExecutionEnabled = internalToolExecutionEnabled;
			return self();
		}

		@Override
		public B frequencyPenalty(@Nullable Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return self();
		}

		@Override
		public B maxTokens(@Nullable Integer maxTokens) {
			if (this.maxCompletionTokens != null) {
				logger.warn(
						"Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
								+ "As maxToken is deprecated, we will ignore it and use maxCompletionToken ({}).",
						this.maxCompletionTokens);
			}
			else {
				this.maxTokens = maxTokens;
			}
			return self();
		}

		@Override
		public B presencePenalty(@Nullable Double presencePenalty) {
			this.presencePenalty = presencePenalty;
			return self();
		}

		@Override
		public B stopSequences(@Nullable List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return self();
		}

		@Override
		public B temperature(@Nullable Double temperature) {
			this.temperature = temperature;
			return self();
		}

		@Override
		public B topK(@Nullable Integer topK) {
			this.topK = topK;
			return self();
		}

		@Override
		public B topP(@Nullable Double topP) {
			this.topP = topP;
			return self();
		}

		public B logitBias(@Nullable Map<String, Integer> logitBias) {
			this.logitBias = logitBias;
			return self();
		}

		public B logprobs(@Nullable Boolean logprobs) {
			this.logprobs = logprobs;
			return self();
		}

		public B topLogprobs(@Nullable Integer topLogprobs) {
			this.topLogprobs = topLogprobs;
			return self();
		}

		public B maxCompletionTokens(@Nullable Integer maxCompletionTokens) {
			if (maxCompletionTokens != null && this.maxTokens != null) {
				logger.warn(
						"Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
								+ "As maxToken is deprecated, we will use maxCompletionToken ({}).",
						maxCompletionTokens);
				this.maxTokens(null);
			}
			this.maxCompletionTokens = maxCompletionTokens;
			return self();
		}

		public B n(@Nullable Integer n) {
			this.n = n;
			return self();
		}

		@Deprecated
		public B N(@Nullable Integer n) {
			return n(n);
		}

		public B outputModalities(@Nullable List<String> outputModalities) {
			this.outputModalities = outputModalities;
			return self();
		}

		public B outputAudio(@Nullable AudioParameters audio) {
			this.outputAudio = audio;
			return self();
		}

		public B responseFormat(OpenAiChatModel.@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B streamOptions(@Nullable StreamOptions streamOptions) {
			this.streamOptions = streamOptions;
			return self();
		}

		public B streamUsage(boolean streamUsage) {
			this.streamOptions = StreamOptions.builder().from(this.streamOptions).includeUsage(streamUsage).build();
			return self();
		}

		public B seed(@Nullable Integer seed) {
			this.seed = seed;
			return self();
		}

		public B stop(@Nullable List<String> stop) {
			return this.stopSequences(stop);
		}

		public B toolChoice(@Nullable Object toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B user(@Nullable String user) {
			this.user = user;
			return self();
		}

		public B parallelToolCalls(@Nullable Boolean parallelToolCalls) {
			this.parallelToolCalls = parallelToolCalls;
			return self();
		}

		public B store(@Nullable Boolean store) {
			this.store = store;
			return self();
		}

		public B metadata(@Nullable Map<String, String> metadata) {
			this.metadata = metadata;
			return self();
		}

		public B reasoningEffort(@Nullable String reasoningEffort) {
			this.reasoningEffort = reasoningEffort;
			return self();
		}

		public B verbosity(@Nullable String verbosity) {
			this.verbosity = verbosity;
			return self();
		}

		public B serviceTier(@Nullable String serviceTier) {
			this.serviceTier = serviceTier;
			return self();
		}

		public B extraBody(@Nullable Map<String, Object> extraBody) {
			this.extraBody = extraBody;
			return self();
		}

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			if (outputSchema != null) {
				this.responseFormat = OpenAiChatModel.ResponseFormat.builder()
					.type(Type.JSON_SCHEMA)
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
			if (other instanceof AbstractBuilder<?> that) {
				if (that.baseUrl != null) {
					this.baseUrl = that.baseUrl;
				}
				if (that.apiKey != null) {
					this.apiKey = that.apiKey;
				}
				if (that.credential != null) {
					this.credential = that.credential;
				}
				if (that.microsoftDeploymentName != null) {
					this.microsoftDeploymentName = that.microsoftDeploymentName;
				}
				if (that.microsoftFoundryServiceVersion != null) {
					this.microsoftFoundryServiceVersion = that.microsoftFoundryServiceVersion;
				}
				if (that.organizationId != null) {
					this.organizationId = that.organizationId;
				}
				if (that.proxy != null) {
					this.proxy = that.proxy;
				}
				if (that.logitBias != null) {
					this.logitBias = that.logitBias;
				}
				if (that.logprobs != null) {
					this.logprobs = that.logprobs;
				}
				if (that.topLogprobs != null) {
					this.topLogprobs = that.topLogprobs;
				}
				if (that.maxCompletionTokens != null) {
					this.maxCompletionTokens = that.maxCompletionTokens;
				}
				if (that.n != null) {
					this.n = that.n;
				}
				if (that.outputModalities != null) {
					this.outputModalities = that.outputModalities;
				}
				if (that.outputAudio != null) {
					this.outputAudio = that.outputAudio;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.streamOptions != null) {
					this.streamOptions = that.streamOptions;
				}
				if (that.seed != null) {
					this.seed = that.seed;
				}
				if (that.toolChoice != null) {
					this.toolChoice = that.toolChoice;
				}
				if (that.user != null) {
					this.user = that.user;
				}
				if (that.parallelToolCalls != null) {
					this.parallelToolCalls = that.parallelToolCalls;
				}
				if (that.store != null) {
					this.store = that.store;
				}
				if (that.metadata != null) {
					this.metadata = that.metadata;
				}
				if (that.reasoningEffort != null) {
					this.reasoningEffort = that.reasoningEffort;
				}
				if (that.verbosity != null) {
					this.verbosity = that.verbosity;
				}
				if (that.serviceTier != null) {
					this.serviceTier = that.serviceTier;
				}
				if (that.extraBody != null) {
					if (this.extraBody == null) {
						this.extraBody = new HashMap<>();
					}
					this.extraBody.putAll(that.extraBody);
				}
				if (that.isMicrosoftFoundry != null) {
					this.isMicrosoftFoundry = that.isMicrosoftFoundry;
				}
				if (that.isGitHubModels != null) {
					this.isGitHubModels = that.isGitHubModels;
				}
				if (that.customHeaders != null && !that.customHeaders.isEmpty()) {
					this.customHeaders = that.customHeaders;
				}
				if (that.timeout != null) {
					this.timeout = that.timeout;
				}
				if (that.maxRetries != null) {
					this.maxRetries = that.maxRetries;
				}
				if (that.model != null) {
					this.model = that.model;
				}
				if (that.frequencyPenalty != null) {
					this.frequencyPenalty = that.frequencyPenalty;
				}
				if (that.maxTokens != null) {
					this.maxTokens = that.maxTokens;
				}
				if (that.presencePenalty != null) {
					this.presencePenalty = that.presencePenalty;
				}
				if (that.stopSequences != null) {
					this.stopSequences = that.stopSequences;
				}
				if (that.temperature != null) {
					this.temperature = that.temperature;
				}
				if (that.topK != null) {
					this.topK = that.topK;
				}
				if (that.topP != null) {
					this.topP = that.topP;
				}
				if (that.toolCallbacks != null) {
					this.toolCallbacks = new ArrayList<>(that.toolCallbacks);
				}
				if (that.toolNames != null) {
					this.toolNames = new HashSet<>(that.toolNames);
				}
				if (that.toolContext != null) {
					if (this.toolContext == null) {
						this.toolContext = new HashMap<>();
					}
					this.toolContext.putAll(that.toolContext);
				}
				if (that.internalToolExecutionEnabled != null) {
					this.internalToolExecutionEnabled = that.internalToolExecutionEnabled;
				}
			}
			return self();
		}

		@Override
		public OpenAiChatOptions build() {
			return new OpenAiChatOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					Boolean.TRUE.equals(this.isMicrosoftFoundry), Boolean.TRUE.equals(this.isGitHubModels),
					this.timeout != null ? this.timeout : AbstractOpenAiOptions.DEFAULT_TIMEOUT,
					this.maxRetries != null ? this.maxRetries : AbstractOpenAiOptions.DEFAULT_MAX_RETRIES, this.proxy,
					this.customHeaders, this.frequencyPenalty, this.maxTokens, this.presencePenalty, this.stopSequences,
					this.temperature, this.topP, this.toolCallbacks, this.toolNames, this.toolContext,
					this.internalToolExecutionEnabled, this.logitBias, this.logprobs, this.topLogprobs,
					this.maxCompletionTokens, this.n, this.outputModalities, this.outputAudio, this.responseFormat,
					this.streamOptions, this.seed, this.toolChoice, this.user, this.parallelToolCalls, this.store,
					this.metadata, this.reasoningEffort, this.verbosity, this.serviceTier, this.extraBody);
		}

	}

}
