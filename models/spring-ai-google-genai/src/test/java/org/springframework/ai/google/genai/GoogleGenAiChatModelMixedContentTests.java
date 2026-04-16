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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for mixed-modality Gemini responses (text + functionCall parts). These
 * tests verify that the fix for issue #5466 correctly handles mixed content.
 *
 * @author ENG
 * @since 1.1.0
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5466">Issue #5466</a>
 */
public class GoogleGenAiChatModelMixedContentTests {

	@Test
	@SuppressWarnings("unchecked")
	void testMixedTextAndFunctionCallParts() throws Exception {
		// Arrange: a candidate with both a TextPart and a FunctionCallPart
		Part textPart = Part.builder().text("The answer is 42.").build();
		Part functionCallPart = Part.builder()
			.functionCall(FunctionCall.builder().name("get_weather").args(Map.of("location", "Toronto")).build())
			.build();

		Content content = Content.builder().parts(List.of(textPart, functionCallPart)).build();

		Candidate candidate = Candidate.builder().content(content).index(0).build();

		// Act: call responseCandidateToGeneration via reflection
		GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
			.defaultOptions(GoogleGenAiChatOptions.builder().build())
			.build();
		Method method = GoogleGenAiChatModel.class.getDeclaredMethod("responseCandidateToGeneration", Candidate.class);
		method.setAccessible(true);
		List<Generation> generations = (List<Generation>) method.invoke(model, candidate);

		// Assert: exactly one generation with both text content and toolCalls
		assertThat(generations).hasSize(1);
		Generation generation = generations.get(0);
		var assistantMessage = (org.springframework.ai.chat.messages.AssistantMessage) generation.getOutput();

		// Text content must be preserved
		assertThat(assistantMessage.getText()).isEqualTo("The answer is 42.");

		// Tool calls must also be preserved (not dropped by allMatch logic)
		assertThat(assistantMessage.getToolCalls()).isNotNull();
		assertThat(assistantMessage.getToolCalls()).hasSize(1);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("get_weather");
		assertThat(assistantMessage.getToolCalls().get(0).arguments()).contains("Toronto");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testFunctionCallOnlyParts() throws Exception {
		// Arrange: a candidate with only functionCall parts
		Part functionCallPart1 = Part.builder()
			.functionCall(FunctionCall.builder().name("get_weather").args(Map.of("location", "Toronto")).build())
			.build();
		Part functionCallPart2 = Part.builder()
			.functionCall(FunctionCall.builder().name("get_time").args(Map.of("timezone", "America/Toronto")).build())
			.build();

		Content content = Content.builder().parts(List.of(functionCallPart1, functionCallPart2)).build();

		Candidate candidate = Candidate.builder().content(content).index(0).build();

		// Act
		GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
			.defaultOptions(GoogleGenAiChatOptions.builder().build())
			.build();
		Method method = GoogleGenAiChatModel.class.getDeclaredMethod("responseCandidateToGeneration", Candidate.class);
		method.setAccessible(true);
		List<Generation> generations = (List<Generation>) method.invoke(model, candidate);

		// Assert: one generation with both tool calls and empty text content
		assertThat(generations).hasSize(1);
		var assistantMessage = (org.springframework.ai.chat.messages.AssistantMessage) generations.get(0).getOutput();

		assertThat(assistantMessage.getText()).isEmpty();
		assertThat(assistantMessage.getToolCalls()).hasSize(2);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("get_weather");
		assertThat(assistantMessage.getToolCalls().get(1).name()).isEqualTo("get_time");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testTextOnlyParts() throws Exception {
		// Arrange: a candidate with only text parts
		Part textPart1 = Part.builder().text("Hello ").build();
		Part textPart2 = Part.builder().text("world!").build();

		Content content = Content.builder().parts(List.of(textPart1, textPart2)).build();

		Candidate candidate = Candidate.builder().content(content).index(0).build();

		// Act
		GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
			.defaultOptions(GoogleGenAiChatOptions.builder().build())
			.build();
		Method method = GoogleGenAiChatModel.class.getDeclaredMethod("responseCandidateToGeneration", Candidate.class);
		method.setAccessible(true);
		List<Generation> generations = (List<Generation>) method.invoke(model, candidate);

		// Assert: one generation with concatenated text and no tool calls
		assertThat(generations).hasSize(1);
		var assistantMessage = (org.springframework.ai.chat.messages.AssistantMessage) generations.get(0).getOutput();

		assertThat(assistantMessage.getText()).isEqualTo("Hello world!");
		assertThat(assistantMessage.getToolCalls()).isNullOrEmpty();
	}

}