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
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that {@link GoogleGenAiChatModel} gives a
 * candidate.
 *
 * @author Christian Tzolov
 */
class GoogleGenAiFinishReasonCategoryTests {

	@Mock
	private Client client;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
			.build();
		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.client, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	@ParameterizedTest
	@CsvSource({ "STOP, COMPLETED", "MAX_TOKENS, TRUNCATED", "SAFETY, FILTERED", "RECITATION, FILTERED",
			"BLOCKLIST, FILTERED", "PROHIBITED_CONTENT, FILTERED", "SPII, FILTERED", "IMAGE_SAFETY, FILTERED",
			"IMAGE_PROHIBITED_CONTENT, FILTERED", "IMAGE_RECITATION, FILTERED", "LANGUAGE, OTHER", "OTHER, OTHER",
			"MALFORMED_FUNCTION_CALL, OTHER", "UNEXPECTED_TOOL_CALL, OTHER", "TOO_MANY_TOOL_CALLS, OTHER",
			"NO_IMAGE, OTHER", "IMAGE_OTHER, OTHER", "FINISH_REASON_UNSPECIFIED, UNSPECIFIED" })
	void classifiesTheFinishReasonsOfTheApi(FinishReason.Known finishReason, FinishReasonCategory expected) {
		assertThat(GoogleGenAiChatModel.finishReasonCategory(new FinishReason(finishReason), false))
			.isEqualTo(expected);
	}

	@Test
	void aFinishReasonTheSdkDoesNotKnowIsOther() {
		assertThat(GoogleGenAiChatModel.finishReasonCategory(new FinishReason("SOMETHING_NEW"), false))
			.isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void aStopWithAToolCallIsAToolCall() {
		assertThat(GoogleGenAiChatModel.finishReasonCategory(new FinishReason(FinishReason.Known.STOP), true))
			.isEqualTo(FinishReasonCategory.TOOL_CALL);
	}

	@Test
	void aFilteredCandidateIsFiltered() {
		Candidate candidate = Candidate.builder().finishReason(new FinishReason(FinishReason.Known.SAFETY)).build();
		this.chatModel
			.setMockGenerateContentResponse(GenerateContentResponse.builder().candidates(List.of(candidate)).build());

		ChatResponse response = this.chatModel.call(new Prompt("Explain"));

		ChatGenerationMetadata metadata = response.getResult().getMetadata();
		assertThat(metadata.getFinishReason()).isEqualTo("SAFETY");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.FILTERED);
		assertThat(response.hasFinishReasonCategory(FinishReasonCategory.FILTERED)).isTrue();
	}

	@Test
	void aCandidateThatAsksForAToolIsAToolCall() {
		Part functionCall = Part.builder()
			.functionCall(FunctionCall.builder().name("weather").args(Map.of("city", "Paris")).build())
			.build();
		Candidate candidate = Candidate.builder()
			.content(Content.builder().role("model").parts(List.of(functionCall)).build())
			.finishReason(new FinishReason(FinishReason.Known.STOP))
			.build();

		ChatGenerationMetadata metadata = this.chatModel.responseCandidateToGeneration(candidate).get(0).getMetadata();

		assertThat(metadata.getFinishReason()).isEqualTo("STOP");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.TOOL_CALL);
	}

}
