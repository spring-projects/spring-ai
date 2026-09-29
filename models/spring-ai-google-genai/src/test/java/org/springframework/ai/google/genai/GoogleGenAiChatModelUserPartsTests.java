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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the mapping of {@link UserMessage} parts to Gemini parts: text and media
 * interleave in part order, legacy messages keep the text-then-media order, and Google
 * unknown parts are replayed verbatim.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
class GoogleGenAiChatModelUserPartsTests {

	private static final String IMAGE_URL = "https://example.com/a.png";

	private static final Media IMAGE = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(IMAGE_URL).build();

	private static final Media AUDIO = Media.builder()
		.mimeType(MimeTypeUtils.parseMimeType("audio/wav"))
		.data(new byte[] { 1, 2, 3 })
		.build();

	@Mock
	private Client client;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH)
			.build();
		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.client, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	@Test
	void interleavedPartsKeepTheirOrder() {
		UserMessage message = UserMessage.builder()
			.part(TextPart.of("Look at "))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("this."))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(3);
		assertThat(parts.get(0).text()).contains("Look at ");
		assertThat(parts.get(1).fileData()).isPresent();
		assertThat(parts.get(1).fileData().get().fileUri()).contains(IMAGE_URL);
		assertThat(parts.get(2).text()).contains("this.");
	}

	@Test
	void legacyMessageKeepsTextThenMedia() {
		UserMessage message = UserMessage.builder().media(IMAGE, AUDIO).text("describe").build();

		List<Part> parts = this.chatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(3);
		assertThat(parts.get(0).text()).contains("describe");
		assertThat(parts.get(1).fileData().get().fileUri()).contains(IMAGE_URL);
		assertThat(parts.get(2).inlineData()).isPresent();
		assertThat(parts.get(2).inlineData().get().mimeType()).contains("audio/wav");
	}

	@Test
	void mediaOnlyMessageProducesTheMediaPartOnly() {
		UserMessage message = UserMessage.builder().part(MediaPart.of(IMAGE)).build();

		List<Part> parts = this.chatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0).text()).isEmpty();
		assertThat(parts.get(0).fileData()).isPresent();
	}

	@Test
	void googleUnknownPartIsReplayedVerbatim() {
		UnknownPart fileData = new UnknownPart(GoogleGenAiChatModel.GOOGLE_PROVIDER, "file_data",
				"{\"fileData\":{\"fileUri\":\"gs://bucket/doc.pdf\",\"mimeType\":\"application/pdf\"}}", null,
				Map.of());
		UserMessage message = UserMessage.builder().part(fileData).part(TextPart.of("Summarize.")).build();

		List<Part> parts = this.chatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).fileData().get().fileUri()).contains("gs://bucket/doc.pdf");
		assertThat(parts.get(1).text()).contains("Summarize.");
	}

	@Test
	void foreignAndUnsupportedPartsAreSkipped() {
		UnknownPart anthropicDocument = new UnknownPart("anthropic", "document", "{\"type\":\"document\"}", null,
				Map.of());
		UserMessage message = UserMessage.builder()
			.part(anthropicDocument)
			.part(ReasoningPart.of("stray"))
			.part(TextPart.of("question"))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0).text()).contains("question");
	}

}
