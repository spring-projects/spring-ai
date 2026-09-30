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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

/**
 * One element of the ordered content of a chat message.
 * <p>
 * A message is a list of parts in the order the model produced or the caller supplied
 * them, so interleavings such as reasoning, tool call, text, tool call survive a round
 * trip through memory and back to the provider. The hierarchy is sealed: provider
 * adapters live in the Spring AI repository and dispatch over the permitted types, while
 * {@link UnknownPart} keeps anything an adapter does not model yet.
 * <p>
 * Parts are serialized with a {@code type} discriminator. A {@code type} value this
 * version does not know deserializes to an {@link UnknownPart} instead of failing.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = UnknownPart.class)
@JsonSubTypes({ @JsonSubTypes.Type(value = TextPart.class, name = "text"),
		@JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
		@JsonSubTypes.Type(value = ToolCallPart.class, name = "tool_call"),
		@JsonSubTypes.Type(value = ToolResultPart.class, name = "tool_result"),
		@JsonSubTypes.Type(value = MediaPart.class, name = "media"),
		@JsonSubTypes.Type(value = UnknownPart.class, name = "unknown") })
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface MessagePart
		permits MediaPart, ReasoningPart, TextPart, ToolCallPart, ToolResultPart, UnknownPart {

	/**
	 * Provider-owned data that must be replayed verbatim to the same provider, such as a
	 * thinking signature or encrypted reasoning, or {@code null} when the part carries
	 * none.
	 * @return the opaque payload, or {@code null}
	 */
	@Nullable OpaquePayload payload();

	/**
	 * Free-form, serialization-safe attributes of this part, such as a streaming part
	 * index or a provider item id. Never {@code null}, never modifiable.
	 * @return the attributes
	 */
	Map<String, String> attributes();

	/**
	 * Returns a copy of this part with the given attributes replacing the current ones.
	 * All other components are unchanged.
	 * @param attributes the new attributes, copied
	 * @return the copy
	 */
	MessagePart withAttributes(Map<String, String> attributes);

}
