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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link BedrockReasoningContent}.
 *
 * @author Soby Chacko
 */
class BedrockReasoningContentTests {

	@Test
	void fromSignedReasoningBlock() {
		ReasoningContentBlock block = ReasoningContentBlock.builder()
			.reasoningText(ReasoningTextBlock.builder().text("I think...").signature("sig-abc").build())
			.build();

		BedrockReasoningContent content = BedrockReasoningContent.from(block);

		assertThat(content.getText()).isEqualTo("I think...");
		assertThat(content.getSignature()).isEqualTo("sig-abc");
		assertThat(content.getRedactedContent()).isNull();
	}

	@Test
	void fromRedactedBlock() {
		byte[] bytes = "opaque".getBytes(StandardCharsets.UTF_8);
		ReasoningContentBlock block = ReasoningContentBlock.builder()
			.redactedContent(SdkBytes.fromByteArray(bytes))
			.build();

		BedrockReasoningContent content = BedrockReasoningContent.from(block);

		assertThat(content.getText()).isNull();
		assertThat(content.getSignature()).isNull();
		assertThat(content.getRedactedContent()).isEqualTo(bytes);
	}

	@Test
	void fromEmptyBlockThrows() {
		// Expect: from() rejects a block where neither reasoningText nor redactedContent
		// is set
		ReasoningContentBlock emptyBlock = ReasoningContentBlock.builder().build();

		assertThatIllegalStateException().isThrownBy(() -> BedrockReasoningContent.from(emptyBlock))
			.withMessageContaining("neither reasoningText nor redactedContent is set");
	}

	@Test
	void builderRejectsTextWithoutSignature() {
		// Expect: build() rejects text set without a corresponding signature
		assertThatIllegalStateException()
			.isThrownBy(() -> BedrockReasoningContent.builder().text("some reasoning").build())
			.withMessageContaining("signature must be set when text is set");
	}

	@Test
	void builderAcceptsSignatureWithText() {
		BedrockReasoningContent content = BedrockReasoningContent.builder()
			.text("some reasoning")
			.signature("sig-xyz")
			.build();

		assertThat(content.getText()).isEqualTo("some reasoning");
		assertThat(content.getSignature()).isEqualTo("sig-xyz");
	}

	@Test
	void builderAcceptsRedactedContentWithoutText() {
		byte[] bytes = "redacted".getBytes(StandardCharsets.UTF_8);
		BedrockReasoningContent content = BedrockReasoningContent.builder().redactedContent(bytes).build();

		assertThat(content.getText()).isNull();
		assertThat(content.getSignature()).isNull();
		assertThat(content.getRedactedContent()).isEqualTo(bytes);
	}

	@Test
	void toStringRedactsTextAndSignature() {
		BedrockReasoningContent content = BedrockReasoningContent.builder()
			.text("secret reasoning")
			.signature("secret-sig")
			.build();
		String str = content.toString();

		assertThat(str).doesNotContain("secret reasoning").doesNotContain("secret-sig").contains("***redacted***");
	}

	@Test
	void redactedContentIsDefensivelyCopied() {
		byte[] original = "data".getBytes(StandardCharsets.UTF_8);
		BedrockReasoningContent content = BedrockReasoningContent.builder().redactedContent(original).build();

		byte[] prevOriginal = original.clone();
		original[0] = 'X';
		// original is modified - but the redactedContent should not be affected by that.
		assertThat(content.getRedactedContent()).isNotEqualTo(original);
		assertThat(content.getRedactedContent()).isEqualTo(prevOriginal);
	}

}
