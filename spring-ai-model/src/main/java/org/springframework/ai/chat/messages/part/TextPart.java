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

import org.springframework.util.Assert;

/**
 * A run of text produced by the model or supplied by the caller.
 *
 * @param text the text, never {@code null} but possibly empty
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record TextPart(String text, @Nullable OpaquePayload payload,
		Map<String, String> attributes) implements MessagePart {

	public TextPart {
		Assert.notNull(text, "text must not be null");
		Assert.notNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	@Override
	public TextPart withAttributes(Map<String, String> attributes) {
		return new TextPart(this.text, this.payload, attributes);
	}

	/**
	 * Creates a text part without payload or attributes.
	 * @param text the text
	 * @return the part
	 */
	public static TextPart of(String text) {
		return new TextPart(text, null, Map.of());
	}

}
