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

package org.springframework.ai.deepseek.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * An object specifying the format that the model must output. Setting to { "type":
 * "json_object" } enables JSON Output, which guarantees the message the model generates
 * is valid JSON.
 * <p>
 * Important: When using JSON Output, you must also instruct the model to produce JSON
 * yourself via a system or user message. Without this, the model may generate an unending
 * stream of whitespace until the generation reaches the token limit, resulting in a
 * long-running and seemingly "stuck" request. Also note that the message content may be
 * partially cut off if finish_reason="length", which indicates the generation exceeded
 * max_tokens or the conversation exceeded the max context length.
 * <p>
 * References:
 * <a href= "https://api-docs.deepseek.com/api/create-chat-completion">DeepSeek API -
 * Create Chat Completion</a>
 *
 * @author Geng Rong
 */

@JsonInclude(Include.NON_NULL)
public class ResponseFormat {

	/**
	 * Type Must be one of 'text', 'json_object'.
	 */
	@JsonProperty("type")
	private Type type;

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	private ResponseFormat(Type type) {
		this.type = type;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ResponseFormat that = (ResponseFormat) o;
		return this.type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.type);
	}

	@Override
	public String toString() {
		return "ResponseFormat{" + "type=" + this.type + '}';
	}

	public static final class Builder {

		private Type type;

		private Builder() {
		}

		public Builder type(Type type) {
			this.type = type;
			return this;
		}

		public ResponseFormat build() {
			return new ResponseFormat(this.type);
		}

	}

	public enum Type {

		/**
		 * Generates a text response. (default)
		 */
		@JsonProperty("text")
		TEXT,

		/**
		 * Enables JSON mode, which guarantees the message the model generates is valid
		 * JSON.
		 */
		@JsonProperty("json_object")
		JSON_OBJECT,

	}

}
