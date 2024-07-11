package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

/**
 * Java class for Watsonx.ai Embedding Response object.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatsonxAiEmbeddingResponse(@JsonProperty("model_id") String model,
		@JsonProperty("created_at") Date createdAt, @JsonProperty("results") List<WatsonxAiEmbeddingResults> results,
		@JsonProperty("input_token_count") Integer inputTokenCount) {
}
