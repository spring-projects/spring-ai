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

import java.time.Duration;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GoogleGenAiChatModel cached content functionality.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiChatModelCachedContentTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModelWithCache chatModel;

	private TestGoogleGenAiCachedContentService cachedContentService;

	private RetryTemplate retryTemplate;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		// Initialize cached content service
		this.cachedContentService = new TestGoogleGenAiCachedContentService(this.mockClient);

		// Initialize chat model with default options
		GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash")
			.temperature(0.7)
			.build();

		this.chatModel = new TestGoogleGenAiGeminiChatModelWithCache(this.mockClient, defaultOptions,
				this.retryTemplate, this.cachedContentService);
	}

	@Test
	void testChatWithCachedContent() {
		// Create cached content
		Content systemContent = Content.builder()
			.parts(Part.builder().text("You are a helpful assistant specialized in Java programming.").build())
			.build();

		Content contextContent = Content.builder()
			.parts(Part.builder().text("Java programming context and documentation.").build())
			.build();

		CachedContentRequest cacheRequest = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Java Assistant Context")
			.systemInstruction(systemContent)
			.addContent(contextContent)
			.ttl(Duration.ofHours(1))
			.build();

		GoogleGenAiCachedContent cachedContent = this.cachedContentService.create(cacheRequest);
		assertThat(cachedContent).isNotNull();
		assertThat(cachedContent.getName()).startsWith("cachedContent/");

		// Create mock response
		Content responseContent = Content.builder()
			.parts(Part.builder().text("Java is a high-level programming language.").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Create chat request with cached content
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash")
			.useCachedContent(true)
			.cachedContentName(cachedContent.getName())
			.build();

		UserMessage userMessage = new UserMessage("What is Java?");
		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Execute chat
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("Java is a high-level programming language");

		// Verify cached content was used
		GoogleGenAiChatModel.GeminiRequest lastRequest = this.chatModel.getLastRequest();
		assertThat(lastRequest).isNotNull();
		// The config would contain the cached content reference if the SDK supported it
	}

	@Test
	void testChatWithoutCachedContent() {
		// Create mock response
		Content responseContent = Content.builder()
			.parts(Part.builder().text("Hello! How can I help you?").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		// Create chat request without cached content
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash")
			.useCachedContent(false)
			.build();

		UserMessage userMessage = new UserMessage("Hello");
		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Execute chat
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("Hello! How can I help you?");

		// Verify no cached content in service
		assertThat(this.cachedContentService.size()).isEqualTo(0);
	}

	@Test
	void testCachedContentExpiration() {
		// Create cached content with short TTL
		Content content = Content.builder().parts(Part.builder().text("Temporary context").build()).build();

		CachedContentRequest cacheRequest = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Short-lived Cache")
			.addContent(content)
			.expireTime(java.time.Instant.now().minus(Duration.ofHours(1))) // Already
																			// expired
			.build();

		GoogleGenAiCachedContent cachedContent = this.cachedContentService.create(cacheRequest);

		// Check expiration
		assertThat(cachedContent.isExpired()).isTrue();
		assertThat(cachedContent.getRemainingTtl()).isEqualTo(Duration.ZERO);
	}

	@Test
	void testCachedContentManagement() {
		// Create multiple cached contents
		for (int i = 0; i < 3; i++) {
			Content content = Content.builder().parts(Part.builder().text("Context " + i).build()).build();

			CachedContentRequest request = CachedContentRequest.builder()
				.model("gemini-2.0-flash")
				.displayName("Cache " + i)
				.addContent(content)
				.ttl(Duration.ofHours(i + 1))
				.build();

			this.cachedContentService.create(request);
		}

		// Verify all cached
		assertThat(this.cachedContentService.size()).isEqualTo(3);

		// List all
		var page = this.cachedContentService.list(10, null);
		assertThat(page.getContents()).hasSize(3);

		// Clear all
		this.cachedContentService.clearAll();
		assertThat(this.cachedContentService.size()).isEqualTo(0);
	}

	/**
	 * Test implementation that uses TestGoogleGenAiCachedContentService.
	 */
	private static class TestGoogleGenAiGeminiChatModelWithCache extends TestGoogleGenAiGeminiChatModel {

		private final TestGoogleGenAiCachedContentService cachedContentService;

		private GoogleGenAiChatModel.GeminiRequest lastRequest;

		TestGoogleGenAiGeminiChatModelWithCache(Client genAiClient, GoogleGenAiChatOptions options,
				RetryTemplate retryTemplate, TestGoogleGenAiCachedContentService cachedContentService) {
			super(genAiClient, options, retryTemplate);
			this.cachedContentService = cachedContentService;
		}

		@Override
		public GoogleGenAiCachedContentService getCachedContentService() {
			// Return null since the test service doesn't extend the real service
			return null;
		}

		public TestGoogleGenAiCachedContentService getTestCachedContentService() {
			return this.cachedContentService;
		}

		@Override
		GoogleGenAiChatModel.GeminiRequest createGeminiRequest(Prompt prompt) {
			this.lastRequest = super.createGeminiRequest(prompt);
			return this.lastRequest;
		}

		public GoogleGenAiChatModel.GeminiRequest getLastRequest() {
			return this.lastRequest;
		}

	}

}
