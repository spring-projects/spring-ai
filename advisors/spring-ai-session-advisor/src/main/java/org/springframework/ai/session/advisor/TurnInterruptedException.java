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

package org.springframework.ai.session.advisor;

import org.springframework.ai.session.status.TurnStatus;

/**
 * Thrown by the {@link SessionInterruptAdvisor} before a model round when the turn no
 * longer runs under the call's run id, because an
 * {@link org.springframework.ai.session.status.TurnService#interrupt interrupt} ended it.
 * The exception unwinds the tool-calling loop. The {@link SessionTurnAdvisor} outside the
 * loop finds the turn already ended and leaves the status alone, so the interrupt stands.
 * <p>
 * Catch it around {@code ChatClient.call()} to decide what an interrupted turn means for
 * the application: discard, keep what ran, a quiet end of the turn. With {@code stream()}
 * it arrives as the error signal of the {@code Flux}. <pre>{@code
 * try {
 *     chatClient.prompt().user("...").call().content();
 * }
 * catch (TurnInterruptedException ex) {
 *     // ex.getStatus().stopReason() says why the turn ended
 * }
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class TurnInterruptedException extends RuntimeException {

	private final String sessionId;

	private final String runId;

	private final transient TurnStatus status;

	public TurnInterruptedException(String sessionId, String runId, TurnStatus status) {
		super("The turn of session '" + sessionId + "' under run '" + runId + "' no longer runs; the status is "
				+ status.status() + (status.stopReason() != null ? " (" + status.stopReason() + ")" : ""));
		this.sessionId = sessionId;
		this.runId = runId;
		this.status = status;
	}

	/** Returns the id of the session the turn ran on. */
	public String getSessionId() {
		return this.sessionId;
	}

	/** Returns the run id the turn ran under. */
	public String getRunId() {
		return this.runId;
	}

	/**
	 * Returns the status of the session as the advisor read it when it stopped the loop.
	 */
	public TurnStatus getStatus() {
		return this.status;
	}

}
