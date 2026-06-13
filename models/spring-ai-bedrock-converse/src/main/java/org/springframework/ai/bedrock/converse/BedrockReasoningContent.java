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

package org.springframework.ai.bedrock.converse;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;

import org.springframework.util.Assert;

/**
 * Spring AI Bedrock value object that preserves a single Bedrock Converse
 * {@code reasoningContent} block so it can be replayed, unmodified, on a later request.
 * <p>
 * Amazon Bedrock requires that the assistant turn containing a {@code toolUse} block also
 * replays the preceding signed {@code reasoningContent} block when the matching
 * {@code toolResult} is sent. To honor that replay invariant this object captures the
 * reasoning text, its signature and any redacted content without modification and can
 * convert itself back into an AWS SDK {@link ContentBlock} via {@link #toContentBlock()}.
 * <p>
 * The reasoning text and signature are intentionally redacted from {@link #toString()}:
 * the signature is an opaque token used only for replay verification and has no reason to
 * leak into logs or assertion output.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 * @see BedrockAssistantMessage
 */
public final class BedrockReasoningContent {

	private final @Nullable String text;

	private final @Nullable String signature;

	private final byte @Nullable [] redactedContent;

	private BedrockReasoningContent(@Nullable String text, @Nullable String signature,
			byte @Nullable [] redactedContent) {
		this.text = text;
		this.signature = signature;
		this.redactedContent = redactedContent != null ? redactedContent.clone() : null;
	}

	/**
	 * Capture a Bedrock {@link ReasoningContentBlock} as a Spring AI value object,
	 * preserving its union member (reasoning text and signature, or redacted content)
	 * without modification.
	 * @param reasoningContentBlock the AWS SDK reasoning content block
	 * @return a value object that can replay the same reasoning block
	 */
	public static BedrockReasoningContent from(ReasoningContentBlock reasoningContentBlock) {
		Assert.notNull(reasoningContentBlock, "reasoningContentBlock must not be null");
		SdkBytes redacted = reasoningContentBlock.redactedContent();
		if (redacted != null) {
			return new BedrockReasoningContent(null, null, redacted.asByteArray());
		}
		ReasoningTextBlock reasoningText = reasoningContentBlock.reasoningText();
		if (reasoningText != null) {
			return new BedrockReasoningContent(reasoningText.text(), reasoningText.signature(), null);
		}
		return new BedrockReasoningContent(null, null, null);
	}

	/**
	 * Convert this value object back into an AWS SDK {@link ContentBlock} carrying a
	 * {@code reasoningContent} block. The reasoning text and signature are replayed
	 * unmodified; signatures are never regenerated.
	 * @return the reasoning content block ready to be added to a Bedrock request
	 */
	public ContentBlock toContentBlock() {
		ReasoningContentBlock.Builder builder = ReasoningContentBlock.builder();
		if (this.redactedContent != null) {
			builder.redactedContent(SdkBytes.fromByteArray(this.redactedContent));
		}
		else {
			builder.reasoningText(ReasoningTextBlock.builder().text(this.text).signature(this.signature).build());
		}
		return ContentBlock.fromReasoningContent(builder.build());
	}

	public @Nullable String getText() {
		return this.text;
	}

	public @Nullable String getSignature() {
		return this.signature;
	}

	public byte @Nullable [] getRedactedContent() {
		return this.redactedContent != null ? this.redactedContent.clone() : null;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof BedrockReasoningContent that)) {
			return false;
		}
		return Objects.equals(this.text, that.text) && Objects.equals(this.signature, that.signature)
				&& Arrays.equals(this.redactedContent, that.redactedContent);
	}

	@Override
	public int hashCode() {
		return 31 * Objects.hash(this.text, this.signature) + Arrays.hashCode(this.redactedContent);
	}

	@Override
	public String toString() {
		return "BedrockReasoningContent [text=" + (this.text != null ? "***redacted***" : "null") + ", signature="
				+ (this.signature != null ? "***redacted***" : "null") + ", redactedContent="
				+ (this.redactedContent != null ? this.redactedContent.length + " bytes" : "null") + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String text;

		private @Nullable String signature;

		private byte @Nullable [] redactedContent;

		private Builder() {
		}

		public Builder text(@Nullable String text) {
			this.text = text;
			return this;
		}

		public Builder signature(@Nullable String signature) {
			this.signature = signature;
			return this;
		}

		public Builder redactedContent(byte @Nullable [] redactedContent) {
			this.redactedContent = redactedContent != null ? redactedContent.clone() : null;
			return this;
		}

		public BedrockReasoningContent build() {
			return new BedrockReasoningContent(this.text, this.signature, this.redactedContent);
		}

	}

}
