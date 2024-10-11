package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.watsonx.WatsonxAiEmbeddingOptions;

import java.util.List;

/**
 * Java class for Watsonx.ai Embedding Request object.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatsonxAiEmbeddingRequest {

	@JsonProperty("model_id")
	String model;

	@JsonProperty("inputs")
	List<String> inputs;

	@JsonProperty("project_id")
	String projectId;

	public String getModel() {
		return model;
	}

	public List<String> getInputs() {
		return inputs;
	}

	private WatsonxAiEmbeddingRequest(String model, List<String> inputs, String projectId) {
		this.model = model;
		this.inputs = inputs;
		this.projectId = projectId;
	}

	public WatsonxAiEmbeddingRequest withProjectId(String projectId) {
		this.projectId = projectId;
		return this;
	}

	public static Builder builder(List<String> inputs) {
		return new Builder(inputs);
	}

	public static class Builder {

		private String model = WatsonxAiEmbeddingOptions.DEFAULT_MODEL;

		private final List<String> inputs;

		public Builder(List<String> inputs) {
			this.inputs = inputs;
		}

		public Builder withModel(String model) {
			this.model = model;
			return this;
		}

		public WatsonxAiEmbeddingRequest build() {
			return new WatsonxAiEmbeddingRequest(model, inputs, "");
		}

	}

}
