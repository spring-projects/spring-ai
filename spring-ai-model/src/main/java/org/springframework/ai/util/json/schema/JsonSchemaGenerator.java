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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.KotlinModule;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.Nullness;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Utilities to generate JSON Schemas from Java types and method signatures. It's designed
 * to work well in the context of tool calling and structured outputs, aiming at ensuring
 * consistency and robustness across different model providers.
 * <p>
 * Metadata such as descriptions and required properties can be specified using one of the
 * following supported annotations:
 * <p>
 * <ul>
 * <li>{@code @ToolParam(required = ..., description = ...)}</li>
 * <li>{@code @JsonProperty(required = ...)}</li>
 * <li>{@code @JsonClassDescription(...)}</li>
 * <li>{@code @JsonPropertyDescription(...)}</li>
 * <li>{@code @Schema(required = ..., description = ...)}</li>
 * <li>{@code @Nullable}</li>
 * </ul>
 * <p>
 * If none of these annotations are present, the default behavior is to consider the
 * property as required and not to include a description.
 * <p>
 * Validation constraints declared via {@code @Schema} (e.g. {@code minLength},
 * {@code maxLength}, {@code pattern}, {@code minimum}, {@code maximum},
 * {@code multipleOf}) and {@code @ArraySchema} (e.g. {@code minItems}, {@code maxItems},
 * {@code uniqueItems}) are also reflected in the generated schema, for both type fields
 * and method parameters.
 * <p>
 *
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public final class JsonSchemaGenerator {

	/**
	 * To ensure consistency and robustness across different model providers, all
	 * properties in the JSON Schema are considered required by default. This behavior can
	 * be overridden by setting the {@link ToolParam#required()},
	 * {@link JsonProperty#required()}, or {@link Schema#requiredMode()}} annotation.
	 */
	private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

	private static final SchemaGenerator typeSchemaGenerator;

	private static final SchemaGenerator subtypeSchemaGenerator;

	/*
	 * Initialize JSON Schema generators.
	 */
	static {
		Module jacksonModule = new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
				JacksonOption.RESPECT_JSONPROPERTY_ORDER);
		Module openApiModule = new Swagger2Module();
		Module springAiSchemaModule = PROPERTY_REQUIRED_BY_DEFAULT ? new SpringAiSchemaModule()
				: new SpringAiSchemaModule(SpringAiSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

		SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder = new SchemaGeneratorConfigBuilder(
				SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(openApiModule)
			.with(springAiSchemaModule)
			.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
			.with(Option.PLAIN_DEFINITION_KEYS);

		if (KotlinDetector.isKotlinReflectPresent()) {
			schemaGeneratorConfigBuilder.with(new KotlinModule());
		}

		SchemaGeneratorConfig typeSchemaGeneratorConfig = schemaGeneratorConfigBuilder.build();
		typeSchemaGenerator = new SchemaGenerator(typeSchemaGeneratorConfig);

		SchemaGeneratorConfig subtypeSchemaGeneratorConfig = schemaGeneratorConfigBuilder
			.without(Option.SCHEMA_VERSION_INDICATOR)
			.build();
		subtypeSchemaGenerator = new SchemaGenerator(subtypeSchemaGeneratorConfig);
	}

	private JsonSchemaGenerator() {
	}

	private static ObjectNode generateSchema(SchemaGenerator generator, Type type) {
		synchronized (generator) {
			return generator.generateSchema(type);
		}
	}

	/**
	 * Generate a JSON Schema for a method's input parameters.
	 */
	public static String generateForMethodInput(Method method, SchemaOption... schemaOptions) {
		ObjectNode schema = JacksonUtils.getDefaultJsonMapper().createObjectNode();
		schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
		schema.put("type", "object");
		ObjectNode defs = schema.putObject("$defs");

		ObjectNode properties = schema.putObject("properties");
		List<String> required = new ArrayList<>();

		for (int i = 0; i < method.getParameterCount(); i++) {
			String parameterName = method.getParameters()[i].getName();
			Type parameterType = method.getGenericParameterTypes()[i];
			if (parameterType instanceof Class<?> parameterClass
					&& ClassUtils.isAssignable(ToolContext.class, parameterClass)) {
				// A ToolContext method parameter is not included in the JSON Schema
				// generation.
				// It's a special type used by Spring AI to pass contextual data to tools
				// outside the model interaction flow.
				continue;
			}
			// A Kotlin suspend function carries a synthetic trailing Continuation
			// parameter that is not part of the tool contract and must not appear in
			// the generated schema.
			if (KotlinDetector.isSuspendingFunction(method) && i == method.getParameterCount() - 1) {
				continue;
			}
			if (isMethodParameterRequired(method, i)) {
				required.add(parameterName);
			}
			ObjectNode parameterNode = generateSchema(subtypeSchemaGenerator, parameterType);
			// victools generates self-contained schemas where $defs and the $ref
			// pointers into them are rooted at the sub-schema. Inlining the
			// sub-schema under properties.<paramName> re-parents existing
			// "#/$defs/<Name>" refs to the outer root, leaving them unresolvable.
			// Hoist $defs to the outer root so those refs resolve again.
			JsonSchemaUtils.hoistDefsToRoot(schema, parameterNode);
			// Remove OpenAPI format as some LLMs (like Mistral) don't handle them.
			parameterNode.remove("format");
			String parameterDescription = getMethodParameterDescription(method, i);
			if (StringUtils.hasText(parameterDescription)) {
				parameterNode.put("description", parameterDescription);
			}
			applyParameterConstraints(parameterNode, method.getParameters()[i]);
			properties.set(parameterName, parameterNode);
		}

		if (defs.isEmpty()) {
			schema.remove("$defs");
		}

		var requiredArray = schema.putArray("required");
		required.forEach(requiredArray::add);

		processSchemaOptions(schemaOptions, schema);

		return schema.toPrettyString();
	}

	/**
	 * Generate a JSON Schema for a class type.
	 */
	public static String generateForType(Type type, SchemaOption... schemaOptions) {
		Assert.notNull(type, "type cannot be null");
		ObjectNode schema = generateSchema(typeSchemaGenerator, type);
		if ((type == Void.class) && !schema.has("properties")) {
			schema.putObject("properties");
		}
		processSchemaOptions(schemaOptions, schema);
		return schema.toPrettyString();
	}

	private static void processSchemaOptions(SchemaOption[] schemaOptions, ObjectNode schema) {
		if (Stream.of(schemaOptions)
			.noneMatch(option -> option == SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT)) {
			forbidAdditionalProperties(schema);
		}
		if (Stream.of(schemaOptions).anyMatch(option -> option == SchemaOption.UPPER_CASE_TYPE_VALUES)) {
			convertTypeValuesToUpperCase(schema);
		}
	}

	/**
	 * Determines whether a property is required based on the presence of a series of *
	 * annotations.
	 *
	 * <p>
	 * <ul>
	 * <li>{@code @ToolParam(required = ...)}</li>
	 * <li>{@code @JsonProperty(required = ...)}</li>
	 * <li>{@code @Schema(required = ...)}</li>
	 * <li>{@code @Nullable}</li>
	 * </ul>
	 * <p>
	 *
	 * If none of these annotations are present, the default behavior is to consider the *
	 * property as required.
	 */
	private static boolean isMethodParameterRequired(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		var toolParamAnnotation = parameter.getAnnotation(ToolParam.class);
		if (toolParamAnnotation != null) {
			return toolParamAnnotation.required();
		}

		var propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
		if (propertyAnnotation != null) {
			return propertyAnnotation.required();
		}

		var schemaAnnotation = parameter.getAnnotation(Schema.class);
		if (schemaAnnotation != null) {
			return schemaAnnotation.requiredMode() == Schema.RequiredMode.REQUIRED
					|| schemaAnnotation.requiredMode() == Schema.RequiredMode.AUTO || schemaAnnotation.required();
		}

		Nullness nullness = Nullness.forParameter(parameter);
		if (nullness == Nullness.NULLABLE) {
			return false;
		}

		return PROPERTY_REQUIRED_BY_DEFAULT;
	}

	/**
	 * Determines a property description based on the presence of a series of annotations.
	 *
	 * <p>
	 * <ul>
	 * <li>{@code @ToolParam(description = ...)}</li>
	 * <li>{@code @JsonPropertyDescription(...)}</li>
	 * <li>{@code @Schema(description = ...)}</li>
	 * </ul>
	 * <p>
	 */
	private static @Nullable String getMethodParameterDescription(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		var toolParamAnnotation = parameter.getAnnotation(ToolParam.class);
		if (toolParamAnnotation != null && StringUtils.hasText(toolParamAnnotation.description())) {
			return toolParamAnnotation.description();
		}

		var jacksonAnnotation = parameter.getAnnotation(JsonPropertyDescription.class);
		if (jacksonAnnotation != null && StringUtils.hasText(jacksonAnnotation.value())) {
			return jacksonAnnotation.value();
		}

		var schemaAnnotation = parameter.getAnnotation(Schema.class);
		if (schemaAnnotation != null && StringUtils.hasText(schemaAnnotation.description())) {
			return schemaAnnotation.description();
		}

		return null;
	}

	/**
	 * Applies the JSON Schema validation keywords declared via {@link Schema} and
	 * {@link ArraySchema} on a method parameter to its generated schema node.
	 *
	 * <p>
	 * Such constraints are honored by the underlying generator when the annotations are
	 * placed on a type's fields, but a method parameter's annotations are invisible to
	 * it. They are therefore applied here so that tool method parameters behave
	 * consistently with type fields. The supported keywords mirror those produced for
	 * fields:
	 * <ul>
	 * <li>{@code minLength}, {@code maxLength}, {@code pattern} for strings;</li>
	 * <li>{@code minimum}, {@code maximum}, {@code exclusiveMinimum},
	 * {@code exclusiveMaximum}, {@code multipleOf} for numbers;</li>
	 * <li>{@code minItems}, {@code maxItems}, {@code uniqueItems} for arrays.</li>
	 * </ul>
	 */
	private static void applyParameterConstraints(ObjectNode parameterNode, Parameter parameter) {
		JsonNode typeNode = parameterNode.get("type");
		String type = (typeNode != null && typeNode.isString()) ? typeNode.asString() : null;

		if ("array".equals(type)) {
			ArraySchema arraySchema = parameter.getAnnotation(ArraySchema.class);
			if (arraySchema != null) {
				if (arraySchema.minItems() != Integer.MAX_VALUE) {
					parameterNode.put("minItems", arraySchema.minItems());
				}
				if (arraySchema.maxItems() != Integer.MIN_VALUE) {
					parameterNode.put("maxItems", arraySchema.maxItems());
				}
				if (arraySchema.uniqueItems()) {
					parameterNode.put("uniqueItems", true);
				}
			}
			return;
		}

		Schema schema = parameter.getAnnotation(Schema.class);
		if (schema == null) {
			return;
		}
		if ("string".equals(type)) {
			if (schema.minLength() > 0) {
				parameterNode.put("minLength", schema.minLength());
			}
			if (schema.maxLength() < Integer.MAX_VALUE) {
				parameterNode.put("maxLength", schema.maxLength());
			}
			if (StringUtils.hasText(schema.pattern())) {
				parameterNode.put("pattern", schema.pattern());
			}
		}
		else if ("integer".equals(type) || "number".equals(type)) {
			if (StringUtils.hasText(schema.minimum())) {
				parameterNode.put(schema.exclusiveMinimum() ? "exclusiveMinimum" : "minimum",
						new BigDecimal(schema.minimum()));
			}
			if (StringUtils.hasText(schema.maximum())) {
				parameterNode.put(schema.exclusiveMaximum() ? "exclusiveMaximum" : "maximum",
						new BigDecimal(schema.maximum()));
			}
			if (schema.multipleOf() != 0) {
				parameterNode.put("multipleOf", BigDecimal.valueOf(schema.multipleOf()));
			}
		}
	}

	/**
	 * Recursively adds {@code "additionalProperties": false} to all object schemas (nodes
	 * with a {@code "properties"} key) that do not already define
	 * {@code "additionalProperties"}. The guard preserves {@code Map<K,V>} schemas where
	 * {@code "additionalProperties"} is a type reference rather than a boolean.
	 */
	private static void forbidAdditionalProperties(ObjectNode node) {
		if (node.has("properties") && !node.has("additionalProperties")) {
			node.put("additionalProperties", false);
		}
		node.properties().forEach(entry -> {
			JsonNode value = entry.getValue();
			if (value.isObject()) {
				forbidAdditionalProperties((ObjectNode) value);
			}
			else if (value.isArray()) {
				value.forEach(element -> {
					if (element.isObject()) {
						forbidAdditionalProperties((ObjectNode) element);
					}
				});
			}
		});
	}

	public static void convertTypeValuesToUpperCase(ObjectNode node) {
		if (node.isObject()) {
			node.properties().forEach(entry -> {
				JsonNode value = entry.getValue();
				if (value.isObject()) {
					convertTypeValuesToUpperCase((ObjectNode) value);
				}
				else if (value.isArray()) {
					value.forEach(element -> {
						if (element.isObject() || element.isArray()) {
							convertTypeValuesToUpperCase((ObjectNode) element);
						}
					});
				}
				else if (value.isString() && entry.getKey().equals("type")) {
					String oldValue = node.get("type").asString();
					node.put("type", oldValue.toUpperCase(Locale.ROOT));
				}
			});
		}
		else if (node.isArray()) {
			node.forEach(element -> {
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
		 * Allow an object to contain additional key/values not defined in the schema.
		 */
		ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT,

		/**
		 * Convert all "type" values to upper case.
		 */
		UPPER_CASE_TYPE_VALUES

	}

}
