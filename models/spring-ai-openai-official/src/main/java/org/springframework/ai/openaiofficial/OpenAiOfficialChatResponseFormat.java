/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openaiofficial;

/**
 * Response format (text, json_object, json_schema) for OpenAiOfficialChatModel responses.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialChatResponseFormat {

	private Type type = Type.TEXT;

	private String jsonSchema;

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getJsonSchema() {
		return jsonSchema;
	}

	public void setJsonSchema(String jsonSchema) {
		this.jsonSchema = jsonSchema;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final OpenAiOfficialChatResponseFormat openAiOfficialChatResponseFormat = new OpenAiOfficialChatResponseFormat();

		private Builder() {
		}

		public Builder type(Type type) {
			this.openAiOfficialChatResponseFormat.setType(type);
			return this;
		}

		public Builder jsonSchema(String jsonSchema) {
			this.openAiOfficialChatResponseFormat.setType(Type.JSON_SCHEMA);
			this.openAiOfficialChatResponseFormat.setJsonSchema(jsonSchema);
			return this;
		}

		public OpenAiOfficialChatResponseFormat build() {
			return this.openAiOfficialChatResponseFormat;
		}

	}

	public enum Type {

		/**
		 * Generates a text response. (default)
		 */
		TEXT,

		/**
		 * Enables JSON mode, which guarantees the message the model generates is valid
		 * JSON.
		 */
		JSON_OBJECT,

		/**
		 * Enables Structured Outputs which guarantees the model will match your supplied
		 * JSON schema.
		 */
		JSON_SCHEMA

	}

}
