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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Reference to a retrieved Tool with its name, relevance score, and summary.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolReference(String toolName, @Nullable Double relevanceScore, String summary) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		@Nullable private String toolName;

		@Nullable private Double relevanceScore;

		@Nullable private String summary;

		public Builder toolName(String toolName) {
			this.toolName = toolName;
			return this;
		}

		public Builder relevanceScore(Double relevanceScore) {
			this.relevanceScore = relevanceScore;
			return this;
		}

		public Builder relevanceScore(Float relevanceScore) {
			this.relevanceScore = relevanceScore.doubleValue();
			return this;
		}

		public Builder summary(String summary) {
			this.summary = summary;
			return this;
		}

		public ToolReference build() {
			return new ToolReference(Objects.requireNonNull(this.toolName), this.relevanceScore,
					Objects.requireNonNull(this.summary));
		}

	}
}
