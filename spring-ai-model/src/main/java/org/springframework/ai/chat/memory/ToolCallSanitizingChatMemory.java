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
 * A {@link ChatMemory} decorator that sanitizes tool-call round-trips out of long-term
 * chat history before they are persisted or read back.
 * <p>
 * When a {@link ToolCallingAdvisor} is wired in front of a
 * {@link MessageChatMemoryAdvisor} the inner advisor's streaming aggregator folds both
 * the assistant {@code tool_calls} chunk and the subsequent final text chunk into a
 * single {@link AssistantMessage}. On the next turn's replay, OpenAI-compatible backends
 * (DeepSeek, OpenAI, etc.) reject the request with HTTP 400:
 * {@code "An assistant message with 'tool_calls' must be followed
 * by tool messages responding to each 'tool_call_id'."} This breaks the most common
 * long-lived agent pattern: stream a tool-using turn, then ask a follow-up on the same
 * conversation id.
 * <p>
 * This decorator addresses the problem at the memory boundary, which is the cleanest
 * point to do so for two reasons:
 * <ol>
 * <li>The assistant- {@code tool_calls} ↔ {@link ToolResponseMessage} pairing is an
 * intra-turn protocol structure already managed by {@code ToolCallingAdvisor} within a
 * single turn. It does not belong in cross-turn long-term memory.</li>
 * <li>Tools have a real lifecycle (disabled, renamed, schema-changed, conditionally
 * registered per request). Replaying a stale {@code tool_call} against a tool that no
 * longer exists or has changed shape is a hallucination / 400 waiting to happen.</li>
 * </ol>
 * <p>
 * Sanitization rules:
 * <ul>
 * <li>Every {@link ToolResponseMessage} is dropped.</li>
 * <li>For any {@link AssistantMessage} carrying {@code toolCalls}, the {@code toolCalls}
 * are stripped and the text is kept. If the message has no text after stripping, the
 * message itself is dropped (an empty message would otherwise call
 * {@code delegate.add(conversationId, List.of())}, which strict repositories may
 * reject).</li>
 * </ul>
 * <p>
 * Read-back ({@link #get(String)}) and write-through ({@link #add(String, List)} and the
 * single-message convenience overload) are both sanitized, so the decorator is symmetric
 * and idempotent: sanitizing an already-sanitized transcript is a no-op.
 * <p>
 * <strong>Usage:</strong>
 *
 * <pre>{@code
 * ChatMemory sanitizing = new ToolCallSanitizingChatMemory(
 *         MessageWindowChatMemory.builder()
 *                 .chatMemoryRepository(new InMemoryChatMemoryRepository())
 *                 .build());
 *
 * ChatClient.builder(chatModel)
 *         .defaultAdvisors(
 *                 MessageChatMemoryAdvisor.builder(sanitizing).build(),
 *                 ToolCallingAdvisor.builder().build())
 *         .build();
 * }</pre>
 *
 * <p>
 * The decorator is intended as a drop-in replacement for any {@link ChatMemory} wired
 * into a {@code MessageChatMemoryAdvisor}. It does <em>not</em> change the in-round
 * tool-execution loop, the wire format the model sees during a single turn, or the
 * behaviour of any other advisor.
 *
 * @author ENG
 * @since 2.0.0
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/6340">spring-ai issue
 * #6340</a>
 */
public final class ToolCallSanitizingChatMemory implements ChatMemory {

	private final ChatMemory delegate;

	/**
	 * Wrap an existing {@link ChatMemory} with tool-call sanitization.
	 * @param delegate the underlying memory to sanitize on top of; must not be
	 * {@code null}.
	 */
	public ToolCallSanitizingChatMemory(ChatMemory delegate) {
		Assert.notNull(delegate, "delegate cannot be null");
		this.delegate = delegate;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		List<Message> sanitized = sanitize(messages);
		if (sanitized.isEmpty()) {
			// Nothing to persist. Avoid delegating an empty list to repositories that
			// reject it (e.g. strict JDBC-backed stores). The memory state is
			// unchanged, which is the intended semantics.
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
	 * Apply the sanitization rules to a list of messages in declaration order.
	 * <p>
	 * Pure function: does not mutate the input list, does not touch the delegate.
	 * @param messages the input messages; may be {@code null} (returns empty list) or
	 * contain nulls (skipped defensively).
	 * @return a new list with {@link ToolResponseMessage} entries removed and tool-call
	 * annotations stripped from {@link AssistantMessage} entries.
	 */
	static List<Message> sanitize(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return List.of();
		}
		List<Message> out = new ArrayList<>(messages.size());
		for (Message m : messages) {
			if (m == null) {
				continue;
			}
			if (m instanceof ToolResponseMessage) {
				continue;
			}
			if (m instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				String text = assistant.getText();
				if (text == null || text.isEmpty()) {
					// Nothing left to keep after stripping the tool calls. Drop the
					// entire message to avoid persisting an empty AssistantMessage.
					continue;
				}
				out.add(AssistantMessage.builder()
					.content(text)
					.properties(assistant.getMetadata())
					.toolCalls(List.of())
					.media(assistant.getMedia())
					.build());
				continue;
			}
			out.add(m);
		}
		return out;
	}

	/**
	 * Expose the wrapped delegate. Useful for tests and for downstream code that needs to
	 * bypass sanitization (e.g. an audit log of the raw transcript). Never mutate the
	 * returned reference's contents without understanding the implications.
	 * @return the underlying {@link ChatMemory}.
	 */
	public ChatMemory getDelegate() {
		return this.delegate;
	}

}
