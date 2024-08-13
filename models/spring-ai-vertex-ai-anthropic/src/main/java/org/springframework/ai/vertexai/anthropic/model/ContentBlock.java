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
package org.springframework.ai.vertexai.anthropic.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @param type the content type can be "text", "image", "tool_use", "tool_result" or
 * "text_delta".
 * @param source The source of the media content. Applicable for "image" types only.
 * @param text The text of the message. Applicable for "text" types only.
 * @param index The index of the content block. Applicable only for streaming responses.
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlock(
// @formatter:off
							@JsonProperty("type") Type type,
							@JsonProperty("source") Source source,
							@JsonProperty("text") String text,

							// applicable only for streaming responses.
							@JsonProperty("index") Integer index,

							// tool_use response only
							@JsonProperty("id") String id,
							@JsonProperty("name") String name,
							@JsonProperty("input") Map<String, Object> input,

							// tool_result response only
							@JsonProperty("tool_use_id") String toolUseId,
							@JsonProperty("content") String content) {
	// @formatter:on

	public ContentBlock(String mediaType, String data) {
		this(new Source(mediaType, data));
	}

	public ContentBlock(Source source) {
		this(Type.IMAGE, source, null, null, null, null, null, null, null);
	}

	public ContentBlock(String text) {
		this(Type.TEXT, null, text, null, null, null, null, null, null);
	}

	// Tool result
	public ContentBlock(Type type, String toolUseId, String content) {
		this(type, null, null, null, null, null, null, toolUseId, content);
	}

	public ContentBlock(Type type, Source source, String text, Integer index) {
		this(type, source, text, index, null, null, null, null, null);
	}

	// Tool use input JSON delta streaming
	public ContentBlock(Type type, String id, String name, Map<String, Object> input) {
		this(type, null, null, null, id, name, input, null, null);
	}

	/**
	 * The ContentBlock type.
	 */
	public enum Type {

		/**
		 * Tool request
		 */
		@JsonProperty("tool_use")
		TOOL_USE("tool_use"),

		/**
		 * Send tool result back to LLM.
		 */
		@JsonProperty("tool_result")
		TOOL_RESULT("tool_result"),

		/**
		 * Text message.
		 */
		@JsonProperty("text")
		TEXT("text"),

		/**
		 * Text delta message. Returned from the streaming response.
		 */
		@JsonProperty("text_delta")
		TEXT_DELTA("text_delta"),

		/**
		 * Tool use input partial JSON delta streaming.
		 */
		@JsonProperty("input_json_delta")
		INPUT_JSON_DELTA("input_json_delta"),

		/**
		 * Image message.
		 */
		@JsonProperty("image")
		IMAGE("image");

		public final String value;

		Type(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * The source of the media content. (Applicable for "image" types only)
	 *
	 * @param type The type of the media content. Only "base64" is supported at the
	 * moment.
	 * @param mediaType The media type of the content. For example, "image/png" or
	 * "image/jpeg".
	 * @param data The base64-encoded data of the content.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Source(
	// @formatter:off
						  @JsonProperty("type") String type,
						  @JsonProperty("media_type") String mediaType,
						  @JsonProperty("data") String data) {
		// @formatter:on

		public Source(String mediaType, String data) {
			this("base64", mediaType, data);
		}
	}
}
