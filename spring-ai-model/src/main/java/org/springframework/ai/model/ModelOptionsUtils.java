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

package org.springframework.ai.model;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Contract;
import org.springframework.util.ObjectUtils;

/**
 * Utility class for manipulating {@link ModelOptions} objects.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author chabinhwang
 * @since 0.8.0
 */
public abstract class ModelOptionsUtils {

	public static final JsonMapper JSON_MAPPER;

	static {
		// Configure coercion for empty strings to null for Enum types
		// This fixes the issue where empty string finish_reason values cause
		// deserialization failures
		JSON_MAPPER = JsonMapper.builder()
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	private static final List<String> BEAN_MERGE_FIELD_EXCISIONS = List.of("class");

	private static final ConcurrentHashMap<Class<?>, List<String>> REQUEST_FIELD_NAMES_PER_CLASS = new ConcurrentHashMap<>();

	private static final AtomicReference<@Nullable SchemaGenerator> SCHEMA_GENERATOR_CACHE = new AtomicReference<>();

	private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {

	};

	/**
	 * Converts the given JSON string to a Map of String and Object using the default
	 * JsonMapper.
	 * @param json the JSON string to convert to a Map.
	 * @return the converted Map.
	 */
	public static Map<String, Object> jsonToMap(String json) {
		return jsonToMap(json, JSON_MAPPER);
	}

	/**
	 * Converts the given JSON string to a Map of String and Object using a custom
	 * JsonMapper.
	 * @param json the JSON string to convert to a Map.
	 * @param jsonMapper the JsonMapper to use for deserialization.
	 * @return the converted Map.
	 */
	public static Map<String, Object> jsonToMap(String json, JsonMapper jsonMapper) {
		return jsonMapper.readValue(json, MAP_TYPE_REF);
	}

	/**
	 * Converts the given JSON string to an Object of the given type.
	 * @param <T> the type of the object to return.
	 * @param json the JSON string to convert to an object.
	 * @param type the type of the object to return.
	 * @return Object instance of the given type.
	 */
	public static <T> T jsonToObject(String json, Class<T> type) {
		return JSON_MAPPER.readValue(json, type);
	}

	/**
	 * Converts the given object to a JSON string.
	 * @param object the object to convert to a JSON string.
	 * @return the JSON string.
	 */
	public static String toJsonString(Object object) {
		return JSON_MAPPER.writeValueAsString(object);
	}

	/**
	 * Converts the given object to a JSON string.
	 * @param object the object to convert to a JSON string.
	 * @return the JSON string.
	 */
	public static String toJsonStringPrettyPrinter(Object object) {
		return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
	}

	/**
	 * Generates JSON Schema (version 2020_12) for the given class.
	 * @param inputType the input {@link Type} to generate JSON Schema from.
	 * @param toUpperCaseTypeValues if true, the type values are converted to upper case.
	 * @return the generated JSON Schema as a String.
	 */
	public static String getJsonSchema(Type inputType, boolean toUpperCaseTypeValues) {

		ObjectNode node = getJsonSchema(inputType);

		if (toUpperCaseTypeValues) { // Required for OpenAPI 3.0 (at least Vertex AI
			// version of it).
			toUpperCaseTypeValues(node);
		}

		return node.toPrettyString();
	}

	public static ObjectNode getJsonSchema(Type inputType) {

		if (SCHEMA_GENERATOR_CACHE.get() == null) {

			JacksonSchemaModule jacksonModule = new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
			Swagger2Module swaggerModule = new Swagger2Module();

			SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
					OptionPreset.PLAIN_JSON)
				.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
				.with(Option.PLAIN_DEFINITION_KEYS)
				.with(swaggerModule)
				.with(jacksonModule);

			if (KotlinDetector.isKotlinReflectPresent()) {
				configBuilder.with(new KotlinModule());
			}

			SchemaGeneratorConfig config = configBuilder.build();
			SchemaGenerator generator = new SchemaGenerator(config);
			SCHEMA_GENERATOR_CACHE.compareAndSet(null, generator);
		}

		@SuppressWarnings("NullAway")
		ObjectNode node = SCHEMA_GENERATOR_CACHE.get().generateSchema(inputType);

		if ((inputType == Void.class) && !node.has("properties")) {
			node.putObject("properties");
		}

		return node;
	}

	public static void toUpperCaseTypeValues(ObjectNode node) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			node.properties().forEach(entry -> {
				JsonNode value = entry.getValue();
				if (value.isObject()) {
					toUpperCaseTypeValues((ObjectNode) value);
				}
				else if (value.isArray()) {
					value.forEach(element -> {
						if (element.isObject() || element.isArray()) {
							toUpperCaseTypeValues((ObjectNode) element);
						}
					});
				}
				else if (value.isTextual() && entry.getKey().equals("type")) {
					String oldValue = ((ObjectNode) node).get("type").asText();
					((ObjectNode) node).put("type", oldValue.toUpperCase());
				}
			});
		}
		else if (node.isArray()) {
			node.forEach(element -> {
				if (element.isObject() || element.isArray()) {
					toUpperCaseTypeValues((ObjectNode) element);
				}
			});
		}
	}

	/**
	 * Return the runtime value if not empty, or else the default value.
	 */
	@Contract("_, !null -> !null")
	public static <T> @Nullable T mergeOption(@Nullable T runtimeValue, @Nullable T defaultValue) {
		return ObjectUtils.isEmpty(runtimeValue) ? defaultValue : runtimeValue;
	}

}
