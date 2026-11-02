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
 * a turn, or to answer the tool calls the session parked on
 * ({@link SessionTurnAdvisor#TOOL_CALL_DECISIONS_CONTEXT_KEY}). The latter carries no
 * user message: text belongs in a new turn.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
enum TurnIntent {

	/** Start a turn with the request's user message. */
	START,

	/** Answer the tool calls the session parked on with the request's decisions. */
	DECISIONS;

	/**
	 * Reads the intent of the request and checks it.
	 * @throws IllegalArgumentException if the request carries decisions and a user
	 * message
	 */
	static TurnIntent of(String sessionId, ChatClientRequest request) {
		if (SessionContexts.decisions(request.context()) == null) {
			return START;
		}
		if (request.prompt().getInstructions().stream().anyMatch(UserMessage.class::isInstance)) {
			throw new IllegalArgumentException("A request that answers the tool calls of session '" + sessionId
					+ "' carries no new user message; send the text in the next call");
		}
		return DECISIONS;
	}

}
