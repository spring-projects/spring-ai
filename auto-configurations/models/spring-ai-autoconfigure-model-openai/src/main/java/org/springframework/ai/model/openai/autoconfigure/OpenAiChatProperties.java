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

package org.springframework.ai.model.openai.autoconfigure;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters;
import org.springframework.ai.openai.OpenAiChatOptions.StreamOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * OpenAI SDK Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(OpenAiChatProperties.CONFIG_PREFIX)
public class OpenAiChatProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.chat";

	public static final String DEFAULT_CHAT_MODEL = OpenAiChatOptions.DEFAULT_CHAT_MODEL;

	private @Nullable String model = DEFAULT_CHAT_MODEL;

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

	private @Nullable ResponseFormat responseFormat;

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

	private @Nullable Map<String, Object> extraBody;

	private @Nullable Boolean internalToolExecutionEnabled;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	public void setLogitBias(@Nullable Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
	}

	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(@Nullable Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(@Nullable Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}

	public void setMaxCompletionTokens(@Nullable Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public @Nullable List<String> getOutputModalities() {
		return this.outputModalities;
	}

	public void setOutputModalities(@Nullable List<String> outputModalities) {
		this.outputModalities = outputModalities;
	}

	public @Nullable AudioParameters getOutputAudio() {
		return this.outputAudio;
	}

	public void setOutputAudio(@Nullable AudioParameters outputAudio) {
		this.outputAudio = outputAudio;
	}

	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public @Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable StreamOptions getStreamOptions() {
		return this.streamOptions;
	}

	public void setStreamOptions(@Nullable StreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public @Nullable List<String> getStop() {
		return this.stop;
	}

	public void setStop(@Nullable List<String> stop) {
		this.stop = stop;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(@Nullable Object toolChoice) {
		this.toolChoice = toolChoice;
	}

	public @Nullable String getUser() {
		return this.user;
	}

	public void setUser(@Nullable String user) {
		this.user = user;
	}

	public @Nullable Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	public @Nullable Boolean getStore() {
		return this.store;
	}

	public void setStore(@Nullable Boolean store) {
		this.store = store;
	}

	public @Nullable Map<String, String> getMetadata() {
		return this.metadata;
	}

	public void setMetadata(@Nullable Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public @Nullable String getReasoningEffort() {
		return this.reasoningEffort;
	}

	public void setReasoningEffort(@Nullable String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	public @Nullable String getVerbosity() {
		return this.verbosity;
	}

	public void setVerbosity(@Nullable String verbosity) {
		this.verbosity = verbosity;
	}

	public @Nullable String getServiceTier() {
		return this.serviceTier;
	}

	public void setServiceTier(@Nullable String serviceTier) {
		this.serviceTier = serviceTier;
	}

	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
	}

	public void setExtraBody(@Nullable Map<String, Object> extraBody) {
		this.extraBody = extraBody;
	}

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public OpenAiChatOptions toOptions() {
		OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
		builder.model(this.getModel());
		if (this.frequencyPenalty != null) {
			builder.frequencyPenalty(this.frequencyPenalty);
		}
		if (this.logitBias != null) {
			builder.logitBias(this.logitBias);
		}
		if (this.logprobs != null) {
			builder.logprobs(this.logprobs);
		}
		if (this.topLogprobs != null) {
			builder.topLogprobs(this.topLogprobs);
		}
		if (this.maxTokens != null) {
			builder.maxTokens(this.maxTokens);
		}
		if (this.maxCompletionTokens != null) {
			builder.maxCompletionTokens(this.maxCompletionTokens);
		}
		if (this.n != null) {
			builder.n(this.n);
		}
		if (this.outputModalities != null) {
			builder.outputModalities(this.outputModalities);
		}
		if (this.outputAudio != null) {
			builder.outputAudio(this.outputAudio);
		}
		if (this.presencePenalty != null) {
			builder.presencePenalty(this.presencePenalty);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.streamOptions != null) {
			builder.streamOptions(this.streamOptions);
		}
		if (this.seed != null) {
			builder.seed(this.seed);
		}
		if (this.stop != null) {
			builder.stopSequences(this.stop);
		}
		if (this.temperature != null) {
			builder.temperature(this.temperature);
		}
		if (this.topP != null) {
			builder.topP(this.topP);
		}
		if (this.toolChoice != null) {
			builder.toolChoice(this.toolChoice);
		}
		if (this.user != null) {
			builder.user(this.user);
		}
		if (this.parallelToolCalls != null) {
			builder.parallelToolCalls(this.parallelToolCalls);
		}
		if (this.store != null) {
			builder.store(this.store);
		}
		if (this.metadata != null) {
			builder.metadata(this.metadata);
		}
		if (this.reasoningEffort != null) {
			builder.reasoningEffort(this.reasoningEffort);
		}
		if (this.verbosity != null) {
			builder.verbosity(this.verbosity);
		}
		if (this.serviceTier != null) {
			builder.serviceTier(this.serviceTier);
		}
		if (this.extraBody != null) {
			builder.extraBody(this.extraBody);
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable Credential credential;

		private @Nullable String microsoftDeploymentName;

		private @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

		private @Nullable String organizationId;

		private @Nullable Boolean microsoftFoundry;

		private @Nullable Boolean gitHubModels;

		private @Nullable Duration timeout;

		private @Nullable Integer maxRetries;

		private @Nullable Proxy proxy;

		private @Nullable Map<String, String> customHeaders;

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.base-url")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getBaseUrl() {
			return this.baseUrl;
		}

		public void setBaseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.api-key")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getApiKey() {
			return this.apiKey;
		}

		public void setApiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.credential")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Credential getCredential() {
			return this.credential;
		}

		public void setCredential(@Nullable Credential credential) {
			this.credential = credential;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.microsoft-deployment-name")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getMicrosoftDeploymentName() {
			return this.microsoftDeploymentName;
		}

		public void setMicrosoftDeploymentName(@Nullable String microsoftDeploymentName) {
			this.microsoftDeploymentName = microsoftDeploymentName;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.deployment-name")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getDeploymentName() {
			return this.microsoftDeploymentName;
		}

		public void setDeploymentName(@Nullable String azureDeploymentName) {
			this.microsoftDeploymentName = azureDeploymentName;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.microsoft-foundry-service-version")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable AzureOpenAIServiceVersion getMicrosoftFoundryServiceVersion() {
			return this.microsoftFoundryServiceVersion;
		}

		public void setMicrosoftFoundryServiceVersion(
				@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
			this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.organization-id")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getOrganizationId() {
			return this.organizationId;
		}

		public void setOrganizationId(@Nullable String organizationId) {
			this.organizationId = organizationId;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.microsoft-foundry")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getMicrosoftFoundry() {
			return this.microsoftFoundry;
		}

		public void setMicrosoftFoundry(@Nullable Boolean microsoftFoundry) {
			this.microsoftFoundry = microsoftFoundry;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.git-hub-models")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getGitHubModels() {
			return this.gitHubModels;
		}

		public void setGitHubModels(@Nullable Boolean gitHubModels) {
			this.gitHubModels = gitHubModels;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.timeout")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Duration getTimeout() {
			return this.timeout;
		}

		public void setTimeout(@Nullable Duration timeout) {
			this.timeout = timeout;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.max-retries")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxRetries() {
			return this.maxRetries;
		}

		public void setMaxRetries(@Nullable Integer maxRetries) {
			this.maxRetries = maxRetries;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.proxy")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Proxy getProxy() {
			return this.proxy;
		}

		public void setProxy(@Nullable Proxy proxy) {
			this.proxy = proxy;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.custom-headers")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, String> getCustomHeaders() {
			return this.customHeaders;
		}

		public void setCustomHeaders(@Nullable Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OpenAiChatProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OpenAiChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return OpenAiChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			OpenAiChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.logit-bias")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, Integer> getLogitBias() {
			return OpenAiChatProperties.this.getLogitBias();
		}

		public void setLogitBias(@Nullable Map<String, Integer> logitBias) {
			OpenAiChatProperties.this.setLogitBias(logitBias);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.logprobs")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getLogprobs() {
			return OpenAiChatProperties.this.getLogprobs();
		}

		public void setLogprobs(@Nullable Boolean logprobs) {
			OpenAiChatProperties.this.setLogprobs(logprobs);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.top-logprobs")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopLogprobs() {
			return OpenAiChatProperties.this.getTopLogprobs();
		}

		public void setTopLogprobs(@Nullable Integer topLogprobs) {
			OpenAiChatProperties.this.setTopLogprobs(topLogprobs);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.max-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxTokens() {
			return OpenAiChatProperties.this.getMaxTokens();
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			OpenAiChatProperties.this.setMaxTokens(maxTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.max-completion-tokens")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMaxCompletionTokens() {
			return OpenAiChatProperties.this.getMaxCompletionTokens();
		}

		public void setMaxCompletionTokens(@Nullable Integer maxCompletionTokens) {
			OpenAiChatProperties.this.setMaxCompletionTokens(maxCompletionTokens);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getN() {
			return OpenAiChatProperties.this.getN();
		}

		public void setN(@Nullable Integer n) {
			OpenAiChatProperties.this.setN(n);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.output-modalities")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getOutputModalities() {
			return OpenAiChatProperties.this.getOutputModalities();
		}

		public void setOutputModalities(@Nullable List<String> outputModalities) {
			OpenAiChatProperties.this.setOutputModalities(outputModalities);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.output-audio")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable AudioParameters getOutputAudio() {
			return OpenAiChatProperties.this.getOutputAudio();
		}

		public void setOutputAudio(@Nullable AudioParameters outputAudio) {
			OpenAiChatProperties.this.setOutputAudio(outputAudio);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return OpenAiChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			OpenAiChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ResponseFormat getResponseFormat() {
			return OpenAiChatProperties.this.getResponseFormat();
		}

		public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
			OpenAiChatProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.stream-options")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable StreamOptions getStreamOptions() {
			return OpenAiChatProperties.this.getStreamOptions();
		}

		public void setStreamOptions(@Nullable StreamOptions streamOptions) {
			OpenAiChatProperties.this.setStreamOptions(streamOptions);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getSeed() {
			return OpenAiChatProperties.this.getSeed();
		}

		public void setSeed(@Nullable Integer seed) {
			OpenAiChatProperties.this.setSeed(seed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.stop")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStop() {
			return OpenAiChatProperties.this.getStop();
		}

		public void setStop(@Nullable List<String> stop) {
			OpenAiChatProperties.this.setStop(stop);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return OpenAiChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			OpenAiChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return OpenAiChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			OpenAiChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.tool-choice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Object getToolChoice() {
			return OpenAiChatProperties.this.getToolChoice();
		}

		public void setToolChoice(@Nullable Object toolChoice) {
			OpenAiChatProperties.this.setToolChoice(toolChoice);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.user")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getUser() {
			return OpenAiChatProperties.this.getUser();
		}

		public void setUser(@Nullable String user) {
			OpenAiChatProperties.this.setUser(user);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.parallel-tool-calls")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getParallelToolCalls() {
			return OpenAiChatProperties.this.getParallelToolCalls();
		}

		public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
			OpenAiChatProperties.this.setParallelToolCalls(parallelToolCalls);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.store")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getStore() {
			return OpenAiChatProperties.this.getStore();
		}

		public void setStore(@Nullable Boolean store) {
			OpenAiChatProperties.this.setStore(store);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.metadata")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, String> getMetadata() {
			return OpenAiChatProperties.this.getMetadata();
		}

		public void setMetadata(@Nullable Map<String, String> metadata) {
			OpenAiChatProperties.this.setMetadata(metadata);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.reasoning-effort")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getReasoningEffort() {
			return OpenAiChatProperties.this.getReasoningEffort();
		}

		public void setReasoningEffort(@Nullable String reasoningEffort) {
			OpenAiChatProperties.this.setReasoningEffort(reasoningEffort);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.verbosity")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getVerbosity() {
			return OpenAiChatProperties.this.getVerbosity();
		}

		public void setVerbosity(@Nullable String verbosity) {
			OpenAiChatProperties.this.setVerbosity(verbosity);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.service-tier")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getServiceTier() {
			return OpenAiChatProperties.this.getServiceTier();
		}

		public void setServiceTier(@Nullable String serviceTier) {
			OpenAiChatProperties.this.setServiceTier(serviceTier);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.extra-body")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, Object> getExtraBody() {
			return OpenAiChatProperties.this.getExtraBody();
		}

		public void setExtraBody(@Nullable Map<String, Object> extraBody) {
			OpenAiChatProperties.this.setExtraBody(extraBody);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return OpenAiChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			OpenAiChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

	}

}
