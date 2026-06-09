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

package org.springframework.ai.chat.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.Assert;

/**
 * A {@link ChatMemory} decorator that strips tool round-trips from the conversation
 * history before they are persisted or replayed.
 * <p>
 * The tool-call protocol inside a single turn — the pairing of
 * {@code AssistantMessage(tool_calls=[...])} with the following
 * {@link ToolResponseMessage} — is intra-turn structure managed by
 * {@code ToolCallAdvisor} (or any other tool-calling loop). It is not part of the
 * cross-turn long-term chat history, and replaying it on the next turn is actively
 * harmful: OpenAI-compatible backends reject it with HTTP 400
 * ({@code "An assistant message with 'tool_calls' must be followed by tool messages
 * responding to each 'tool_call_id'"}), and tools may have been disabled, renamed, or had
 * their schema changed between turns — replaying a stale {@code tool_call} against a tool
 * that no longer exists is a hallucination waiting to happen.
 * <p>
 * Sanitization rules, applied on every {@code add(...)} and {@code get(...)}:
 * <ol>
 * <li>Drop every {@link ToolResponseMessage}.</li>
 * <li>For any {@link AssistantMessage} carrying {@code toolCalls}, keep only the text
 * content. The {@code toolCalls} are discarded.</li>
 * <li>If, after stripping {@code toolCalls}, an {@link AssistantMessage} has no text
 * content (the common case for a pure tool-call intermediate frame), drop it
 * entirely.</li>
 * </ol>
 * <p>
 * Typical usage: wrap an existing memory in the sanitizing decorator and pass that to
 * {@code MessageChatMemoryAdvisor.builder(...)}:
 *
 * <pre>{@code
 * ChatMemory memory = new ToolCallSanitizingChatMemory(
 *         MessageWindowChatMemory.builder()
 *                 .chatMemoryRepository(new InMemoryChatMemoryRepository())
 *                 .build());
 * }</pre>
 *
 * <p>
 * This addresses the bug described in
 * <a href= "https://github.com/spring-projects/spring-ai/issues/6340">spring-ai#6340</a>:
 * {@code stream() + ToolCallAdvisor.streamToolCallResponses(true) +
 * MessageChatMemoryAdvisor} used to persist a malformed
 * {@code AssistantMessage(text+tool_calls, no ToolResponseMessage)} to memory, which then
 * failed with HTTP 400 on the next turn's replay.
 *
 * @author redinside-dev
 * @since 2.0.0
 */
public final class ToolCallSanitizingChatMemory implements ChatMemory {

	private final ChatMemory delegate;

	/**
	 * Wrap the given delegate {@link ChatMemory} with tool-round-trip sanitization.
	 * @param delegate the underlying memory to sanitize writes/reads for; must not be
	 * {@code null}
	 */
	public ToolCallSanitizingChatMemory(ChatMemory delegate) {
		Assert.notNull(delegate, "delegate ChatMemory cannot be null");
		this.delegate = delegate;
	}

	/**
	 * Returns the wrapped delegate memory. Useful for tests and for unwrapping in
	 * diagnostics.
	 * @return the underlying {@link ChatMemory} that this decorator sanitizes
	 */
	public ChatMemory getDelegate() {
		return this.delegate;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		List<Message> sanitized = sanitize(messages);
		if (sanitized.isEmpty()) {
			return;
		}
		this.delegate.add(conversationId, sanitized);
	}

	@Override
	public void add(String conversationId, Message message) {
		// Override the default delegation in ChatMemory: a single empty-after-sanitize
		// message would otherwise turn into delegate.add(conversationId, []), which
		// some implementations (and strict repositories) reject. Drop it silently
		// here.
		List<Message> sanitized = sanitize(List.of(message));
		if (sanitized.isEmpty()) {
			return;
		}
		this.delegate.add(conversationId, sanitized);
	}

	@Override
	public List<Message> get(String conversationId) {
		return sanitize(this.delegate.get(conversationId));
	}

	@Override
	public void clear(String conversationId) {
		this.delegate.clear(conversationId);
	}

	/**
	 * Apply sanitization rules to a list of messages. Visible for testing and reuse.
	 * <ul>
	 * <li>Drop every {@link ToolResponseMessage}.</li>
	 * <li>For any {@link AssistantMessage} with {@code toolCalls}, keep the text only; if
	 * no text remains, drop the message.</li>
	 * <li>All other messages pass through unchanged.</li>
	 * </ul>
	 * @param input the messages to sanitize (may be {@code null} or empty)
	 * @return a new list of sanitized messages; never {@code null}
	 */
	static List<Message> sanitize(List<Message> input) {
		if (input == null || input.isEmpty()) {
			return List.of();
		}
		List<Message> out = new ArrayList<>(input.size());
		for (Message m : input) {
			if (m instanceof ToolResponseMessage) {
				continue;
			}
			if (m instanceof AssistantMessage am && am.hasToolCalls()) {
				String text = am.getText();
				if (text == null || text.isEmpty()) {
					// Pure tool-call intermediate frame, no replayable text.
					continue;
				}
				out.add(AssistantMessage.builder()
					.content(text)
					.properties(am.getMetadata())
					.media(am.getMedia())
					.build());
				continue;
			}
			out.add(m);
		}
		return out;
	}

}
