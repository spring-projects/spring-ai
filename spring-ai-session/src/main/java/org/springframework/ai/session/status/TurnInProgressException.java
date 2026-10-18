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

import org.springframework.ai.session.SessionService;

/**
 * Thrown by {@link SessionService#startTurn} when the session is busy: a turn already
 * runs on it, under the run id this exception carries. Nothing is written and nothing is
 * queued; the session keeps the turn it has. The caller decides what a busy session means
 * for it: fail the call, retry later, or {@link SessionService#interrupt interrupt} the
 * running turn and start again.
 * <p>
 * Not thrown for anything else. A start that fails because concurrent appends kept
 * overtaking its write, while no turn was running, throws an
 * {@link IllegalStateException} instead, so a handler that interrupts the busy turn never
 * interrupts an idle session.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class TurnInProgressException extends IllegalStateException {

	private final String sessionId;

	private final String runId;

	/**
	 * Creates the exception for a session on which a turn runs.
	 * @param sessionId the busy session
	 * @param runId the run id of the turn that runs on it
	 */
	public TurnInProgressException(String sessionId, String runId) {
		this(sessionId, runId, "A turn is in progress on session '" + sessionId + "' under run '" + runId + "'");
	}

	public TurnInProgressException(String sessionId, String runId, String message) {
		super(message);
		this.sessionId = sessionId;
		this.runId = runId;
	}

	/** Returns the id of the busy session, the one the turn was to start on. */
	public String getSessionId() {
		return this.sessionId;
	}

	/** Returns the run id of the turn that runs on the session. */
	public String getRunId() {
		return this.runId;
	}

}
