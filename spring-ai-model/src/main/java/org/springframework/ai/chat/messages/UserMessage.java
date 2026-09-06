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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.ContentPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A message of the type 'user' passed as input Messages with the user role are from the
 * end-user or developer. They represent questions, prompts, or any input that you want
 * the generative to respond to.
 * <p>
 * The content of a user message can be expressed in two equivalent ways. The flat form
 * pairs a single text with a collection of media, which every provider serializes as text
 * followed by all media. The ordered form, set through
 * {@link Builder#contentParts(List)}, is a {@link ContentPart} list that interleaves text
 * and media in a caller-defined sequence — required by prompts where each media item must
 * directly follow its own text.
 * <p>
 * The two forms are always both available: {@link #getText()} and {@link #getMedia()} are
 * projections of {@link #getContentParts()} and vice versa, so a message built either way
 * reads correctly through either accessor and providers that cannot express ordering
 * degrade to text-then-media automatically.
 */
public class UserMessage extends AbstractMessage implements MediaContent {

	/**
	 * Separator used to join the text of multiple text parts into the flat
	 * {@link #getText()} projection. Deliberately a literal newline rather than
	 * {@code System.lineSeparator()}: the projection is persisted and participates in
	 * {@link #equals(Object)}, so it must not vary by platform.
	 */
	private static final String TEXT_PART_SEPARATOR = "\n";

	protected final List<Media> media;

	private final List<ContentPart> contentParts;

	public UserMessage(@Nullable String textContent) {
		this(textContent, new ArrayList<>(), Map.of());
	}

	private UserMessage(@Nullable String textContent, Collection<Media> media, Map<String, Object> metadata) {
		super(MessageType.USER, textContent, metadata);
		Assert.notNull(media, "media cannot be null");
		Assert.noNullElements(media, "media cannot have null elements");
		this.media = List.copyOf(media);
		this.contentParts = deriveContentParts(textContent, media);
	}

	private UserMessage(List<ContentPart> contentParts, Map<String, Object> metadata) {
		super(MessageType.USER, joinText(contentParts), metadata);
		this.contentParts = List.copyOf(contentParts);
		this.media = deriveMedia(contentParts);
	}

	public UserMessage(Resource resource) {
		this(MessageUtils.readResource(resource));
	}

	/**
	 * Projects the flat text-plus-media form onto an ordered content part list.
	 * Derivation keys on {@code textContent != null} rather than on the text being
	 * non-blank, so that a message carrying empty or whitespace-only text round-trips
	 * unchanged through {@link #copy()}.
	 */
	private static List<ContentPart> deriveContentParts(@Nullable String textContent, Collection<Media> media) {
		List<ContentPart> parts = new ArrayList<>(media.size() + 1);
		if (textContent != null) {
			parts.add(new ContentPart.TextPart(textContent));
		}
		for (Media mediaItem : media) {
			parts.add(new ContentPart.MediaPart(mediaItem));
		}
		return List.copyOf(parts);
	}

	/**
	 * Projects an ordered content part list onto the flat text form. Media parts
	 * contribute nothing: components that treat user text as a query (retrieval
	 * augmentation, for instance) would otherwise embed a media marker into that query.
	 */
	private static String joinText(List<ContentPart> contentParts) {
		Assert.notNull(contentParts, "contentParts cannot be null");
		StringBuilder text = new StringBuilder();
		boolean firstTextPart = true;
		for (ContentPart part : contentParts) {
			if (part instanceof ContentPart.TextPart textPart) {
				if (!firstTextPart) {
					text.append(TEXT_PART_SEPARATOR);
				}
				text.append(textPart.text());
				firstTextPart = false;
			}
		}
		return text.toString();
	}

	/**
	 * Projects an ordered content part list onto the flat media form, preserving the
	 * order in which the media parts appear.
	 */
	private static List<Media> deriveMedia(List<ContentPart> contentParts) {
		List<Media> media = new ArrayList<>();
		for (ContentPart part : contentParts) {
			if (part instanceof ContentPart.MediaPart mediaPart) {
				media.add(mediaPart.media());
			}
		}
		return List.copyOf(media);
	}

	@Override
	public String toString() {
		return "UserMessage{" + "content='" + getText() + '\'' + ", metadata=" + this.metadata + ", messageType="
				+ this.messageType + '}';
	}

	/**
	 * Returns the media of this message, in order. The returned list is unmodifiable:
	 * mutating it would desynchronize it from {@link #getContentParts()}.
	 */
	@Override
	public List<Media> getMedia() {
		return this.media;
	}

	/**
	 * Returns the ordered content of this message as text and media parts.
	 * <p>
	 * Never empty for a message that carries any text or media — for a message built in
	 * the flat form this returns the derived {@code [text, media…]} sequence — so
	 * providers need only one code path. The parts may include a text part whose text is
	 * empty or whitespace-only, which providers should skip when serializing since
	 * several model APIs reject empty text blocks.
	 * @return the unmodifiable, ordered content parts of this message
	 * @since 2.0.1
	 */
	public List<ContentPart> getContentParts() {
		return this.contentParts;
	}

	/**
	 * Whether this message's content needs the ordered form to be expressed faithfully,
	 * which is the case when the parts are anything other than a single text followed by
	 * media — for instance media followed by more text, or several separate texts.
	 * <p>
	 * Providers whose wire format offers a flat text-plus-media shortcut can keep taking
	 * it while this returns {@code false}, because flattening then loses nothing: the
	 * parts are exactly what {@link #getText()} and {@link #getMedia()} already convey.
	 * When it returns {@code true}, only {@link #getContentParts()} carries the full
	 * content.
	 * @return true if flattening this message's content to text-plus-media would lose
	 * information
	 * @since 2.0.1
	 */
	public boolean hasInterleavedContent() {
		boolean textSeen = false;
		boolean mediaSeen = false;
		for (ContentPart part : this.contentParts) {
			if (part instanceof ContentPart.TextPart) {
				if (textSeen || mediaSeen) {
					return true;
				}
				textSeen = true;
			}
			else {
				mediaSeen = true;
			}
		}
		return false;
	}

	public UserMessage copy() {
		return mutate().build();
	}

	public Builder mutate() {
		Builder builder = new Builder().metadata(Map.copyOf(getMetadata()));
		// Seed the flat slots first and the ordered content last: contentParts wins, but
		// the flat slots stay populated so that a caller who overwrites the text with
		// text(...) still keeps this message's media.
		builder.media(List.copyOf(getMedia()));
		if (this.textContent != null) {
			builder.text(this.textContent);
		}
		builder.contentParts(this.contentParts);
		return builder;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String textContent;

		private @Nullable Resource resource;

		private List<Media> media = new ArrayList<>();

		private @Nullable List<ContentPart> contentParts;

		private Map<String, Object> metadata = new HashMap<>();

		/**
		 * Sets the text of the message, discarding any content parts previously set on
		 * this builder. The resulting message carries the given text followed by whatever
		 * media is set, losing any interleaving — which is what lets components that
		 * rewrite user text keep working against ordered content.
		 */
		public Builder text(String textContent) {
			this.textContent = textContent;
			this.contentParts = null;
			return this;
		}

		/**
		 * Sets the text of the message from a resource, discarding any content parts
		 * previously set on this builder.
		 */
		public Builder text(Resource resource) {
			this.resource = resource;
			this.contentParts = null;
			return this;
		}

		/**
		 * Sets the media of the message, discarding any content parts previously set on
		 * this builder.
		 */
		public Builder media(List<Media> media) {
			this.media = media;
			this.contentParts = null;
			return this;
		}

		/**
		 * Sets the media of the message, discarding any content parts previously set on
		 * this builder.
		 */
		public Builder media(Media... media) {
			this.media = Arrays.asList(media);
			this.contentParts = null;
			return this;
		}

		/**
		 * Sets the ordered content of the message as text and media parts, taking
		 * precedence over any text and media previously set on this builder.
		 * @param contentParts the ordered content parts; must not be null or contain null
		 * elements
		 * @return this builder
		 * @since 2.0.1
		 */
		public Builder contentParts(List<ContentPart> contentParts) {
			Assert.notNull(contentParts, "contentParts cannot be null");
			Assert.noNullElements(contentParts, "contentParts cannot have null elements");
			this.contentParts = List.copyOf(contentParts);
			return this;
		}

		/**
		 * Sets the ordered content of the message as text and media parts, taking
		 * precedence over any text and media previously set on this builder.
		 * @param contentParts the ordered content parts; must not be null or contain null
		 * elements
		 * @return this builder
		 * @since 2.0.1
		 */
		public Builder contentParts(ContentPart... contentParts) {
			Assert.notNull(contentParts, "contentParts cannot be null");
			return contentParts(Arrays.asList(contentParts));
		}

		/**
		 * Appends text to the end of the message's content, preserving any ordering
		 * already established.
		 * <p>
		 * Where {@link #text(String)} replaces the content and flattens it, this adds a
		 * trailing text part to ordered content, or concatenates onto the existing text
		 * of flat content. Flat content is concatenated verbatim, with no separator
		 * inserted; for ordered content the appended text becomes its own part, so the
		 * flat {@link #getText()} projection separates it from the preceding text with a
		 * newline. Null and blank text are ignored.
		 * @param text the text to append
		 * @return this builder
		 * @since 2.0.1
		 */
		public Builder appendText(@Nullable String text) {
			if (!StringUtils.hasText(text)) {
				return this;
			}
			if (this.contentParts != null) {
				List<ContentPart> appended = new ArrayList<>(this.contentParts);
				appended.add(new ContentPart.TextPart(text));
				this.contentParts = List.copyOf(appended);
				return this;
			}
			this.textContent = (this.textContent != null) ? this.textContent + text : text;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public UserMessage build() {
			if (this.contentParts != null) {
				return new UserMessage(this.contentParts, this.metadata);
			}
			if (StringUtils.hasText(this.textContent) && this.resource != null) {
				throw new IllegalArgumentException("textContent and resource cannot be set at the same time");
			}
			else if (this.resource != null) {
				this.textContent = MessageUtils.readResource(this.resource);
			}
			return new UserMessage(this.textContent, this.media, this.metadata);
		}

	}

}
