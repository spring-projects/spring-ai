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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * The configuration information for the embedding requests.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
public class AzureOpenAiEmbeddingOptions implements EmbeddingOptions {

	/**
	 * An identifier for the caller or end user of the operation. This may be used for
	 * tracking or rate-limiting purposes.
	 */
	private String user;

	/**
	 * The deployment name as defined in Azure Open AI Studio when creating a deployment
	 * backed by an Azure OpenAI base model. If using Azure OpenAI library to communicate
	 * with OpenAI (not Azure OpenAI) then this value will be used as the name of the
	 * model. The json serialization of this field is 'model'.
	 */
	private String deploymentName;

	/*
	 * When using Azure OpenAI, specifies the input type to use for embedding search.
	 */
	private String inputType;

	/*
	 * The number of dimensions the resulting output embeddings should have. Only
	 * supported in `text-embedding-3` and later models.
	 */
	private Integer dimensions;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AzureOpenAiEmbeddingOptions options = new AzureOpenAiEmbeddingOptions();

		public Builder from(AzureOpenAiEmbeddingOptions fromOptions) {
			this.options.setUser(fromOptions.getUser());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setInputType(fromOptions.getInputType());
			this.options.setDimensions(fromOptions.getDimensions());

			return this;
		}

		public Builder merge(EmbeddingOptions from) {
			if (from != null && from instanceof AzureOpenAiEmbeddingOptions castFrom) {

				if (castFrom.getUser() != null) {
					this.options.setUser(castFrom.getUser());
				}
				if (castFrom.getDeploymentName() != null) {
					this.options.setDeploymentName(castFrom.getDeploymentName());
				}
				if (castFrom.getInputType() != null) {
					this.options.setInputType(castFrom.getInputType());
				}
				if (castFrom.getDimensions() != null) {
					this.options.setDimensions(castFrom.getDimensions());
				}
			}
			return this;
		}

		public Builder from(com.azure.ai.openai.models.EmbeddingsOptions azureOptions) {
			this.options.setUser(azureOptions.getUser());
			this.options.setDeploymentName(azureOptions.getModel());
			this.options.setInputType(azureOptions.getInputType());
			this.options.setDimensions(azureOptions.getDimensions());

			return this;
		}

		public Builder withUser(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder withDeploymentName(String model) {
			this.options.setDeploymentName(model);
			return this;
		}

		public Builder withInputType(String inputType) {
			this.options.inputType = inputType;
			return this;
		}

		public Builder withDimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public AzureOpenAiEmbeddingOptions build() {
			return this.options;
		}

	}

	@Override
	@JsonIgnore
	public String getModel() {
		return getDeploymentName();
	}

	@JsonIgnore
	public void setModel(String model) {
		setDeploymentName(model);
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

	public String getInputType() {
		return this.inputType;
	}

	public void setInputType(String inputType) {
		this.inputType = inputType;
	}

	@Override
	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public com.azure.ai.openai.models.EmbeddingsOptions toAzureOptions(List<String> instructions) {

		var azureOptions = new com.azure.ai.openai.models.EmbeddingsOptions(instructions);
		azureOptions.setModel(this.getDeploymentName());
		azureOptions.setUser(this.getUser());
		azureOptions.setInputType(this.getInputType());
		azureOptions.setDimensions(this.getDimensions());

		return azureOptions;
	}

}
