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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ToolContext;
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

	@Test
	void aMatchOmitsTheNote() throws Exception {
		try (RegexToolIndex toolIndex = new RegexToolIndex()) {
			toolIndex.indexTool("s",
					ToolReference.builder().toolName("weather").summary("Returns the weather").build());
			ToolSearchToolResult result = search(new ToolSearchTool(toolIndex, null), "weather");

			assertThat(result.tools()).containsExactly("weather");
			assertThat(result.note()).isNull();
		}
	}

	@Test
	void aKeywordMissSaysToTryOtherWords() throws Exception {
		try (RegexToolIndex toolIndex = new RegexToolIndex()) {
			toolIndex.indexTool("s",
					ToolReference.builder().toolName("weather").summary("Returns the weather").build());
			ToolSearchToolResult result = search(new ToolSearchTool(toolIndex, null), "zzzznotatool");

			assertThat(result.tools()).isEmpty();
			assertThat(result.note()).isEqualTo("No match for 'zzzznotatool'; try other words.");
		}
	}

	@Test
	void noneAppliesSaysNoToolDoesIt() {
		ToolIndex index = new ToolIndex() {
			@Override
			public void indexTool(String sessionId, ToolReference toolReference) {
			}

			@Override
			public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
				return ToolSearchResponse.builder().toolReferences(List.of()).totalMatches(0).noneApplies(true).build();
			}

			@Override
			public void clearIndex(String sessionId) {
			}
		};

		ToolSearchToolResult result = search(new ToolSearchTool(index, null), "pay a vendor bill");

		assertThat(result.tools()).isEmpty();
		assertThat(result.note())
			.isEqualTo("No tool matches 'pay a vendor bill'. If none of your tools does it, no tool here does.");
	}

	@Test
	void theToolResultJsonCarriesTheNoteOnlyWhenNothingMatches() {
		ToolIndex index = new ToolIndex() {
			@Override
			public void indexTool(String sessionId, ToolReference toolReference) {
			}

			@Override
			public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
				if ("pay a vendor bill".equals(toolSearchRequest.query())) {
					return ToolSearchResponse.builder()
						.toolReferences(List.of())
						.totalMatches(0)
						.noneApplies(true)
						.build();
				}
				return ToolSearchResponse.builder()
					.toolReferences(
							List.of(ToolReference.builder().toolName("weather").summary("Returns the weather").build()))
					.totalMatches(1)
					.build();
			}

			@Override
			public void clearIndex(String sessionId) {
			}
		};
		ToolCallback callback = MethodToolCallbackProvider.builder()
			.toolObjects(new ToolSearchTool(index, null))
			.build()
			.getToolCallbacks()[0];
		ToolContext context = context();

		assertThat(callback.call("{\"query\":\"pay a vendor bill\"}", context)).isEqualTo(
				"{\"tools\":[],\"note\":\"No tool matches 'pay a vendor bill'. If none of your tools does it, no tool here does.\"}");
		assertThat(callback.call("{\"query\":\"weather\"}", context)).isEqualTo("{\"tools\":[\"weather\"]}");
	}

	private static ToolSearchToolResult search(ToolSearchTool tool, String query) {
		return tool.toolSearchTool(query, null, null, context());
	}

	private static ToolContext context() {
		return new ToolContext(Map.of(ToolSearchTool.TOOL_SEARCH_TOOL_SESSION_ID_KEY, "s"));
	}

}
