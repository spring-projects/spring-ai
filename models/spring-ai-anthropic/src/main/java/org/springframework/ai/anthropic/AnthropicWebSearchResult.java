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

package org.springframework.ai.anthropic;

import org.jspecify.annotations.Nullable;

/**
 * Represents an individual web search result returned by Anthropic's built-in web search
 * tool. Accessible via {@code chatResponse.getMetadata().get("web-search-results")}.
 *
 * @param title the page title
 * @param url the source URL
 * @param pageAge how old the page is, or null if not available
 * @author Soby Chacko
 * @since 1.0.0
 */
public record AnthropicWebSearchResult(String title, String url, @Nullable String pageAge) {
}
