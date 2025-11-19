/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openaiofficial;

import com.openai.models.embeddings.EmbeddingCreateParams;
import org.springframework.ai.embedding.EmbeddingOptions;

import java.util.List;

import static com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_ADA_002;

/**
 * Configuration information for the Embedding Model implementation using the OpenAI Java
 * SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialEmbeddingOptions extends AbstractOpenAiOfficialOptions implements EmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = TEXT_EMBEDDING_ADA_002.asString();

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
		return "OpenAiOfficialEmbeddingOptions{" + "user='" + user + '\'' + ", model='" + getModel() + '\''
				+ ", deploymentName='" + getDeploymentName() + '\'' + ", dimensions=" + dimensions + '}';
	}

	public EmbeddingCreateParams toOpenAiCreateParams(List<String> instructions) {

		EmbeddingCreateParams.Builder builder = EmbeddingCreateParams.builder();

		// Use deployment name if available (for Azure AI Foundry), otherwise use model
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

		private final OpenAiOfficialEmbeddingOptions options = new OpenAiOfficialEmbeddingOptions();

		public Builder from(OpenAiOfficialEmbeddingOptions fromOptions) {
			this.options.setUser(fromOptions.getUser());
			this.options.setModel(fromOptions.getModel());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setDimensions(fromOptions.getDimensions());
			return this;
		}

		public Builder merge(EmbeddingOptions from) {
			if (from instanceof OpenAiOfficialEmbeddingOptions castFrom) {

				if (castFrom.getUser() != null) {
					this.options.setUser(castFrom.getUser());
				}
				if (castFrom.getModel() != null) {
					this.options.setModel(castFrom.getModel());
				}
				if (castFrom.getDeploymentName() != null) {
					this.options.setDeploymentName(castFrom.getDeploymentName());
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

		public Builder dimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public OpenAiOfficialEmbeddingOptions build() {
			return this.options;
		}

	}

}
