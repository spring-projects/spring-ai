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
 * Container annotation for multiple {@link ToolCommonDescription} annotations. This
 * allows defining multiple reusable description fragments within a class.
 *
 * <p>
 * Usage example: <pre>{@code
 * &#64;ToolCommonDescriptions({
 *     &#64;ToolCommonDescription(key = "dateTimeFormats",
 *                             description = "Supported formats: yyyy-MM-dd, dd/MM/yyyy, etc. Timezone: UTC, America/New_York, etc."),
 *     &#64;ToolCommonDescription(key = "validationRules",
 *                             description = "Input validation: non-null, non-empty, valid format required")
 * })
 * public class TimeTools {
 *     // tools can reference these common descriptions
 * }
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolCommonDescriptions {

	/**
	 * Array of {@link ToolCommonDescription} annotations defining reusable description
	 * fragments.
	 */
	ToolCommonDescription[] value();

}
