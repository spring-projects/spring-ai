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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.session.SessionEvent;

/**
 * The history a {@link SessionMemoryAdvisor} sends with a model call, and how it becomes
 * the prompt, in two stages:
 * <ol>
 * <li>{@link #of}: which history to send, from the session's active view: the system
 * prompt stored in the session, if any, then the synthetic system messages (summaries of
 * older versions or custom compaction strategies), and every other message as the
 * conversation.</li>
 * <li>{@link #prompt}: the prompt, valid whatever the history: only the part of the
 * history the request does not already carry is prepended (a tool-calling loop re-sends
 * the turn so far each round), a tool call or tool result left without its counterpart is
 * dropped, and the system messages go first, each text once.</li>
 * </ol>
 * Pure functions over messages: nothing here reads or writes the session.
 *
 * @param system the system messages of the history, in log order
 * @param conversation the other messages of the history, in log order
 * @author Christian Tzolov
 * @since 2.1.0
 */
record PromptHistory(List<Message> system, List<Message> conversation) {

	// The advisor's category, so its logging configuration keeps applying
	private static final Logger logger = LoggerFactory.getLogger(SessionMemoryAdvisor.class);

	PromptHistory {
		system = List.copyOf(system);
		conversation = List.copyOf(conversation);
	}

	/**
	 * The history to send from the events of the active view. A {@code ChatClient} that
	 * sends its system prompt with each request stores none, so {@code systemPrompt} is
	 * {@code null} and only synthetic summaries come from the log.
	 * @param events the events read, oldest first
	 * @param systemPrompt the system prompt stored in the session (the latest stored
	 * system message, an opt-in), wherever it was found; {@code null} when there is none
	 */
	static PromptHistory of(List<SessionEvent> events, @Nullable Message systemPrompt) {
		List<Message> system = new ArrayList<>();
		if (systemPrompt != null) {
			system.add(systemPrompt);
		}
		List<Message> conversation = new ArrayList<>();
		for (SessionEvent event : events) {
			if (event.getMessageType() != MessageType.SYSTEM) {
				conversation.add(event.getMessage());
			}
			else if (event.isSynthetic()) {
				system.add(event.getMessage());
			}
			// a stored system message is the system prompt (passed in) or superseded
		}
		return new PromptHistory(system, conversation);
	}

	/**
	 * The latest stored system message among the events: the session's system prompt.
	 * Earlier ones are superseded; synthetic ones do not count.
	 */
	static @Nullable SessionEvent latestStoredSystemPrompt(List<SessionEvent> events) {
		for (int i = events.size() - 1; i >= 0; i--) {
			SessionEvent event = events.get(i);
			if (!event.isSynthetic() && event.getMessageType() == MessageType.SYSTEM) {
				return event;
			}
		}
		return null;
	}

	/**
	 * The prompt for the request's messages: the history the request does not carry, then
	 * the request's messages, with every system message first.
	 * @param requestMessages the messages of the request, as the tool-calling loop or the
	 * application sends them
	 * @param sessionId the session, for the warning about dropped tool messages
	 */
	List<Message> prompt(List<Message> requestMessages, String sessionId) {
		List<Message> requestConversation = requestMessages.stream()
			.filter(message -> !(message instanceof SystemMessage))
			.toList();
		// A tool-calling loop re-sends the turn so far, which the log ends with: prepend
		// only what comes before that overlap. System messages are left out on both
		// sides, they all go first
		List<Message> missing = this.conversation.subList(0,
				this.conversation.size() - overlap(this.conversation, requestConversation));

		List<Message> prompt = new ArrayList<>(this.system);
		prompt.addAll(withoutOrphanToolMessages(missing, requestConversation, sessionId));
		prompt.addAll(requestMessages);

		// Every system message first, in order, each text once: a stored system prompt
		// the request also sends goes once. Texts are never merged or rewritten, so the
		// system prompt stays byte-stable for prompt caching
		Set<String> seenSystemTexts = new HashSet<>();
		List<Message> systemMessages = prompt.stream()
			.filter(SystemMessage.class::isInstance)
			.filter(message -> seenSystemTexts.add(Objects.requireNonNullElse(message.getText(), "")))
			.toList();
		if (!systemMessages.isEmpty()) {
			prompt.removeIf(SystemMessage.class::isInstance);
			prompt.addAll(0, systemMessages);
		}
		return prompt;
	}

	/** The last user or tool response message, or {@code null} when there is none. */
	static @Nullable Message lastUserOrToolResponse(List<Message> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			Message message = messages.get(i);
			if (message instanceof UserMessage || message instanceof ToolResponseMessage) {
				return message;
			}
		}
		return null;
	}

	/**
	 * How many of the last {@code history} messages the request starts with: the largest
	 * {@code k} for which the history's last {@code k} messages are the request's first
	 * {@code k}, compared by {@link #sameContent}.
	 */
	private static int overlap(List<Message> history, List<Message> request) {
		for (int k = Math.min(history.size(), request.size()); k > 0; k--) {
			int offset = history.size() - k;
			int i = 0;
			while (i < k && sameContent(history.get(offset + i), request.get(i))) {
				i++;
			}
			if (i == k) {
				return k;
			}
		}
		return 0;
	}

	/**
	 * Compares two messages by what the model sees rather than by {@code equals}: the
	 * type, the text, the tool calls and the tool responses. A repository may not
	 * round-trip everything else (e.g. {@code JdbcSessionRepository} stores neither
	 * metadata such as the finish reason nor media), so a message reloaded from the
	 * session would otherwise never equal the one the tool-calling loop re-sends. Media
	 * is compared only when both messages carry it.
	 */
	private static boolean sameContent(Message a, Message b) {
		if (a == b) {
			return true;
		}
		if (a.getMessageType() != b.getMessageType()
				|| !Objects.requireNonNullElse(a.getText(), "").equals(Objects.requireNonNullElse(b.getText(), ""))) {
			return false;
		}
		if (a instanceof AssistantMessage assistantA && b instanceof AssistantMessage assistantB
				&& !assistantA.getToolCalls().equals(assistantB.getToolCalls())) {
			return false;
		}
		if (a instanceof ToolResponseMessage toolA && b instanceof ToolResponseMessage toolB
				&& !toolA.getResponses().equals(toolB.getResponses())) {
			return false;
		}
		if (a instanceof MediaContent mediaA && b instanceof MediaContent mediaB && !mediaA.getMedia().isEmpty()
				&& !mediaB.getMedia().isEmpty() && !mediaA.getMedia().equals(mediaB.getMedia())) {
			return false;
		}
		return true;
	}

	/**
	 * Returns {@code history} without tool messages that have no counterpart: an
	 * assistant message whose tool calls are not all answered by the tool responses that
	 * directly follow it (in the history, then in {@code following}, the request's own
	 * messages), or a tool response that does not answer the assistant tool call directly
	 * before it. Calls and responses are matched by id; one response message may answer
	 * several parallel calls, and a partially answered call is dropped together with its
	 * responses. The request's messages are never dropped.
	 */
	private static List<Message> withoutOrphanToolMessages(List<Message> history, List<Message> following,
			String sessionId) {
		if (history.stream()
			.noneMatch(m -> m instanceof ToolResponseMessage
					|| (m instanceof AssistantMessage assistant && assistant.hasToolCalls()))) {
			return history;
		}
		List<Message> all = new ArrayList<>(history);
		all.addAll(following);
		boolean[] keep = new boolean[history.size()];
		Arrays.fill(keep, true);
		for (int i = 0; i < history.size(); i++) {
			Message message = history.get(i);
			if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				Set<String> unanswered = new HashSet<>();
				assistant.getToolCalls().forEach(call -> unanswered.add(call.id()));
				for (int j = i + 1; j < all.size() && !unanswered.isEmpty(); j++) {
					if (!(all.get(j) instanceof ToolResponseMessage responses)) {
						break;
					}
					responses.getResponses().forEach(response -> unanswered.remove(response.id()));
				}
				keep[i] = unanswered.isEmpty();
			}
			else if (message instanceof ToolResponseMessage responses) {
				int call = i - 1;
				while (call >= 0 && history.get(call) instanceof ToolResponseMessage) {
					call--;
				}
				keep[i] = call >= 0 && keep[call] && history.get(call) instanceof AssistantMessage assistant
						&& assistant.hasToolCalls() && answers(assistant, responses);
			}
		}
		List<Message> kept = new ArrayList<>(history.size());
		int dropped = 0;
		for (int i = 0; i < history.size(); i++) {
			if (keep[i]) {
				kept.add(history.get(i));
			}
			else {
				dropped++;
			}
		}
		if (dropped > 0) {
			logger.warn("Dropped {} tool message(s) without their counterpart from the prompt for session [{}]. "
					+ "A messageTypes filter that hides TOOL events, a log an earlier version wrote with a "
					+ "MessageFilter that skipped them, or a loop that failed mid-way is the likely cause; the "
					+ "session log is unchanged", dropped, sessionId);
		}
		return kept;
	}

	/** Whether every response answers one of the assistant's tool calls. */
	private static boolean answers(AssistantMessage assistant, ToolResponseMessage responses) {
		Set<String> callIds = new HashSet<>();
		assistant.getToolCalls().forEach(call -> callIds.add(call.id()));
		return responses.getResponses().stream().allMatch(response -> callIds.contains(response.id()));
	}

}
