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

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Christian Tzolov
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolParam {

	/**
	 * Whether the tool argument is required.
	 */
	boolean required() default true;

	/**
	 * The description of the tool argument.
	 */
	String description() default "";

	/**
	 * Whether Streamable HTTP clients must also send this parameter as the
	 * {@code Mcp-Param-<name>} header (the {@code x-mcp-header} schema keyword of the
	 * 2026-07-28 revision). Only parameters whose schema type is string, integer or
	 * boolean may be mirrored; other types are rejected at startup. Read on method
	 * parameters only: on a field of a parameter type the flag has no effect.
	 * @since 2.1.0
	 */
	boolean header() default false;

}
