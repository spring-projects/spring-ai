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
 * Content Security Policy configuration for an MCP App UI resource (SEP-1865).
 *
 * <p>
 * Declares which external origins the MCP App iframe is allowed to access. The host
 * builds the iframe's Content Security Policy from these declarations. Per the
 * specification, hosts MUST NOT allow undeclared origins. Hosts SHOULD warn users when
 * the UI requires external domain access and SHOULD log CSP configurations for security
 * review.
 *
 * <p>
 * The CSP belongs to the UI resource, not to the tool: hosts read it from the
 * {@code resources/read} content item's {@code _meta.ui.csp} (the current draft also
 * reads it from the {@code resources/list} entry); it is not defined on a tool's
 * {@code _meta}. Declare it on the {@link McpResource#csp()} of the {@code ui://}
 * resource.
 *
 * <p>
 * Domain entries are origins including the scheme, for example
 * {@code "https://api.example.com"}. For {@link #resourceDomains()} the specification
 * also allows wildcard subdomains such as {@code "https://*.example.com"}.
 *
 * <p>
 * Opening links ({@code ui/open-link}) is host-mediated and is not governed by the CSP.
 *
 * <p>
 * On the wire, these are serialized into {@code _meta.ui.csp}. Only non-empty fields are
 * emitted, and {@code csp} is omitted entirely when all fields are empty:
 *
 * <pre>
 * {
 *   "csp": {
 *     "connectDomains": ["https://api.example.com"],
 *     "resourceDomains": ["https://cdn.example.com", "https://*.example.org"],
 *     "frameDomains": ["https://embed.example.com"],
 *     "baseUriDomains": ["https://static.example.com"]
 *   }
 * }
 * </pre>
 *
 * @author Alexandros Pappas
 * @see McpResource#csp()
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpCsp {

	/**
	 * Origins the iframe can make network requests to (fetch, XHR, WebSocket). Maps to
	 * the CSP {@code connect-src} directive. Empty means no external origins are allowed.
	 */
	String[] connectDomains() default {};

	/**
	 * Origins the iframe can load static assets from (scripts, images, stylesheets,
	 * fonts, media). Wildcard subdomains such as {@code https://*.example.com} are
	 * allowed by the specification.
	 */
	String[] resourceDomains() default {};

	/**
	 * Origins the iframe can embed as nested frames (for example, third-party embeds).
	 * Maps to the CSP {@code frame-src} directive. Empty means {@code frame-src 'none'}.
	 */
	String[] frameDomains() default {};

	/**
	 * Origins allowed as the document base URI. Maps to the CSP {@code base-uri}
	 * directive. Empty means {@code base-uri 'self'}.
	 */
	String[] baseUriDomains() default {};

}
