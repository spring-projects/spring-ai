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
 * Controls how the {@link GovernanceAdvisor} reacts to a detected policy violation.
 *
 * <ul>
 * <li>{@link #OBSERVE} — the advisor only logs violations; the request proceeds
 * normally.</li>
 * <li>{@link #MONITOR} — the advisor logs violations and records metrics; the request
 * still proceeds.</li>
 * <li>{@link #ENFORCE} — the advisor blocks the request and throws a
 * {@link GovernanceViolationException} on any detected violation.</li>
 * </ul>
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public enum GovernanceMode {

	/**
	 * Passive observation: violations are logged but never block the request.
	 */
	OBSERVE,

	/**
	 * Active monitoring: violations are logged and recorded; the request still proceeds.
	 */
	MONITOR,

	/**
	 * Strict enforcement: any violation immediately blocks the request with a
	 * {@link GovernanceViolationException}.
	 */
	ENFORCE

}
