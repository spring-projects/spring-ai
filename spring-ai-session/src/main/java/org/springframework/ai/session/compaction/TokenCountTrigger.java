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
 * Fires when the estimated tokens of the view reach a threshold: the latest stored system
 * message, the summaries and the conversation, measured on {@link EventText} with a
 * {@link TokenCountEstimator}, JTokkit by default. A {@link CompactionWindow#tokenBudget
 * token budget} uses the same measure, so the threshold and the budget are in the same
 * units.
 *
 * @see CompactionWindow#tokenBudget(int)
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class TokenCountTrigger implements CompactionTrigger {

	private final int threshold;

	private final TokenCountEstimator tokenCountEstimator;

	/**
	 * @param threshold the estimated token count at which compaction fires; positive
	 * @param tokenCountEstimator the estimator applied to {@link EventText}
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
