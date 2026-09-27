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

import org.springframework.util.Assert;

/**
 * Immutable audit record produced by the {@link GovernanceAdvisor} for every evaluated
 * request.
 *
 * <p>
 * A {@code GovernanceDecision} carries:
 * <ul>
 * <li>a {@code correlationId} that can be used to link this decision to the originating
 * {@link org.springframework.ai.chat.client.ChatClientRequest} via its context;</li>
 * <li>the {@code action} taken — one of {@code "ALLOW"} or {@code "DENY"};</li>
 * <li>a human-readable {@code reason} describing why the action was taken;</li>
 * <li>a {@code riskScore} in the range {@code [0.0, 1.0]} indicating the severity of any
 * detected violation (0.0 means no risk);</li>
 * <li>the {@code evaluationTimeMs} in milliseconds it took to reach the decision.</li>
 * </ul>
 *
 * @param correlationId opaque identifier linking this decision to a request; never
 * {@code null}
 * @param action either {@code "ALLOW"} or {@code "DENY"}; never {@code null}
 * @param reason human-readable description of the decision; never {@code null}
 * @param riskScore detected risk level in {@code [0.0, 1.0]}
 * @param evaluationTimeMs time taken to produce this decision in milliseconds
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public record GovernanceDecision(String correlationId, String action, String reason, double riskScore,
		long evaluationTimeMs) {

	/**
	 * Action constant for a request that passed all governance checks.
	 */
	public static final String ACTION_ALLOW = "ALLOW";

	/**
	 * Action constant for a request that was blocked by a governance check.
	 */
	public static final String ACTION_DENY = "DENY";

	public GovernanceDecision {
		Assert.hasText(correlationId, "correlationId must not be blank");
		Assert.hasText(action, "action must not be blank");
		Assert.hasText(reason, "reason must not be blank");
		Assert.isTrue(riskScore >= 0.0 && riskScore <= 1.0, "riskScore must be in [0.0, 1.0]");
		Assert.isTrue(evaluationTimeMs >= 0, "evaluationTimeMs must be non-negative");
	}

	/**
	 * Returns {@code true} when the request was allowed.
	 */
	public boolean isAllowed() {
		return ACTION_ALLOW.equals(this.action);
	}

	/**
	 * Returns {@code true} when the request was denied.
	 */
	public boolean isDenied() {
		return ACTION_DENY.equals(this.action);
	}

}
