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
import org.springframework.ai.session.SessionService;

/**
 * Whether a turn runs on a session. A {@link EventType#STATUS} event records it and
 * {@link TurnStatus#status()} reports it. A session without status events is
 * {@link #IDLE}: no harness has run a turn on it yet.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public enum SessionStatus {

	/**
	 * No turn runs on the session. The next {@link SessionService#startTurn} starts one.
	 */
	IDLE,

	/**
	 * A turn runs on the session, under the run id the status names, until that run ends
	 * it or an {@link SessionService#interrupt} does.
	 */
	RUNNING

}
