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

package org.springframework.ai.anthropic.api.tool;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Anthropic Tool Data Class.
 *
 * @author Jonghoon Park
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tool {

	@JsonProperty("name")
	String name;

	@JsonProperty("description")
	String description;

	@JsonProperty("input_schema")
	Map<String, Object> inputSchema;

	/**
	 * Tool description.
	 * @param name The name of the tool.
	 * @param description A description of the tool.
	 * @param inputSchema The input schema of the tool.
	 */
	public Tool(String name, String description, Map<String, Object> inputSchema) {
		this.name = name;
		this.description = description;
		this.inputSchema = inputSchema;
	}

}
