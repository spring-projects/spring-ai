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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Request for searching tools within a session's index.
 *
 * @param sessionId identifies the session whose tool index is searched
 * @param query natural-language description of the needed capability
 * @param maxResults maximum number of results to return; {@code null} lets the tool index
 * apply its own default
 * @param categoryFilter optional hint to narrow the search to a specific tool category;
 * support depends on the
 * {@link org.springframework.ai.chat.client.advisor.toolsearch.ToolIndex} implementation
 * @author Christian Tzolov
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolSearchRequest(String sessionId, String query, @Nullable Integer maxResults,
		@Nullable String categoryFilter) {

}
