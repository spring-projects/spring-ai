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

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * What a request asks of the {@link SessionTurnAdvisor}, read from its context: to start
 * a turn, to answer the tool calls the session parked on
 * ({@link SessionTurnAdvisor#TOOL_CALL_DECISIONS_CONTEXT_KEY}), or to continue the
 * session's last turn ({@link SessionTurnAdvisor#RESUME_CONTEXT_KEY}). The two latter
 * carry no user message, text belongs in a new turn, and exclude each other.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
enum TurnIntent {

	/** Start a turn with the request's user message. */
	START,

	/** Answer the tool calls the session parked on with the request's decisions. */
	DECISIONS,

	/** Continue the session's last turn where it stopped. */
	RESUME;

	/**
	 * Reads the intent of the request and checks it.
	 * @throws IllegalArgumentException if the request carries both decisions and the
	 * resume flag, or carries a user message with either
	 */
	static TurnIntent of(String sessionId, ChatClientRequest request) {
		boolean decisions = SessionContexts.decisions(request.context()) != null;
		boolean resume = SessionContexts.resume(request.context());
		if (decisions && resume) {
			throw new IllegalArgumentException("A request on session '" + sessionId
					+ "' either answers the tool calls the session parked on or continues its last turn, not both");
		}
		if (!decisions && !resume) {
			return START;
		}
		if (request.prompt().getInstructions().stream().anyMatch(UserMessage.class::isInstance)) {
			throw new IllegalArgumentException("A request that "
					+ (decisions ? "answers the tool calls" : "continues the last turn") + " of session '" + sessionId
					+ "' carries no new user message; send the text in the next call");
		}
		return decisions ? DECISIONS : RESUME;
	}

}
