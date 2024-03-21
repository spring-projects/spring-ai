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
package org.springframework.ai.watsonx.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.watsonx.WatsonxAiChatOptions;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatsonxAiRequest {

    @JsonProperty("input")
    private String input;
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    @JsonProperty("model_id")
    private String modelId = "";
    @JsonProperty("project_id")
    private String projectId = "";

    private WatsonxAiRequest(String input, Map<String, Object> parameters, String modelId, String projectId) {
        this.input = input;
        this.parameters = parameters;
        this.modelId = modelId;
        this.projectId = projectId;
    }

    public WatsonxAiRequest withModelId(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public WatsonxAiRequest withProjectId(String projectId) {
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
            this.parameters = WatsonxAiChatOptions.filterNonSupportedFields(parameters);
            return this;
        }

        public WatsonxAiRequest build() {
            return new WatsonxAiRequest(input, parameters, model, "");
        }

    }

}