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

import com.github.victools.jsonschema.generator.MemberScope;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.annotation.ToolParam;
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
public final class SpringAiSchemaModule extends AbstractSpringAiSchemaModule {

	public SpringAiSchemaModule(Option... options) {
		super(options);
	}

	@Override
	protected @Nullable String resolveToolParamDescription(MemberScope<?, ?> member) {
		var annotation = member.getAnnotationConsideringFieldAndGetter(ToolParam.class);
		if (annotation != null && StringUtils.hasText(annotation.description())) {
			return annotation.description();
		}
		return null;
	}

	@Override
	protected @Nullable Boolean resolveToolParamRequired(MemberScope<?, ?> member) {
		var annotation = member.getAnnotationConsideringFieldAndGetter(ToolParam.class);
		return annotation != null ? annotation.required() : null;
	}

}
