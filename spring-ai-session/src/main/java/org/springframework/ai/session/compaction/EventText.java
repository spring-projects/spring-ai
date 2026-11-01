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

package org.springframework.ai.session.compaction;

import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.session.SessionEvent;

/**
 * One line of text for an event or a message: what compaction counts tokens on, and what
 * a summarizer puts in its prompt. A plain user, assistant or system message renders as
 * {@code "Role: text"}; an {@link AssistantMessage} with tool calls as {@code "Assistant
 * [tool calls: name(args), ...]"}, after its text when it has one; a
 * {@link ToolResponseMessage} as {@code "Tool [responses: name -> data, ...]"}; an event
 * without a message as an empty string, since it is not sent to the model.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class EventText {

	private EventText() {
	}

	/** The text of an event; empty for an event that carries no message. */
	public static String of(SessionEvent event) {
		return event.hasMessage() ? of(event.getMessage()) : "";
	}

	/** The text of a message. */
	public static String of(Message message) {
		String role = switch (message.getMessageType()) {
			case USER -> "User";
			case ASSISTANT -> "Assistant";
			case SYSTEM -> "System";
			case TOOL -> "Tool";
		};
		if (message instanceof AssistantMessage am && am.hasToolCalls()) {
			String calls = am.getToolCalls()
				.stream()
				.map(tc -> tc.name() + "(" + tc.arguments() + ")")
				.collect(Collectors.joining(", "));
			String text = am.getText();
			return (text != null && !text.isBlank()) ? role + ": " + text + " [tool calls: " + calls + "]"
					: role + " [tool calls: " + calls + "]";
		}
		if (message instanceof ToolResponseMessage trm) {
			String responses = trm.getResponses()
				.stream()
				.map(r -> r.name() + " -> " + r.responseData())
				.collect(Collectors.joining(", "));
			return role + " [responses: " + responses + "]";
		}
		String text = message.getText();
		return role + ": " + (text != null ? text : "[no text content]");
	}

}
