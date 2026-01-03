/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.mistralai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ReferenceChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.TextChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ThinkChunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for content parsing in {@link ChatCompletionMessage}. Tests the parsing of
 * content returned by Magistral reasoning models which can return content as either a
 * simple string or an array of typed blocks (text, thinking, reference).
 *
 * @author Kyle Kreuter
 */
class MistralAiContentParsingTests {

	// String Content Parsing Tests (Backward Compatibility)

	@Test
	void testParseSimpleStringContent() {
		String textContent = "Hello, I am a response from Mistral AI.";
		ChatCompletionMessage message = new ChatCompletionMessage(textContent, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo(textContent);
		assertThat(message.thinkingContent()).isNull();
		assertThat(message.rawContent()).isEqualTo(textContent);
	}

	@Test
	void testReturnNullContentForNullRawContent() {
		ChatCompletionMessage message = new ChatCompletionMessage(null, Role.ASSISTANT);

		assertThat(message.content()).isNull();
		assertThat(message.thinkingContent()).isNull();
	}

	@Test
	void testReturnEmptyStringContentAsIs() {
		ChatCompletionMessage message = new ChatCompletionMessage("", Role.ASSISTANT);

		assertThat(message.content()).isEmpty();
		assertThat(message.thinkingContent()).isNull();
	}

	@Test
	void testParseStringContentWithSpecialCharacters() {
		String textContent = "Response with special chars: <>&\"' and unicode: \u00e9\u00e8\u00ea";
		ChatCompletionMessage message = new ChatCompletionMessage(textContent, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo(textContent);
	}

	// Array Content Parsing Tests (Magistral Models)

	@Test
	void testParseArrayContentWithTextChunkOnly() {
		List<Map<String, Object>> content = List.of(Map.of("type", "text", "text", "This is the response text."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo("This is the response text.");
		assertThat(message.thinkingContent()).isNull();
	}

	@Test
	void testParseArrayContentWithThinkChunkOnly() {
		List<Map<String, Object>> content = List
			.of(Map.of("type", "thinking", "thinking", "Let me reason through this problem..."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isNull();
		assertThat(message.thinkingContent()).isEqualTo("Let me reason through this problem...");
	}

	@Test
	void testParseArrayContentWithBothTextAndThinkChunks() {
		List<Map<String, Object>> content = List.of(
				Map.of("type", "thinking", "thinking", "First, I need to analyze the question..."),
				Map.of("type", "text", "text", "The answer is 42."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo("The answer is 42.");
		assertThat(message.thinkingContent()).isEqualTo("First, I need to analyze the question...");
	}

	@Test
	void testParseArrayContentWithReferenceChunk() {
		List<Map<String, Object>> content = List.of(Map.of("type", "text", "text", "According to the sources..."),
				Map.of("type", "reference", "reference_ids", List.of(1, 2, 3)));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo("According to the sources...");

		List<ContentChunk> chunks = message.contentChunks();
		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0)).isInstanceOf(TextChunk.class);
		assertThat(chunks.get(1)).isInstanceOf(ReferenceChunk.class);

		ReferenceChunk refChunk = (ReferenceChunk) chunks.get(1);
		assertThat(refChunk.referenceIds()).containsExactly(1, 2, 3);
	}

	@Test
	void testConcatenateMultipleTextChunksWithNewlines() {
		List<Map<String, Object>> content = List.of(Map.of("type", "text", "text", "First paragraph."),
				Map.of("type", "text", "text", "Second paragraph."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo("First paragraph.\nSecond paragraph.");
	}

	@Test
	void testConcatenateMultipleThinkingChunksWithNewlines() {
		List<Map<String, Object>> content = List.of(Map.of("type", "thinking", "thinking", "Step 1: Analyze..."),
				Map.of("type", "thinking", "thinking", "Step 2: Evaluate..."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.thinkingContent()).isEqualTo("Step 1: Analyze...\nStep 2: Evaluate...");
	}

	@Test
	void testHandleEmptyArrayContent() {
		List<Map<String, Object>> content = List.of();

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isNull();
		assertThat(message.thinkingContent()).isNull();
		assertThat(message.contentChunks()).isEmpty();
	}

	@Test
	void testHandleArrayWithUnknownChunkTypesGracefully() {
		List<Map<String, Object>> content = List.of(Map.of("type", "unknown", "data", "some data"),
				Map.of("type", "text", "text", "Valid text content."));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo("Valid text content.");
		assertThat(message.contentChunks()).hasSize(1);
	}

	@Test
	void testHandleArrayWithNullTextInTextChunk() {
		// Use HashMap to allow null values (Map.of() doesn't support nulls)
		java.util.HashMap<String, Object> map = new java.util.HashMap<>();
		map.put("type", "text");
		map.put("text", null);
		List<Map<String, Object>> content = List.of(map);

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		assertThat(message.content()).isNull();
		assertThat(message.contentChunks()).isEmpty();
	}

	// ContentChunks Parsing Tests

	@Test
	void testReturnSingleTextChunkForStringContent() {
		String textContent = "Simple text response";
		ChatCompletionMessage message = new ChatCompletionMessage(textContent, Role.ASSISTANT);

		List<ContentChunk> chunks = message.contentChunks();

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0)).isInstanceOf(TextChunk.class);
		assertThat(((TextChunk) chunks.get(0)).text()).isEqualTo(textContent);
	}

	@Test
	void testReturnEmptyListForNullContent() {
		ChatCompletionMessage message = new ChatCompletionMessage(null, Role.ASSISTANT);

		assertThat(message.contentChunks()).isEmpty();
	}

	@Test
	void testParseAllChunkTypesCorrectly() {
		List<Map<String, Object>> content = List.of(Map.of("type", "thinking", "thinking", "Reasoning..."),
				Map.of("type", "text", "text", "Answer text"),
				Map.of("type", "reference", "reference_ids", List.of(1, 2)));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		List<ContentChunk> chunks = message.contentChunks();
		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0)).isInstanceOf(ThinkChunk.class);
		assertThat(chunks.get(1)).isInstanceOf(TextChunk.class);
		assertThat(chunks.get(2)).isInstanceOf(ReferenceChunk.class);

		assertThat(((ThinkChunk) chunks.get(0)).thinking()).isEqualTo("Reasoning...");
		assertThat(((TextChunk) chunks.get(1)).text()).isEqualTo("Answer text");
		assertThat(((ReferenceChunk) chunks.get(2)).referenceIds()).containsExactly(1, 2);
	}

	@Test
	void testHandleReferenceChunkWithNumericValues() {
		List<Map<String, Object>> content = List.of(Map.of("type", "reference", "reference_ids", List.of(1L, 2L, 3L)));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		List<ContentChunk> chunks = message.contentChunks();
		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0)).isInstanceOf(ReferenceChunk.class);
		assertThat(((ReferenceChunk) chunks.get(0)).referenceIds()).containsExactly(1, 2, 3);
	}

	// Edge Cases and Error Handling Tests

	@Test
	void testThrowExceptionForUnexpectedContentType() {
		// Using an Integer as content which is not a supported type
		ChatCompletionMessage message = new ChatCompletionMessage(12345, Role.ASSISTANT);

		assertThatThrownBy(message::content).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unexpected content type");
	}

	@Test
	void testHandleMultilineTextContent() {
		String multilineText = """
				Line 1
				Line 2
				Line 3
				""";
		ChatCompletionMessage message = new ChatCompletionMessage(multilineText, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo(multilineText);
	}

	@Test
	void testHandleVeryLongContent() {
		String longContent = "A".repeat(10000);
		ChatCompletionMessage message = new ChatCompletionMessage(longContent, Role.ASSISTANT);

		assertThat(message.content()).isEqualTo(longContent);
		assertThat(message.content()).hasSize(10000);
	}

	@Test
	void testHandleMixedContentWithEmptyStrings() {
		List<Map<String, Object>> content = List.of(Map.of("type", "thinking", "thinking", ""),
				Map.of("type", "text", "text", "Valid text"));

		ChatCompletionMessage message = new ChatCompletionMessage(content, Role.ASSISTANT);

		// Empty thinking should result in empty chunk being added (but still parsed)
		assertThat(message.content()).isEqualTo("Valid text");
	}

	@Test
	void testPreserveMessageRole() {
		ChatCompletionMessage assistantMessage = new ChatCompletionMessage("content", Role.ASSISTANT);
		ChatCompletionMessage userMessage = new ChatCompletionMessage("content", Role.USER);
		ChatCompletionMessage systemMessage = new ChatCompletionMessage("content", Role.SYSTEM);

		assertThat(assistantMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(userMessage.role()).isEqualTo(Role.USER);
		assertThat(systemMessage.role()).isEqualTo(Role.SYSTEM);
	}

	// Record Components Tests

	@Test
	void testTextChunkRecordEquality() {
		TextChunk chunk1 = new TextChunk("Hello");
		TextChunk chunk2 = new TextChunk("Hello");
		TextChunk chunk3 = new TextChunk("World");

		assertThat(chunk1.text()).isEqualTo("Hello");
		assertThat(chunk1).isEqualTo(chunk2);
		assertThat(chunk1).isNotEqualTo(chunk3);
		assertThat(chunk1.hashCode()).isEqualTo(chunk2.hashCode());
	}

	@Test
	void testThinkChunkRecordEquality() {
		ThinkChunk chunk1 = new ThinkChunk("Thinking...");
		ThinkChunk chunk2 = new ThinkChunk("Thinking...");
		ThinkChunk chunk3 = new ThinkChunk("Different thinking");

		assertThat(chunk1.thinking()).isEqualTo("Thinking...");
		assertThat(chunk1).isEqualTo(chunk2);
		assertThat(chunk1).isNotEqualTo(chunk3);
		assertThat(chunk1.hashCode()).isEqualTo(chunk2.hashCode());
	}

	@Test
	void testReferenceChunkRecordEquality() {
		ReferenceChunk chunk1 = new ReferenceChunk(List.of(1, 2, 3));
		ReferenceChunk chunk2 = new ReferenceChunk(List.of(1, 2, 3));
		ReferenceChunk chunk3 = new ReferenceChunk(List.of(4, 5));

		assertThat(chunk1.referenceIds()).containsExactly(1, 2, 3);
		assertThat(chunk1).isEqualTo(chunk2);
		assertThat(chunk1).isNotEqualTo(chunk3);
		assertThat(chunk1.hashCode()).isEqualTo(chunk2.hashCode());
	}

}
