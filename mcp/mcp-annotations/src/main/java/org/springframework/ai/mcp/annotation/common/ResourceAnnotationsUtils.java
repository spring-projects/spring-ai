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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.Method;
import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpResource.McpAnnotations;

/**
 * Utility for converting the nested {@link McpAnnotations} value on a {@link McpResource}
 * declaration into the wire-level {@link McpSchema.Annotations} record.
 *
 * <p>
 * Java annotation elements can never be {@code null}, so the compiler always materializes
 * the declared default. This means every {@code @McpResource} instance appears to carry
 * an "annotations" value regardless of whether the user actually set one. To avoid
 * publishing that spurious default to MCP clients, this helper first compares the runtime
 * value against the declared default of {@code McpResource#annotations()} — obtained via
 * reflection so no default constants have to be duplicated — and returns {@code null}
 * whenever they are equal (annotations use structural equality). Any user-supplied value
 * is copied verbatim, including {@code audience}, {@code priority} and
 * {@code lastModified}.
 * </p>
 *
 * @author Shiyang Chen
 */
public final class ResourceAnnotationsUtils {

	private static final McpAnnotations DEFAULT_ANNOTATIONS = resolveDefaultAnnotations();

	private ResourceAnnotationsUtils() {
	}

	/**
	 * Convert a {@link McpAnnotations} value declared on a {@link McpResource} into the
	 * corresponding {@link McpSchema.Annotations} payload.
	 * @param annotations the annotation value as returned by
	 * {@link McpResource#annotations()}. May be {@code null} for defensive use, in which
	 * case {@code null} is returned.
	 * @return the converted schema annotations, or {@code null} if {@code annotations} is
	 * {@code null} or equal to the declared default.
	 */
	public static McpSchema.Annotations toSchemaAnnotations(McpAnnotations annotations) {
		if (annotations == null || annotations.equals(DEFAULT_ANNOTATIONS)) {
			return null;
		}
		return McpSchema.Annotations.builder()
			.audience(List.of(annotations.audience()))
			.priority(annotations.priority())
			.lastModified(annotations.lastModified())
			.build();
	}

	private static McpAnnotations resolveDefaultAnnotations() {
		try {
			Method annotationsMethod = McpResource.class.getDeclaredMethod("annotations");
			Object defaultValue = annotationsMethod.getDefaultValue();
			if (defaultValue instanceof McpAnnotations mcpAnnotations) {
				return mcpAnnotations;
			}
			return null;
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException("McpResource#annotations() is expected to be defined", ex);
		}
	}

}
