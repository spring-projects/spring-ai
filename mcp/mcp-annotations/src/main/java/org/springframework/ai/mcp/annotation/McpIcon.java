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
 * An icon of a tool, resource or prompt, published in its definition so that clients can
 * render it. Used inside {@link McpTool#icons()}, {@link McpResource#icons()} and
 * {@link McpPrompt#icons()}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpIcon {

	/**
	 * The icon location: an {@code https://} URL or a {@code data:} URI.
	 */
	String src();

	/**
	 * Optional MIME type, for example {@code image/png} or {@code image/svg+xml}.
	 */
	String mimeType() default "";

	/**
	 * Optional size hints such as {@code "48x48"}, or {@code "any"} for scalable icons.
	 */
	String[] sizes() default {};

	/**
	 * Optional theme the icon is designed for: {@code "light"} or {@code "dark"}.
	 */
	String theme() default "";

}
