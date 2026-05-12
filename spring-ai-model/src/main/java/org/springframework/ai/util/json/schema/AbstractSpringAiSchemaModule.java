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

import org.springframework.core.KotlinDetector;
import org.springframework.core.Nullness;

/**
 * Abstract base for JSON Schema Generator Modules in Spring AI.
 * <p>
 * Provides shared logic for description resolution and required-field determination,
 * delegating annotation-specific lookups to subclasses via
 * {@link #resolveToolParamDescription} and {@link #resolveToolParamRequired}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AbstractSpringAiSchemaModule implements Module {

	private final boolean requiredByDefault;

	protected AbstractSpringAiSchemaModule(Option... options) {
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
	 * Extract the description from the tool-param annotation for the given member, or
	 * return {@code null} if the annotation is absent or its description is blank.
	 */
	protected abstract @Nullable String resolveToolParamDescription(MemberScope<?, ?> member);

	/**
	 * Extract the required flag from the tool-param annotation for the given member, or
	 * return {@code null} if the annotation is absent.
	 */
	protected abstract @Nullable Boolean resolveToolParamRequired(MemberScope<?, ?> member);

	private @Nullable String resolveDescription(MemberScope<?, ?> member) {
		return resolveToolParamDescription(member);
	}

	/**
	 * Determines whether a property is required based on the presence of a series of
	 * annotations.
	 * <p>
	 * <ul>
	 * <li>tool-param annotation ({@code required = ...})</li>
	 * <li>{@code @JsonProperty(required = ...)}</li>
	 * <li>{@code @Schema(required = ...)}</li>
	 * <li>{@code @Nullable}</li>
	 * </ul>
	 * <p>
	 * If none of these annotations are present, the default behavior is to consider the
	 * property as required, unless the {@link Option#PROPERTY_REQUIRED_FALSE_BY_DEFAULT}
	 * option is set.
	 */
	@SuppressWarnings("deprecation") // Schema.required() kept for backwards compatibility
										// with pre-requiredMode usages
	private boolean checkRequired(MemberScope<?, ?> member) {
		Boolean toolParamRequired = resolveToolParamRequired(member);
		if (toolParamRequired != null) {
			return toolParamRequired;
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
		if (KotlinDetector.isKotlinReflectPresent()
				&& KotlinDetector.isKotlinType(member.getDeclaringType().getErasedType())) {
			// Defer to KotlinModule for additional checks like default values
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
