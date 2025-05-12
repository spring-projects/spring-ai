/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool.definition;

/**
 * Definition used by the AI model to determine when and how to call the tool.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolDefinition {

	/**
	 * The tool name. Unique within the tool set provided to a model.
	 */
	String name();

	/**
	 * The tool description, used by the AI model to determine what the tool does.
	 */
	String description();

	/**
	 * The schema of the parameters used to call the tool.
	 */
	String inputSchema();

	/**
	 * Create a default {@link ToolDefinition} builder.
	 */
	static DefaultToolDefinition.Builder builder() {
		return DefaultToolDefinition.builder();
	}

}
