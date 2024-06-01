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
package org.springframework.ai.dashscope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author Nottyjay ji
 * @since 1.0.0-SNAPSHOT
 */
@JsonInclude(Include.NON_NULL)
public class DashscopeEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	/**
	 * The parameters of the embedding.
	 */
	private @JsonProperty("parameters") EmbeddingParameter parameters;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected DashscopeEmbeddingOptions options;

		public Builder() {
			this.options = new DashscopeEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withParameters(EmbeddingParameter parameters) {
			this.options.setParameters(parameters);
			return this;
		}

		public DashscopeEmbeddingOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public EmbeddingParameter getParameters() {
		return parameters;
	}

	public void setParameters(EmbeddingParameter parameters) {
		this.parameters = parameters;
	}

	public static class EmbeddingParameter {

		private @JsonProperty("text_type") String textType;

		public String getTextType() {
			return textType;
		}

		public void setTextType(String textType) {
			this.textType = textType;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			protected EmbeddingParameter parameter;

			public Builder() {
				this.parameter = new EmbeddingParameter();
			}

			public Builder withTextType(EmbeddingTextType textType) {
				this.parameter.setTextType(textType.getTextType());
				return this;
			}

			public EmbeddingParameter build() {
				return this.parameter;
			}

		}

		public enum EmbeddingTextType {

			QUERY("query"), DOCUMENT("document");

			String textType;

			EmbeddingTextType(String textType) {
				this.textType = textType;
			}

			public String getTextType() {
				return textType;
			}

		}

	}

}
