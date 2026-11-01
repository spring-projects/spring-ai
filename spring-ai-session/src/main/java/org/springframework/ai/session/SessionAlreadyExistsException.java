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

package org.springframework.ai.session;

/**
 * Thrown by {@link SessionService#create} when a session with the requested id exists;
 * the existing session is left untouched. Two callers that create the same id
 * concurrently get one session and one of these, so the second can read what the first
 * created; {@link SessionService#findOrCreate} does.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SessionAlreadyExistsException extends IllegalStateException {

	private final String sessionId;

	public SessionAlreadyExistsException(String sessionId) {
		super("Session already exists: " + sessionId);
		this.sessionId = sessionId;
	}

	/** The id of the session that already exists. */
	public String getSessionId() {
		return this.sessionId;
	}

}
