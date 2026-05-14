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

package org.springframework.ai.mcp.annotation.method.tool.utils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.model.KotlinModule;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator.SchemaOption;
import org.springframework.core.KotlinDetector;
import org.springframework.core.Nullness;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

public final class McpJsonSchemaGenerator {

	private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

	/**
	 * Schema generator for method parameter types. Used by
	 * {@link #generateForMethodInput} to produce per-parameter schema nodes. Configured
	 * with {@link McpSpringAiSchemaModule} so that {@code @McpToolParam} annotations on
	 * method parameters are honoured, and without the schema-version indicator so that
	 * each node does not carry a redundant {@code $schema} field.
	 */
	private static final SchemaGenerator SUBTYPE_SCHEMA_GENERATOR;

	private static final Map<Method, String> methodSchemaCache = new ConcurrentReferenceHashMap<>(256);

	private static final Map<Type, String> typeSchemaCache = new ConcurrentReferenceHashMap<>(256);

	/*
	 * Initialize the subtype schema generator used for per-parameter schema nodes.
	 * Type-level schema generation (generateFromType / generateFromClass) is delegated to
	 * spring-ai-model's JsonSchemaGenerator.
	 */
	static {
		Module jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
		Module openApiModule = new Swagger2Module();
		Module springAiSchemaModule = PROPERTY_REQUIRED_BY_DEFAULT ? new McpSpringAiSchemaModule()
				: new McpSpringAiSchemaModule(McpSpringAiSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

		SchemaGeneratorConfigBuilder subtypeConfigBuilder = new SchemaGeneratorConfigBuilder(
				SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(openApiModule)
			.with(springAiSchemaModule)
			.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
			.with(Option.STANDARD_FORMATS)
			.with(Option.PLAIN_DEFINITION_KEYS)
			.without(Option.SCHEMA_VERSION_INDICATOR);

		if (KotlinDetector.isKotlinReflectPresent()) {
			subtypeConfigBuilder.with(new KotlinModule());
		}

		SchemaGeneratorConfig subtypeConfig = subtypeConfigBuilder.build();

		SUBTYPE_SCHEMA_GENERATOR = new SchemaGenerator(subtypeConfig);
	}

	private McpJsonSchemaGenerator() {
	}

	public static String generateForMethodInput(Method method) {
		Assert.notNull(method, "method cannot be null");
		return methodSchemaCache.computeIfAbsent(method, McpJsonSchemaGenerator::internalGenerateFromMethodArguments);
	}

	private static String internalGenerateFromMethodArguments(Method method) {
		// Check if method has CallToolRequest parameter
		boolean hasCallToolRequestParam = Arrays.stream(method.getParameterTypes())
			.anyMatch(type -> CallToolRequest.class.isAssignableFrom(type));

		// If method has CallToolRequest, return minimal schema unless there are other
		// non-infrastructure parameters alongside it.
		if (hasCallToolRequestParam) {
			boolean hasOtherParams = Arrays.stream(method.getParameters()).anyMatch(param -> {
				Class<?> type = param.getType();
				return !McpSyncRequestContext.class.isAssignableFrom(type)
						&& !McpAsyncRequestContext.class.isAssignableFrom(type)
						&& !CallToolRequest.class.isAssignableFrom(type)
						&& !McpSyncServerExchange.class.isAssignableFrom(type)
						&& !McpAsyncServerExchange.class.isAssignableFrom(type)
						&& !McpTransportContext.class.isAssignableFrom(type)
						&& !param.isAnnotationPresent(McpProgressToken.class) && !McpMeta.class.isAssignableFrom(type);
			});

			if (!hasOtherParams) {
				ObjectNode schema = JsonParser.getJsonMapper().createObjectNode();
				schema.put("type", "object");
				schema.putObject("properties");
				schema.putArray("required");
				return schema.toPrettyString();
			}
		}

		ObjectNode schema = JsonParser.getJsonMapper().createObjectNode();
		schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");
		List<String> required = new ArrayList<>();

		for (int i = 0; i < method.getParameterCount(); i++) {
			Parameter parameter = method.getParameters()[i];
			String parameterName = parameter.getName();
			Type parameterType = method.getGenericParameterTypes()[i];

			// Skip parameters annotated with @McpProgressToken
			if (parameter.isAnnotationPresent(McpProgressToken.class)) {
				continue;
			}

			// Skip McpMeta parameters
			if (parameterType instanceof Class<?> parameterClass && McpMeta.class.isAssignableFrom(parameterClass)) {
				continue;
			}

			// Skip MCP infrastructure parameter types
			if (parameterType instanceof Class<?> parameterClass
					&& (ClassUtils.isAssignable(McpSyncRequestContext.class, parameterClass)
							|| ClassUtils.isAssignable(McpAsyncRequestContext.class, parameterClass)
							|| ClassUtils.isAssignable(McpSyncServerExchange.class, parameterClass)
							|| ClassUtils.isAssignable(McpAsyncServerExchange.class, parameterClass)
							|| ClassUtils.isAssignable(McpTransportContext.class, parameterClass)
							|| ClassUtils.isAssignable(CallToolRequest.class, parameterClass))) {
				continue;
			}

			if (isMethodParameterRequired(method, i)) {
				required.add(parameterName);
			}
			ObjectNode parameterNode = SUBTYPE_SCHEMA_GENERATOR.generateSchema(parameterType);
			// victools generates self-contained schemas where $defs and the $ref
			// pointers into them are rooted at the sub-schema. Inlining the
			// sub-schema under properties.<paramName> re-parents existing
			// "#/$defs/<Name>" refs to the outer root, leaving them unresolvable.
			// Hoist $defs to the outer root so those refs resolve again.
			hoistDefsToRoot(schema, parameterNode);
			String parameterDescription = getMethodParameterDescription(method, i);
			if (Utils.hasText(parameterDescription)) {
				parameterNode.put("description", parameterDescription);
			}
			properties.set(parameterName, parameterNode);
		}

		var requiredArray = schema.putArray("required");
		required.forEach(requiredArray::add);

		return schema.toPrettyString();
	}

	/**
	 * Generate a JSON Schema for a class type. Delegates to
	 * {@link org.springframework.ai.util.json.schema.JsonSchemaGenerator#generateForType}.
	 * @param clazz the class to generate a schema for
	 * @return the JSON Schema as a string
	 */
	public static String generateFromClass(Class<?> clazz) {
		Assert.notNull(clazz, "clazz cannot be null");
		return typeSchemaCache.computeIfAbsent(clazz, McpJsonSchemaGenerator::internalGenerateFromType);
	}

	/**
	 * Generate a JSON Schema for a generic type. Delegates to
	 * {@link org.springframework.ai.util.json.schema.JsonSchemaGenerator#generateForType}.
	 * @param type the type to generate a schema for
	 * @return the JSON Schema as a string
	 */
	public static String generateFromType(Type type) {
		Assert.notNull(type, "type cannot be null");
		return typeSchemaCache.computeIfAbsent(type, McpJsonSchemaGenerator::internalGenerateFromType);
	}

	private static String internalGenerateFromType(Type type) {
		return org.springframework.ai.util.json.schema.JsonSchemaGenerator.generateForType(type,
				SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT);
	}

	/**
	 * Check if a method has a CallToolRequest parameter.
	 * @param method The method to check
	 * @return true if the method has a CallToolRequest parameter, false otherwise
	 */
	public static boolean hasCallToolRequestParameter(Method method) {
		return Arrays.stream(method.getParameterTypes()).anyMatch(type -> CallToolRequest.class.isAssignableFrom(type));
	}

	/**
	 * Moves any {@code $defs} block found on {@code subSchema} into the {@code $defs}
	 * block of {@code rootSchema}, creating one on the root if needed. On key collisions
	 * the existing root entry is reused when the two definitions are structurally equal;
	 * otherwise the incoming entry is renamed with a numeric suffix and every
	 * {@code "#/$defs/<oldKey>"} reference in the inlined sub-schema and in every
	 * definition inserted by this hoist call is rewritten to point at the new key.
	 */
	private static void hoistDefsToRoot(ObjectNode rootSchema, ObjectNode subSchema) {
		JsonNode nestedDefs = subSchema.remove("$defs");
		if (nestedDefs == null || !nestedDefs.isObject()) {
			return;
		}
		ObjectNode rootDefs = rootSchema.has("$defs") ? (ObjectNode) rootSchema.get("$defs")
				: rootSchema.putObject("$defs");
		Map<String, String> renames = new LinkedHashMap<>();
		List<String> insertedKeys = new ArrayList<>();
		((ObjectNode) nestedDefs).properties().forEach(entry -> {
			String key = entry.getKey();
			JsonNode value = entry.getValue();
			if (!rootDefs.has(key)) {
				rootDefs.set(key, value);
				insertedKeys.add(key);
				return;
			}
			if (rootDefs.get(key).equals(value)) {
				return;
			}
			String renamed = uniqueDefsKey(rootDefs, key);
			rootDefs.set(renamed, value);
			renames.put(key, renamed);
			insertedKeys.add(renamed);
		});
		if (renames.isEmpty()) {
			return;
		}
		rewriteDefsRefs(subSchema, renames);
		for (String insertedKey : insertedKeys) {
			rewriteDefsRefs(rootDefs.get(insertedKey), renames);
		}
	}

	private static String uniqueDefsKey(ObjectNode rootDefs, String base) {
		int suffix = 2;
		while (rootDefs.has(base + "_" + suffix)) {
			suffix++;
		}
		return base + "_" + suffix;
	}

	private static void rewriteDefsRefs(JsonNode node, Map<String, String> renames) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			ObjectNode object = (ObjectNode) node;
			JsonNode refNode = object.get("$ref");
			if (refNode != null && refNode.isTextual()) {
				String ref = refNode.asText();
				String prefix = "#/$defs/";
				if (ref.startsWith(prefix)) {
					String rest = ref.substring(prefix.length());
					int slash = rest.indexOf('/');
					String key = slash < 0 ? rest : rest.substring(0, slash);
					String renamed = renames.get(key);
					if (renamed != null) {
						object.put("$ref", prefix + renamed + (slash < 0 ? "" : rest.substring(slash)));
					}
				}
			}
			object.properties().forEach(entry -> rewriteDefsRefs(entry.getValue(), renames));
		}
		else if (node.isArray()) {
			node.forEach(child -> rewriteDefsRefs(child, renames));
		}
	}

	private static boolean isMethodParameterRequired(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
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

		if (Nullness.forParameter(parameter) == Nullness.NULLABLE) {
			return false;
		}

		return PROPERTY_REQUIRED_BY_DEFAULT;
	}

	private static @Nullable String getMethodParameterDescription(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
		if (toolParamAnnotation != null && Utils.hasText(toolParamAnnotation.description())) {
			return toolParamAnnotation.description();
		}

		var jacksonAnnotation = parameter.getAnnotation(JsonPropertyDescription.class);
		if (jacksonAnnotation != null && Utils.hasText(jacksonAnnotation.value())) {
			return jacksonAnnotation.value();
		}

		var schemaAnnotation = parameter.getAnnotation(Schema.class);
		if (schemaAnnotation != null && Utils.hasText(schemaAnnotation.description())) {
			return schemaAnnotation.description();
		}

		return null;
	}

}
