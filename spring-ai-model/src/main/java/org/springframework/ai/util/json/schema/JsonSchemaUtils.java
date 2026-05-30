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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.model.KotlinModule;
import org.springframework.ai.util.JsonHelper;
import org.springframework.core.KotlinDetector;
import org.springframework.util.StringUtils;

/**
 * Utility methods for working with JSON schemas.
 *
 * @author Guangdong Liu
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public final class JsonSchemaUtils {

	private static final JsonHelper jsonHelper = new JsonHelper();

	private static final AtomicReference<@Nullable SchemaGenerator> SCHEMA_GENERATOR_CACHE = new AtomicReference<>();

	private JsonSchemaUtils() {
	}

	private static ObjectNode generateSchema(SchemaGenerator generator, Type inputType) {
		synchronized (generator) {
			return generator.generateSchema(inputType);
		}
	}

	/**
	 * Moves any {@code $defs} block found on {@code subSchema} into the {@code $defs}
	 * block of {@code rootSchema}, creating one on the root if needed. On key collisions
	 * the existing root entry is reused when the two definitions are structurally equal;
	 * otherwise the incoming entry is renamed with a numeric suffix and every
	 * {@code "#/$defs/<oldKey>"} reference inside the inlined sub-schema and inside every
	 * definition inserted by this call is rewritten to point at the new key.
	 * <p>
	 * This is needed because victools generates self-contained schemas where
	 * {@code $defs} and the {@code $ref} pointers into them are rooted at the sub-schema.
	 * Inlining the sub-schema under {@code properties.<paramName>} re-parents existing
	 * {@code "#/$defs/<Name>"} refs to the outer root, leaving them unresolvable unless
	 * {@code $defs} is hoisted first.
	 * @param rootSchema the wrapper schema that will receive the hoisted definitions
	 * @param subSchema the per-parameter sub-schema whose {@code $defs} block is consumed
	 */
	public static void hoistDefsToRoot(ObjectNode rootSchema, ObjectNode subSchema) {
		JsonNode nestedDefs = subSchema.remove("$defs");
		if (nestedDefs == null || !nestedDefs.isObject()) {
			return;
		}
		ObjectNode rootDefs = rootSchema.has("$defs") ? (ObjectNode) rootSchema.get("$defs")
				: rootSchema.putObject("$defs");
		// Collect all keys from this batch upfront so uniqueDefsKey can avoid claiming
		// a suffix that another pending entry in the same batch already occupies.
		Set<String> batchKeys = new HashSet<>();
		((ObjectNode) nestedDefs).properties().forEach(e -> batchKeys.add(e.getKey()));
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
			String renamed = uniqueDefsKey(rootDefs, key, batchKeys);
			rootDefs.set(renamed, value);
			renames.put(key, renamed);
			insertedKeys.add(renamed);
		});
		if (renames.isEmpty()) {
			return;
		}
		rewriteDefsRefs(subSchema, renames);
		// Only rewrite $refs inside the definitions inserted by THIS call. Entries added
		// to rootDefs by earlier hoistDefsToRoot calls have already had their own renames
		// applied; rewriting them again with the current batch's rename map would corrupt
		// any $refs that legitimately point at the original (pre-rename) keys.
		for (String insertedKey : insertedKeys) {
			rewriteDefsRefs(rootDefs.get(insertedKey), renames);
		}
	}

	private static String uniqueDefsKey(ObjectNode rootDefs, String base, Set<String> batchKeys) {
		int suffix = 2;
		String candidate;
		do {
			candidate = base + "_" + suffix++;
		}
		while (rootDefs.has(candidate) || batchKeys.contains(candidate));
		return candidate;
	}

	private static void rewriteDefsRefs(@Nullable JsonNode node, Map<String, String> renames) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			ObjectNode object = (ObjectNode) node;
			JsonNode refNode = object.get("$ref");
			if (refNode != null && refNode.isString()) {
				String ref = refNode.asString();
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
			object.properties().forEach(e -> rewriteDefsRefs(e.getValue(), renames));
		}
		else if (node.isArray()) {
			node.forEach(child -> rewriteDefsRefs(child, renames));
		}
	}

	/**
	 * Ensures that the input schema is valid for AI model APIs. Many AI models require
	 * that the parameters object must have a "properties" field, even if it's empty. This
	 * method normalizes schemas from external sources (like MCP tools) that may not
	 * include this field.
	 * @param inputSchema the input schema as a JSON string
	 * @return a valid input schema as a JSON string with required fields
	 */
	public static String ensureValidInputSchema(String inputSchema) {
		if (!StringUtils.hasText(inputSchema)) {
			return inputSchema;
		}

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(inputSchema);

		if (schemaMap.isEmpty()) {
			// Create a minimal valid schema
			schemaMap = new java.util.HashMap<>();
			schemaMap.put("type", "object");
			schemaMap.put("properties", new java.util.HashMap<>());
			return jsonHelper.toJson(schemaMap);
		}

		// Ensure "type" field exists
		if (!schemaMap.containsKey("type")) {
			schemaMap.put("type", "object");
		}

		// Ensure "properties" field exists for object types
		if ("object".equals(schemaMap.get("type")) && !schemaMap.containsKey("properties")) {
			schemaMap.put("properties", new java.util.HashMap<>());
		}

		return jsonHelper.toJson(schemaMap);
	}

	/**
	 * Generates JSON Schema (version 2020_12) for the given class.
	 * @param inputType the input {@link Type} to generate JSON Schema from.
	 * @return the generated JSON Schema as a String.
	 * @since 2.0.0
	 */
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

		SchemaGenerator schemaGenerator = SCHEMA_GENERATOR_CACHE.get();
		if (schemaGenerator == null) {
			throw new IllegalStateException("JSON Schema generator has not been initialized");
		}
		ObjectNode node = generateSchema(schemaGenerator, inputType);

		if ((inputType == Void.class) && !node.has("properties")) {
			node.putObject("properties");
		}

		return node;
	}

}
