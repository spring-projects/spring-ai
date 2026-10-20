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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.util.Assert;

/**
 * A tool call requested by the model.
 * <p>
 * The {@link #payload()} slot exists on tool calls because some providers attach their
 * reasoning token to the call rather than to a {@link ReasoningPart} (for example a
 * Gemini {@code thought_signature}).
 *
 * @param toolCall the tool call
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record ToolCallPart(ToolCall toolCall, @Nullable OpaquePayload payload,
		Map<String, String> attributes) implements MessagePart {

	public ToolCallPart {
		Assert.notNull(toolCall, "toolCall must not be null");
		Assert.notNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	@Override
	public ToolCallPart withAttributes(Map<String, String> attributes) {
		return new ToolCallPart(this.toolCall, this.payload, attributes);
	}

	/**
	 * Creates a tool call part without payload or attributes.
	 * @param toolCall the tool call
	 * @return the part
	 */
	public static ToolCallPart of(ToolCall toolCall) {
		return new ToolCallPart(toolCall, null, Map.of());
	}

}
