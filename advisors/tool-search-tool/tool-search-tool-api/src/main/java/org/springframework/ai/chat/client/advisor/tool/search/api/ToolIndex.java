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

package org.springframework.ai.chat.client.advisor.tool.search.api;

import java.util.List;

/**
 * Interface for searching and discovering tools on-demand.
 * <p>
 * Implementations provide different search strategies — such as embedding-based semantic
 * search, full-text keyword search, or regex pattern matching — to find relevant tools
 * from a registered catalog based on search queries. This aligns with the Tool Search
 * Tool concept for dynamic tool discovery.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public interface ToolIndex {

	/**
	 * Registers a tool in the search index for the specified session.
	 * @param sessionId the session identifier for tool isolation
	 * @param toolReference the reference to the tool being indexed
	 */
	void indexTool(String sessionId, ToolReference toolReference);

	/**
	 * Registers multiple tools in the search index for the specified session in a single
	 * batch operation.
	 * <p>
	 * Implementations should override this method when a batch operation is more
	 * efficient than repeated {@link #indexTool} calls (e.g. a single embedding API call
	 * or a single write transaction). The default implementation simply iterates and
	 * delegates to {@link #indexTool}.
	 * @param sessionId the session identifier for tool isolation
	 * @param toolReferences the tools to register
	 */
	@SuppressWarnings("null")
	default void indexTools(String sessionId, List<ToolReference> toolReferences) {
		for (ToolReference ref : toolReferences) {
			indexTool(sessionId, ref);
		}
	}

	/**
	 * Searches for tools matching the given request criteria.
	 * @param toolSearchRequest the search request containing query and parameters
	 * @return a response containing matching tool references
	 */
	ToolSearchResponse search(ToolSearchRequest toolSearchRequest);

	/**
	 * Clears all indexed tools for the specified session.
	 * @param sessionId the session identifier
	 */
	void clearIndex(String sessionId);

}
