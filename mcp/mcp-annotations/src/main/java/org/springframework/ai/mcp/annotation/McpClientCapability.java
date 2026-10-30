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
 * A client capability that a tool, prompt or resource cannot work without. Used in
 * {@link McpTool#requiredCapabilities()}, {@link McpPrompt#requiredCapabilities()} and
 * {@link McpResource#requiredCapabilities()}.
 * <p>
 * On a 2026-07-28 ("modern") MCP server a client declares its capabilities in every
 * request, and the server may only ask for input whose capability the request declares.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public enum McpClientCapability {

	/**
	 * Form mode elicitation ({@code elicitation.form}): the method asks the user for
	 * structured input.
	 */
	ELICITATION_FORM,

	/**
	 * URL mode elicitation ({@code elicitation.url}): the method sends the user to a URL.
	 */
	ELICITATION_URL,

	/**
	 * Sampling ({@code sampling}): the method asks the client's model for a completion.
	 */
	SAMPLING,

	/**
	 * Roots ({@code roots}): the method asks the client for its root URIs.
	 */
	ROOTS

}
