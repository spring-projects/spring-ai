package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.dashscope.record.TokenUsage;

/**
 * Represents a chat completion response returned by model, based on the provided input.
 *
 * @param output Represents a chat completion response output.
 * @param usage Usage statistics for the completion request.
 * @param requestId A unique identifier for the chat completion.
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletion(@JsonProperty("output") ChatCompletionOutput output,
		@JsonProperty("usage") TokenUsage usage, @JsonProperty("request_id") String requestId) {
}
