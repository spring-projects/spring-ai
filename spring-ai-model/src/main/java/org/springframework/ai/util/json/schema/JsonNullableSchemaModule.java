/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.util.json.schema;

import com.fasterxml.classmate.ResolvedType;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.impl.module.FlattenedWrapperModule;
import org.openapitools.jackson.nullable.JsonNullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON Schema Generator Module that unwraps
 * {@link org.openapitools.jackson.nullable.JsonNullable JsonNullable&lt;T&gt;} to its
 * wrapped type {@code T} and marks the resulting schema as nullable, so the model can
 * either omit the field or send an explicit {@code null}.
 * <p>
 * This module is registered automatically by {@link JsonSchemaGenerator} when
 * {@code org.openapitools.jackson.nullable.JsonNullable} is present on the classpath.
 *
 * @author Jewoo Shin
 * @since 2.0.0
 */
public final class JsonNullableSchemaModule extends FlattenedWrapperModule<JsonNullable> {

	public JsonNullableSchemaModule() {
		super(JsonNullable.class);
	}

	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		builder.forTypesInGeneral().withCustomDefinitionProvider((javaType, context) -> {
			if (!this.isWrapperType(javaType)) {
				return null;
			}
			ResolvedType wrappedType = context.getTypeContext().getTypeParameterFor(javaType, JsonNullable.class, 0);
			if (wrappedType == null) {
				return null;
			}
			ObjectNode schema = context.createStandardDefinition(wrappedType, null);
			addNullToType(schema);
			return new CustomDefinition(schema, CustomDefinition.DefinitionType.INLINE,
					CustomDefinition.AttributeInclusion.YES);
		});
	}

	private static void addNullToType(ObjectNode schema) {
		JsonNode typeNode = schema.get("type");
		if (typeNode == null) {
			ArrayNode types = schema.putArray("type");
			types.add("null");
			return;
		}
		if (typeNode.isTextual()) {
			String existing = typeNode.asText();
			ArrayNode types = schema.putArray("type");
			types.add(existing);
			types.add("null");
			return;
		}
		if (typeNode.isArray()) {
			ArrayNode types = (ArrayNode) typeNode;
			for (int i = 0; i < types.size(); i++) {
				if ("null".equals(types.get(i).asText())) {
					return;
				}
			}
			types.add("null");
		}
	}

}
