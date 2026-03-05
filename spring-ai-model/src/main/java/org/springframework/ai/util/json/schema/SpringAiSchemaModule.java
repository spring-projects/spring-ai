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

package org.springframework.ai.util.json.schema;

import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.MethodScope;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigPart;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.Nullness;
import org.springframework.util.StringUtils;

/**
 * JSON Schema Generator Module for Spring AI.
 * <p>
 * This module provides a set of customizations to the JSON Schema generator to support
 * the Spring AI framework. It allows to extract descriptions from
 * {@code @ToolParam(description = ...)} annotations and to determine whether a property
 * is required based on the presence of a series of annotations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class SpringAiSchemaModule implements Module {

	private final boolean requiredByDefault;

	public SpringAiSchemaModule(Option... options) {
		this.requiredByDefault = Stream.of(options)
			.noneMatch(option -> option == Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);
	}

	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		this.applyToConfigBuilder(builder.forFields());
	}

	private void applyToConfigBuilder(SchemaGeneratorConfigPart<FieldScope> configPart) {
		configPart.withDescriptionResolver(this::resolveDescription);
		configPart.withRequiredCheck(this::checkRequired);
	}

	/**
	 * Extract description from {@code @ToolParam(description = ...)} for the given field.
	 */
	private @Nullable String resolveDescription(MemberScope<?, ?> member) {
		var toolParamAnnotation = member.getAnnotationConsideringFieldAndGetter(ToolParam.class);
		if (toolParamAnnotation != null && StringUtils.hasText(toolParamAnnotation.description())) {
			return toolParamAnnotation.description();
		}
		return null;
	}

	/**
	 * Determines whether a property is required based on the presence of a series of
	 * annotations.
	 * <p>
	 * <ul>
	 * <li>{@code @ToolParam(required = ...)}</li>
	 * <li>{@code @JsonProperty(required = ...)}</li>
	 * <li>{@code @Schema(required = ...)}</li>
	 * <li>{@code @Nullable}</li>
	 * </ul>
	 * <p>
	 * If none of these annotations are present, the default behavior is to consider the
	 * property as required, unless the {@link Option#PROPERTY_REQUIRED_FALSE_BY_DEFAULT}
	 * option is set.
	 */
	private boolean checkRequired(MemberScope<?, ?> member) {
		var toolParamAnnotation = member.getAnnotationConsideringFieldAndGetter(ToolParam.class);
		if (toolParamAnnotation != null) {
			return toolParamAnnotation.required();
		}

		var propertyAnnotation = member.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
		if (propertyAnnotation != null) {
			return propertyAnnotation.required();
		}

		var schemaAnnotation = member.getAnnotationConsideringFieldAndGetter(Schema.class);
		if (schemaAnnotation != null) {
			return schemaAnnotation.requiredMode() == Schema.RequiredMode.REQUIRED
					|| schemaAnnotation.requiredMode() == Schema.RequiredMode.AUTO || schemaAnnotation.required();
		}

		Nullness nullness;
		if (member instanceof FieldScope fs) {
			nullness = Nullness.forField(fs.getRawMember());
		}
		else if (member instanceof MethodScope ms) {
			nullness = Nullness.forMethodReturnType(ms.getRawMember());
		}
		else {
			throw new IllegalStateException("Unsupported member type: " + member);
		}
		if (nullness == Nullness.NULLABLE) {
			return false;
		}

		return this.requiredByDefault;
	}

	/**
	 * Options for customizing the behavior of the module.
	 */
	public enum Option {

		/**
		 * Properties are only required if marked as such via one of the supported
		 * annotations.
		 */
		PROPERTY_REQUIRED_FALSE_BY_DEFAULT

	}

}
