package org.springframework.ai.qwen.api;

import java.util.List;

/**
 * The information searched on the Internet will be returned after the search_options
 * parameter is set.
 *
 * @param searchResults a list of results from online searches
 */
public record QwenSearchInfo(List<QwenSearchResult> searchResults) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<QwenSearchResult> searchResults;

		public Builder searchResults(List<QwenSearchResult> searchResults) {
			this.searchResults = searchResults;
			return this;
		}

		public QwenSearchInfo build() {
			return new QwenSearchInfo(searchResults);
		}

	}
}
