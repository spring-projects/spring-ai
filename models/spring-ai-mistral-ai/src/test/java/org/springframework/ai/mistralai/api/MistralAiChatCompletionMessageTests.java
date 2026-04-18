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

package org.springframework.ai.mistralai.api;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentDeserializer;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentSerializer;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk.ImageUrl;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk.ImageUrl.ImageDetail;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ReferenceChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.TextChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ThinkChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ThinkingContentChunk;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Nicolas Krier
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MistralAiChatCompletionMessageTests {

	@SuppressWarnings("UnusedDeclaration")
	private JacksonTester<ChatCompletionMessage> chatCompletionMessageTester;

	private static final String STRING = "text";

	private static final String JSON_STRING = "\"text\"";

	private static final String CONTENT_TEXT = "content text";

	private static final String THINKING_TEXT = "thinking text";

	private static final List<ContentChunk> CONTENT_CHUNKS = createContentChunks();

	private static final String CONTENT_CHUNKS_AS_STRING = extractResourceContent("contents/content_chunks.json");

	private static final ChatCompletionMessage CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT = createChatCompletionMessageWithStringContent();

	private static final String CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT_AS_STRING = extractResourceContent(
			"messages/text_message.json");

	private static final ChatCompletionMessage CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT = createChatCompletionMessageWithContentChunksContent();

	private static final String CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT_AS_STRING = extractResourceContent(
			"messages/content_chunks_message.json");

	private static List<ContentChunk> createContentChunks() {
		return createContentChunks(createThinkingContentChunks());
	}

	private static List<ContentChunk> createContentChunks(List<ThinkingContentChunk> thinkingContentChunks) {
		// @formatter:off
		return List.of(
			new ImageUrlChunk(new ImageUrl("https://example.com/image.png", ImageDetail.AUTO)),
			new ReferenceChunk(List.of(1, 2, 3)),
			new TextChunk(CONTENT_TEXT),
			new ThinkChunk(thinkingContentChunks, true)
		);
		// @formatter:on
	}

	private static List<ThinkingContentChunk> createThinkingContentChunks() {
		// @formatter:off
		return List.of(
			new ReferenceChunk(List.of(4, 5, 6)),
			new TextChunk(THINKING_TEXT)
		);
		// @formatter:on
	}

	private static ChatCompletionMessage createChatCompletionMessageWithStringContent() {
		return new ChatCompletionMessage(STRING, Role.USER);
	}

	private static ChatCompletionMessage createChatCompletionMessageWithContentChunksContent() {
		return new ChatCompletionMessage(CONTENT_CHUNKS, Role.USER);
	}

	@BeforeAll
	void initializeJacksonTester() {
		JacksonTester.initFields(this, JsonMapper.shared());
	}

	private static JsonContent<?> createJsonContent(String json) {
		return new JsonContent<>(MistralAiChatCompletionMessageTests.class, null, json);
	}

	private static String extractResourceContent(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ioException) {
			throw new IllegalStateException("Unable to extract file content!", ioException);
		}
	}

	@Nested
	class ChatCompletionMessageSerializationTests {

		@Test
		void serializeChatCompletionMessageWithStringContent() throws IOException {
			assertThat(chatCompletionMessageTester.write(CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT))
				.isEqualToJson(CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT_AS_STRING);
		}

		@Test
		void serializeChatCompletionMessageWithContentChunksContent() throws IOException {
			assertThat(chatCompletionMessageTester.write(CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT))
				.isEqualToJson(CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT_AS_STRING);
		}

	}

	@Nested
	class ChatCompletionMessageDeserializationTests {

		@Test
		void deserializeChatCompletionMessageWithStringContent() throws IOException {
			assertThat(chatCompletionMessageTester.parse(CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT_AS_STRING))
				.isEqualTo(CHAT_COMPLETION_MESSAGE_WITH_STRING_CONTENT);

		}

		@Test
		void deserializeChatCompletionMessageWithContentChunksContent() throws IOException {
			assertThat(chatCompletionMessageTester.parse(CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT_AS_STRING))
				.isEqualTo(CHAT_COMPLETION_MESSAGE_WITH_CONTENT_CHUNKS_CONTENT);
		}

	}

	@Nested
	class ContentSerializerTests {

		private final ContentSerializer contentSerializer = new ContentSerializer();

		@Test
		void serializeInteger() {
			// @formatter:off
			assertThatThrownBy(() -> serialize(123))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Unexpected value type class java.lang.Integer!");
			// @formatter:on
		}

		@Test
		void serializeIntegers() {
			// @formatter:off
			assertThatThrownBy(() -> serialize(List.of(123, 234, 456)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Unexpected value type class java.lang.Integer in the list!");
			// @formatter:on
		}

		@Test
		void serializeString() {
			assertThat(serialize(STRING)).isEqualTo(JSON_STRING);
		}

		@Test
		void serializeContentChunks() {
			assertThat(createJsonContent(serialize(CONTENT_CHUNKS))).isEqualToJson(CONTENT_CHUNKS_AS_STRING);
		}

		private String serialize(Object value) {
			var stringWriter = new StringWriter();
			var jsonMapper = JsonMapper.shared();
			var serializationContext = jsonMapper._serializationContext();

			try (var jsonGenerator = jsonMapper.createGenerator(stringWriter)) {
				this.contentSerializer.serialize(value, jsonGenerator, serializationContext);
			}

			return stringWriter.toString();
		}

	}

	@Nested
	class ContentDeserializerTests {

		private final ContentDeserializer contentDeserializer = new ContentDeserializer();

		@Test
		void deserializeInteger() {
			// @formatter:off
			assertThatThrownBy(() -> deserialize("123"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unexpected JSON token VALUE_NUMBER_INT!");
			// @formatter:on
		}

		@Test
		void deserializeIntegers() {
			// @formatter:off
			assertThatThrownBy(() -> deserialize("[123, 234, 456]"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unexpected JSON token VALUE_NUMBER_INT within the array!");
			// @formatter:on
		}

		@Test
		void deserializeString() {
			assertThat(deserialize(JSON_STRING)).isEqualTo(STRING);
		}

		@Test
		void deserializeContentChunks() {
			assertThat(deserialize(CONTENT_CHUNKS_AS_STRING)).isEqualTo(CONTENT_CHUNKS);
		}

		private Object deserialize(String value) {
			var jsonMapper = JsonMapper.shared();
			var deserializationContext = jsonMapper._deserializationContext();

			try (var jsonParser = jsonMapper.createParser(value)) {
				var ignored = jsonParser.nextToken();

				return this.contentDeserializer.deserialize(jsonParser, deserializationContext);
			}
		}

	}

	@Nested
	class ExtractChatCompletionMessageContentTests {

		@Test
		void extractNullContent() {
			var chatCompletionMessage = new ChatCompletionMessage(null, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractContent()).isNull();
		}

		@Test
		void extractIntegerContent() {
			var chatCompletionMessage = new ChatCompletionMessage(123, Role.USER);
			assertThatThrownBy(chatCompletionMessage::extractContent).isInstanceOf(IllegalStateException.class)
				.hasMessage("The content is not a string nor a list!");
		}

		@Test
		void extractStringContent() {
			var chatCompletionMessage = new ChatCompletionMessage(CONTENT_TEXT, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractContent()).isEqualTo(CONTENT_TEXT);
		}

		@Test
		void extractEmptyContentChunksContent() {
			List<ContentChunk> contentChunks = List.of();
			var chatCompletionMessage = new ChatCompletionMessage(contentChunks, Role.USER);
			assertThat(chatCompletionMessage.extractContent()).isEmpty();
		}

		@Test
		void extractContentChunksContentHavingStringContent() {
			var anotherContentText = "another content text";
			var textChunk = new TextChunk(anotherContentText);
			// @formatter:off
			var contentChunks = Stream.concat(
				createContentChunks().stream(),
				Stream.of(textChunk)
			).toList();
			// @formatter:on
			var chatCompletionMessage = new ChatCompletionMessage(contentChunks, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractContent()).startsWith(CONTENT_TEXT)
				.contains(System.lineSeparator())
				.endsWith(anotherContentText);
		}

	}

	@Nested
	class ExtractChatCompletionMessageThinkingContentTests {

		@Test
		void extractNullContent() {
			var chatCompletionMessage = new ChatCompletionMessage(null, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractThinkingContent()).isNull();
		}

		@Test
		void extractIntegerContent() {
			var chatCompletionMessage = new ChatCompletionMessage(123, Role.USER);
			assertThatThrownBy(chatCompletionMessage::extractThinkingContent).isInstanceOf(IllegalStateException.class)
				.hasMessage("The content is not a string nor a list!");
		}

		@Test
		void extractStringContent() {
			var chatCompletionMessage = new ChatCompletionMessage(CONTENT_TEXT, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractThinkingContent()).isNull();
		}

		@Test
		void extractEmptyContentChunksContent() {
			List<ContentChunk> contentChunks = List.of();
			var chatCompletionMessage = new ChatCompletionMessage(contentChunks, Role.USER);
			assertThat(chatCompletionMessage.extractThinkingContent()).isEmpty();
		}

		@Test
		void extractContentChunksContentHavingEmptyThinkingContentChunks() {
			List<ThinkingContentChunk> thinkingContentChunks = List.of();
			var contentChunks = createContentChunks(thinkingContentChunks);
			var chatCompletionMessage = new ChatCompletionMessage(contentChunks, Role.USER);
			assertThat(chatCompletionMessage.extractThinkingContent()).isEmpty();
		}

		@Test
		void extractContentChunksContentHavingThinkingContentChunks() {
			var anotherThinkingText = "another thinking text";
			var textChunk = new TextChunk(anotherThinkingText);
			// @formatter:off
			var thinkingContentChunks = Stream.concat(
				createThinkingContentChunks().stream(),
				Stream.of(textChunk)
			).toList();
			// @formatter:on
			var contentChunks = createContentChunks(thinkingContentChunks);
			var chatCompletionMessage = new ChatCompletionMessage(contentChunks, Role.ASSISTANT);
			assertThat(chatCompletionMessage.extractThinkingContent()).startsWith(THINKING_TEXT)
				.contains(System.lineSeparator())
				.endsWith(anotherThinkingText);
		}

	}

}
