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
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.springframework.ai.openai.api.assistants.tools;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the Function Tool for OpenAI Assistants API.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/assistants">Assistants API
 * Reference</a>
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionTool extends Tool {

	@JsonProperty("function")
	private final Function function;

	@JsonCreator
	public FunctionTool(@JsonProperty("function") Function function) {
		super(ToolType.FUNCTION);
		this.function = function;
	}

	public Function getFunction() {
		return function;
	}

	/**
	 * Represents the details of a function.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class Function {

		@JsonProperty("name")
		private final String name;

		@JsonProperty("description")
		private final String description;

		@JsonProperty("parameters")
		private Map<String, Object> parameters;

		@JsonProperty("strict")
		private final Boolean strict;

		@JsonCreator
		public Function(@JsonProperty("name") String name, @JsonProperty("description") String description,
				@JsonProperty("parameters") Map<String, Object> parameters, @JsonProperty("strict") Boolean strict) {
			this.name = name;
			this.description = description;
			this.parameters = parameters;
			this.strict = strict;
		}

		public String getName() {
			return this.name;
		}

		public String getDescription() {
			return this.description;
		}

		public Map<String, Object> getParameters() {
			return this.parameters;
		}

		public Boolean getStrict() {
			return this.strict;
		}

	}

}
