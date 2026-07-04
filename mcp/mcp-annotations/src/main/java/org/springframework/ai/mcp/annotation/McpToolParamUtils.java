/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.annotation;

import java.lang.reflect.Parameter;

import com.github.victools.jsonschema.generator.MemberScope;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * Utilities for resolving external MCP tool argument names from {@link McpToolParam}.
 */
public abstract class McpToolParamUtils {

	private McpToolParamUtils() {
	}

	public static String resolveExternalName(Parameter parameter) {
		McpToolParam annotation = parameter.getAnnotation(McpToolParam.class);
		if (annotation != null && StringUtils.hasText(annotation.name())) {
			return annotation.name();
		}
		return parameter.getName();
	}

	public static @Nullable String resolveExternalPropertyName(MemberScope<?, ?> member) {
		McpToolParam annotation = member.getAnnotationConsideringFieldAndGetter(McpToolParam.class);
		if (annotation != null && StringUtils.hasText(annotation.name())) {
			return annotation.name();
		}
		return null;
	}

}
