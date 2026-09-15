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
 * Model reasoning (thinking) that preceded or accompanied the answer.
 * <p>
 * The provider-neutral {@code text} and {@code summary} are for display. The
 * {@link #payload()} carries what the producing provider requires to be replayed verbatim
 * (a signature, encrypted content, a thought signature) and is only meaningful to that
 * provider; see {@link #replayableTo(String)}. A {@code redacted} part has no text and
 * carries only its payload.
 *
 * @param text the human-readable reasoning, or {@code null} when redacted or absent
 * @param summary a provider-supplied summary of the reasoning, or {@code null}
 * @param redacted whether the provider withheld the reasoning text
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record ReasoningPart(@Nullable String text, @Nullable String summary, boolean redacted,
		@Nullable OpaquePayload payload, Map<String, String> attributes) implements MessagePart {

	public ReasoningPart {
		Assert.notNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	@Override
	public ReasoningPart withAttributes(Map<String, String> attributes) {
		return new ReasoningPart(this.text, this.summary, this.redacted, this.payload, attributes);
	}

	/**
	 * Creates a plain reasoning part with text only, replayable to any provider.
	 * @param text the reasoning text
	 * @return the part
	 */
	public static ReasoningPart of(String text) {
		Assert.notNull(text, "text must not be null");
		return new ReasoningPart(text, null, false, null, Map.of());
	}

	/**
	 * Whether this part can be sent to the given provider without losing its payload:
	 * true when there is no payload, or when the payload was produced by that provider.
	 * @param provider the target provider, for example {@code anthropic}
	 * @return whether the part is replayable to the provider
	 */
	public boolean replayableTo(String provider) {
		return this.payload == null || this.payload.provider().equals(provider);
	}

}
