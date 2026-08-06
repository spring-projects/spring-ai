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

package org.springframework.ai.tool.toolsearch;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolSearchTool}.
 *
 * @author Christian Tzolov
 */
class ToolSearchToolTests {

	/**
	 * Regression guard for the {@code -parameters} javac flag being dropped by a
	 * {@code compilerArgs} merge bug (see root {@code pom.xml}, {@code java-compile}
	 * execution). Without {@code -parameters}, reflection-based schema generation falls
	 * back to synthetic names ({@code arg0}, {@code arg1}, ...) instead of the method's
	 * declared parameter names, and OpenAI's strict-schema validation then rejects the
	 * tool definition because the (also synthetically-named) optional parameters are
	 * missing from "required".
	 */
	@Test
	void inputSchemaUsesDeclaredParameterNamesNotSyntheticOnes() throws Exception {
		try (RegexToolIndex toolIndex = new RegexToolIndex()) {
			ToolCallback toolSearchToolCallback = MethodToolCallbackProvider.builder()
				.toolObjects(new ToolSearchTool(toolIndex, null))
				.build()
				.getToolCallbacks()[0];

			String schema = toolSearchToolCallback.getToolDefinition().inputSchema();

			assertThat(schema).contains("\"query\"", "\"maxResults\"", "\"categoryFilter\"")
				.doesNotContain("\"arg0\"", "\"arg1\"", "\"arg2\"");
			// toolContext is a ToolContext parameter, injected at invocation time rather
			// than supplied by the model, so it must not appear in the schema at all.
			assertThat(schema).doesNotContain("\"toolContext\"", "\"arg3\"");
		}
	}

}
