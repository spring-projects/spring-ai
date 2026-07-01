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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Response containing a list of ToolReferences matching the search criteria, total
 * matches, and search metadata.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolSearchResponse(List<ToolReference> toolReferences, @Nullable Integer totalMatches,
		@Nullable SearchMetadata searchMetadata) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<ToolReference> toolReferences = new ArrayList<>();

		@Nullable private Integer totalMatches;

		@Nullable private SearchMetadata searchMetadata;

		public Builder toolReferences(List<ToolReference> toolReferences) {
			this.toolReferences = toolReferences;
			return this;
		}

		public Builder addToolReference(ToolReference toolReference) {
			this.toolReferences.add(toolReference);
			return this;
		}

		public Builder totalMatches(Integer totalMatches) {
			this.totalMatches = totalMatches;
			return this;
		}

		public Builder searchMetadata(SearchMetadata searchMetadata) {
			this.searchMetadata = searchMetadata;
			return this;
		}

		public ToolSearchResponse build() {
			return new ToolSearchResponse(Objects.requireNonNull(this.toolReferences), this.totalMatches,
					this.searchMetadata);
		}

	}

	/**
	 * Metadata about how a search was performed.
	 *
	 * @param searchType a label identifying the {@link ToolIndex} implementation that
	 * produced this response — typically the simple class name (e.g.
	 * {@code "LuceneToolIndex"}). Custom implementations may return any non-null,
	 * non-empty string.
	 * @param query the original query string passed to the tool index
	 * @param searchTimeMs elapsed time in milliseconds, or {@code null} if not measured
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SearchMetadata(String searchType, String query, @Nullable Long searchTimeMs) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			@Nullable private String searchType;

			@Nullable private String query;

			@Nullable private Long searchTimeMs;

			public Builder searchType(String searchType) {
				this.searchType = searchType;
				return this;
			}

			public Builder query(String query) {
				this.query = query;
				return this;
			}

			public Builder searchTimeMs(Long searchTimeMs) {
				this.searchTimeMs = searchTimeMs;
				return this;
			}

			@SuppressWarnings("null")
			public SearchMetadata build() {
				return new SearchMetadata(Objects.requireNonNull(this.searchType), Objects.requireNonNull(this.query),
						this.searchTimeMs);
			}

		}
	}
}
