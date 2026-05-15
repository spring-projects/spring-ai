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
import java.util.Map;
import java.util.Objects;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.moderation.ModerationOptions;

/**
 * OpenAI SDK Moderation Options.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiModerationOptions extends AbstractOpenAiOptions implements ModerationOptions {

	/**
	 * Default moderation model.
	 */
	public static final String DEFAULT_MODERATION_MODEL = "omni-moderation-latest";

	protected OpenAiModerationOptions(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders) {
		super(baseUrl, apiKey, credential, model != null ? model : DEFAULT_MODERATION_MODEL, microsoftDeploymentName,
				microsoftFoundryServiceVersion, organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries,
				proxy, customHeaders);
	}

	public static Builder builder() {
		return new Builder();
	}

	public OpenAiModerationOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiModerationOptions that)) {
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
				&& Objects.equals(getCustomHeaders(), that.getCustomHeaders());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getModel(), getBaseUrl(), getApiKey(), getCredential(), getMicrosoftDeploymentName(),
				getMicrosoftFoundryServiceVersion(), getOrganizationId(), isMicrosoftFoundry(), isGitHubModels(),
				getTimeout(), getMaxRetries(), getProxy(), getCustomHeaders());
	}

	@Override
	public String toString() {
		return "OpenAiModerationOptions{" + "model='" + getModel() + '\'' + ", baseUrl='" + getBaseUrl() + '\''
				+ ", organizationId='" + getOrganizationId() + '\'' + ", microsoftDeploymentName='"
				+ getMicrosoftDeploymentName() + '\'' + ", timeout=" + getTimeout() + ", maxRetries=" + getMaxRetries()
				+ '}';
	}

	public static final class Builder extends AbstractBuilder<OpenAiModerationOptions, Builder> {

		private Builder() {
		}

		public Builder from(OpenAiModerationOptions options) {
			this.model = options.getModel();
			this.baseUrl = options.getBaseUrl();
			this.apiKey = options.getApiKey();
			this.credential = options.getCredential();
			this.microsoftDeploymentName = options.getMicrosoftDeploymentName();
			this.microsoftFoundryServiceVersion = options.getMicrosoftFoundryServiceVersion();
			this.organizationId = options.getOrganizationId();
			this.isMicrosoftFoundry = options.isMicrosoftFoundry();
			this.isGitHubModels = options.isGitHubModels();
			this.timeout = options.getTimeout();
			this.maxRetries = options.getMaxRetries();
			this.proxy = options.getProxy();
			if (options.getCustomHeaders() != null) {
				this.customHeaders = options.getCustomHeaders();
			}
			return this;
		}

		public Builder merge(@Nullable ModerationOptions options) {
			if (options == null) {
				return this;
			}
			if (options.getModel() != null) {
				this.model = options.getModel();
			}
			if (options instanceof AbstractOpenAiOptions castFrom) {

				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getDeploymentName() != null) {
					this.microsoftDeploymentName = castFrom.getDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.isMicrosoftFoundry = castFrom.isMicrosoftFoundry();
				this.isGitHubModels = castFrom.isGitHubModels();
				if (castFrom.getTimeout() != null) {
					this.timeout = castFrom.getTimeout();
				}
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				if (castFrom.getCustomHeaders() != null) {
					this.customHeaders = castFrom.getCustomHeaders();
				}
			}
			if (options instanceof OpenAiModerationOptions castFrom) {
				// No specific properties to merge for now
			}
			return this;
		}

		@Override
		public OpenAiModerationOptions build() {
			return new OpenAiModerationOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders);
		}

	}

}
