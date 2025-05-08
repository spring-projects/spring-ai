/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.tool.support;

import java.lang.reflect.Method;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.util.Assert;

/**
 * Utility class for creating {@link ToolDefinition} builders and instances from Java
 * {@link Method} objects.
 * <p>
 * This class provides static methods to facilitate the construction of
 * {@link ToolDefinition} objects by extracting relevant metadata from Java reflection
 * {@link Method} instances.
 * </p>
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
public class ToolDefinitions {

	/**
	 * Create a default {@link ToolDefinition} builder from a {@link Method}.
	 */
	public static DefaultToolDefinition.Builder builder(Method method) {
		Assert.notNull(method, "method cannot be null");
		return DefaultToolDefinition.builder()
			.name(ToolUtils.getToolName(method))
			.description(ToolUtils.getToolDescription(method))
			.inputSchema(JsonSchemaGenerator.generateForMethodInput(method));
	}

	/**
	 * Create a default {@link ToolDefinition} instance from a {@link Method}.
	 */
	public static ToolDefinition from(Method method) {
		return builder(method).build();
	}

}
