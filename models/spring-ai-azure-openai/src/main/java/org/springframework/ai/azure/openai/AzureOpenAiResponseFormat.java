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

package org.springframework.ai.azure.openai;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.StringUtils;

/**
 * Utility enumeration for representing the response format that may be requested from the
 * Azure OpenAI model. Please check <a href=
 * "https://platform.openai.com/docs/api-reference/chat/create#chat-create-response_format">OpenAI
 * API documentation</a> for more details.
 */
@JsonInclude(Include.NON_NULL)
public class AzureOpenAiResponseFormat {

	/*
	 * From the OpenAI API documentation: Compatibility: Compatible with GPT-4 Turbo and
	 * all GPT-3.5 Turbo models newer than gpt-3.5-turbo-1106. Caveats: This enables JSON
	 * mode, which guarantees the message the model generates is valid JSON. Important:
	 * when using JSON mode, you must also instruct the model to produce JSON yourself via
	 * a system or user message. Without this, the model may generate an unending stream
	 * of whitespace until the generation reaches the token limit, resulting in a
	 * long-running and seemingly "stuck" request. Also note that the message content may
	 * be partially cut off if finish_reason="length", which indicates the generation
	 * exceeded max_tokens or the conversation exceeded the max context length.
	 *
	 * Type Must be one of 'text', 'json_object' or 'json_schema'.
	 */
	@JsonProperty("type")
	private Type type;

	/**
	 * JSON schema object that describes the format of the JSON object. Only applicable
	 * when type is 'json_schema'.
	 */
	@JsonProperty("json_schema")
	private JsonSchema jsonSchema = null;

	private String schema;

	public AzureOpenAiResponseFormat() {

	}

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public JsonSchema getJsonSchema() {
		return this.jsonSchema;
	}

	public void setJsonSchema(JsonSchema jsonSchema) {
		this.jsonSchema = jsonSchema;
	}

	public String getSchema() {
		return this.schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
		if (schema != null) {
			this.jsonSchema = JsonSchema.builder().schema(schema).strict(true).build();
		}
	}

	private AzureOpenAiResponseFormat(Type type, JsonSchema jsonSchema) {
		this.type = type;
		this.jsonSchema = jsonSchema;
	}

	public AzureOpenAiResponseFormat(Type type, String schema) {
		this(type, StringUtils.hasText(schema) ? JsonSchema.builder().schema(schema).strict(true).build() : null);
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
		AzureOpenAiResponseFormat that = (AzureOpenAiResponseFormat) o;
		return this.type == that.type && Objects.equals(this.jsonSchema, that.jsonSchema);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.type, this.jsonSchema);
	}

	@Override
	public String toString() {
		return "ResponseFormat{" + "type=" + this.type + ", jsonSchema=" + this.jsonSchema + '}';
	}

	public static final class Builder {

		private Type type;

		private JsonSchema jsonSchema;

		private Builder() {
		}

		public Builder type(Type type) {
			this.type = type;
			return this;
		}

		public Builder jsonSchema(JsonSchema jsonSchema) {
			this.jsonSchema = jsonSchema;
			return this;
		}

		public Builder jsonSchema(String jsonSchema) {
			this.jsonSchema = JsonSchema.builder().schema(jsonSchema).build();
			return this;
		}

		public AzureOpenAiResponseFormat build() {
			return new AzureOpenAiResponseFormat(this.type, this.jsonSchema);
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

		/**
		 * Enables Structured Outputs which guarantees the model will match your supplied
		 * JSON schema.
		 */
		@JsonProperty("json_schema")
		JSON_SCHEMA

	}

	/**
	 * JSON schema object that describes the format of the JSON object. Applicable for the
	 * 'json_schema' type only.
	 */
	@JsonInclude(Include.NON_NULL)
	public static class JsonSchema {

		@JsonProperty("name")
		private String name;

		@JsonProperty("schema")
		private Map<String, Object> schema;

		@JsonProperty("strict")
		private Boolean strict;

		public JsonSchema() {

		}

		public String getName() {
			return this.name;
		}

		public Map<String, Object> getSchema() {
			return this.schema;
		}

		public Boolean getStrict() {
			return this.strict;
		}

		private JsonSchema(String name, Map<String, Object> schema, Boolean strict) {
			this.name = name;
			this.schema = schema;
			this.strict = strict;
		}

		public static Builder builder() {
			return new Builder();
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.name, this.schema, this.strict);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			JsonSchema that = (JsonSchema) o;
			return Objects.equals(this.name, that.name) && Objects.equals(this.schema, that.schema)
					&& Objects.equals(this.strict, that.strict);
		}

		public static final class Builder {

			private String name = "custom_schema";

			private Map<String, Object> schema;

			private Boolean strict = true;

			private Builder() {
			}

			public Builder name(String name) {
				this.name = name;
				return this;
			}

			public Builder schema(Map<String, Object> schema) {
				this.schema = schema;
				return this;
			}

			public Builder schema(String schema) {
				this.schema = ModelOptionsUtils.jsonToMap(schema);
				return this;
			}

			public Builder strict(Boolean strict) {
				this.strict = strict;
				return this;
			}

			public JsonSchema build() {
				return new JsonSchema(this.name, this.schema, this.strict);
			}

		}

	}

}
