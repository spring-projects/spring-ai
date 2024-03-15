/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.azure.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * The configuration information for the embedding requests.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class AzureOpenAiEmbeddingOptions implements EmbeddingOptions {

	/**
	 * An identifier for the caller or end user of the operation. This may be used for
	 * tracking or rate-limiting purposes.
	 */
	@JsonProperty(value = "user")
	private String user;

	/**
	 * The deployment name as defined in Azure Open AI Studio when creating a deployment
	 * backed by an Azure OpenAI base model. If using Azure OpenAI library to communicate
	 * with OpenAI (not Azure OpenAI) then this value will be used as the name of the
	 * model. The json serialization of this field is 'model'.
	 */
	@JsonProperty(value = "model")
	private String deploymentName;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AzureOpenAiEmbeddingOptions options = new AzureOpenAiEmbeddingOptions();

		public Builder withUser(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder withDeploymentName(String model) {
			this.options.setDeploymentName(model);
			return this;
		}

		public AzureOpenAiEmbeddingOptions build() {
			return this.options;
		}

	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getDeploymentName() {
		return this.deploymentName;
	}

	public void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;
	}

}
