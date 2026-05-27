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

import java.util.Map;

/**
 * Convenience return type for MCP App tool methods that need to provide both model-facing
 * content and widget-facing structured content.
 *
 * @param text plain-text content sent to the LLM in content[]
 * @param structuredContent map sent to the widget UI as structuredContent
 */
public record McpAppResult(String text, Map<String, Object> structuredContent) {

	public static McpAppResult of(String text, Map<String, Object> structuredContent) {
		return new McpAppResult(text, structuredContent);
	}

}
