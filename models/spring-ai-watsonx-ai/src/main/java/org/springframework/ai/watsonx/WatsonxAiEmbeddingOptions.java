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

package org.springframework.ai.watsonx;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * The configuration information for the embedding requests.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatsonxAiEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_MODEL = "ibm/slate-30m-english-rtrvr";

	/**
	 * The embedding model identifier
	 */
	@JsonProperty("model_id")
	private String model;

	/**
	 * Helper factory method to create a new {@link WatsonxAiEmbeddingOptions} instance.
	 * @return A new {@link WatsonxAiEmbeddingOptions} instance.
	 */
	public static WatsonxAiEmbeddingOptions create() {
		return new WatsonxAiEmbeddingOptions();
	}

	public static WatsonxAiEmbeddingOptions fromOptions(WatsonxAiEmbeddingOptions fromOptions) {
		return new WatsonxAiEmbeddingOptions().withModel(fromOptions.getModel());
	}

	public WatsonxAiEmbeddingOptions withModel(String model) {
		this.model = model;
		return this;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

}
