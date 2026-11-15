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
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.HarmProbability;
import com.google.genai.types.SafetyRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.metadata.GoogleGenAiPromptFeedback;
import org.springframework.ai.google.genai.metadata.GoogleGenAiSafetyRating;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the safety feedback that {@link GoogleGenAiChatModel} keeps from a Gemini
 * response: the finish message and safety ratings of a candidate, and the prompt feedback
 * of a response without candidates.
 *
 * @author Hyunjoon Choi
 * @since 2.1.0
 */
public class GoogleGenAiChatModelSafetyFeedbackTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	private RetryTemplate retryTemplate;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		// Initialize chat model with default options
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash-thinking-exp")
			.temperature(0.7)
			.build();

		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, options, this.retryTemplate);
	}

	@Test
	void testSafetyRatingsIsNotNull() {
		// a candidate that Gemini blocked while generating the answer
		// (values taken from the live response captured in GH-7401)
		String finishMessage = "The model output could not be generated. This output contains sensitive words "
				+ "that violate Google's [Generative AI Prohibited Use policy]"
				+ "(https://policies.google.com/terms/generative-ai/use-policy). "
				+ "If you think this was an error, [send feedback](https://aistudio.google.com/docs/troubleshooting).";

		Candidate candidate = Candidate.builder()
			.index(0)
			.finishReason(FinishReason.Known.SAFETY)
			.finishMessage(finishMessage)
			.safetyRatings(List.of(
					SafetyRating.builder()
						.category(HarmCategory.Known.HARM_CATEGORY_HARASSMENT)
						.probability(HarmProbability.Known.LOW)
						.build(),
					SafetyRating.builder()
						.category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
						.probability(HarmProbability.Known.NEGLIGIBLE)
						.build(),
					SafetyRating.builder()
						.category(HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT)
						.probability(HarmProbability.Known.NEGLIGIBLE)
						.build(),
					SafetyRating.builder()
						.category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
						.probability(HarmProbability.Known.NEGLIGIBLE)
						.build()))
			.build();

		this.chatModel
			.setMockGenerateContentResponse(GenerateContentResponse.builder().candidates(List.of(candidate)).build());

		ChatResponse response = this.chatModel.call(new Prompt("Explain"));

		ChatGenerationMetadata metadata = response.getResult().getMetadata();
		assertThat(metadata.<String>get("finishMessage")).isEqualTo(finishMessage);
		List<GoogleGenAiSafetyRating> safetyRatings = metadata.get("safetyRatings");
		assertThat(safetyRatings).hasSize(4).first().satisfies(rating -> {
			assertThat(rating.category()).isEqualTo("HARM_CATEGORY_HARASSMENT");
			assertThat(rating.probability()).isEqualTo("LOW");
		});
	}

	@Test
	void keepsASafetyRatingValueTheSdkDoesNotKnow() {
		Candidate candidate = Candidate.builder()
			.index(0)
			.finishReason(FinishReason.Known.SAFETY)
			.safetyRatings(List.of(SafetyRating.builder()
				.category(new HarmCategory("HARM_CATEGORY_SOMETHING_NEW"))
				.probability(new HarmProbability("SOMETHING_NEW"))
				.build()))
			.build();
		this.chatModel
			.setMockGenerateContentResponse(GenerateContentResponse.builder().candidates(List.of(candidate)).build());

		ChatResponse response = this.chatModel.call(new Prompt("Explain"));

		List<GoogleGenAiSafetyRating> safetyRatings = response.getResult().getMetadata().get("safetyRatings");
		assertThat(safetyRatings).singleElement().satisfies(rating -> {
			assertThat(rating.category()).isEqualTo("HARM_CATEGORY_SOMETHING_NEW");
			assertThat(rating.probability()).isEqualTo("SOMETHING_NEW");
		});
	}

	@Test
	void testPromptFeedbackIsNotNull() {
		// a response whose prompt Gemini blocked, so it has no candidates
		String blockReasonMessage = "Prompt was blocked";

		GenerateContentResponse response = GenerateContentResponse.builder()
			.candidates(List.of())
			.promptFeedback(GenerateContentResponsePromptFeedback.builder()
				.blockReason(BlockedReason.Known.SAFETY)
				.blockReasonMessage(blockReasonMessage)
				.build())
			.build();

		this.chatModel.setMockGenerateContentResponse(response);

		ChatResponse chatResponse = this.chatModel.call(new Prompt("Explain"));

		assertThat(chatResponse.getResults()).isEmpty();
		GoogleGenAiPromptFeedback promptFeedback = chatResponse.getMetadata().get("promptFeedback");
		assertThat(promptFeedback).isNotNull();
		assertThat(promptFeedback.blockReason()).isEqualTo("SAFETY");
		assertThat(promptFeedback.blockReasonMessage()).isEqualTo(blockReasonMessage);
	}

}
