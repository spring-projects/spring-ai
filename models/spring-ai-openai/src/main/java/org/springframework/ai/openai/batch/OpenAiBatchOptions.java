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

package org.springframework.ai.openai.batch;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.AbstractOpenAiOptions;

/**
 * Options for OpenAI Batch API operations.
 * <p>
 * Extends {@link AbstractOpenAiOptions} for connection configuration and adds
 * batch-specific settings such as completion window, rate limits, retry policies, and
 * token budget configuration.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public class OpenAiBatchOptions extends AbstractOpenAiOptions {

	/**
	 * Default completion window for batch jobs.
	 */
	public static final String DEFAULT_COMPLETION_WINDOW = "24h";

	/**
	 * Default maximum number of requests per batch.
	 */
	public static final int DEFAULT_MAX_REQUESTS_PER_BATCH = 50_000;

	/**
	 * Default maximum file size in bytes (200 MB).
	 */
	public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 200L * 1024 * 1024;

	/**
	 * Default safety factor applied to token estimates.
	 */
	public static final double DEFAULT_TOKEN_SAFETY_FACTOR = 1.2;

	/**
	 * Default minimum tokens required before submitting a batch.
	 */
	public static final long DEFAULT_MINIMUM_TOKENS_TO_SUBMIT = 5_000_000L;

	/**
	 * Default maximum retry attempts for failed requests.
	 */
	public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 2;

	/**
	 * Default whether to clean up files after processing.
	 */
	public static final boolean DEFAULT_DELETE_FILES_AFTER_PROCESSING = true;

	/**
	 * Default handler version stored in batch metadata.
	 */
	public static final int DEFAULT_HANDLER_VERSION = 1;

	private @Nullable String completionWindow;

	private int maxRequestsPerBatch = DEFAULT_MAX_REQUESTS_PER_BATCH;

	private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;

	private double tokenSafetyFactor = DEFAULT_TOKEN_SAFETY_FACTOR;

	private long minimumTokensToSubmit = DEFAULT_MINIMUM_TOKENS_TO_SUBMIT;

	private int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;

	private boolean deleteFilesAfterProcessing = DEFAULT_DELETE_FILES_AFTER_PROCESSING;

	private int handlerVersion = DEFAULT_HANDLER_VERSION;

	public static Builder builder() {
		return new Builder();
	}

	public String getCompletionWindow() {
		return this.completionWindow != null ? this.completionWindow : DEFAULT_COMPLETION_WINDOW;
	}

	public void setCompletionWindow(@Nullable String completionWindow) {
		this.completionWindow = completionWindow;
	}

	public int getMaxRequestsPerBatch() {
		return this.maxRequestsPerBatch;
	}

	public void setMaxRequestsPerBatch(int maxRequestsPerBatch) {
		this.maxRequestsPerBatch = maxRequestsPerBatch;
	}

	public long getMaxFileSizeBytes() {
		return this.maxFileSizeBytes;
	}

	public void setMaxFileSizeBytes(long maxFileSizeBytes) {
		this.maxFileSizeBytes = maxFileSizeBytes;
	}

	public double getTokenSafetyFactor() {
		return this.tokenSafetyFactor;
	}

	public void setTokenSafetyFactor(double tokenSafetyFactor) {
		this.tokenSafetyFactor = tokenSafetyFactor;
	}

	public long getMinimumTokensToSubmit() {
		return this.minimumTokensToSubmit;
	}

	public void setMinimumTokensToSubmit(long minimumTokensToSubmit) {
		this.minimumTokensToSubmit = minimumTokensToSubmit;
	}

	public int getMaxRetryAttempts() {
		return this.maxRetryAttempts;
	}

	public void setMaxRetryAttempts(int maxRetryAttempts) {
		this.maxRetryAttempts = maxRetryAttempts;
	}

	public boolean isDeleteFilesAfterProcessing() {
		return this.deleteFilesAfterProcessing;
	}

	public void setDeleteFilesAfterProcessing(boolean deleteFilesAfterProcessing) {
		this.deleteFilesAfterProcessing = deleteFilesAfterProcessing;
	}

	public int getHandlerVersion() {
		return this.handlerVersion;
	}

	public void setHandlerVersion(int handlerVersion) {
		this.handlerVersion = handlerVersion;
	}

	public OpenAiBatchOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiBatchOptions that)) {
			return false;
		}
		return this.maxRequestsPerBatch == that.maxRequestsPerBatch && this.maxFileSizeBytes == that.maxFileSizeBytes
				&& Double.compare(this.tokenSafetyFactor, that.tokenSafetyFactor) == 0
				&& this.minimumTokensToSubmit == that.minimumTokensToSubmit
				&& this.maxRetryAttempts == that.maxRetryAttempts
				&& this.deleteFilesAfterProcessing == that.deleteFilesAfterProcessing
				&& this.handlerVersion == that.handlerVersion
				&& Objects.equals(this.completionWindow, that.completionWindow)
				&& Objects.equals(getBaseUrl(), that.getBaseUrl()) && Objects.equals(getApiKey(), that.getApiKey());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.completionWindow, this.maxRequestsPerBatch, this.maxFileSizeBytes,
				this.tokenSafetyFactor, this.minimumTokensToSubmit, this.maxRetryAttempts,
				this.deleteFilesAfterProcessing, this.handlerVersion, getBaseUrl(), getApiKey());
	}

	@Override
	public String toString() {
		return "OpenAiBatchOptions{" + "completionWindow='" + getCompletionWindow() + '\'' + ", maxRequestsPerBatch="
				+ this.maxRequestsPerBatch + ", maxFileSizeBytes=" + this.maxFileSizeBytes + ", tokenSafetyFactor="
				+ this.tokenSafetyFactor + ", minimumTokensToSubmit=" + this.minimumTokensToSubmit
				+ ", maxRetryAttempts=" + this.maxRetryAttempts + ", deleteFilesAfterProcessing="
				+ this.deleteFilesAfterProcessing + ", handlerVersion=" + this.handlerVersion + ", baseUrl='"
				+ getBaseUrl() + '\'' + '}';
	}

	public static final class Builder {

		private @Nullable String completionWindow;

		private int maxRequestsPerBatch = DEFAULT_MAX_REQUESTS_PER_BATCH;

		private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;

		private double tokenSafetyFactor = DEFAULT_TOKEN_SAFETY_FACTOR;

		private long minimumTokensToSubmit = DEFAULT_MINIMUM_TOKENS_TO_SUBMIT;

		private int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;

		private boolean deleteFilesAfterProcessing = DEFAULT_DELETE_FILES_AFTER_PROCESSING;

		private int handlerVersion = DEFAULT_HANDLER_VERSION;

		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable Credential credential;

		private @Nullable String microsoftDeploymentName;

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

		public Builder completionWindow(String completionWindow) {
			this.completionWindow = completionWindow;
			return this;
		}

		public Builder maxRequestsPerBatch(int maxRequestsPerBatch) {
			this.maxRequestsPerBatch = maxRequestsPerBatch;
			return this;
		}

		public Builder maxFileSizeBytes(long maxFileSizeBytes) {
			this.maxFileSizeBytes = maxFileSizeBytes;
			return this;
		}

		public Builder tokenSafetyFactor(double tokenSafetyFactor) {
			this.tokenSafetyFactor = tokenSafetyFactor;
			return this;
		}

		public Builder minimumTokensToSubmit(long minimumTokensToSubmit) {
			this.minimumTokensToSubmit = minimumTokensToSubmit;
			return this;
		}

		public Builder maxRetryAttempts(int maxRetryAttempts) {
			this.maxRetryAttempts = maxRetryAttempts;
			return this;
		}

		public Builder deleteFilesAfterProcessing(boolean deleteFilesAfterProcessing) {
			this.deleteFilesAfterProcessing = deleteFilesAfterProcessing;
			return this;
		}

		public Builder handlerVersion(int handlerVersion) {
			this.handlerVersion = handlerVersion;
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

		public Builder microsoftDeploymentName(String deploymentName) {
			this.microsoftDeploymentName = deploymentName;
			return this;
		}

		public Builder microsoftFoundryServiceVersion(AzureOpenAIServiceVersion serviceVersion) {
			this.microsoftFoundryServiceVersion = serviceVersion;
			return this;
		}

		public Builder organizationId(String organizationId) {
			this.organizationId = organizationId;
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

		public Builder from(OpenAiBatchOptions options) {
			this.completionWindow = options.completionWindow;
			this.maxRequestsPerBatch = options.maxRequestsPerBatch;
			this.maxFileSizeBytes = options.maxFileSizeBytes;
			this.tokenSafetyFactor = options.tokenSafetyFactor;
			this.minimumTokensToSubmit = options.minimumTokensToSubmit;
			this.maxRetryAttempts = options.maxRetryAttempts;
			this.deleteFilesAfterProcessing = options.deleteFilesAfterProcessing;
			this.handlerVersion = options.handlerVersion;
			this.baseUrl = options.getBaseUrl();
			this.apiKey = options.getApiKey();
			this.credential = options.getCredential();
			this.microsoftDeploymentName = options.getMicrosoftDeploymentName();
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

		public OpenAiBatchOptions build() {
			OpenAiBatchOptions options = new OpenAiBatchOptions();
			options.setCompletionWindow(this.completionWindow);
			options.setMaxRequestsPerBatch(this.maxRequestsPerBatch);
			options.setMaxFileSizeBytes(this.maxFileSizeBytes);
			options.setTokenSafetyFactor(this.tokenSafetyFactor);
			options.setMinimumTokensToSubmit(this.minimumTokensToSubmit);
			options.setMaxRetryAttempts(this.maxRetryAttempts);
			options.setDeleteFilesAfterProcessing(this.deleteFilesAfterProcessing);
			options.setHandlerVersion(this.handlerVersion);
			options.setBaseUrl(this.baseUrl);
			options.setApiKey(this.apiKey);
			options.setCredential(this.credential);
			options.setDeploymentName(this.microsoftDeploymentName);
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
