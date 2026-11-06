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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DefaultDecisionOptions;
import org.springframework.ai.model.ApiKey;

/**
 * Options for the OpenAI Decisions API, used by {@link OpenAiDecisionModel}. The model
 * name is left unset here; {@link OpenAiDecisionModel} applies
 * {@link #DEFAULT_DECISION_MODEL} to its default options when they name none, so that
 * options given at runtime only override what they set.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class OpenAiDecisionOptions extends AbstractOpenAiOptions implements DecisionOptions {

	public static final String DEFAULT_DECISION_MODEL = "gpt-6-luna";

	/**
	 * A stable identifier of the end user, which OpenAI uses to detect abuse. It is sent
	 * as {@code safety_identifier}.
	 */
	private final @Nullable String safetyIdentifier;

	protected OpenAiDecisionOptions(@Nullable String baseUrl, @Nullable String apiKey, @Nullable Credential credential,
			@Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable String safetyIdentifier) {
		super(baseUrl, apiKey, credential, model, microsoftDeploymentName, microsoftFoundryServiceVersion,
				organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries, proxy, customHeaders);
		this.safetyIdentifier = safetyIdentifier;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getSafetyIdentifier() {
		return this.safetyIdentifier;
	}

	@Override
	public Builder mutate() {
		return builder()
			// AbstractOpenAiOptions
			.baseUrl(getBaseUrl())
			.apiKey(getApiKey())
			.credential(getCredential())
			.model(getModel())
			.deploymentName(getDeploymentName())
			.microsoftFoundryServiceVersion(getMicrosoftFoundryServiceVersion())
			.organizationId(getOrganizationId())
			.microsoftFoundry(isMicrosoftFoundry())
			.gitHubModels(isGitHubModels())
			.timeout(getTimeout())
			.maxRetries(getMaxRetries())
			.proxy(getProxy())
			.customHeaders(getCustomHeaders())
			// OpenAiDecisionOptions
			.safetyIdentifier(this.safetyIdentifier);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiDecisionOptions that)) {
			return false;
		}
		return Objects.equals(getModel(), that.getModel()) && Objects.equals(getBaseUrl(), that.getBaseUrl())
				&& Objects.equals(getApiKey(), that.getApiKey())
				&& Objects.equals(getCredential(), that.getCredential())
				&& Objects.equals(getMicrosoftDeploymentName(), that.getMicrosoftDeploymentName())
				&& Objects.equals(getMicrosoftFoundryServiceVersion(), that.getMicrosoftFoundryServiceVersion())
				&& Objects.equals(getOrganizationId(), that.getOrganizationId())
				&& isMicrosoftFoundry() == that.isMicrosoftFoundry() && isGitHubModels() == that.isGitHubModels()
				&& Objects.equals(getTimeout(), that.getTimeout()) && getMaxRetries() == that.getMaxRetries()
				&& Objects.equals(getProxy(), that.getProxy())
				&& Objects.equals(getCustomHeaders(), that.getCustomHeaders())
				&& Objects.equals(this.safetyIdentifier, that.safetyIdentifier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getModel(), getBaseUrl(), getApiKey(), getCredential(), getMicrosoftDeploymentName(),
				getMicrosoftFoundryServiceVersion(), getOrganizationId(), isMicrosoftFoundry(), isGitHubModels(),
				getTimeout(), getMaxRetries(), getProxy(), getCustomHeaders(), this.safetyIdentifier);
	}

	@Override
	public String toString() {
		return "OpenAiDecisionOptions{model='" + getModel() + "', safetyIdentifier='" + this.safetyIdentifier + "'}";
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultDecisionOptions.Builder<B> {

		protected @Nullable String baseUrl;

		protected @Nullable String apiKey;

		protected @Nullable Credential credential;

		protected @Nullable String microsoftDeploymentName;

		protected @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

		protected @Nullable String organizationId;

		protected @Nullable Boolean isMicrosoftFoundry;

		protected @Nullable Boolean isGitHubModels;

		protected @Nullable Duration timeout;

		protected @Nullable Integer maxRetries;

		protected @Nullable Proxy proxy;

		protected @Nullable Map<String, String> customHeaders;

		protected @Nullable String safetyIdentifier;

		@Override
		public B clone() {
			B copy = super.clone();
			copy.customHeaders = this.customHeaders == null ? null : new HashMap<>(this.customHeaders);
			return copy;
		}

		public B baseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
			return self();
		}

		public B apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return self();
		}

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

		public B gitHubModels(@Nullable Boolean gitHubModels) {
			this.isGitHubModels = gitHubModels;
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
			this.customHeaders = customHeaders == null ? null : new HashMap<>(customHeaders);
			return self();
		}

		public B safetyIdentifier(@Nullable String safetyIdentifier) {
			this.safetyIdentifier = safetyIdentifier;
			return self();
		}

		@Override
		public B combineWith(DecisionOptions.Builder<?> other) {
			super.combineWith(other);
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
				if (that.isGitHubModels != null) {
					this.isGitHubModels = that.isGitHubModels;
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
					Map<String, String> merged = this.customHeaders == null ? new HashMap<>()
							: new HashMap<>(this.customHeaders);
					merged.putAll(that.customHeaders);
					this.customHeaders = merged;
				}
				if (that.safetyIdentifier != null) {
					this.safetyIdentifier = that.safetyIdentifier;
				}
			}
			return self();
		}

		@Override
		public OpenAiDecisionOptions build() {
			return new OpenAiDecisionOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.safetyIdentifier);
		}

	}

}
