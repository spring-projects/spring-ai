package org.springframework.ai.openai.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ChatCompletionResponse(

		@JsonProperty("id") String id,

		@JsonProperty("choices") List<Choice> choices,

		@JsonProperty("created") Integer created,

		@JsonProperty("model") String model,

		@JsonProperty("system_fingerprint") String systemFingerprint,

		@JsonProperty("object") String object,

		@JsonProperty("usage") Map<String, Object> usage) {
	public record Choice(

			@JsonProperty("finish_reason") String finishReason,

			@JsonProperty("index") Integer index,

			@JsonProperty("message") OpenAiChatMessage message) {
	}
}