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

import org.springframework.ai.session.EventType;

/**
 * Why a turn ended. The {@link EventType#STATUS} event that ends the turn records it, and
 * {@link TurnStatus#stopReason()} reports it while the session is idle.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public enum StopReason {

	/** The harness ran the turn to its end: the model answered. */
	END_TURN,

	/** The turn failed: the call threw, and the harness ended the turn with the error. */
	ERROR,

	/**
	 * An {@link TurnService#interrupt interrupt} ended the turn before it ran to its end.
	 * The {@link EventType#INTERRUPT} event that asked for the stop sits right before the
	 * {@code STATUS} event that records this reason.
	 */
	INTERRUPTED,

	/**
	 * The client stopped listening before the turn ran to its end: it cancelled the
	 * stream, closed the connection or timed out. Nobody asked the session to stop, so no
	 * {@link EventType#INTERRUPT} event goes with this reason.
	 */
	CANCELLED,

	/**
	 * The turn's lease ran out and a new turn took the session over: the harness that
	 * held it never ended it, after a crash for example, and
	 * {@link TurnService#start(String, String, java.time.Duration) start} found the lease
	 * passed. Written by {@code start} in the same append as the new turn's start. No
	 * {@link EventType#INTERRUPT} event goes with this reason either.
	 */
	EXPIRED

}
