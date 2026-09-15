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

package org.springframework.ai.openai.responses;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import com.openai.models.ChatModel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.AbstractOpenAiOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * Configuration for {@link OpenAiResponsesChatModel}.
 * <p>
 * Deliberately absent: {@code previousResponseId}, {@code conversationId},
 * {@code background} and any other knob for a deferred feature. Adding options later is
 * additive; removing them is not. In particular there is no way to ask OpenAI to remember
 * the conversation - this model always sends the whole {@link ChatOptions prompt}.
 * <p>
 * {@code store} is absent for the same reason, even though the API accepts it: the model
 * always sends {@code store: false}, because a stored response keeps its reasoning at
 * OpenAI instead of returning it, and replaying that reasoning is what makes stateless
 * mode correct. See {@code ResponsesRequestBuilder}.
 * <p>
 * Portable {@link ChatOptions} the Responses API has no equivalent for - stop sequences,
 * frequency and presence penalties, {@code topK} - return {@code null} and are warned
 * about once per option.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
public class OpenAiResponsesChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	private static final Log logger = LogFactory.getLog(OpenAiResponsesChatOptions.class);

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GPT_5_MINI.asString();

	/**
	 * Options a caller may set through the portable {@link ChatOptions} surface but which
	 * the Responses API does not support. Warned about once per JVM, not per request.
	 */
	private static final Set<String> WARNED_UNSUPPORTED_OPTIONS = ConcurrentHashMap.newKeySet();

	private final @Nullable String baseUrl;

	private final @Nullable String apiKey;

	private final @Nullable Credential credential;

	private final String model;

	private final @Nullable String microsoftDeploymentName;

	private final @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

	private final @Nullable String organizationId;

	private final boolean isMicrosoftFoundry;

	private final @Nullable Duration timeout;

	private final int maxRetries;

	private final @Nullable Proxy proxy;

	private final @Nullable Map<String, String> customHeaders;

	private final @Nullable Integer maxOutputTokens;

	private final @Nullable Double temperature;

	private final @Nullable Double topP;

	private final @Nullable Integer topLogprobs;

	private final @Nullable String reasoningEffort;

	private final @Nullable String reasoningSummary;

	private final @Nullable String verbosity;

	private final OpenAiChatModel.@Nullable ResponseFormat responseFormat;

	private final @Nullable Boolean strict;

	private final @Nullable Object toolChoice;

	private final @Nullable Boolean parallelToolCalls;

	private final @Nullable Integer maxToolCalls;

	private final @Nullable List<HostedTool> hostedTools;

	private final @Nullable List<String> include;

	private final @Nullable String truncation;

	private final @Nullable String serviceTier;

	private final @Nullable String promptCacheKey;

	private final @Nullable String safetyIdentifier;

	private final @Nullable Map<String, String> metadata;

	private final @Nullable Map<String, Object> extraBody;

	private final @Nullable List<ToolCallback> toolCallbacks;

	private final @Nullable Map<String, Object> toolContext;

	protected OpenAiResponsesChatOptions(AbstractBuilder<?> builder) {
		this.baseUrl = builder.baseUrl;
		this.apiKey = builder.apiKey;
		this.credential = builder.credential;
		this.model = builder.resolveModel() != null ? builder.resolveModel() : DEFAULT_CHAT_MODEL;
		this.microsoftDeploymentName = builder.microsoftDeploymentName;
		this.microsoftFoundryServiceVersion = builder.microsoftFoundryServiceVersion;
		this.organizationId = builder.organizationId;
		this.isMicrosoftFoundry = builder.isMicrosoftFoundry != null ? builder.isMicrosoftFoundry : false;
		this.timeout = builder.timeout;
		this.maxRetries = builder.maxRetries != null ? builder.maxRetries : AbstractOpenAiOptions.DEFAULT_MAX_RETRIES;
		this.proxy = builder.proxy;
		this.customHeaders = builder.customHeaders != null ? Map.copyOf(builder.customHeaders) : null;
		this.maxOutputTokens = builder.resolveMaxTokens();
		this.temperature = builder.resolveTemperature();
		this.topP = builder.resolveTopP();
		this.topLogprobs = builder.topLogprobs;
		this.reasoningEffort = builder.reasoningEffort;
		this.reasoningSummary = builder.reasoningSummary;
		this.verbosity = builder.verbosity;
		this.responseFormat = builder.responseFormat;
		this.strict = builder.strict;
		this.toolChoice = builder.toolChoice;
		this.parallelToolCalls = builder.parallelToolCalls;
		this.maxToolCalls = builder.maxToolCalls;
		this.hostedTools = builder.hostedTools != null ? List.copyOf(builder.hostedTools) : null;
		this.include = builder.include != null ? List.copyOf(builder.include) : null;
		this.truncation = builder.truncation;
		this.serviceTier = builder.serviceTier;
		this.promptCacheKey = builder.promptCacheKey;
		this.safetyIdentifier = builder.safetyIdentifier;
		this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : null;
		this.extraBody = builder.extraBody != null ? Map.copyOf(builder.extraBody) : null;
		this.toolCallbacks = builder.resolveToolCallbacks() != null ? List.copyOf(builder.resolveToolCallbacks())
				: null;
		this.toolContext = builder.resolveToolContext() != null ? Map.copyOf(builder.resolveToolContext()) : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public @Nullable Credential getCredential() {
		return this.credential;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public @Nullable String getMicrosoftDeploymentName() {
		return this.microsoftDeploymentName;
	}

	/**
	 * Alias for {@link #getMicrosoftDeploymentName()}.
	 */
	public @Nullable String getDeploymentName() {
		return this.microsoftDeploymentName;
	}

	public @Nullable AzureOpenAIServiceVersion getMicrosoftFoundryServiceVersion() {
		return this.microsoftFoundryServiceVersion;
	}

	public @Nullable String getOrganizationId() {
		return this.organizationId;
	}

	public boolean isMicrosoftFoundry() {
		return this.isMicrosoftFoundry;
	}

	/**
	 * Return the request timeout, or {@code null} to inherit the timeout configured on
	 * the OpenAI client.
	 */
	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	public @Nullable Proxy getProxy() {
		return this.proxy;
	}

	public @Nullable Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	/**
	 * Maximum number of tokens the model may generate, {@code max_output_tokens} on the
	 * wire. Same value as {@link #getMaxTokens()}.
	 */
	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxOutputTokens;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	/**
	 * How hard the model should think: {@code none}, {@code minimal}, {@code low},
	 * {@code medium}, {@code high}, ... Kept as a {@code String} because the accepted
	 * values move faster than releases.
	 */
	public @Nullable String getReasoningEffort() {
		return this.reasoningEffort;
	}

	/**
	 * Whether and how to summarize the model's reasoning: {@code auto}, {@code concise}
	 * or {@code detailed}. Summaries surface under
	 * {@link OpenAiResponsesMetadata#REASONING_CONTENT}; raw reasoning text is never
	 * returned by OpenAI.
	 */
	public @Nullable String getReasoningSummary() {
		return this.reasoningSummary;
	}

	/**
	 * How verbose the answer should be: {@code low}, {@code medium} or {@code high}.
	 */
	public @Nullable String getVerbosity() {
		return this.verbosity;
	}

	public OpenAiChatModel.@Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	/**
	 * Whether tool schemas are sent as strict.
	 * <p>
	 * When unset, this model sends {@code strict=false} explicitly: the Responses API
	 * otherwise attempts strict mode, and Spring AI's generated schemas are frequently
	 * not strict-compatible, which would make the same tool behave differently across the
	 * two OpenAI models.
	 */
	public @Nullable Boolean getStrict() {
		return this.strict;
	}

	/**
	 * {@code auto}, {@code none}, {@code required}, a function name as
	 * {@code {"type":"function","name":"..."}}, or a hosted tool type as
	 * {@code {"type":"web_search"}}.
	 */
	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}

	public @Nullable Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	public @Nullable Integer getMaxToolCalls() {
		return this.maxToolCalls;
	}

	/**
	 * Tools OpenAI runs server-side within the request.
	 */
	public @Nullable List<HostedTool> getHostedTools() {
		return this.hostedTools;
	}

	/**
	 * Extra fields to include in the response, e.g. {@code web_search_call.results}.
	 * {@code reasoning.encrypted_content} is always added by the model, because stateless
	 * reasoning is impossible without it.
	 */
	public @Nullable List<String> getInclude() {
		return this.include;
	}

	/**
	 * {@code auto} to let OpenAI drop middle-of-conversation items when the context
	 * window overflows, or {@code disabled} to fail instead.
	 */
	public @Nullable String getTruncation() {
		return this.truncation;
	}

	public @Nullable String getServiceTier() {
		return this.serviceTier;
	}

	public @Nullable String getPromptCacheKey() {
		return this.promptCacheKey;
	}

	public @Nullable String getSafetyIdentifier() {
		return this.safetyIdentifier;
	}

	public @Nullable Map<String, String> getMetadata() {
		return this.metadata;
	}

	/**
	 * Extra request body properties, for OpenAI-compatible providers and for API fields
	 * that are not modelled here.
	 */
	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
	}

	@Override
	public @Nullable List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public @Nullable Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public @Nullable String getOutputSchema() {
		OpenAiChatModel.ResponseFormat format = this.responseFormat;
		return format != null ? format.getJsonSchema() : null;
	}

	@Override
	public @Nullable Integer getTopK() {
		return null;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return null;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return null;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return null;
	}

	/**
	 * Log once per JVM that a portable option the Responses API has no equivalent for was
	 * set and is being ignored.
	 * @param option the portable option name
	 */
	static void warnUnsupported(String option) {
		if (logger.isWarnEnabled() && WARNED_UNSUPPORTED_OPTIONS.add(option)) {
			logger.warn("The " + option + " option is not supported by the OpenAI Responses API. Ignoring."
					+ " Use OpenAiChatModel with OpenAiChatOptions if you need it.");
		}
	}

	@Override
	public Builder mutate() {
		return builder()
			// connection
			.baseUrl(this.baseUrl)
			.apiKey(this.apiKey)
			.credential(this.credential)
			.model(this.model)
			.deploymentName(this.microsoftDeploymentName)
			.microsoftFoundryServiceVersion(this.microsoftFoundryServiceVersion)
			.organizationId(this.organizationId)
			.microsoftFoundry(this.isMicrosoftFoundry)
			.timeout(this.timeout)
			.maxRetries(this.maxRetries)
			.proxy(this.proxy)
			.customHeaders(this.customHeaders)
			// generation
			.maxTokens(this.maxOutputTokens)
			.temperature(this.temperature)
			.topP(this.topP)
			.topLogprobs(this.topLogprobs)
			.reasoningEffort(this.reasoningEffort)
			.reasoningSummary(this.reasoningSummary)
			.verbosity(this.verbosity)
			.responseFormat(this.responseFormat)
			// tools
			.strict(this.strict)
			.toolChoice(this.toolChoice)
			.parallelToolCalls(this.parallelToolCalls)
			.maxToolCalls(this.maxToolCalls)
			.hostedTools(this.hostedTools)
			.toolCallbacks(this.toolCallbacks)
			.toolContext(this.toolContext)
			// platform
			.include(this.include)
			.truncation(this.truncation)
			.serviceTier(this.serviceTier)
			.promptCacheKey(this.promptCacheKey)
			.safetyIdentifier(this.safetyIdentifier)
			.metadata(this.metadata)
			.extraBody(this.extraBody);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiResponsesChatOptions that = (OpenAiResponsesChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.maxOutputTokens, that.maxOutputTokens)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topLogprobs, that.topLogprobs)
				&& Objects.equals(this.reasoningEffort, that.reasoningEffort)
				&& Objects.equals(this.reasoningSummary, that.reasoningSummary)
				&& Objects.equals(this.verbosity, that.verbosity)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.strict, that.strict)
				&& Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.parallelToolCalls, that.parallelToolCalls)
				&& Objects.equals(this.maxToolCalls, that.maxToolCalls)
				&& Objects.equals(this.hostedTools, that.hostedTools) && Objects.equals(this.include, that.include)
				&& Objects.equals(this.truncation, that.truncation)
				&& Objects.equals(this.serviceTier, that.serviceTier)
				&& Objects.equals(this.promptCacheKey, that.promptCacheKey)
				&& Objects.equals(this.safetyIdentifier, that.safetyIdentifier)
				&& Objects.equals(this.metadata, that.metadata) && Objects.equals(this.extraBody, that.extraBody)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxOutputTokens, this.temperature, this.topP, this.topLogprobs,
				this.reasoningEffort, this.reasoningSummary, this.verbosity, this.responseFormat, this.strict,
				this.toolChoice, this.parallelToolCalls, this.maxToolCalls, this.hostedTools, this.include,
				this.truncation, this.serviceTier, this.promptCacheKey, this.safetyIdentifier, this.metadata,
				this.extraBody, this.toolCallbacks, this.toolContext);
	}

	/**
	 * Public builder exposed to users, so they do not have to name the generic parameter.
	 */
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> implements StructuredOutputChatOptions.Builder<B> {

		protected @Nullable String baseUrl;

		protected @Nullable String apiKey;

		protected @Nullable Credential credential;

		protected @Nullable String microsoftDeploymentName;

		protected @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

		protected @Nullable String organizationId;

		protected @Nullable Boolean isMicrosoftFoundry;

		protected @Nullable Duration timeout;

		protected @Nullable Integer maxRetries;

		protected @Nullable Proxy proxy;

		protected @Nullable Map<String, String> customHeaders;

		protected @Nullable Integer topLogprobs;

		protected @Nullable String reasoningEffort;

		protected @Nullable String reasoningSummary;

		protected @Nullable String verbosity;

		protected OpenAiChatModel.@Nullable ResponseFormat responseFormat;

		protected @Nullable Boolean strict;

		protected @Nullable Object toolChoice;

		protected @Nullable Boolean parallelToolCalls;

		protected @Nullable Integer maxToolCalls;

		protected @Nullable List<HostedTool> hostedTools;

		protected @Nullable List<String> include;

		protected @Nullable String truncation;

		protected @Nullable String serviceTier;

		protected @Nullable String promptCacheKey;

		protected @Nullable String safetyIdentifier;

		protected @Nullable Map<String, String> metadata;

		protected @Nullable Map<String, Object> extraBody;

		// The fields below are inherited from DefaultChatOptionsBuilder and
		// DefaultToolCallingChatOptions.Builder. Protected members of a superclass in
		// another package are only reachable from inside this subclass, so the enclosing
		// options class reads them through these accessors.

		@Nullable String resolveModel() {
			return this.model;
		}

		@Nullable Integer resolveMaxTokens() {
			return this.maxTokens;
		}

		@Nullable Double resolveTemperature() {
			return this.temperature;
		}

		@Nullable Double resolveTopP() {
			return this.topP;
		}

		@Nullable List<ToolCallback> resolveToolCallbacks() {
			return this.toolCallbacks;
		}

		@Nullable Map<String, Object> resolveToolContext() {
			return this.toolContext;
		}

		@Override
		public B clone() {
			B copy = super.clone();
			AbstractBuilder<?> builder = (AbstractBuilder<?>) copy;
			builder.customHeaders = this.customHeaders == null ? null : new HashMap<>(this.customHeaders);
			builder.hostedTools = this.hostedTools == null ? null : new ArrayList<>(this.hostedTools);
			builder.include = this.include == null ? null : new ArrayList<>(this.include);
			builder.metadata = this.metadata == null ? null : new HashMap<>(this.metadata);
			builder.extraBody = this.extraBody == null ? null : new HashMap<>(this.extraBody);
			return copy;
		}

		@Override
		public B stopSequences(@Nullable List<String> stopSequences) {
			warnUnsupported("stopSequences");
			return self();
		}

		@Override
		public B frequencyPenalty(@Nullable Double frequencyPenalty) {
			warnUnsupported("frequencyPenalty");
			return self();
		}

		@Override
		public B presencePenalty(@Nullable Double presencePenalty) {
			warnUnsupported("presencePenalty");
			return self();
		}

		@Override
		public B topK(@Nullable Integer topK) {
			warnUnsupported("topK");
			return self();
		}

		public B baseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
			return self();
		}

		public B apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return self();
		}

		/**
		 * Sets the API key using an {@link ApiKey} instance. Pass a {@link NoopApiKey} to
		 * disable authentication.
		 */
		public B apiKey(@Nullable ApiKey apiKey) {
			this.apiKey = (apiKey != null) ? apiKey.getValue() : null;
			return self();
		}

		public B credential(@Nullable Credential credential) {
			this.credential = credential;
			return self();
		}

		public B deploymentName(@Nullable String deploymentName) {
			this.microsoftDeploymentName = deploymentName;
			return self();
		}

		public B microsoftFoundryServiceVersion(@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
			this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
			return self();
		}

		public B organizationId(@Nullable String organizationId) {
			this.organizationId = organizationId;
			return self();
		}

		public B microsoftFoundry(@Nullable Boolean microsoftFoundry) {
			this.isMicrosoftFoundry = microsoftFoundry;
			return self();
		}

		public B timeout(@Nullable Duration timeout) {
			this.timeout = timeout;
			return self();
		}

		public B maxRetries(@Nullable Integer maxRetries) {
			this.maxRetries = maxRetries;
			return self();
		}

		public B proxy(@Nullable Proxy proxy) {
			this.proxy = proxy;
			return self();
		}

		public B customHeaders(@Nullable Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
			return self();
		}

		/**
		 * Alias for {@link #maxTokens(Integer)}, named after the wire field.
		 */
		public B maxOutputTokens(@Nullable Integer maxOutputTokens) {
			return this.maxTokens(maxOutputTokens);
		}

		public B topLogprobs(@Nullable Integer topLogprobs) {
			this.topLogprobs = topLogprobs;
			return self();
		}

		public B reasoningEffort(@Nullable String reasoningEffort) {
			this.reasoningEffort = reasoningEffort;
			return self();
		}

		public B reasoningSummary(@Nullable String reasoningSummary) {
			this.reasoningSummary = reasoningSummary;
			return self();
		}

		public B verbosity(@Nullable String verbosity) {
			this.verbosity = verbosity;
			return self();
		}

		public B responseFormat(OpenAiChatModel.@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return self();
		}

		public B strict(@Nullable Boolean strict) {
			this.strict = strict;
			return self();
		}

		public B toolChoice(@Nullable Object toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B parallelToolCalls(@Nullable Boolean parallelToolCalls) {
			this.parallelToolCalls = parallelToolCalls;
			return self();
		}

		public B maxToolCalls(@Nullable Integer maxToolCalls) {
			this.maxToolCalls = maxToolCalls;
			return self();
		}

		public B hostedTools(@Nullable List<HostedTool> hostedTools) {
			this.hostedTools = hostedTools;
			return self();
		}

		public B hostedTools(HostedTool... hostedTools) {
			this.hostedTools = List.of(hostedTools);
			return self();
		}

		public B include(@Nullable List<String> include) {
			this.include = include;
			return self();
		}

		public B truncation(@Nullable String truncation) {
			this.truncation = truncation;
			return self();
		}

		public B serviceTier(@Nullable String serviceTier) {
			this.serviceTier = serviceTier;
			return self();
		}

		public B promptCacheKey(@Nullable String promptCacheKey) {
			this.promptCacheKey = promptCacheKey;
			return self();
		}

		public B safetyIdentifier(@Nullable String safetyIdentifier) {
			this.safetyIdentifier = safetyIdentifier;
			return self();
		}

		public B metadata(@Nullable Map<String, String> metadata) {
			this.metadata = metadata;
			return self();
		}

		public B extraBody(@Nullable Map<String, Object> extraBody) {
			this.extraBody = extraBody;
			return self();
		}

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			this.responseFormat = outputSchema != null ? OpenAiChatModel.ResponseFormat.builder()
				.type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(outputSchema)
				.build() : null;
			return self();
		}

		/**
		 * Report and drop portable options the Responses API has no equivalent for.
		 * <p>
		 * The setters above already reject them, but {@link #combineWith} copies the
		 * portable fields straight into the inherited state - that is how
		 * {@code ChatClient} applies its defaults - so they are swept here as well.
		 */
		private void dropUnsupportedPortableOptions() {
			if (this.stopSequences != null) {
				warnUnsupported("stopSequences");
				this.stopSequences = null;
			}
			if (this.frequencyPenalty != null) {
				warnUnsupported("frequencyPenalty");
				this.frequencyPenalty = null;
			}
			if (this.presencePenalty != null) {
				warnUnsupported("presencePenalty");
				this.presencePenalty = null;
			}
			if (this.topK != null) {
				warnUnsupported("topK");
				this.topK = null;
			}
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			dropUnsupportedPortableOptions();
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
				if (that.isMicrosoftFoundry != null) {
					this.isMicrosoftFoundry = that.isMicrosoftFoundry;
				}
				if (that.timeout != null) {
					this.timeout = that.timeout;
				}
				if (that.maxRetries != null) {
					this.maxRetries = that.maxRetries;
				}
				if (that.proxy != null) {
					this.proxy = that.proxy;
				}
				if (that.customHeaders != null) {
					this.customHeaders = merge(this.customHeaders, that.customHeaders);
				}
				if (that.topLogprobs != null) {
					this.topLogprobs = that.topLogprobs;
				}
				if (that.reasoningEffort != null) {
					this.reasoningEffort = that.reasoningEffort;
				}
				if (that.reasoningSummary != null) {
					this.reasoningSummary = that.reasoningSummary;
				}
				if (that.verbosity != null) {
					this.verbosity = that.verbosity;
				}
				if (that.responseFormat != null) {
					this.responseFormat = that.responseFormat;
				}
				if (that.strict != null) {
					this.strict = that.strict;
				}
				if (that.toolChoice != null) {
					this.toolChoice = that.toolChoice;
				}
				if (that.parallelToolCalls != null) {
					this.parallelToolCalls = that.parallelToolCalls;
				}
				if (that.maxToolCalls != null) {
					this.maxToolCalls = that.maxToolCalls;
				}
				if (that.hostedTools != null) {
					this.hostedTools = that.hostedTools;
				}
				if (that.include != null) {
					// Union: the model always adds reasoning.encrypted_content, and a
					// runtime request asking for one more field should not drop the
					// fields configured on the bean.
					Set<String> union = new LinkedHashSet<>(this.include != null ? this.include : List.of());
					union.addAll(that.include);
					this.include = new ArrayList<>(union);
				}
				if (that.truncation != null) {
					this.truncation = that.truncation;
				}
				if (that.serviceTier != null) {
					this.serviceTier = that.serviceTier;
				}
				if (that.promptCacheKey != null) {
					this.promptCacheKey = that.promptCacheKey;
				}
				if (that.safetyIdentifier != null) {
					this.safetyIdentifier = that.safetyIdentifier;
				}
				if (that.metadata != null) {
					this.metadata = merge(this.metadata, that.metadata);
				}
				if (that.extraBody != null) {
					this.extraBody = merge(this.extraBody, that.extraBody);
				}
			}
			return self();
		}

		private static <V> Map<String, V> merge(@Nullable Map<String, V> current, Map<String, V> incoming) {
			if (current == null) {
				return new HashMap<>(incoming);
			}
			Map<String, V> merged = new HashMap<>(current);
			merged.putAll(incoming);
			return merged;
		}

		@Override
		public OpenAiResponsesChatOptions build() {
			dropUnsupportedPortableOptions();
			return new OpenAiResponsesChatOptions(this);
		}

	}

}
