/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.titan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel.InputType;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.Assert;

/**
 * Options for the Titan Embedding API.
 *
 * @author Wei Jiang
 * @author Thomas Vitale
 */
@JsonInclude(Include.NON_NULL)
public class BedrockTitanEmbeddingOptions implements EmbeddingOptions {

	/**
	 * Titan Embedding API input types. Could be either text or image (encoded in base64).
	 */
	private InputType inputType;

	public static Builder builder() {
		return new Builder();
	}

	public InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

	public static final class Builder {

		private BedrockTitanEmbeddingOptions options = new BedrockTitanEmbeddingOptions();

		public Builder inputType(InputType inputType) {
			Assert.notNull(inputType, "input type can not be null.");

			this.options.setInputType(inputType);
			return this;
		}

		public BedrockTitanEmbeddingOptions build() {
			return this.options;
		}

	}

}
