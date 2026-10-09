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

package org.springframework.ai.deepseek;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.content.Media;
import org.springframework.util.StringUtils;

/**
 * DeepSeek-specific {@link AssistantMessage} that exposes the reasoning content and the
 * prefix completion flag through dedicated accessors.
 * <p>
 * Both are views over state the plain {@link AssistantMessage} carries: the reasoning
 * content is the text of the {@link ReasoningPart}s of the message (see
 * {@link #getReasoning()}), and the prefix flag is the
 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY} metadata entry. {@link DeepSeekChatModel}
 * still returns this type, so existing casts keep working.
 *
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Sun Yuhan
 * @author guan xu
 * @author Dimitar Proynov
 * @deprecated since 2.1.0 for removal: read the reasoning through
 * {@link AssistantMessage#getReasoning()}, and build a prefix message as an
 * {@link AssistantMessage} whose metadata holds {@code true} under
 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY}
 */
@Deprecated(since = "2.1.0", forRemoval = true)
public class DeepSeekAssistantMessage extends AssistantMessage {

	/**
	 * The value set through {@link #setReasoningContent(String)}, which the immutable
	 * parts cannot reflect; only meaningful when {@link #isReasoningContentOverridden}.
	 */
	private @Nullable String reasoningContentOverride;

	private boolean isReasoningContentOverridden;

	protected DeepSeekAssistantMessage(@Nullable String content, @Nullable String reasoningContent,
			@Nullable Boolean prefix, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media) {
		this(partsOf(content, reasoningContent, toolCalls, media), withPrefix(properties, prefix));
	}

	/**
	 * Creates a message from an ordered list of parts.
	 * @param parts the parts, in order
	 * @param properties the message metadata, holding the prefix flag under
	 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY}, if any
	 * @since 2.1.0
	 */
	protected DeepSeekAssistantMessage(List<MessagePart> parts, Map<String, Object> properties) {
		super(parts, properties);
	}

	/**
	 * The parts of the legacy constructor arguments, in the order DeepSeek produces them:
	 * reasoning, text, tool calls, then media.
	 */
	private static List<MessagePart> partsOf(@Nullable String content, @Nullable String reasoningContent,
			List<ToolCall> toolCalls, List<Media> media) {
		List<MessagePart> parts = new ArrayList<>();
		if (StringUtils.hasLength(reasoningContent)) {
			parts.add(ReasoningPart.of(reasoningContent));
		}
		if (content != null) {
			parts.add(TextPart.of(content));
		}
		toolCalls.forEach(toolCall -> parts.add(ToolCallPart.of(toolCall)));
		media.forEach(medium -> parts.add(MediaPart.of(medium)));
		return parts;
	}

	private static Map<String, Object> withPrefix(Map<String, Object> properties, @Nullable Boolean prefix) {
		if (prefix == null) {
			return properties;
		}
		Map<String, Object> result = new LinkedHashMap<>(properties);
		result.put(DeepSeekChatModel.PREFIX_METADATA_KEY, prefix);
		return result;
	}

	/**
	 * The prefix completion flag, read from the
	 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY} metadata entry.
	 * @return the flag, or {@code null} when it is not set
	 */
	public @Nullable Boolean getPrefix() {
		return (getMetadata().get(DeepSeekChatModel.PREFIX_METADATA_KEY) instanceof Boolean prefix) ? prefix : null;
	}

	/**
	 * Sets the prefix completion flag, as the
	 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY} metadata entry.
	 * @param prefix the flag
	 */
	public void setPrefix(Boolean prefix) {
		getMetadata().put(DeepSeekChatModel.PREFIX_METADATA_KEY, prefix);
	}

	/**
	 * The reasoning content: the joined text of the {@link ReasoningPart}s, unless it was
	 * overridden through {@link #setReasoningContent(String)}.
	 * @return the reasoning content, or {@code null} when there is none
	 */
	public @Nullable String getReasoningContent() {
		if (this.isReasoningContentOverridden) {
			return this.reasoningContentOverride;
		}
		List<ReasoningPart> reasoning = getReasoning();
		if (reasoning.isEmpty()) {
			return null;
		}
		return reasoning.stream().map(ReasoningPart::text).filter(Objects::nonNull).collect(Collectors.joining());
	}

	/**
	 * Overrides the reasoning content returned by {@link #getReasoningContent()} and sent
	 * back to DeepSeek. The parts of a message are immutable, so they keep the original
	 * {@link ReasoningPart}s; {@link #mutate()} and {@link #copy()} turn the override
	 * into the reasoning part of the new message.
	 * <p>
	 * To replace the reasoning parts themselves, build a new message instead:
	 * {@code message.mutate().reasoningContent("...").build()}.
	 * @param reasoningContent the reasoning content
	 */
	public void setReasoningContent(@Nullable String reasoningContent) {
		this.reasoningContentOverride = reasoningContent;
		this.isReasoningContentOverridden = true;
	}

	/**
	 * Whether {@link #setReasoningContent(String)} overrode the reasoning of the parts,
	 * so that {@link DeepSeekChatModel} replays {@link #getReasoningContent()} instead.
	 */
	boolean isReasoningContentOverridden() {
		return this.isReasoningContentOverridden;
	}

	/**
	 * A builder pre-populated with this message's parts and metadata, the prefix flag
	 * included. A reasoning content set through {@link #setReasoningContent(String)}
	 * replaces the {@link ReasoningPart}s.
	 */
	@Override
	public Builder mutate() {
		Builder builder = builder().parts(getParts()).properties(getMetadata());
		if (this.isReasoningContentOverridden) {
			builder.reasoningContent(this.reasoningContentOverride);
		}
		return builder;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DeepSeekAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(getReasoningContent(), that.getReasoningContent());
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), getReasoningContent());
	}

	@Override
	public String toString() {
		return "DeepSeekAssistantMessage [messageType=" + this.messageType + ", parts=" + getParts() + ", textContent="
				+ getText() + ", reasoningContent=" + getReasoningContent() + ", prefix=" + getPrefix() + ", metadata="
				+ this.metadata + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	public static class AbstractBuilder<B extends AbstractBuilder<B>> extends AssistantMessage.Builder<B> {

		protected @Nullable Boolean prefix;

		protected @Nullable String reasoningContent;

		private boolean reasoningContentSet;

		/**
		 * Sets the prefix completion flag, stored as the
		 * {@link DeepSeekChatModel#PREFIX_METADATA_KEY} metadata entry.
		 * @param prefix the flag, or {@code null} to keep the metadata as it is
		 * @return this builder
		 */
		public B prefix(@Nullable Boolean prefix) {
			this.prefix = prefix;
			return self();
		}

		/**
		 * Sets the reasoning content. Like {@link #content(String)} for the text, it
		 * replaces the {@link ReasoningPart}s of the message, or goes before every other
		 * part when there is none; a {@code null} or empty value removes them.
		 * @param reasoningContent the reasoning content
		 * @return this builder
		 */
		public B reasoningContent(@Nullable String reasoningContent) {
			this.reasoningContent = reasoningContent;
			this.reasoningContentSet = true;
			return self();
		}

		@Override
		protected boolean supportsParts() {
			return true;
		}

		@Override
		protected List<MessagePart> buildParts() {
			List<MessagePart> parts = super.buildParts();
			if (this.reasoningContentSet) {
				List<ReasoningPart> reasoning = StringUtils.hasLength(this.reasoningContent)
						? List.of(ReasoningPart.of(this.reasoningContent)) : List.of();
				replace(parts, ReasoningPart.class, reasoning, 0);
			}
			return parts;
		}

		@Override
		public DeepSeekAssistantMessage build() {
			return new DeepSeekAssistantMessage(buildParts(), withPrefix(this.properties, this.prefix));
		}

	}

}
