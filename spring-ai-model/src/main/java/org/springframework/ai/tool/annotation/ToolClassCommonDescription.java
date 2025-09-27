/*
 * Copyright 2025-2025 the original author or authors.
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
 * Annotation that defines a common description shared by all tools in a class. This helps
 * reduce duplication when multiple tools have common parameter descriptions or
 * constraints, which can help stay within input length limits for language models.
 *
 * <p>
 * Usage example: <pre>{@code
 * &#64;ToolClassCommonDescription("Supported formats are 'yyyy-MM-dd', 'dd/MM/yyyy', etc. " +
 *                              "Timezone: UTC, America/New_York, etc.")
 * public class TimeTools {
 *
 *     &#64;Tool(description = "get current time")
 *     public Response getCurrentTime(&#64;ToolParam(description = "the expected output") String format) { ... }
 *
 *     &#64;Tool(description = "format timestamp")
 *     public Response formatTimestamp(&#64;ToolParam(description = "the expected output") String format) { ... }
 * }
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolClassCommonDescription {

	/**
	 * The common description text that will be shared by all tools in this class. This
	 * description will be prepended to each tool's individual description.
	 */
	String value();

}
