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
import java.util.function.Supplier;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.jspecify.annotations.Nullable;
import reactor.util.context.ContextView;

import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.SessionAttributes;

/**
 * What the session advisors share around a request: what they read from the advisor
 * context, the session id, the run id and the request that creates the session on first
 * use, and the caller's context they carry to the session calls of a stream.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class SessionContexts {

	private static final ContextSnapshotFactory CONTEXT_SNAPSHOTS = ContextSnapshotFactory.builder().build();

	private SessionContexts() {
	}

	/**
	 * Captures the caller's context, to restore it around a session call that runs on the
	 * scheduler instead of the caller's thread: the thread locals of a blocking caller,
	 * the security context or the current observation for example, and the Reactor
	 * context of a reactive caller, the latter winning where both hold a key.
	 * @param contextView the Reactor context of the stream, or {@code null} outside one
	 * @return the snapshot, to pass to {@link #withContext} or to wrap a callback with
	 */
	static ContextSnapshot snapshot(@Nullable ContextView contextView) {
		return (contextView != null) ? CONTEXT_SNAPSHOTS.captureAll(contextView) : CONTEXT_SNAPSHOTS.captureAll();
	}

	/**
	 * Runs the action with the snapshot's context set as thread locals, and clears them
	 * again afterwards.
	 * @param context the snapshot {@link #snapshot} captured
	 * @param action the action, a session call as a rule
	 * @param <T> the action's result type
	 * @return the action's result
	 */
	static <T> T withContext(ContextSnapshot context, Supplier<T> action) {
		try (ContextSnapshot.Scope ignored = context.setThreadLocals()) {
			return action.get();
		}
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
	 * Returns the run id of the call from the advisor context, under
	 * {@link SessionTurnAdvisor#RUN_ID_CONTEXT_KEY}, or {@code null} when the context has
	 * none or a blank one.
	 * @throws IllegalArgumentException if the value is not a string
	 */
	static @Nullable String runId(Map<String, @Nullable Object> context) {
		Object value = context.get(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY);
		if (value == null) {
			return null;
		}
		if (!(value instanceof String s)) {
			throw new IllegalArgumentException("Advisor context value for '" + SessionTurnAdvisor.RUN_ID_CONTEXT_KEY
					+ "' must be a String but was " + value.getClass().getName());
		}
		return s.isBlank() ? null : s;
	}

	/**
	 * Returns the caller's decisions on the tool calls a parked session waits on, from
	 * {@link SessionTurnAdvisor#TOOL_CALL_DECISIONS_CONTEXT_KEY}, or {@code null} when
	 * the context has none.
	 * @throws IllegalArgumentException if the value is not a {@link ToolCallDecisions}
	 */
	static @Nullable ToolCallDecisions decisions(Map<String, @Nullable Object> context) {
		Object value = context.get(SessionTurnAdvisor.TOOL_CALL_DECISIONS_CONTEXT_KEY);
		if (value == null) {
			return null;
		}
		if (!(value instanceof ToolCallDecisions decisions)) {
			throw new IllegalArgumentException(
					"Advisor context value for '" + SessionTurnAdvisor.TOOL_CALL_DECISIONS_CONTEXT_KEY
							+ "' must be a ToolCallDecisions but was " + value.getClass().getName());
		}
		return decisions;
	}

	/**
	 * The request the advisors pass to {@code SessionService.findOrCreate}: the id, and
	 * the metadata from {@link SessionMemoryAdvisor#SESSION_METADATA_CONTEXT_KEY}, such
	 * as the agent attributes ({@link SessionAttributes#AGENT_NAME}).
	 */
	static CreateSessionRequest createRequest(String sessionId, Map<String, @Nullable Object> context) {
		return CreateSessionRequest.builder().id(sessionId).metadata(sessionMetadata(context)).build();
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
