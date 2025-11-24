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

package org.springframework.ai.tool.execution;

import java.lang.reflect.Type;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.Nullable;

/**
 * An exception thrown when a tool parameter conversion fails, typically when the model
 * provides a value that cannot be converted to the expected parameter type.
 *
 * <p>
 * This exception provides detailed context about the conversion failure including:
 * <ul>
 * <li>The parameter name that failed conversion</li>
 * <li>The expected parameter type</li>
 * <li>The actual value provided by the model</li>
 * <li>Helpful suggestions for fixing the issue</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 1.1.0
 */
public class ToolParameterConversionException extends ToolExecutionException {

	@Nullable
	private final String parameterName;

	private final Type expectedType;

	@Nullable
	private final Object actualValue;

	/**
	 * Creates a new ToolParameterConversionException with detailed parameter context.
	 * @param toolDefinition the tool definition where the conversion failed
	 * @param parameterName the name of the parameter that failed conversion (may be null
	 * if not available)
	 * @param expectedType the expected parameter type
	 * @param actualValue the actual value provided by the model that failed conversion
	 * @param cause the underlying exception that caused the conversion failure
	 */
	public ToolParameterConversionException(ToolDefinition toolDefinition, @Nullable String parameterName,
			Type expectedType, @Nullable Object actualValue, Throwable cause) {
		super(toolDefinition, new RuntimeException(
				buildMessage(toolDefinition, parameterName, expectedType, actualValue, cause), cause));
		this.parameterName = parameterName;
		this.expectedType = expectedType;
		this.actualValue = actualValue;
	}

	/**
	 * Creates a new ToolParameterConversionException without parameter name context.
	 * @param toolDefinition the tool definition where the conversion failed
	 * @param expectedType the expected parameter type
	 * @param actualValue the actual value provided by the model that failed conversion
	 * @param cause the underlying exception that caused the conversion failure
	 */
	public ToolParameterConversionException(ToolDefinition toolDefinition, Type expectedType,
			@Nullable Object actualValue, Throwable cause) {
		this(toolDefinition, null, expectedType, actualValue, cause);
	}

	private static String buildMessage(ToolDefinition toolDefinition, @Nullable String parameterName, Type expectedType,
			@Nullable Object actualValue, Throwable cause) {

		StringBuilder message = new StringBuilder("Tool parameter conversion failed");

		if (parameterName != null) {
			message.append(" for parameter '").append(parameterName).append("'");
		}

		message.append(" in tool '").append(toolDefinition.name()).append("': ");

		String typeName = expectedType instanceof Class<?> ? ((Class<?>) expectedType).getSimpleName()
				: expectedType.getTypeName();
		message.append("Expected type: ").append(typeName);

		if (actualValue != null) {
			if (actualValue instanceof String && ((String) actualValue).isEmpty()) {
				message.append(", but received: \"\" (empty string)");
			}
			else {
				String valueStr = actualValue.toString();
				if (valueStr.length() > 50) {
					valueStr = valueStr.substring(0, 47) + "...";
				}
				message.append(", but received: \"").append(valueStr).append("\"");
				message.append(" (").append(actualValue.getClass().getSimpleName()).append(")");
			}
		}
		else {
			message.append(", but received: null");
		}

		// Add helpful suggestions
		message.append(". ");
		if (isNumericType(expectedType) && actualValue instanceof String && ((String) actualValue).isEmpty()) {
			message.append(
					"Suggestion: Ensure your prompt clearly specifies that numeric parameters should contain valid numbers, not empty strings. ");
			message.append(
					"Consider making the parameter optional or providing a default value in your tool description.");
		}
		else if (isNumericType(expectedType)) {
			message.append("Suggestion: Verify that the model is providing numeric values for numeric parameters. ");
			message.append("Review your tool description and prompt to ensure clarity about expected parameter types.");
		}
		else {
			message.append(
					"Suggestion: Review your tool description and prompt to ensure the model provides values compatible with the expected parameter type.");
		}

		if (cause != null && cause.getMessage() != null) {
			message.append(" Original error: ").append(cause.getMessage());
		}

		return message.toString();
	}

	private static boolean isNumericType(Type type) {
		if (type instanceof Class<?>) {
			Class<?> clazz = (Class<?>) type;
			return clazz == Byte.class || clazz == byte.class || clazz == Short.class || clazz == short.class
					|| clazz == Integer.class || clazz == int.class || clazz == Long.class || clazz == long.class
					|| clazz == Float.class || clazz == float.class || clazz == Double.class || clazz == double.class;
		}
		return false;
	}

	/**
	 * Returns the name of the parameter that failed conversion.
	 * @return the parameter name, or null if not available
	 */
	@Nullable
	public String getParameterName() {
		return this.parameterName;
	}

	/**
	 * Returns the expected parameter type.
	 * @return the expected type
	 */
	public Type getExpectedType() {
		return this.expectedType;
	}

	/**
	 * Returns the actual value provided by the model that failed conversion.
	 * @return the actual value, or null if the value was null
	 */
	@Nullable
	public Object getActualValue() {
		return this.actualValue;
	}

}