package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(@JsonProperty("model") String model, @JsonProperty("steam") Boolean stream,
		@JsonProperty("input") ChatCompletionRequestInput chatCompletionInput,
		@JsonProperty("parameters") ChatCompletionRequestParameters parameters) {
	public ChatCompletionRequest(ChatCompletionRequestInput chatCompletionRequestInput, boolean stream) {
		this(null, stream, chatCompletionRequestInput, null);
	}
}
