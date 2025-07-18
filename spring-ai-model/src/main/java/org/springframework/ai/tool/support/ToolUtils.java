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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.util.ParsingUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Miscellaneous tool utility methods. Mainly for internal use within the framework.
 *
 * @author Thomas Vitale
 */
public final class ToolUtils {

	/**
	 * Regular expression pattern for valid tool names. Tool names can only contain
	 * alphanumeric characters, underscores, hyphens, and dots.
	 */
	private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.-]+$");

	private ToolUtils() {
	}

	public static String getToolName(Method method) {
		Assert.notNull(method, "method cannot be null");
		var tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
		String toolName;
		if (tool == null) {
			toolName = method.getName();
		}
		else {
			toolName = StringUtils.hasText(tool.name()) ? tool.name() : method.getName();
		}
		validateToolName(toolName);
		return toolName;
	}

	public static String getToolDescriptionFromName(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");
		return ParsingUtils.reConcatenateCamelCase(toolName, " ");
	}

	public static String getToolDescription(Method method) {
		Assert.notNull(method, "method cannot be null");
		var tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
		if (tool == null) {
			return ParsingUtils.reConcatenateCamelCase(method.getName(), " ");
		}
		return StringUtils.hasText(tool.description()) ? tool.description() : method.getName();
	}

	public static boolean getToolReturnDirect(Method method) {
		Assert.notNull(method, "method cannot be null");
		var tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
		return tool != null && tool.returnDirect();
	}

	public static ToolCallResultConverter getToolCallResultConverter(Method method) {
		Assert.notNull(method, "method cannot be null");
		var tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
		if (tool == null) {
			return new DefaultToolCallResultConverter();
		}
		var type = tool.resultConverter();
		try {
			return type.getDeclaredConstructor().newInstance();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to instantiate ToolCallResultConverter: " + type, e);
		}
	}

	public static List<String> getDuplicateToolNames(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		return toolCallbacks.stream()
			.collect(Collectors.groupingBy(toolCallback -> toolCallback.getToolDefinition().name(),
					Collectors.counting()))
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue() > 1)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	public static List<String> getDuplicateToolNames(ToolCallback... toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		return getDuplicateToolNames(Arrays.asList(toolCallbacks));
	}

	/**
	 * Validates that a tool name conforms to the required pattern. Tool names can only
	 * contain alphanumeric characters, underscores, hyphens, and dots.
	 * @param toolName the tool name to validate
	 * @throws IllegalArgumentException if the tool name contains invalid characters
	 */
	private static void validateToolName(String toolName) {
		Assert.hasText(toolName, "Tool name cannot be null or empty");
		if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
			throw new IllegalArgumentException(
					"Tool name '%s' contains invalid characters. Tool names can only contain alphanumeric characters, underscores, hyphens, and dots."
						.formatted(toolName));
		}
	}

}
