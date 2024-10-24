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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.watsonx.WatsonxAiChatOptions;
import org.springframework.util.Assert;

/**
 * Java class for Watsonx.ai Chat Request object.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatsonxAiChatRequest {

    @JsonProperty("input")
    private String input;
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    @JsonProperty("model_id")
    private String modelId = "";
    @JsonProperty("project_id")
    private String projectId = "";

    private WatsonxAiChatRequest(String input, Map<String, Object> parameters, String modelId, String projectId) {
        this.input = input;
        this.parameters = parameters;
        this.modelId = modelId;
        this.projectId = projectId;
    }

    public static Builder builder(String input) { return new Builder(input); }

    public WatsonxAiChatRequest withProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getInput() { return this.input; }

    public Map<String, Object> getParameters() { return this.parameters; }

    public String getModelId() { return this.modelId; }

    public static class Builder {
        public static final String MODEL_PARAMETER_IS_REQUIRED = "Model parameter is required";
        private final String input;
        private Map<String, Object> parameters;
        private String model = "";

        public Builder(String input) {
            this.input = input;
        }

        public Builder withParameters(Map<String, Object> parameters) {
            Assert.notNull(parameters.get("model"), MODEL_PARAMETER_IS_REQUIRED);
            this.model = parameters.get("model").toString();
            this.parameters = WatsonxAiChatOptions.filterNonSupportedFields(parameters);
            return this;
        }

        public WatsonxAiChatRequest build() {
            return new WatsonxAiChatRequest(this.input, this.parameters, this.model, "");
        }

    }

}