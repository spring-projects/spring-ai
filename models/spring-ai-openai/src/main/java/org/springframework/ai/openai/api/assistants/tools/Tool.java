/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai.api.assistants.tools;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Abstract base class for tools supported by OpenAI Assistants API.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/assistants">Assistants API
 * Reference</a>
 * @author Alexandros Pappas
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
		visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = CodeInterpreterTool.class, name = "code_interpreter"),
		@JsonSubTypes.Type(value = FileSearchTool.class, name = "file_search"),
		@JsonSubTypes.Type(value = FunctionTool.class, name = "function") })
public abstract class Tool {

	@JsonProperty("type")
	private final ToolType type;

	protected Tool(ToolType type) {
		this.type = type;
	}

	public ToolType getType() {
		return this.type;
	}

	public enum ToolType {

		@JsonProperty("code_interpreter")
		CODE_INTERPRETER,

		@JsonProperty("file_search")
		FILE_SEARCH,

		@JsonProperty("function")
		FUNCTION

	}

}
