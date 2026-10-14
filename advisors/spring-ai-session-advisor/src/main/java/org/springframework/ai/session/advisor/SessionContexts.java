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

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAlreadyExistsException;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionService;

/**
 * What the session advisors read from the advisor context: the session id, and the
 * session to create on first use.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class SessionContexts {

	private SessionContexts() {
	}

	/**
	 * The session id from {@link SessionMemoryAdvisor#SESSION_ID_CONTEXT_KEY}.
	 * @throws IllegalStateException when it is missing, so that sessions are never shared
	 * by accident
	 */
	static String sessionId(Map<String, @Nullable Object> context) {
		Object value = context.get(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY);
		if (value instanceof String s && !s.isBlank()) {
			return s;
		}
		throw new IllegalStateException("No session ID found in advisor context. Set SESSION_ID_CONTEXT_KEY on "
				+ "every request: .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))");
	}

	/**
	 * Returns the session, creating it on first use with the metadata from
	 * {@link SessionMemoryAdvisor#SESSION_METADATA_CONTEXT_KEY}, such as the agent
	 * attributes ({@link SessionAttributes#AGENT_NAME}). {@code SessionService.create}
	 * inserts atomically, so if a concurrent request for the same new session id created
	 * it first, this request's create is rejected and the <em>stored</em> session is
	 * re-read instead.
	 */
	static Session findOrCreate(SessionService sessionService, String sessionId,
			Map<String, @Nullable Object> context) {
		Session session = sessionService.findById(sessionId);
		if (session != null) {
			return session;
		}
		try {
			return sessionService
				.create(CreateSessionRequest.builder().id(sessionId).metadata(sessionMetadata(context)).build());
		}
		catch (SessionAlreadyExistsException ex) {
			// A concurrent request created it first: use the stored session
			Session existing = sessionService.findById(sessionId);
			if (existing == null) {
				throw ex;
			}
			return existing;
		}
	}

	private static Map<String, Object> sessionMetadata(Map<String, @Nullable Object> context) {
		Object value = context.get(SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY);
		if (value == null) {
			return Map.of();
		}
		if (!(value instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException(
					"Advisor context value for '" + SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY
							+ "' must be a Map but was " + value.getClass().getName());
		}
		Map<String, Object> metadata = new HashMap<>();
		map.forEach((k, v) -> {
			if (k != null && v != null) {
				metadata.put(String.valueOf(k), v);
			}
		});
		return metadata;
	}

}
