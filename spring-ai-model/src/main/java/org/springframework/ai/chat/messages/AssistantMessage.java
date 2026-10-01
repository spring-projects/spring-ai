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
		return select(ReasoningPart.class).toList();
	}

	public List<ToolCall> getToolCalls() {
		return select(ToolCallPart.class).map(ToolCallPart::toolCall).toList();
	}

	public boolean hasToolCalls() {
		return getParts().stream().anyMatch(ToolCallPart.class::isInstance);
	}

	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	public AssistantMessage copy() {
		return mutate().build();
	}

	/**
	 * A builder pre-populated with this message's parts and metadata. Calling
	 * {@link Builder#content(String)}, {@link Builder#toolCalls(List)} or
	 * {@link Builder#media(List)} on it replaces the corresponding parts in place, so
	 * rewriting the text keeps the reasoning and tool calls where they were and vice
	 * versa.
	 * @return the builder
	 */
	public Builder<?> mutate() {
		return builder().parts(getParts()).properties(getMetadata());
	}

	@Override
	public String toString() {
		return "AssistantMessage [messageType=" + this.messageType + ", parts=" + getParts() + ", textContent="
				+ getText() + ", metadata=" + this.metadata + "]";
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
	 * {@link #reasoning(ReasoningPart)} keep the order they were added in. The legacy
	 * setters replace the parts of their type at {@link #build()} time:
	 * {@link #content(String)} replaces the text parts, {@link #toolCalls(List)} the tool
	 * call parts and {@link #media(List)} the media parts. The new parts take the place
	 * of the first part of that type, or go where the legacy order has them when there is
	 * none (text before tool calls and media, tool calls before media), and a
	 * {@code null} content or an empty list removes the parts of that type. The setters
	 * take effect at {@link #build()} time, so they also replace parts of their type
	 * added after the setter was called. A builder without explicit parts therefore
	 * produces the legacy order (text, tool calls, media) whatever the setter call order.
	 *
	 * @param <B> the concrete builder type, for subclass builders
	 */
	public static class Builder<B extends Builder<B>> {

		protected final List<MessagePart> explicitParts = new ArrayList<>();

		/**
		 * The text set through {@link #content(String)}. A subclass builder must set it
		 * through that method: on a builder that also holds parts, a value assigned to
		 * the field directly is ignored.
		 */
		protected @Nullable String content;

		protected Map<String, Object> properties = Map.of();

		/**
		 * The tool calls set through {@link #toolCalls(List)}. As for {@link #content}, a
		 * subclass builder must set them through that method.
		 */
		protected List<ToolCall> toolCalls = List.of();

		/**
		 * The media set through {@link #media(List)}. As for {@link #content}, a subclass
		 * builder must set them through that method.
		 */
		protected List<Media> media = List.of();

		private boolean contentSet;

		private boolean toolCallsSet;

		private boolean mediaSet;

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
		 * The parts this builder would put in the message: the explicit parts, with the
		 * text, tool call and media parts replaced by the corresponding legacy setters
		 * that were called. A setter whose part type is not among the explicit parts puts
		 * its parts where the legacy order has them: the text before the first tool call
		 * or media part, the tool calls before the first media part, the media at the
		 * end.
		 * @return the parts, in message order
		 * @since 2.1.0
		 */
		protected List<MessagePart> buildParts() {
			if (this.explicitParts.isEmpty()) {
				return legacyParts(this.content, this.toolCalls, this.media);
			}
			List<MessagePart> parts = new ArrayList<>(this.explicitParts);
			if (this.contentSet) {
				List<TextPart> textParts = (this.content != null) ? List.of(TextPart.of(this.content)) : List.of();
				replace(parts, TextPart.class, textParts, firstIndexOf(parts, ToolCallPart.class, MediaPart.class));
			}
			if (this.toolCallsSet) {
				Assert.notNull(this.toolCalls, "Tool calls must not be null");
				List<ToolCallPart> toolCallParts = this.toolCalls.stream().map(ToolCallPart::of).toList();
				replace(parts, ToolCallPart.class, toolCallParts, firstIndexOf(parts, MediaPart.class));
			}
			if (this.mediaSet) {
				Assert.notNull(this.media, "Media must not be null");
				List<MediaPart> mediaParts = this.media.stream().map(MediaPart::of).toList();
				replace(parts, MediaPart.class, mediaParts, parts.size());
			}
			return parts;
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
			this.contentSet = true;
			return self();
		}

		public B properties(Map<String, Object> properties) {
			this.properties = properties;
			return self();
		}

		public B toolCalls(List<ToolCall> toolCalls) {
			this.toolCalls = toolCalls;
			this.toolCallsSet = true;
			return self();
		}

		public B media(List<Media> media) {
			this.media = media;
			this.mediaSet = true;
			return self();
		}

		public AssistantMessage build() {
			return new AssistantMessage(buildParts(), this.properties);
		}

	}

}
