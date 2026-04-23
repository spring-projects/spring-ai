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

	private @Nullable String model;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model != null ? this.model : DEFAULT_MODERATION_MODEL;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
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
		return Objects.equals(this.model, that.model) && Objects.equals(getBaseUrl(), that.getBaseUrl())
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
		return Objects.hash(this.model, getBaseUrl(), getApiKey(), getCredential(), getMicrosoftDeploymentName(),
				getMicrosoftFoundryServiceVersion(), getOrganizationId(), isMicrosoftFoundry(), isGitHubModels(),
				getTimeout(), getMaxRetries(), getProxy(), getCustomHeaders());
	}

	@Override
	public String toString() {
		return "OpenAiModerationOptions{" + "model='" + this.model + '\'' + ", baseUrl='" + getBaseUrl() + '\''
				+ ", organizationId='" + getOrganizationId() + '\'' + ", microsoftDeploymentName='"
				+ getMicrosoftDeploymentName() + '\'' + ", timeout=" + getTimeout() + ", maxRetries=" + getMaxRetries()
				+ '}';
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable Credential credential;

		private @Nullable String deploymentName;

		private @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

		private @Nullable String organizationId;

		private boolean microsoftFoundry;

		private boolean gitHubModels;

		private @Nullable Duration timeout;

		private @Nullable Integer maxRetries;

		private @Nullable Proxy proxy;

		private @Nullable Map<String, String> customHeaders;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder credential(Credential credential) {
			this.credential = credential;
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.deploymentName = deploymentName;
			return this;
		}

		public Builder organizationId(String organizationId) {
			this.organizationId = organizationId;
			return this;
		}

		public Builder microsoftFoundryServiceVersion(AzureOpenAIServiceVersion serviceVersion) {
			this.microsoftFoundryServiceVersion = serviceVersion;
			return this;
		}

		public Builder microsoftFoundry(boolean isMicrosoftFoundry) {
			this.microsoftFoundry = isMicrosoftFoundry;
			return this;
		}

		public Builder gitHubModels(boolean isGitHubModels) {
			this.gitHubModels = isGitHubModels;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder maxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public Builder proxy(Proxy proxy) {
			this.proxy = proxy;
			return this;
		}

		public Builder customHeaders(Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
			return this;
		}

		public Builder from(OpenAiModerationOptions options) {
			this.model = options.getModel();
			this.baseUrl = options.getBaseUrl();
			this.apiKey = options.getApiKey();
			this.credential = options.getCredential();
			this.deploymentName = options.getMicrosoftDeploymentName();
			this.microsoftFoundryServiceVersion = options.getMicrosoftFoundryServiceVersion();
			this.organizationId = options.getOrganizationId();
			this.microsoftFoundry = options.isMicrosoftFoundry();
			this.gitHubModels = options.isGitHubModels();
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
			if (options instanceof OpenAiModerationOptions castFrom) {
				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getMicrosoftDeploymentName() != null) {
					this.deploymentName = castFrom.getMicrosoftDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.microsoftFoundry = castFrom.isMicrosoftFoundry();
				this.gitHubModels = castFrom.isGitHubModels();
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
			return this;
		}

		public OpenAiModerationOptions build() {
			OpenAiModerationOptions options = new OpenAiModerationOptions();
			options.setModel(this.model);
			options.setBaseUrl(this.baseUrl);
			options.setApiKey(this.apiKey);
			options.setCredential(this.credential);
			options.setDeploymentName(this.deploymentName);
			options.setMicrosoftFoundryServiceVersion(this.microsoftFoundryServiceVersion);
			options.setOrganizationId(this.organizationId);
			options.setMicrosoftFoundry(this.microsoftFoundry);
			options.setGitHubModels(this.gitHubModels);
			if (this.timeout != null) {
				options.setTimeout(this.timeout);
			}
			if (this.maxRetries != null) {
				options.setMaxRetries(this.maxRetries);
			}
			options.setProxy(this.proxy);
			if (this.customHeaders != null) {
				options.setCustomHeaders(this.customHeaders);
			}
			return options;
		}

	}

}
