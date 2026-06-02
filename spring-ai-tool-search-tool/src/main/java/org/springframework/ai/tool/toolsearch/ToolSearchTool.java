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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.toolsearch.advisor.ToolSearchToolCallingAdvisor;

public class ToolSearchTool {

	private final ToolIndex toolIndex;

	@Nullable private final Integer advisorMaxResults;

	public ToolSearchTool(ToolIndex toolIndex, @Nullable Integer advisorMaxResults) {
		this.toolIndex = toolIndex;
		this.advisorMaxResults = advisorMaxResults;
	}

	// @formatter:off
	@Tool(name = "toolSearchTool", description = """
			Search for tools in the tool registry to discover capabilities for completing the current task.
			Use this when you need functionality not provided by your currently available tools.
			The search queries against tool names, descriptions, and parameter information to find the most relevant tools.
			Returns references to matching tools which will be expanded into full definitions you can then invoke.
			""")
	public List<String> toolSearchTool(
		@ToolParam(description = "A natural language search query describing the tool capability you need. Be specific and include relevant keywords.") String query,
		@ToolParam(description = "Maximum number of tool references to return (1-10). Default is 5.", required = false) Integer maxResults,
		@ToolParam(description = "Optional filter to narrow search to a specific tool category.", required = false) String categoryFilter,
		ToolContext toolContext) { // @formatter:on

		String sessionId = Objects
			.requireNonNull(toolContext.getContext().get(ToolSearchToolCallingAdvisor.TOOL_SEARCH_TOOL_SESSION_ID_KEY))
			.toString();

		// Advisor-configured maxResults is the fallback when the LLM does not provide
		// one.
		maxResults = (maxResults != null) ? maxResults : this.advisorMaxResults;

		ToolSearchResponse toolSearchResponse = this.toolIndex
			.search(new ToolSearchRequest(sessionId, query, maxResults, categoryFilter));

		return toolSearchResponse.toolReferences().stream().map(tr -> tr.toolName()).toList();
	}

}
