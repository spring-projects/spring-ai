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

package org.springframework.ai.util.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utilities to generate JSON Schemas from Java entities.
 */
public final class JsonSchemaGenerator {

	private static final SchemaGenerator TYPE_SCHEMA_GENERATOR;

	private static final SchemaGenerator SUBTYPE_SCHEMA_GENERATOR;

	/*
	 * Initialize JSON Schema generators.
	 */
	static {
		var schemaGeneratorConfigBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
				OptionPreset.PLAIN_JSON)
			.with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED))
			.with(new Swagger2Module())
			.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
			.with(Option.PLAIN_DEFINITION_KEYS);

		var typeSchemaGeneratorConfig = schemaGeneratorConfigBuilder.without(Option.SCHEMA_VERSION_INDICATOR).build();
		TYPE_SCHEMA_GENERATOR = new SchemaGenerator(typeSchemaGeneratorConfig);

		var subtypeSchemaGeneratorConfig = schemaGeneratorConfigBuilder.build();
		SUBTYPE_SCHEMA_GENERATOR = new SchemaGenerator(subtypeSchemaGeneratorConfig);
	}

	private JsonSchemaGenerator() {
	}

	/**
	 * Generate a JSON Schema for a method's input parameters.
	 */
	public static String generateForMethodInput(Method method, SchemaOption... schemaOptions) {
		ObjectNode schema = JsonParser.getObjectMapper().createObjectNode();
		schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");
		List<String> required = new ArrayList<>();

		for (int i = 0; i < method.getParameterCount(); i++) {
			var parameterName = method.getParameters()[i].getName();
			var parameterType = method.getGenericParameterTypes()[i];
			if (isMethodParameterRequired(method, i)) {
				required.add(parameterName);
			}
			properties.set(parameterName, SUBTYPE_SCHEMA_GENERATOR.generateSchema(parameterType));
		}

		var requiredArray = schema.putArray("required");
		if (Stream.of(schemaOptions).anyMatch(option -> option == SchemaOption.RESPECT_JSON_PROPERTY_REQUIRED)) {
			required.forEach(requiredArray::add);
		}
		else {
			Stream.of(method.getParameters()).map(Parameter::getName).forEach(requiredArray::add);
		}

		if (Stream.of(schemaOptions)
			.noneMatch(option -> option == SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT)) {
			schema.put("additionalProperties", false);
		}

		if (Stream.of(schemaOptions).anyMatch(option -> option == SchemaOption.UPPER_CASE_TYPE_VALUES)) {
			convertTypeValuesToUpperCase(schema);
		}

		return schema.toPrettyString();
	}

	/**
	 * Generate a JSON Schema for a class type.
	 */
	public static String generateForType(Type type, SchemaOption... schemaOptions) {
		Assert.notNull(type, "type cannot be null");
		ObjectNode schema = TYPE_SCHEMA_GENERATOR.generateSchema(type);
		if ((type == Void.class) && !schema.has("properties")) {
			schema.putObject("properties");
		}
		if (Stream.of(schemaOptions)
			.noneMatch(option -> option == SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT)) {
			schema.put("additionalProperties", false);
		}
		if (Stream.of(schemaOptions).anyMatch(option -> option == SchemaOption.UPPER_CASE_TYPE_VALUES)) {
			convertTypeValuesToUpperCase(schema);
		}
		return schema.toPrettyString();
	}

	private static boolean isMethodParameterRequired(Method method, int index) {
		var jsonPropertyAnnotation = method.getParameters()[index].getAnnotation(JsonProperty.class);
		if (jsonPropertyAnnotation == null) {
			return false;
		}
		return jsonPropertyAnnotation.required();
	}

	// Based on the method in ModelOptionsUtils.
	private static void convertTypeValuesToUpperCase(ObjectNode node) {
		if (node.isObject()) {
			node.fields().forEachRemaining(entry -> {
				JsonNode value = entry.getValue();
				if (value.isObject()) {
					convertTypeValuesToUpperCase((ObjectNode) value);
				}
				else if (value.isArray()) {
					value.elements().forEachRemaining(element -> {
						if (element.isObject() || element.isArray()) {
							convertTypeValuesToUpperCase((ObjectNode) element);
						}
					});
				}
				else if (value.isTextual() && entry.getKey().equals("type")) {
					String oldValue = node.get("type").asText();
					node.put("type", oldValue.toUpperCase());
				}
			});
		}
		else if (node.isArray()) {
			node.elements().forEachRemaining(element -> {
				if (element.isObject() || element.isArray()) {
					convertTypeValuesToUpperCase((ObjectNode) element);
				}
			});
		}
	}

	/**
	 * Options for generating JSON Schemas.
	 */
	public enum SchemaOption {

		/**
		 * Properties are only required if marked as such via the Jackson annotation
		 * "@JsonProperty(required = true)". Beware, that OpenAI requires all properties
		 * to be required.
		 */
		RESPECT_JSON_PROPERTY_REQUIRED,

		/**
		 * Allow additional properties by default. Beware, that OpenAI requires additional
		 * properties NOT to be allowed.
		 */
		ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT,

		/**
		 * Convert all "type" values to upper case. For example, it's require in OpenAPI
		 * 3.0 with Vertex AI.
		 */
		UPPER_CASE_TYPE_VALUES;

	}

}
