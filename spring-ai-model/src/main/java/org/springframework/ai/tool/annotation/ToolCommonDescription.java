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
 * Defines a reusable description fragment that can be referenced by multiple tools. This
 * helps reduce token usage by avoiding duplication of common descriptions.
 *
 * <p>
 * This annotation is used within {@link ToolCommonDescriptions} to define reusable
 * description fragments that multiple tools can reference, helping to reduce input length
 * when sending tool definitions to language models.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolCommonDescription {

	/**
	 * The unique key to identify this common description within the class. This key will
	 * be referenced by tools that want to include this description.
	 */
	String key();

	/**
	 * The actual description text that will be shared.
	 */
	String description();

}
