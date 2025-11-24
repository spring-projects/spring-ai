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

import java.util.List;

import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Configuration information for the Embedding Model implementation using the OpenAI Java
 * SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiSdkEmbeddingOptions extends AbstractOpenAiSdkOptions implements EmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.TEXT_EMBEDDING_ADA_002.asString();

	/**
	 * An identifier for the caller or end user of the operation. This may be used for
	 * tracking or rate-limiting purposes.
	 */
	private String user;

	/*
	 * The number of dimensions the resulting output embeddings should have. Only
	 * supported in `text-embedding-3` and later models.
	 */
	private Integer dimensions;

	public static Builder builder() {
		return new Builder();
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	@Override
	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public String toString() {
		return "OpenAiSdkEmbeddingOptions{" + "user='" + this.user + '\'' + ", model='" + this.getModel() + '\''
				+ ", deploymentName='" + this.getDeploymentName() + '\'' + ", dimensions=" + this.dimensions + '}';
	}

	public EmbeddingCreateParams toOpenAiCreateParams(List<String> instructions) {

		EmbeddingCreateParams.Builder builder = EmbeddingCreateParams.builder();

		// Use deployment name if available (for Microsoft Foundry), otherwise use model
		// name
		if (this.getDeploymentName() != null) {
			builder.model(this.getDeploymentName());
		}
		else if (this.getModel() != null) {
			builder.model(this.getModel());
		}

		if (instructions != null && !instructions.isEmpty()) {
			builder.input(EmbeddingCreateParams.Input.ofArrayOfStrings(instructions));
		}
		if (this.getUser() != null) {
			builder.user(this.getUser());
		}
		if (this.getDimensions() != null) {
			builder.dimensions(this.getDimensions());
		}
		return builder.build();
	}

	public static final class Builder {

		private final OpenAiSdkEmbeddingOptions options = new OpenAiSdkEmbeddingOptions();

		public Builder from(OpenAiSdkEmbeddingOptions fromOptions) {
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
			this.options.setCustomHeaders(fromOptions.getCustomHeaders());
			// Child class fields
			this.options.setUser(fromOptions.getUser());
			this.options.setDimensions(fromOptions.getDimensions());
			return this;
		}

		public Builder merge(EmbeddingOptions from) {
			if (from instanceof OpenAiSdkEmbeddingOptions castFrom) {
				// Parent class fields
				if (castFrom.getBaseUrl() != null) {
					this.options.setBaseUrl(castFrom.getBaseUrl());
				}
				if (castFrom.getApiKey() != null) {
					this.options.setApiKey(castFrom.getApiKey());
				}
				if (castFrom.getCredential() != null) {
					this.options.setCredential(castFrom.getCredential());
				}
				if (castFrom.getModel() != null) {
					this.options.setModel(castFrom.getModel());
				}
				if (castFrom.getDeploymentName() != null) {
					this.options.setDeploymentName(castFrom.getDeploymentName());
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.options.setMicrosoftFoundryServiceVersion(castFrom.getMicrosoftFoundryServiceVersion());
				}
				if (castFrom.getOrganizationId() != null) {
					this.options.setOrganizationId(castFrom.getOrganizationId());
				}
				this.options.setMicrosoftFoundry(castFrom.isMicrosoftFoundry());
				this.options.setGitHubModels(castFrom.isGitHubModels());
				if (castFrom.getTimeout() != null) {
					this.options.setTimeout(castFrom.getTimeout());
				}
				if (castFrom.getMaxRetries() != null) {
					this.options.setMaxRetries(castFrom.getMaxRetries());
				}
				if (castFrom.getProxy() != null) {
					this.options.setProxy(castFrom.getProxy());
				}
				if (castFrom.getCustomHeaders() != null) {
					this.options.setCustomHeaders(castFrom.getCustomHeaders());
				}
				// Child class fields
				if (castFrom.getUser() != null) {
					this.options.setUser(castFrom.getUser());
				}
				if (castFrom.getDimensions() != null) {
					this.options.setDimensions(castFrom.getDimensions());
				}
			}
			return this;
		}

		public Builder from(EmbeddingCreateParams openAiCreateParams) {

			if (openAiCreateParams.user().isPresent()) {
				this.options.setUser(openAiCreateParams.user().get());
			}
			if (openAiCreateParams.dimensions().isPresent()) {
				this.options.setDimensions(Math.toIntExact(openAiCreateParams.dimensions().get()));
			}
			return this;
		}

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
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

		public Builder customHeaders(java.util.Map<String, String> customHeaders) {
			this.options.setCustomHeaders(customHeaders);
			return this;
		}

		public Builder dimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public OpenAiSdkEmbeddingOptions build() {
			return this.options;
		}

	}

}
