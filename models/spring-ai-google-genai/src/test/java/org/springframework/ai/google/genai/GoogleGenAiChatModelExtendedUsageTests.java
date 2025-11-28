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

package org.springframework.ai.google.genai;

import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.MediaModality;
import com.google.genai.types.ModalityTokenCount;
import com.google.genai.types.Part;
import com.google.genai.types.TrafficType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.metadata.GoogleGenAiModalityTokenCount;
import org.springframework.ai.google.genai.metadata.GoogleGenAiTrafficType;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GoogleGenAiChatModel extended usage metadata functionality.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiChatModelExtendedUsageTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	private RetryTemplate retryTemplate;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		// Initialize chat model with default options
		GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash-thinking-exp")
			.temperature(0.7)
			.build();

		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, defaultOptions, this.retryTemplate);
	}

	@Test
	void testExtendedUsageWithThinkingTokens() {
		// Create mock response with thinking tokens
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(175)
			.thoughtsTokenCount(25) // Thinking tokens for thinking models
			.build();

		Content responseContent = Content.builder()
			.parts(Part.builder().text("This is a thoughtful response").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash-thinking-exp")
			.build();

		// Set the mock response
		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Execute chat call
		UserMessage userMessage = new UserMessage("Tell me about thinking models");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Verify extended usage metadata
		assertThat(response).isNotNull();
		ChatResponseMetadata metadata = response.getMetadata();
		assertThat(metadata).isNotNull();

		Usage usage = metadata.getUsage();
		assertThat(usage).isInstanceOf(GoogleGenAiUsage.class);

		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) usage;
		assertThat(genAiUsage.getPromptTokens()).isEqualTo(100);
		assertThat(genAiUsage.getCompletionTokens()).isEqualTo(50);
		assertThat(genAiUsage.getTotalTokens()).isEqualTo(175);
		assertThat(genAiUsage.getThoughtsTokenCount()).isEqualTo(25); // Verify thinking
																		// tokens
	}

	@Test
	void testExtendedUsageWithCachedContent() {
		// Create mock response with cached content tokens
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(200)
			.candidatesTokenCount(50)
			.totalTokenCount(250)
			.cachedContentTokenCount(80) // Cached content tokens
			.build();

		Content responseContent = Content.builder()
			.parts(Part.builder().text("Response using cached context").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Execute chat call
		UserMessage userMessage = new UserMessage("Continue our conversation");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Verify cached content metadata
		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) response.getMetadata().getUsage();
		assertThat(genAiUsage.getCachedContentTokenCount()).isEqualTo(80);
		assertThat(genAiUsage.getPromptTokens()).isEqualTo(200); // Includes cached
																	// content
	}

	@Test
	void testExtendedUsageWithToolUseTokens() {
		// Create mock response with tool-use tokens
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(150)
			.candidatesTokenCount(75)
			.totalTokenCount(255)
			.toolUsePromptTokenCount(30) // Tool-use tokens
			.build();

		Content responseContent = Content.builder()
			.parts(Part.builder().text("Executed tool and got result").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Execute chat call
		UserMessage userMessage = new UserMessage("Calculate something using tools");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Verify tool-use tokens
		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) response.getMetadata().getUsage();
		assertThat(genAiUsage.getToolUsePromptTokenCount()).isEqualTo(30);
	}

	@Test
	void testExtendedUsageWithModalityBreakdown() {
		// Create modality token counts
		ModalityTokenCount textPromptModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(80)
			.build();

		ModalityTokenCount imagePromptModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.IMAGE))
			.tokenCount(120)
			.build();

		ModalityTokenCount textResponseModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(50)
			.build();

		// Create mock response with modality breakdowns
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(200)
			.candidatesTokenCount(50)
			.totalTokenCount(250)
			.promptTokensDetails(List.of(textPromptModality, imagePromptModality))
			.candidatesTokensDetails(List.of(textResponseModality))
			.build();

		Content responseContent = Content.builder().parts(Part.builder().text("Analyzed your image").build()).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Execute chat call
		UserMessage userMessage = new UserMessage("Analyze this image");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Verify modality breakdowns
		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) response.getMetadata().getUsage();

		List<GoogleGenAiModalityTokenCount> promptDetails = genAiUsage.getPromptTokensDetails();
		assertThat(promptDetails).hasSize(2);
		assertThat(promptDetails.get(0).getModality()).isEqualTo("TEXT");
		assertThat(promptDetails.get(0).getTokenCount()).isEqualTo(80);
		assertThat(promptDetails.get(1).getModality()).isEqualTo("IMAGE");
		assertThat(promptDetails.get(1).getTokenCount()).isEqualTo(120);

		List<GoogleGenAiModalityTokenCount> candidateDetails = genAiUsage.getCandidatesTokensDetails();
		assertThat(candidateDetails).hasSize(1);
		assertThat(candidateDetails.get(0).getModality()).isEqualTo("TEXT");
		assertThat(candidateDetails.get(0).getTokenCount()).isEqualTo(50);
	}

	@Test
	void testExtendedUsageWithTrafficType() {
		// Test ON_DEMAND traffic type
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.trafficType(new TrafficType(TrafficType.Known.ON_DEMAND))
			.build();

		Content responseContent = Content.builder().parts(Part.builder().text("Response").build()).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Test traffic type");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) response.getMetadata().getUsage();
		assertThat(genAiUsage.getTrafficType()).isEqualTo(GoogleGenAiTrafficType.ON_DEMAND);
	}

	@Test
	void testExtendedUsageDisabled() {
		// Configure to disable extended metadata
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash")
			.includeExtendedUsageMetadata(false) // Disable extended metadata
			.build();

		TestGoogleGenAiGeminiChatModel modelWithBasicUsage = new TestGoogleGenAiGeminiChatModel(this.mockClient,
				options, this.retryTemplate);

		// Create mock response
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.thoughtsTokenCount(25) // This should be ignored
			.build();

		Content responseContent = Content.builder().parts(Part.builder().text("Response").build()).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		modelWithBasicUsage.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Test");
		Prompt prompt = new Prompt(List.of(userMessage), options);
		ChatResponse response = modelWithBasicUsage.call(prompt);

		// Should get basic usage, not GoogleGenAiUsage
		Usage usage = response.getMetadata().getUsage();
		assertThat(usage).isNotInstanceOf(GoogleGenAiUsage.class);
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);
	}

	@Test
	void testCompleteExtendedUsageScenario() {
		// Create comprehensive mock response with all metadata
		ModalityTokenCount textPrompt = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(70)
			.build();

		ModalityTokenCount imagePrompt = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.IMAGE))
			.tokenCount(30)
			.build();

		ModalityTokenCount textCandidate = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(50)
			.build();

		ModalityTokenCount cachedText = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(40)
			.build();

		ModalityTokenCount toolUseText = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(20)
			.build();

		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(195)
			.thoughtsTokenCount(25)
			.cachedContentTokenCount(40)
			.toolUsePromptTokenCount(20)
			.promptTokensDetails(List.of(textPrompt, imagePrompt))
			.candidatesTokensDetails(List.of(textCandidate))
			.cacheTokensDetails(List.of(cachedText))
			.toolUsePromptTokensDetails(List.of(toolUseText))
			.trafficType(new TrafficType(TrafficType.Known.PROVISIONED_THROUGHPUT))
			.build();

		Content responseContent = Content.builder()
			.parts(Part.builder().text("Comprehensive response").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash-thinking-exp")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Complex request");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Comprehensive verification
		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) response.getMetadata().getUsage();

		// Basic tokens
		assertThat(genAiUsage.getPromptTokens()).isEqualTo(100);
		assertThat(genAiUsage.getCompletionTokens()).isEqualTo(50);
		assertThat(genAiUsage.getTotalTokens()).isEqualTo(195);

		// Extended tokens
		assertThat(genAiUsage.getThoughtsTokenCount()).isEqualTo(25);
		assertThat(genAiUsage.getCachedContentTokenCount()).isEqualTo(40);
		assertThat(genAiUsage.getToolUsePromptTokenCount()).isEqualTo(20);

		// Modality breakdowns
		assertThat(genAiUsage.getPromptTokensDetails()).hasSize(2);
		assertThat(genAiUsage.getCandidatesTokensDetails()).hasSize(1);
		assertThat(genAiUsage.getCacheTokensDetails()).hasSize(1);
		assertThat(genAiUsage.getToolUsePromptTokensDetails()).hasSize(1);

		// Traffic type
		assertThat(genAiUsage.getTrafficType()).isEqualTo(GoogleGenAiTrafficType.PROVISIONED_THROUGHPUT);

		// Native usage preserved
		assertThat(genAiUsage.getNativeUsage()).isNotNull();
		assertThat(genAiUsage.getNativeUsage()).isInstanceOf(GenerateContentResponseUsageMetadata.class);
	}

	@Test
	void testUsageWithNullMetadata() {
		// Create mock response without usage metadata
		Content responseContent = Content.builder().parts(Part.builder().text("Response").build()).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.0-flash")
			// No usage metadata
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Test");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		// Should handle null gracefully
		Usage usage = response.getMetadata().getUsage();
		assertThat(usage).isInstanceOf(GoogleGenAiUsage.class);

		GoogleGenAiUsage genAiUsage = (GoogleGenAiUsage) usage;
		assertThat(genAiUsage.getPromptTokens()).isEqualTo(0);
		assertThat(genAiUsage.getCompletionTokens()).isEqualTo(0);
		assertThat(genAiUsage.getTotalTokens()).isEqualTo(0);
		assertThat(genAiUsage.getThoughtsTokenCount()).isNull();
		assertThat(genAiUsage.getCachedContentTokenCount()).isNull();
	}

}
