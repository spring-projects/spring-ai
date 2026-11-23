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

import org.jspecify.annotations.Nullable;

/**
 * The well-known metadata attributes of a {@link Session}, and the accessors that read
 * them. A session has no owner field: who it belongs to is the {@link #PRINCIPAL_NAME}
 * attribute, in the shape of Spring Session's principal-name index, which every store
 * indexes ({@link SessionRepository#findByMetadata}) and an authorization layer decides
 * on. {@link #AGENT_NAME}, {@link #AGENT_VERSION} and {@link #AGENT_MODEL} label the
 * agent a session runs, set at creation, for listings and a UI.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionAttributes {

	/**
	 * The name of the principal a session belongs to, as {@code Principal.getName()}
	 * returns it in Spring Security. Indexed by every repository.
	 */
	public static final String PRINCIPAL_NAME = "principalName";

	/** The name of the agent a session runs; absent unless set at creation. */
	public static final String AGENT_NAME = "agentName";

	/** The version of the agent definition a session was created with, when known. */
	public static final String AGENT_VERSION = "agentVersion";

	/** The model the agent a session runs is configured with, when known. */
	public static final String AGENT_MODEL = "agentModel";

	private SessionAttributes() {
	}

	/** The principal name of a session, or {@code null} when it has none. */
	public static @Nullable String principalName(Session session) {
		return text(session, PRINCIPAL_NAME);
	}

	/** The agent name of a session, or {@code null} when none was set. */
	public static @Nullable String agentName(Session session) {
		return text(session, AGENT_NAME);
	}

	/** The agent version of a session, or {@code null} when none was set. */
	public static @Nullable String agentVersion(Session session) {
		return text(session, AGENT_VERSION);
	}

	/** The model of the agent a session runs, or {@code null} when none was set. */
	public static @Nullable String agentModel(Session session) {
		return text(session, AGENT_MODEL);
	}

	private static @Nullable String text(Session session, String key) {
		Object value = session.metadata().get(key);
		return (value != null) ? String.valueOf(value) : null;
	}

}
