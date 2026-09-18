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

import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.util.Assert;

/**
 * The ToolResponseMessage class represents a message with a function content in a chat
 * application.
 * <p>
 * The message content is an ordered list of {@link MessagePart}s, one
 * {@link ToolResultPart} per tool result, so a result can carry more than a string, for
 * example media or an error flag. {@link #getResponses()} is a view over the parts that
 * keeps only the text of each result. Messages built through the legacy constructor or
 * the {@link Builder#responses(List)} setter get one text-only result part per response,
 * so existing code keeps working unchanged.
 *
 * @author Christian Tzolov
 * @author Eric Bottard
 * @since 1.0.0
 */
public class ToolResponseMessage extends AbstractMessage {

	/**
	 * The responses of this message, derived from its {@link ToolResultPart}s.
	 * @deprecated since 2.1.0 in favor of {@link #getResponses()}; kept so existing
	 * subclasses that read the field keep compiling. Will be removed in a future release.
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	protected final List<ToolResponse> responses;

	protected ToolResponseMessage(List<ToolResponse> responses, Map<String, Object> metadata) {
		this(metadata, legacyParts(responses));
	}

	/**
	 * Creates a message from an ordered list of parts. The metadata comes first because
	 * the legacy constructor already takes a list and a map in the other order and the
	 * two would otherwise have the same erasure.
	 * @param metadata the message metadata
	 * @param parts the parts, in order
	 * @since 2.1.0
	 */
	protected ToolResponseMessage(Map<String, Object> metadata, List<MessagePart> parts) {
		super(MessageType.TOOL, parts, metadata);
		this.responses = select(ToolResultPart.class).map(ToolResultPart::toToolResponse).toList();
	}

	private static List<MessagePart> legacyParts(List<ToolResponse> responses) {
		Assert.notNull(responses, "responses must not be null");
		List<MessagePart> parts = new ArrayList<>(responses.size());
		for (ToolResponse response : responses) {
			parts.add(ToolResultPart.of(response));
		}
		return parts;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The tool results of this message, in part order.
	 * @return an unmodifiable list of result parts
	 * @since 2.1.0
	 */
	public List<ToolResultPart> getResults() {
		return select(ToolResultPart.class).toList();
	}

	/**
	 * The tool responses of this message: the text of each {@link ToolResultPart}, in
	 * part order. Content a string cannot carry, such as media, is not included; read
	 * {@link #getResults()} for it.
	 * @return an unmodifiable list of responses
	 */
	public List<ToolResponse> getResponses() {
		return this.responses;
	}

	/**
	 * Tool response messages never had a {@code null} text, even with no responses at
	 * all; only {@link AbstractMessage}'s generic rule for an empty parts list would say
	 * otherwise.
	 */
	@Override
	public String getText() {
		String text = super.getText();
		return (text != null) ? text : "";
	}

	/**
	 * A builder pre-populated with this message's parts and metadata.
	 * @return the builder
	 * @since 2.1.0
	 */
	public Builder mutate() {
		return new Builder().parts(getParts()).metadata(Map.copyOf(getMetadata()));
	}

	@Override
	public String toString() {
		return "ToolResponseMessage{" + "parts=" + getParts() + ", messageType=" + this.messageType + ", metadata="
				+ this.metadata + '}';
	}

	/**
	 * A tool response.
	 *
	 * @param id the id of the tool call this response answers
	 * @param name the tool name
	 * @param responseData the response text
	 */
	public record ToolResponse(String id, String name, String responseData) {

	}

	/**
	 * Builder for {@link ToolResponseMessage}.
	 * <p>
	 * Parts added through {@link #result(ToolResultPart)}, {@link #results(List)} and
	 * {@link #parts(List)} keep their order and come first. The legacy
	 * {@link #responses(List)} setter is materialized after them at {@link #build()}, one
	 * text-only {@link ToolResultPart} per response.
	 */
	public static final class Builder {

		private final List<MessagePart> explicitParts = new ArrayList<>();

		private List<ToolResponse> responses = List.of();

		private Map<String, Object> metadata = Map.of();

		private Builder() {
		}

		/**
		 * Adds a tool result.
		 * @param result the result part
		 * @return this builder
		 * @since 2.1.0
		 */
		public Builder result(ToolResultPart result) {
			Assert.notNull(result, "result must not be null");
			this.explicitParts.add(result);
			return this;
		}

		/**
		 * Adds tool results, in order.
		 * @param results the result parts
		 * @return this builder
		 * @since 2.1.0
		 */
		public Builder results(List<ToolResultPart> results) {
			Assert.notNull(results, "results must not be null");
			Assert.noNullElements(results, "results must not contain null elements");
			this.explicitParts.addAll(results);
			return this;
		}

		/**
		 * Adds parts, in order, for example when rebuilding a persisted message.
		 * @param parts the parts
		 * @return this builder
		 * @since 2.1.0
		 */
		public Builder parts(List<MessagePart> parts) {
			Assert.notNull(parts, "parts must not be null");
			Assert.noNullElements(parts, "parts must not contain null elements");
			this.explicitParts.addAll(parts);
			return this;
		}

		public Builder responses(List<ToolResponse> responses) {
			this.responses = responses;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public ToolResponseMessage build() {
			List<MessagePart> parts = new ArrayList<>(this.explicitParts);
			parts.addAll(legacyParts(this.responses));
			return new ToolResponseMessage(this.metadata, parts);
		}

	}

}
