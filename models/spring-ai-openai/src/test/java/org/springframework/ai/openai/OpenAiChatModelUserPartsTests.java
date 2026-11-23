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

package org.springframework.ai.openai;

import java.util.List;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link OpenAiChatModel} sends the parts of a {@link UserMessage} in order,
 * so text and media can interleave, while legacy messages keep the text-then-media order.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatModelUserPartsTests {

	private static final Media IMAGE = Media.builder()
		.mimeType(MimeTypeUtils.IMAGE_PNG)
		.data("https://example.com/a.png")
		.build();

	@Mock
	OpenAIClient openAiClient;

	@Mock
	OpenAIClientAsync openAiClientAsync;

	@Test
	void interleavedPartsKeepTheirOrder() {
		UserMessage message = UserMessage.builder()
			.part(TextPart.of("Look at "))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("this."))
			.build();

		List<ChatCompletionContentPart> parts = contentParts(message);

		assertThat(parts).hasSize(3);
		assertThat(parts.get(0).asText().text()).isEqualTo("Look at ");
		assertThat(parts.get(1).asImageUrl().imageUrl().url()).isEqualTo("https://example.com/a.png");
		assertThat(parts.get(2).asText().text()).isEqualTo("this.");
	}

	@Test
	void legacyMessageStaysTextThenMedia() {
		UserMessage message = UserMessage.builder().text("describe").media(IMAGE).build();

		List<ChatCompletionContentPart> parts = contentParts(message);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).asText().text()).isEqualTo("describe");
		assertThat(parts.get(1).asImageUrl().imageUrl().url()).isEqualTo("https://example.com/a.png");
	}

	@Test
	void textOnlyMessageIsSentAsPlainString() {
		ChatCompletionUserMessageParam param = userParam(new UserMessage("just text"));

		assertThat(param.content().isText()).isTrue();
		assertThat(param.content().asText()).isEqualTo("just text");
	}

	@Test
	void mediaOnlyMessageSendsTheMediaPartOnly() {
		UserMessage message = UserMessage.builder().part(MediaPart.of(IMAGE)).build();

		List<ChatCompletionContentPart> parts = contentParts(message);

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0).isImageUrl()).isTrue();
	}

	@Test
	void emptyTextPartsAreSkipped() {
		UserMessage message = UserMessage.builder()
			.part(TextPart.of(""))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("What is it?"))
			.build();

		List<ChatCompletionContentPart> parts = contentParts(message);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).isImageUrl()).isTrue();
		assertThat(parts.get(1).asText().text()).isEqualTo("What is it?");
	}

	@Test
	void foreignPartsAreSkipped() {
		UserMessage message = UserMessage.builder()
			.part(new UnknownPart("anthropic", "document", "{\"type\":\"document\"}", null, Map.of()))
			.part(TextPart.of("Summarize."))
			.part(MediaPart.of(IMAGE))
			.build();

		List<ChatCompletionContentPart> parts = contentParts(message);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).asText().text()).isEqualTo("Summarize.");
		assertThat(parts.get(1).isImageUrl()).isTrue();
	}

	private List<ChatCompletionContentPart> contentParts(UserMessage message) {
		return userParam(message).content().asArrayOfContentParts();
	}

	private ChatCompletionUserMessageParam userParam(UserMessage message) {
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
			.build();
		Prompt prompt = new Prompt(List.of(message), chatModel.getOptions());
		return chatModel.createRequest(prompt, false)
			.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isUser)
			.map(ChatCompletionMessageParam::asUser)
			.findFirst()
			.orElseThrow();
	}

}
