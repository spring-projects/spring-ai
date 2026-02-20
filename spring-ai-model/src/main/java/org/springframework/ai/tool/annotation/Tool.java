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

package org.springframework.ai.tool.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * Marks a method as a tool in Spring AI.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

	/**
	 * The name of the tool. If not provided, the method name will be used.
	 * <p>
	 * For maximum compatibility across different LLMs, it is recommended to use only
	 * alphanumeric characters, underscores, hyphens, and dots in tool names. Using spaces
	 * or special characters may cause issues with some LLMs (e.g., OpenAI).
	 * </p>
	 * <p>
	 * Examples of recommended names: "get_weather", "search-docs", "tool.v1"
	 * </p>
	 * <p>
	 * Examples of names that may cause compatibility issues: "get weather" (contains
	 * space), "tool()" (contains parentheses)
	 * </p>
	 */
	String name() default "";

	/**
	 * The description of the tool. If not provided, the method name will be used.
	 */
	String description() default "";

	/**
	 * Whether the tool result should be returned directly or passed back to the model.
	 */
	boolean returnDirect() default false;

	/**
	 * Whether, in streaming mode and when internal tool execution is enabled, the model
	 * should continue its response after the tool execution result is sent back.
	 * <p>
	 * When enabled, the already-streamed assistant content is provided as prefix to the
	 * assistant tool-call message so that the model can continue instead of starting a
	 * new answer.
	 */
	boolean continuousStream() default false;

	/**
	 * The class to use to convert the tool call result to a String.
	 */
	Class<? extends ToolCallResultConverter> resultConverter() default DefaultToolCallResultConverter.class;

}
