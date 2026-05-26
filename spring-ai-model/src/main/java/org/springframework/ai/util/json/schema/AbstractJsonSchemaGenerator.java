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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.core.Nullness;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for JSON Schema generators in Spring AI.
 * <p>
 * Provides shared logic for determining whether a method parameter is required and for
 * resolving its description, delegating annotation-specific lookups to subclasses via
 * {@link #getToolAnnotationRequired} and {@link #getToolAnnotationDescription}.
 * <p>
 * Also provides shared static utilities for post-processing generated schemas.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.0.0
 */
public abstract class AbstractJsonSchemaGenerator {

	protected static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

	/**
	 * Returns the required value from the tool-specific annotation on the given
	 * parameter, or {@code null} if no such annotation is present.
	 */
	protected abstract @Nullable Boolean getToolAnnotationRequired(Parameter parameter);

	/**
	 * Returns the description from the tool-specific annotation on the given parameter,
	 * or {@code null} if no such annotation is present or the description is blank.
	 */
	protected abstract @Nullable String getToolAnnotationDescription(Parameter parameter);

	/**
	 * Determines whether a method parameter is required based on the presence of a series
	 * of annotations.
	 * <p>
	 * <ul>
	 * <li>tool-param annotation ({@code required = ...})</li>
	 * <li>{@code @JsonProperty(required = ...)}</li>
	 * <li>{@code @Schema(required = ...)}</li>
	 * <li>{@code @Nullable}</li>
	 * </ul>
	 * <p>
	 * If none of these annotations are present, the default behavior is to consider the
	 * parameter as required.
	 */
	@SuppressWarnings("deprecation")
	protected boolean isMethodParameterRequired(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		Boolean toolRequired = getToolAnnotationRequired(parameter);
		if (toolRequired != null) {
			return toolRequired;
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
	 * Determines a method parameter's description based on the presence of a series of
	 * annotations.
	 * <p>
	 * <ul>
	 * <li>tool-param annotation ({@code description = ...})</li>
	 * <li>{@code @JsonPropertyDescription(...)}</li>
	 * <li>{@code @Schema(description = ...)}</li>
	 * </ul>
	 */
	protected @Nullable String getMethodParameterDescription(Method method, int index) {
		Parameter parameter = method.getParameters()[index];

		String toolDescription = getToolAnnotationDescription(parameter);
		if (toolDescription != null) {
			return toolDescription;
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
	 * Recursively adds {@code "additionalProperties": false} to all object schemas (nodes
	 * with a {@code "properties"} key) that do not already define
	 * {@code "additionalProperties"}. The guard preserves {@code Map<K,V>} schemas where
	 * {@code "additionalProperties"} is a type reference rather than a boolean.
	 */
	protected static void forbidAdditionalProperties(ObjectNode node) {
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
					String oldValue = node.get("type").stringValue();
					node.put("type", oldValue.toUpperCase());
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

}
