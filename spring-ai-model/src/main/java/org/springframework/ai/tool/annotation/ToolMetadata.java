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

/**
 * Annotation to add metadata to a tool method. Can be used in conjunction with
 * {@link Tool} annotation. Metadata can be used for filtering, categorization, and other
 * purposes when managing large sets of tools.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre class="code">
 * &#64;Tool(description = "Analyzes real-time market data")
 * &#64;ToolMetadata(type = "RealTimeAnalysis", category = "market", priority = 8)
 * public String analyzeMarketData(String symbol) {
 *     // implementation
 * }
 * </pre>
 *
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolMetadata {

	/**
	 * Additional metadata entries in key=value format. Example:
	 * {"environment=production", "version=1.0"}
	 * @return array of metadata key-value pairs
	 */
	String[] value() default {};

	/**
	 * Tool category for classification purposes. Can be used to group related tools
	 * together.
	 * @return the category name
	 */
	String category() default "";

	/**
	 * Tool type for filtering purposes. Useful for distinguishing between different kinds
	 * of operations (e.g., "RealTimeAnalysis", "HistoricalAnalysis").
	 * @return the type identifier
	 */
	String type() default "";

	/**
	 * Priority level for tool selection (1-10, where 10 is highest priority). Can be used
	 * to influence tool selection when multiple tools are available.
	 * @return the priority level
	 */
	int priority() default 5;

	/**
	 * Tags associated with the tool. Useful for flexible categorization and filtering.
	 * @return array of tags
	 */
	String[] tags() default {};

}
