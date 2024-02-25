package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatsonxAIResults(
        @JsonProperty("generated_text") String generatedText,
        @JsonProperty("generated_token_count") Integer generatedTokenCount,
        @JsonProperty("input_token_count") Integer inputTokenCount,
        @JsonProperty("stop_reason") String stopReason
) { }
