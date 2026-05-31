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

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that GoogleGenAiChatModel filters out candidates with empty text content.
 * Regression for GH-4556: Google API rejects follow-up requests when chat history
 * contains empty AssistantMessage entries produced by empty-text candidates.
 *
 * @author Gorre Surya
 */
class GoogleGenAiEmptyCandidateFilterTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder().model("gemini-2.0-flash-001").build();
		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, defaultOptions,
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	@Test
	void emptyCandidateIsFilteredFromResults() {
		// Gemini occasionally returns a second candidate with empty text content.
		// That empty AssistantMessage must NOT appear in the ChatResponse.
		Candidate validCandidate = candidate("The forecast is sunny with 20°C.");
		Candidate emptyCandidate = candidate("");

		this.chatModel.setMockGenerateContentResponse(response(List.of(validCandidate, emptyCandidate)));

		ChatResponse chatResponse = this.chatModel.call(new Prompt(List.of(new UserMessage("What's the weather?"))));

		assertThat(chatResponse.getResults()).hasSize(1);
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("The forecast is sunny with 20°C.");
	}

	@Test
	void allEmptyCandidatesProduceEmptyGenerations() {
		Candidate emptyCandidate1 = candidate("");
		Candidate emptyCandidate2 = candidate("");

		this.chatModel.setMockGenerateContentResponse(response(List.of(emptyCandidate1, emptyCandidate2)));

		ChatResponse chatResponse = this.chatModel.call(new Prompt(List.of(new UserMessage("Trigger empty response"))));

		assertThat(chatResponse.getResults()).isEmpty();
	}

	@Test
	void validCandidateIsPreservedWhenNoEmptyCandidatesPresent() {
		Candidate validCandidate = candidate("Hello! How can I help you today?");

		this.chatModel.setMockGenerateContentResponse(response(List.of(validCandidate)));

		ChatResponse chatResponse = this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"))));

		assertThat(chatResponse.getResults()).hasSize(1);
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("Hello! How can I help you today?");
	}

	@Test
	void multipleCandidatesWithSomeEmptyAreFilteredCorrectly() {
		Candidate first = candidate("First answer.");
		Candidate empty = candidate("");
		Candidate second = candidate("Second answer.");

		this.chatModel.setMockGenerateContentResponse(response(List.of(first, empty, second)));

		ChatResponse chatResponse = this.chatModel.call(new Prompt(List.of(new UserMessage("Give me two answers"))));

		assertThat(chatResponse.getResults()).hasSize(2);
		assertThat(chatResponse.getResults().get(0).getOutput().getText()).isEqualTo("First answer.");
		assertThat(chatResponse.getResults().get(1).getOutput().getText()).isEqualTo("Second answer.");
	}

	private static Candidate candidate(String text) {
		Part part = Part.builder().text(text).build();
		Content content = Content.builder().parts(List.of(part)).build();
		return Candidate.builder().content(content).finishReason(FinishReason.Known.STOP).index(0).build();
	}

	private static GenerateContentResponse response(List<Candidate> candidates) {
		GenerateContentResponseUsageMetadata usage = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(10)
			.candidatesTokenCount(5)
			.totalTokenCount(15)
			.build();
		return GenerateContentResponse.builder()
			.candidates(candidates)
			.usageMetadata(usage)
			.modelVersion("gemini-2.0-flash-001")
			.build();
	}

}
