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

package org.springframework.ai.chat.messages.part;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.util.Assert;

/**
 * The result of one tool call. The nested {@code content} lets a result carry media,
 * which a plain string cannot.
 *
 * @param id the id of the tool call this result answers, or {@code null} when the
 * provider returned no id (Ollama can)
 * @param name the tool name, or {@code null} when unknown
 * @param content the result content, copied and unmodifiable
 * @param isError whether the tool reported an error
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record ToolResultPart(@Nullable String id, @Nullable String name, List<MessagePart> content, boolean isError,
		@Nullable OpaquePayload payload, Map<String, String> attributes) implements MessagePart {

	public ToolResultPart {
		Assert.notNull(content, "content must not be null");
		Assert.notNull(attributes, "attributes must not be null");
		content = List.copyOf(content);
		attributes = Map.copyOf(attributes);
	}

	@Override
	public ToolResultPart withAttributes(Map<String, String> attributes) {
		return new ToolResultPart(this.id, this.name, this.content, this.isError, this.payload, attributes);
	}

	/**
	 * Wraps a legacy {@link ToolResponse} as a result part with a single text part. A
	 * response without text (a {@code null} response data, which the tool execution
	 * allows) becomes a part without content, so it converts back to the same response.
	 * @param response the tool response
	 * @return the part
	 */
	// ToolResponse leaves its components unannotated but carries null values in practice
	@SuppressWarnings("NullAway")
	public static ToolResultPart of(ToolResponse response) {
		Assert.notNull(response, "response must not be null");
		List<MessagePart> content = response.responseData() != null ? List.of(TextPart.of(response.responseData()))
				: List.of();
		return new ToolResultPart(response.id(), response.name(), content, false, null, Map.of());
	}

	/**
	 * Converts this part back to a legacy {@link ToolResponse} by joining its text
	 * content. Non-text content is dropped, since a {@link ToolResponse} can only carry a
	 * string, and a part without any text content yields a {@code null} response data.
	 * @return the tool response
	 */
	// ToolResponse leaves id and name unannotated but carries null values in practice
	@SuppressWarnings("NullAway")
	public ToolResponse toToolResponse() {
		StringBuilder sb = null;
		for (MessagePart part : this.content) {
			if (part instanceof TextPart textPart) {
				if (sb == null) {
					sb = new StringBuilder();
				}
				sb.append(textPart.text());
			}
		}
		return new ToolResponse(this.id, this.name, sb != null ? sb.toString() : null);
	}

}
