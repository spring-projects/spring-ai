package org.springframework.ai.openai.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiSseResponse(

		@JsonProperty("created") Integer created,

		@JsonProperty("model") String model,

		@JsonProperty("id") String id,

		@JsonProperty("system_fingerprint") String systemFingerprint,

		@JsonProperty("choices") List<Choice> choices,

		@JsonProperty("object") String object) {
	public record Choice(

			@JsonProperty("finish_reason") String finishReason,

			@JsonProperty("delta") Delta delta,

			@JsonProperty("index") Integer index) {
		public record Delta(

				@JsonProperty("role") String role,

				@JsonProperty("content") String content) {
		}
	}
}