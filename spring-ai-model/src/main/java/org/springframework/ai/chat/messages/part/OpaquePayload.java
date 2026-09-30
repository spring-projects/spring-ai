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

import org.springframework.util.Assert;

/**
 * Provider-owned data attached to a {@link MessagePart} (or to a {@link ReasoningPart})
 * that Spring AI never interprets and that must be sent back unchanged to the provider
 * that produced it.
 * <p>
 * Examples: an Anthropic thinking {@code signature}, an OpenAI Responses
 * {@code encrypted_content}, a Gemini {@code thought_signature} carried on a function
 * call. Binary payloads are base64-encoded by the adapter that produced them so that
 * {@code data} is always a plain string.
 *
 * @param provider the provider that produced the payload, for example {@code anthropic},
 * {@code openai}, {@code google}, {@code bedrock}
 * @param kind the provider's own name for the payload, for example {@code signature},
 * {@code encrypted_content}, {@code thought_signature}, {@code redacted_thinking}
 * @param data the payload, always a string
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record OpaquePayload(String provider, String kind, String data) {

	public OpaquePayload {
		Assert.hasText(provider, "provider must not be empty");
		Assert.hasText(kind, "kind must not be empty");
		Assert.notNull(data, "data must not be null");
	}

}
