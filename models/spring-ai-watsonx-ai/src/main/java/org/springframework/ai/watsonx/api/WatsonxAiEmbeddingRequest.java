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

package org.springframework.ai.watsonx.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.watsonx.WatsonxAiEmbeddingOptions;

/**
 * Java class for Watsonx.ai Embedding Request object.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WatsonxAiEmbeddingRequest {

	@JsonProperty("model_id")
	String model;

	@JsonProperty("inputs")
	List<String> inputs;

	@JsonProperty("project_id")
	String projectId;

	private WatsonxAiEmbeddingRequest(String model, List<String> inputs, String projectId) {
		this.model = model;
		this.inputs = inputs;
		this.projectId = projectId;
	}

	public static Builder builder(List<String> inputs) {
		return new Builder(inputs);
	}

	public String getModel() {
		return this.model;
	}

	public List<String> getInputs() {
		return this.inputs;
	}

	public WatsonxAiEmbeddingRequest withProjectId(String projectId) {
		this.projectId = projectId;
		return this;
	}

	public static class Builder {

		private final List<String> inputs;

		private String model = WatsonxAiEmbeddingOptions.DEFAULT_MODEL;

		public Builder(List<String> inputs) {
			this.inputs = inputs;
		}

		public Builder withModel(String model) {
			this.model = model;
			return this;
		}

		public WatsonxAiEmbeddingRequest build() {
			return new WatsonxAiEmbeddingRequest(this.model, this.inputs, "");
		}

	}

}
