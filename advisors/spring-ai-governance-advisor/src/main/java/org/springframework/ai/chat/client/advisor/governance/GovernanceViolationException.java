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
 * Thrown by the {@link GovernanceAdvisor} when it operates in
 * {@link GovernanceMode#ENFORCE} mode and a policy violation is detected.
 *
 * <p>
 * The exception always carries the {@link GovernanceDecision} that triggered it, so
 * callers can inspect the violation details (correlation id, reason, risk score, etc.)
 * without having to re-evaluate the request.
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public class GovernanceViolationException extends RuntimeException {

	private final GovernanceDecision decision;

	/**
	 * Creates a new exception for the given {@code decision}.
	 * @param decision the {@link GovernanceDecision} that caused the violation; must not
	 * be {@code null}
	 */
	public GovernanceViolationException(GovernanceDecision decision) {
		super("Governance violation [" + decision.action() + "]: " + decision.reason());
		this.decision = decision;
	}

	/**
	 * Returns the {@link GovernanceDecision} that triggered this exception.
	 * @return the decision; never {@code null}
	 */
	public GovernanceDecision getDecision() {
		return this.decision;
	}

}
