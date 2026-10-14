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

package org.springframework.ai.session.compaction;

import java.util.List;

import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * Triggers compaction when the estimated token count of the session's events reaches a
 * threshold. Token count is measured on {@link EventText} with a configurable
 * {@link TokenCountEstimator}, defaulting to {@link JTokkitTokenCountEstimator}: the same
 * measure a {@link CompactionWindow#tokenBudget token budget} uses, so the threshold and
 * the budget are expressed in the same units and can be calibrated against each other.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see CompactionWindow#tokenBudget(int)
 */
public final class TokenCountTrigger implements CompactionTrigger {

	private final int threshold;

	private final TokenCountEstimator tokenCountEstimator;

	/**
	 * Creates a trigger with the given threshold and token count estimator.
	 * @param threshold minimum total token count that triggers compaction. Must be
	 * positive.
	 * @param tokenCountEstimator estimator used to measure each event's token cost. Must
	 * not be {@code null}.
	 */
	private TokenCountTrigger(int threshold, TokenCountEstimator tokenCountEstimator) {
		Assert.isTrue(threshold > 0, "threshold must be greater than 0");
		Assert.notNull(tokenCountEstimator, "tokenCountEstimator must not be null");
		this.threshold = threshold;
		this.tokenCountEstimator = tokenCountEstimator;
	}

	@Override
	public boolean shouldCompact(CompactionRequest request) {
		// Count what is sent to the model: the latest stored system message, the summary
		// and the conversation; a superseded system message is not sent
		List<SessionEvent> events = request.events();
		List<SessionEvent> pinned = WindowCompactionStrategy.latestStoredSystem(events);
		int totalTokens = events.stream()
			.filter(e -> !WindowCompactionStrategy.isStoredSystem(e) || pinned.contains(e))
			.mapToInt(e -> this.tokenCountEstimator.estimate(EventText.of(e)))
			.sum();
		return totalTokens >= this.threshold;
	}

	public int getThreshold() {
		return this.threshold;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private int threshold;

		private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

		private Builder() {
		}

		public Builder threshold(int threshold) {
			Assert.isTrue(threshold > 0, "threshold must be greater than 0");
			this.threshold = threshold;
			return this;
		}

		public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
			Assert.notNull(tokenCountEstimator, "tokenCountEstimator must not be null");
			this.tokenCountEstimator = tokenCountEstimator;
			return this;
		}

		public TokenCountTrigger build() {
			return new TokenCountTrigger(this.threshold, this.tokenCountEstimator);
		}

	}

}
