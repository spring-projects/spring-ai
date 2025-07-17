package org.springframework.ai.qwen.api;

/**
 * Results from online searches.
 *
 * @see QwenSearchInfo
 * @param siteName the name of the website from which the search results came
 * @param icon the URL of the icon from the source website, or an empty string if there is
 * no icon
 * @param index the sequence number of the search result, indicating the index of the
 * search result in search_results
 * @param title the title of the search result
 * @param url the URL of the search result
 */
public record QwenSearchResult(String siteName, String icon, Integer index, String title, String url) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String siteName;

		private String icon;

		private Integer index;

		private String title;

		private String url;

		public Builder siteName(String siteName) {
			this.siteName = siteName;
			return this;
		}

		public Builder icon(String icon) {
			this.icon = icon;
			return this;
		}

		public Builder index(Integer index) {
			this.index = index;
			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder url(String url) {
			this.url = url;
			return this;
		}

		public QwenSearchResult build() {
			return new QwenSearchResult(siteName, icon, index, title, url);
		}

	}
}
