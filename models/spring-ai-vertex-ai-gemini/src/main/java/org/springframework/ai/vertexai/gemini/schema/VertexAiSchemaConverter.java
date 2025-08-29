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

package org.springframework.ai.vertexai.gemini.schema;

import com.google.cloud.vertexai.api.Schema;
import com.google.protobuf.util.JsonFormat;

/**
 * Utility class for converting OpenAPI schemas to Vertex AI Schema objects.
 *
 * @since 1.1.0
 */
public final class VertexAiSchemaConverter {

	private VertexAiSchemaConverter() {
		// Prevent instantiation
	}

	/**
	 * Converts an OpenAPI schema string to a Vertex AI Schema object.
	 * @param openApiSchema The OpenAPI schema in JSON format
	 * @return A Schema object representing the OpenAPI schema
	 * @throws RuntimeException if the schema cannot be parsed
	 */
	public static Schema fromOpenApiSchema(String openApiSchema) {
		try {
			var schemaBuilder = Schema.newBuilder();
			JsonFormat.parser().ignoringUnknownFields().merge(openApiSchema, schemaBuilder);
			return schemaBuilder.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
