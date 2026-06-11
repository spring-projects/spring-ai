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
 * Content Security Policy configuration for MCP App tools (SEP-1865).
 *
 * <p>
 * Declares which external domains the MCP App iframe is allowed to access. All external
 * network access must be declared — missing declarations result in silently blocked
 * requests.
 *
 * <p>
 * Domain entries must be full origins including the scheme, for example
 * {@code "https://api.example.com"}.
 *
 * <p>
 * On the wire, these are serialized into {@code _meta.ui.csp} as:
 *
 * <pre>
 * {
 *   "csp": {
 *     "connectDomains": ["https://api.example.com"],
 *     "resourceDomains": ["https://cdn.example.com"],
 *     "redirectDomains": ["https://auth.example.com"]
 *   }
 * }
 * </pre>
 *
 * @author Alexandros Pappas
 * @see McpTool#csp()
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpCsp {

	/**
	 * Domains the iframe can make API calls to (fetch, XHR, WebSocket).
	 */
	String[] connectDomains() default {};

	/**
	 * Domains the iframe can load static assets from (scripts, images, stylesheets).
	 */
	String[] resourceDomains() default {};

	/**
	 * Domains the iframe can navigate or redirect to.
	 */
	String[] redirectDomains() default {};

}
