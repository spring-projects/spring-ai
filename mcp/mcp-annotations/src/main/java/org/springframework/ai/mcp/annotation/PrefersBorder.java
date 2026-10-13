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

/**
 * The visual boundary preference of an MCP App UI resource per SEP-1865.
 *
 * <p>
 * Controls whether a visible border and background is provided by the host. Specifying an
 * explicit value for this is recommended because hosts' defaults may vary.
 *
 * <p>
 * On the wire, the preference is serialized as the boolean
 * {@code _meta.ui.prefersBorder}. This enum stands in for that optional boolean because
 * annotation attributes cannot be {@code null}: {@link #UNSET} omits the field.
 *
 * @author Alexandros Pappas
 * @see McpResource#prefersBorder()
 */
public enum PrefersBorder {

	/**
	 * No preference: {@code prefersBorder} is omitted and the host decides the border.
	 */
	UNSET,

	/**
	 * Request visible border and background: {@code prefersBorder} is {@code true}.
	 */
	TRUE,

	/**
	 * Request no visible border and background: {@code prefersBorder} is {@code false}.
	 */
	FALSE

}
