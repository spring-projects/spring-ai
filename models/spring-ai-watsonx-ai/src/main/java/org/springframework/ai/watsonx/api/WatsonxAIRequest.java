package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatsonxAIRequest {

    @JsonProperty("input")
    private String input;
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    @JsonProperty("model_id")
    private String modelId = "";
    @JsonProperty("project_id")
    private String projectId = "";

    private WatsonxAIRequest(String input, Map<String, Object> parameters, String modelId, String projectId) {
        this.input = input;
        this.parameters = parameters;
        this.modelId = modelId;
        this.projectId = projectId;
    }

    public WatsonxAIRequest withModelId(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public WatsonxAIRequest withProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getInput() { return input; }

    public Map<String, Object> getParameters() { return parameters; }

    public String getModelId() { return modelId; }


    public static Builder builder(String input) { return new Builder(input); }

    public static class Builder {
        private final String input;
        private Map<String, Object> parameters;
        private String model = "";

        public Builder(String input) {
            this.input = input;
        }

        public Builder withParameters(Map<String, Object> parameters) {
            this.model = parameters.get("model").toString();
            this.parameters = WatsonxAIOptions.filterNonSupportedFields(parameters);
            return this;
        }

        public WatsonxAIRequest build() {
            return new WatsonxAIRequest(input, parameters, model, "");
        }

    }

}