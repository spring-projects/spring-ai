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

package org.springframework.ai.session.status;

import org.springframework.util.Assert;

/**
 * Thrown by {@link TurnService#start} when the session is parked on a question. A parked
 * session waits for an answer, and a new turn would leave the question open and its reply
 * unanswered in the log. The caller answers it with {@link TurnService#resume} instead,
 * with the person's decision or with one that says the person moved on, and runs its turn
 * as the resumed one. {@link TurnService#pendingQuestion} returns the question.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see TurnStatus#isParked()
 */
public class TurnParkedException extends IllegalStateException {

	private final String sessionId;

	/**
	 * Creates the exception for a parked session.
	 * @param sessionId the parked session
	 */
	public TurnParkedException(String sessionId) {
		super("Session '" + sessionId + "' is parked on a question and waits for an answer; resume it instead of "
				+ "starting a turn");
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		this.sessionId = sessionId;
	}

	/** Returns the id of the parked session, the one the turn was to start on. */
	public String getSessionId() {
		return this.sessionId;
	}

}
