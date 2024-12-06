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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.springframework.ai.openai.api.assistants.tools;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the File Search Tool for OpenAI Assistants API.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/assistants">Assistants API
 * Reference</a>
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileSearchTool extends Tool {

	@JsonProperty("file_search")
	private final FileSearchOptions fileSearch;

	@JsonCreator
	public FileSearchTool(FileSearchOptions fileSearch) {
		super(ToolType.FILE_SEARCH);
		this.fileSearch = fileSearch;
	}

	public FileSearchOptions getFileSearch() {
		return fileSearch;
	}

	/**
	 * Represents options for file search.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FileSearchOptions {

		@JsonProperty("max_num_results")
		private final Integer maxNumResults;

		@JsonProperty("ranking_options")
		private final RankingOptions rankingOptions;

		@JsonCreator
		public FileSearchOptions(@JsonProperty("max_num_results") Integer maxNumResults,
				@JsonProperty("ranking_options") RankingOptions rankingOptions) {
			this.maxNumResults = maxNumResults;
			this.rankingOptions = rankingOptions;
		}

		public Integer getMaxNumResults() {
			return this.maxNumResults;
		}

		public RankingOptions getRankingOptions() {
			return this.rankingOptions;
		}

		/**
		 * Ranking options for file search.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public static class RankingOptions {

			@JsonProperty("ranker")
			private final String ranker;

			@JsonProperty("score_threshold")
			private final Float scoreThreshold;

			@JsonCreator
			public RankingOptions(@JsonProperty("ranker") String ranker,
					@JsonProperty("score_threshold") Float scoreThreshold) {
				this.ranker = ranker;
				this.scoreThreshold = scoreThreshold;
			}

			public String getRanker() {
				return this.ranker;
			}

			public Float getScoreThreshold() {
				return this.scoreThreshold;
			}

		}

	}

}
