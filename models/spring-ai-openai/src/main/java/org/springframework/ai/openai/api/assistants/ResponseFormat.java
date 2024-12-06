/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai.api.assistants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;

/**
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(using = ResponseFormat.ResponseFormatSerializer.class)
@JsonDeserialize(using = ResponseFormat.ResponseFormatDeserializer.class)
public class ResponseFormat {

	@JsonProperty("type")
	private final OpenAiAssistantApi.ResponseFormatType type;

	@JsonProperty("json_schema")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final OpenAiAssistantApi.JsonSchema jsonSchema;

	// Private constructor to enforce factory methods
	private ResponseFormat(OpenAiAssistantApi.ResponseFormatType type, OpenAiAssistantApi.JsonSchema jsonSchema) {
		this.type = type;
		this.jsonSchema = jsonSchema;
	}

	public static ResponseFormat auto() {
		return new ResponseFormat(OpenAiAssistantApi.ResponseFormatType.AUTO, null);
	}

	public static ResponseFormat jsonObject() {
		return new ResponseFormat(OpenAiAssistantApi.ResponseFormatType.JSON_OBJECT, null);
	}

	public static ResponseFormat text() {
		return new ResponseFormat(OpenAiAssistantApi.ResponseFormatType.TEXT, null);
	}

	public static ResponseFormat jsonSchema(OpenAiAssistantApi.JsonSchema schema) {
		return new ResponseFormat(OpenAiAssistantApi.ResponseFormatType.JSON_SCHEMA, schema);
	}

	public OpenAiAssistantApi.ResponseFormatType getType() {
		return this.type;
	}

	public OpenAiAssistantApi.JsonSchema getJsonSchema() {
		return this.jsonSchema;
	}

	// Custom serializer
	public static class ResponseFormatSerializer extends JsonSerializer<ResponseFormat> {

		@Override
		public void serialize(ResponseFormat value, JsonGenerator gen, SerializerProvider serializers)
				throws IOException {
			if (value.type == OpenAiAssistantApi.ResponseFormatType.AUTO) {
				// Serialize as a plain string
				gen.writeString("auto");
			}
			else {
				// Serialize as an object
				gen.writeStartObject();
				gen.writeStringField("type", value.type.toString().toLowerCase());
				if (value.type == OpenAiAssistantApi.ResponseFormatType.JSON_SCHEMA && value.jsonSchema != null) {
					gen.writeObjectField("json_schema", value.jsonSchema);
				}
				gen.writeEndObject();
			}
		}

	}

	// Custom deserializer
	public static class ResponseFormatDeserializer extends JsonDeserializer<ResponseFormat> {

		@Override
		public ResponseFormat deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonNode node = p.getCodec().readTree(p);

			if (node.isTextual()) {
				String type = node.asText();
				if ("auto".equalsIgnoreCase(type)) {
					return ResponseFormat.auto();
				}
				throw new IllegalArgumentException("Unsupported string value for ResponseFormat: " + type);
			}
			else if (node.isObject()) {
				String type = node.get("type").asText();
				OpenAiAssistantApi.ResponseFormatType formatType = OpenAiAssistantApi.ResponseFormatType
					.valueOf(type.toUpperCase());

				if (formatType == OpenAiAssistantApi.ResponseFormatType.JSON_SCHEMA) {
					JsonNode schemaNode = node.get("json_schema");
					OpenAiAssistantApi.JsonSchema schema = null;
					if (schemaNode != null) {
						schema = p.getCodec().treeToValue(schemaNode, OpenAiAssistantApi.JsonSchema.class);
					}
					return ResponseFormat.jsonSchema(schema);
				}
				else if (formatType == OpenAiAssistantApi.ResponseFormatType.JSON_OBJECT) {
					return ResponseFormat.jsonObject();
				}
				else if (formatType == OpenAiAssistantApi.ResponseFormatType.TEXT) {
					return ResponseFormat.text();
				}

				throw new IllegalArgumentException("Unsupported object value for ResponseFormat: " + type);
			}

			throw new IllegalArgumentException("Unexpected JSON format for ResponseFormat.");
		}

	}

}
