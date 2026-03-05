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

package org.springframework.ai.mcp.annotation.adapter;

import java.lang.reflect.Method;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpComplete;

/**
 * Utility class for adapting between McpComplete annotations and
 * McpSchema.CompleteReference objects.
 *
 * @author Christian Tzolov
 */
public final class CompleteAdapter {

	private CompleteAdapter() {
	}

	/**
	 * Convert a McpComplete annotation to a McpSchema.CompleteReference object.
	 * @param mcpComplete The McpComplete annotation
	 * @return The corresponding McpSchema.CompleteReference object
	 * @throws IllegalArgumentException if neither prompt nor uri is provided, or if both
	 * are provided
	 */
	public static McpSchema.CompleteReference asCompleteReference(McpComplete mcpComplete) {
		Assert.notNull(mcpComplete, "mcpComplete cannot be null");

		String prompt = mcpComplete.prompt();
		String uri = mcpComplete.uri();

		// Validate that either prompt or uri is provided, but not both
		if ((prompt == null || prompt.isEmpty()) && (uri == null || uri.isEmpty())) {
			throw new IllegalArgumentException("Either prompt or uri must be provided in McpComplete annotation");
		}
		if ((prompt != null && !prompt.isEmpty()) && (uri != null && !uri.isEmpty())) {
			throw new IllegalArgumentException("Only one of prompt or uri can be provided in McpComplete annotation");
		}

		// Create the appropriate reference type based on what's provided
		if (prompt != null && !prompt.isEmpty()) {
			return new McpSchema.PromptReference(prompt);
		}
		else {
			return new McpSchema.ResourceReference(uri);
		}
	}

	/**
	 * Convert a McpComplete annotation and Method to a McpSchema.CompleteReference
	 * object.
	 * @param mcpComplete The McpComplete annotation
	 * @param method The method annotated with McpComplete
	 * @return The corresponding McpSchema.CompleteReference object
	 * @throws IllegalArgumentException if neither prompt nor uri is provided, or if both
	 * are provided
	 */
	public static McpSchema.CompleteReference asCompleteReference(McpComplete mcpComplete, Method method) {
		Assert.notNull(method, "method cannot be null");
		return asCompleteReference(mcpComplete);
	}

}
