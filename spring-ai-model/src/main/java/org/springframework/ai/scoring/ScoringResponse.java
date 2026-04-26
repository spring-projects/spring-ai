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

package org.springframework.ai.scoring;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelResponse;

/**
 * Response for scoring (reranking) operations.
 *
 * @since 2.0.0
 */
public class ScoringResponse implements ModelResponse<ScoringResult> {

	private final List<ScoringResult> results;

	public ScoringResponse(List<ScoringResult> results) {
		this.results = results;
	}

	@Override
	public @Nullable ScoringResult getResult() {
		return this.results.isEmpty() ? null : this.results.get(0);
	}

	@Override
	public List<ScoringResult> getResults() {
		return this.results;
	}

	@Override
	public ScoringResponseMetadata getMetadata() {
		return new ScoringResponseMetadata();
	}

}
