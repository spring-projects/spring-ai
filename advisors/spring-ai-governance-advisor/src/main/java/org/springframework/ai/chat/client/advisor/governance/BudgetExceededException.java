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

package org.springframework.ai.chat.client.advisor.governance;

/**
 * Thrown when a session exceeds the token budget configured in
 * {@link SessionCostTracker}.
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public class BudgetExceededException extends RuntimeException {

	private final String sessionId;

	private final long tokenLimit;

	private final long currentTokenCount;

	/**
	 * Creates a new exception indicating that {@code sessionId} has exceeded its budget.
	 * @param sessionId the identifier of the session that exceeded the budget
	 * @param tokenLimit the maximum token count allowed per session
	 * @param currentTokenCount the token count accumulated so far in the session
	 */
	public BudgetExceededException(String sessionId, long tokenLimit, long currentTokenCount) {
		super(String.format("Session '%s' has exceeded its token budget (limit=%d, current=%d).", sessionId, tokenLimit,
				currentTokenCount));
		this.sessionId = sessionId;
		this.tokenLimit = tokenLimit;
		this.currentTokenCount = currentTokenCount;
	}

	/**
	 * Returns the identifier of the session that exceeded the budget.
	 */
	public String getSessionId() {
		return this.sessionId;
	}

	/**
	 * Returns the token limit configured for the session.
	 */
	public long getTokenLimit() {
		return this.tokenLimit;
	}

	/**
	 * Returns the token count accumulated in the session at the time of the exception.
	 */
	public long getCurrentTokenCount() {
		return this.currentTokenCount;
	}

}
