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

package org.springframework.ai.chat.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.Assert;

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 * <p>
 * The message content is an ordered list of {@link MessagePart}s, inherited from
 * {@link AbstractMessage} so that {@link UserMessage} can carry the same kind of content.
 * The text, tool calls, media and reasoning accessors are views over that list:
 * {@link #getText()} joins the {@link TextPart}s, {@link #getToolCalls()} selects the
 * {@link ToolCallPart}s, {@link #getMedia()} selects the {@link MediaPart}s and
 * {@link #getReasoning()} selects the {@link ReasoningPart}s, in part order. Because
 * reasoning is a part like any other, a message preserves the exact interleaving some
 * providers stream between reasoning, tool calls, text and media (for example a reasoning
 * block immediately followed by the tool call it justified). Messages built through the
 * legacy constructor or builder setters get their parts in the legacy order: text, tool
 * calls, media.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author guan xu
 * @since 1.0.0
 */
public class AssistantMessage extends AbstractMessage implements MediaContent {

	private final List<ToolCall> toolCalls;

	private final List<ReasoningPart> reasoning;

	/**
	 * The media of this message, derived from its {@link MediaPart}s.
	 * @deprecated since 2.1.0 in favor of {@link #getMedia()}; kept so existing
	 * subclasses that read the field keep compiling. Will be removed in a future release.
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	protected final List<Media> media;

	public AssistantMessage(@Nullable String content) {
		this(content, Map.of(), List.of(), List.of());
	}

	protected AssistantMessage(@Nullable String content, Map<String, Object> properties, List<ToolCall> toolCalls,
			List<Media> media) {
		this(legacyParts(content, toolCalls, media), properties);
	}

	/**
	 * Creates a message from an ordered list of content parts, which may include
	 * {@link ReasoningPart}s interleaved with the rest of the content.
	 * @param parts the parts, in order
	 * @param properties the message metadata
	 * @since 2.1.0
	 */
	protected AssistantMessage(List<MessagePart> parts, Map<String, Object> properties) {
		super(MessageType.ASSISTANT, parts, properties);
		this.media = select(MediaPart.class).map(MediaPart::media).toList();
		this.toolCalls = select(ToolCallPart.class).map(ToolCallPart::toolCall).toList();
		this.reasoning = select(ReasoningPart.class).toList();
	}

	private static List<MessagePart> legacyParts(@Nullable String content, List<ToolCall> toolCalls,
			List<Media> media) {
		Assert.notNull(toolCalls, "Tool calls must not be null");
		Assert.notNull(media, "Media must not be null");
		List<MessagePart> parts = new ArrayList<>();
		if (content != null) {
			parts.add(TextPart.of(content));
		}
		for (ToolCall toolCall : toolCalls) {
			parts.add(ToolCallPart.of(toolCall));
		}
		for (Media medium : media) {
			parts.add(MediaPart.of(medium));
		}
		return parts;
	}

	/**
	 * The reasoning parts of this message, in part order.
	 * @return an unmodifiable list of reasoning parts
	 * @since 2.1.0
	 */
	public List<ReasoningPart> getReasoning() {
		return this.reasoning;
	}

	public List<ToolCall> getToolCalls() {
		return this.toolCalls;
	}

	public boolean hasToolCalls() {
		return !this.toolCalls.isEmpty();
	}

	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	public AssistantMessage copy() {
		return mutate().build();
	}

	public Builder<?> mutate() {
		return builder().parts(getParts()).properties(getMetadata());
	}

	@Override
	public String toString() {
		return "AssistantMessage [messageType=" + this.messageType + ", parts=" + getParts() + ", textContent="
				+ this.textContent + ", metadata=" + this.metadata + "]";
	}

	public static Builder<?> builder() {
		return new Builder<>();
	}

	public record ToolCall(String id, String type, String name, String arguments) {

	}

	/**
	 * Builder for {@link AssistantMessage}.
	 * <p>
	 * Parts added through {@link #part(MessagePart)}, {@link #parts(List)} and
	 * {@link #reasoning(ReasoningPart)} come first, in the order they were added. The
	 * legacy setters ({@link #content(String)}, {@link #toolCalls(List)},
	 * {@link #media(List)}) are stored and materialized after them at {@link #build()}
	 * time in the legacy order (text, tool calls, media), so the setter call order does
	 * not affect the result.
	 *
	 * @param <B> the concrete builder type, for subclass builders
	 */
	public static class Builder<B extends Builder<B>> {

		protected final List<MessagePart> explicitParts = new ArrayList<>();

		protected @Nullable String content;

		protected Map<String, Object> properties = Map.of();

		protected List<ToolCall> toolCalls = List.of();

		protected List<Media> media = List.of();

		protected Builder() {
		}

		@SuppressWarnings("unchecked")
		protected B self() {
			return (B) this;
		}

		/**
		 * Appends a content part.
		 * @param part the part
		 * @return this builder
		 * @since 2.1.0
		 */
		public B part(MessagePart part) {
			Assert.notNull(part, "Content part must not be null");
			if (!supportsParts()) {
				throw new UnsupportedOperationException(getClass().getName()
						+ " overrides build() through the legacy constructor and would drop explicit parts;"
						+ " override supportsParts() to return true and build from buildParts()");
			}
			this.explicitParts.add(part);
			return self();
		}

		/**
		 * Whether {@link #build()} of this builder honors {@link #part(MessagePart)},
		 * {@link #parts(List)} and {@link #reasoning(ReasoningPart)}. The base builder
		 * does. A subclass builder that overrides {@link #build()} must override this
		 * method to return {@code true} and construct its message from
		 * {@link #buildParts()}; otherwise {@link #part(MessagePart)} throws so parts are
		 * never dropped silently.
		 * @return whether explicit parts are supported
		 * @since 2.1.0
		 */
		protected boolean supportsParts() {
			return getClass() == Builder.class;
		}

		/**
		 * The parts this builder would put in the message: explicit parts first, then the
		 * legacy text, tool calls and media in that order.
		 * @return the parts, in message order
		 * @since 2.1.0
		 */
		protected List<MessagePart> buildParts() {
			List<MessagePart> allParts = new ArrayList<>(this.explicitParts);
			allParts.addAll(legacyParts(this.content, this.toolCalls, this.media));
			return allParts;
		}

		/**
		 * Appends content parts in order.
		 * @param parts the parts
		 * @return this builder
		 * @since 2.1.0
		 */
		public B parts(List<MessagePart> parts) {
			Assert.notNull(parts, "Content parts must not be null");
			for (MessagePart part : parts) {
				part(part);
			}
			return self();
		}

		/**
		 * Appends a reasoning part. Equivalent to {@link #part(MessagePart)}, since
		 * {@link ReasoningPart} is a {@link MessagePart}.
		 * @param reasoning the reasoning part
		 * @return this builder
		 * @since 2.1.0
		 */
		public B reasoning(ReasoningPart reasoning) {
			return part(reasoning);
		}

		/**
		 * Appends reasoning parts in order.
		 * @param reasoning the reasoning parts
		 * @return this builder
		 * @since 2.1.0
		 */
		public B reasoning(List<ReasoningPart> reasoning) {
			Assert.notNull(reasoning, "Reasoning must not be null");
			for (ReasoningPart part : reasoning) {
				reasoning(part);
			}
			return self();
		}

		public B content(@Nullable String content) {
			this.content = content;
			return self();
		}

		public B properties(Map<String, Object> properties) {
			this.properties = properties;
			return self();
		}

		public B toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			return self();
		}

		public B media(List<Media> media) {
			this.media = media;
			return self();
		}

		public AssistantMessage build() {
			return new AssistantMessage(buildParts(), this.properties);
		}

	}

}
