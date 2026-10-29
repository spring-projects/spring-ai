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

import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpResource;

/**
 * Helpers for the {@link McpResource#ttlMs()} and {@link McpResource#cacheScope()} cache
 * hints, which only the 2026-07-28 ("modern") MCP server can transmit.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class McpResourceCacheHints {

	private static final Log logger = LogFactory.getLog(McpResourceCacheHints.class);

	private McpResourceCacheHints() {
	}

	/**
	 * Whether the annotation carries cache hints other than the defaults.
	 */
	public static boolean isSet(McpResource annotation) {
		return annotation.ttlMs() != 0 || annotation.cacheScope() != CacheScope.PRIVATE;
	}

	/**
	 * Logs a warning when a method served by a legacy (pre 2026-07-28) server declares
	 * cache hints, because the legacy {@code resources/read} result has no field to carry
	 * them and they are silently dropped.
	 */
	public static void warnIfIgnored(Method method, McpResource annotation) {
		if (isSet(annotation) && logger.isWarnEnabled()) {
			logger.warn("@McpResource ttlMs/cacheScope on " + method.getDeclaringClass().getName() + "."
					+ method.getName() + " are ignored: cache hints require spring.ai.mcp.server.protocol=MODERN");
		}
	}

}
