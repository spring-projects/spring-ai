package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.Map;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatsonxAIResponse(
        @JsonProperty("model_id") String modelId,
        @JsonProperty("created_at") Date createdAt,
        @JsonProperty("results") List<WatsonxAIResults> results,
        @JsonProperty("system") Map<String, Object> system
) {}
