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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpClientCapability;

/**
 * Helper for the {@code requiredCapabilities} attribute of {@code @McpTool},
 * {@code @McpPrompt} and {@code @McpResource}, which only the 2026-07-28 ("modern") MCP
 * server enforces.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class McpRequiredCapabilities {

	private static final Log logger = LogFactory.getLog(McpRequiredCapabilities.class);

	private McpRequiredCapabilities() {
	}

	/**
	 * Logs a warning when a method served by a legacy (pre 2026-07-28) server declares
	 * required client capabilities, because the legacy servers check the capabilities the
	 * client sent at initialization and the attribute is silently dropped.
	 * @param method the annotated method
	 * @param annotation the simple name of the annotation, for the message
	 * @param required the annotation's {@code requiredCapabilities}
	 */
	public static void warnIfIgnored(Method method, String annotation, McpClientCapability[] required) {
		if (required.length > 0 && logger.isWarnEnabled()) {
			logger.warn("@" + annotation + " requiredCapabilities " + Arrays.toString(required) + " on "
					+ method.getDeclaringClass().getName() + "." + method.getName()
					+ " are ignored: required client capabilities need spring.ai.mcp.server.protocol=MODERN");
		}
	}

}
