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

package org.springframework.ai.anthropic;

import java.util.Objects;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.RedactedThinkingBlockParam;
import com.anthropic.models.messages.ThinkingBlockParam;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Anthropic thinking content preserved for request replay.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
public final class AnthropicThinkingContent {

	private final @Nullable String thinking;

	private final @Nullable String signature;

	private final @Nullable String redactedData;

	private AnthropicThinkingContent(@Nullable String thinking, @Nullable String signature,
			@Nullable String redactedData) {
		this.thinking = thinking;
		this.signature = signature;
		this.redactedData = redactedData;
	}

	/**
	 * Create a thinking content block.
	 * @param thinking the thinking text
	 * @param signature the thinking signature
	 * @return a thinking content instance
	 */
	public static AnthropicThinkingContent thinking(String thinking, String signature) {
		Assert.notNull(thinking, "thinking must not be null");
		Assert.notNull(signature, "signature must not be null");
		return new AnthropicThinkingContent(thinking, signature, null);
	}

	/**
	 * Create a redacted thinking content block.
	 * @param data the redacted thinking data
	 * @return a redacted thinking content instance
	 */
	public static AnthropicThinkingContent redacted(String data) {
		Assert.notNull(data, "data must not be null");
		return new AnthropicThinkingContent(null, null, data);
	}

	/**
	 * Return the thinking text, if this is a thinking content block.
	 * @return the thinking text or null
	 */
	public @Nullable String getThinking() {
		return this.thinking;
	}

	/**
	 * Return the thinking signature, if this is a thinking content block.
	 * @return the thinking signature or null
	 */
	public @Nullable String getSignature() {
		return this.signature;
	}

	/**
	 * Return the redacted thinking data, if this is a redacted thinking content block.
	 * @return the redacted thinking data or null
	 */
	public @Nullable String getRedactedData() {
		return this.redactedData;
	}

	ContentBlockParam toContentBlockParam() {
		if (this.redactedData != null) {
			return ContentBlockParam
				.ofRedactedThinking(RedactedThinkingBlockParam.builder().data(this.redactedData).build());
		}
		String thinking = this.thinking;
		String signature = this.signature;
		Assert.notNull(thinking, "thinking must not be null");
		Assert.notNull(signature, "signature must not be null");
		return ContentBlockParam
			.ofThinking(ThinkingBlockParam.builder().thinking(thinking).signature(signature).build());
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicThinkingContent that)) {
			return false;
		}
		return Objects.equals(this.thinking, that.thinking) && Objects.equals(this.signature, that.signature)
				&& Objects.equals(this.redactedData, that.redactedData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.thinking, this.signature, this.redactedData);
	}

	@Override
	public String toString() {
		String type = this.redactedData != null ? "redacted_thinking" : "thinking";
		return "AnthropicThinkingContent[type=" + type + "]";
	}

}
