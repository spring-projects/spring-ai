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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.CitationDocument;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Anthropic Citations API support.
 *
 * @author Soby Chacko
 * @since 1.1.0
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicCitationIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicCitationIT.class);

	@Autowired
	private AnthropicChatModel chatModel;

	@Test
	void testPlainTextCitation() {
		// Create a citation document with plain text
		CitationDocument document = CitationDocument.builder()
			.plainText(
					"The Eiffel Tower is located in Paris, France. It was completed in 1889 and stands 330 meters tall.")
			.title("Eiffel Tower Facts")
			.citationsEnabled(true)
			.build();

		// Create a prompt asking a question about the document
		// Use explicit instruction to answer from the provided document
		UserMessage userMessage = new UserMessage(
				"Based solely on the provided document, where is the Eiffel Tower located and when was it completed?");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getName())
			.maxTokens(2048)
			.temperature(0.0) // Use temperature 0 for more deterministic responses
			.citationDocuments(document)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Call the model
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response exists and is not empty
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();

		// Verify citations are present in metadata (this is the core feature being
		// tested)
		Object citationsObj = response.getMetadata().get("citations");
		assertThat(citationsObj).as("Citations should be present in response metadata").isNotNull();

		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) citationsObj;
		assertThat(citations).as("Citation list should not be empty").isNotEmpty();

		// Verify citation structure - all citations should have proper fields
		for (Citation citation : citations) {
			assertThat(citation.getType()).as("Citation type should be CHAR_LOCATION for plain text")
				.isEqualTo(Citation.LocationType.CHAR_LOCATION);
			assertThat(citation.getCitedText()).as("Cited text should not be blank").isNotBlank();
			assertThat(citation.getDocumentIndex()).as("Document index should be 0 (first document)").isEqualTo(0);
			assertThat(citation.getDocumentTitle()).as("Document title should match").isEqualTo("Eiffel Tower Facts");
			assertThat(citation.getStartCharIndex()).as("Start char index should be non-negative")
				.isGreaterThanOrEqualTo(0);
			assertThat(citation.getEndCharIndex()).as("End char index should be greater than start")
				.isGreaterThan(citation.getStartCharIndex());
		}
	}

	@Test
	void testMultipleCitationDocuments() {
		// Create multiple citation documents
		CitationDocument parisDoc = CitationDocument.builder()
			.plainText("Paris is the capital city of France. It has a population of about 2.1 million people.")
			.title("Paris Information")
			.citationsEnabled(true)
			.build();

		CitationDocument eiffelDoc = CitationDocument.builder()
			.plainText("The Eiffel Tower was designed by Gustave Eiffel and completed in 1889 for the World's Fair.")
			.title("Eiffel Tower History")
			.citationsEnabled(true)
			.build();

		// Use explicit instruction to answer from the provided documents
		UserMessage userMessage = new UserMessage(
				"Based solely on the provided documents, what is the capital of France and who designed the Eiffel Tower?");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getName())
			.maxTokens(1024)
			.temperature(0.0) // Use temperature 0 for more deterministic responses
			.citationDocuments(parisDoc, eiffelDoc)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Call the model
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response exists and is not empty
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();

		// Verify citations are present (this is the core feature being tested)
		Object citationsObj = response.getMetadata().get("citations");
		assertThat(citationsObj).as("Citations should be present in response metadata").isNotNull();

		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) citationsObj;
		assertThat(citations).as("Citation list should not be empty").isNotEmpty();

		// Verify we have citations from both documents
		// Check that citations reference both document indices (0 and 1)
		boolean hasDoc0 = citations.stream().anyMatch(c -> c.getDocumentIndex() == 0);
		boolean hasDoc1 = citations.stream().anyMatch(c -> c.getDocumentIndex() == 1);
		assertThat(hasDoc0 && hasDoc1).as("Should have citations from at least one document").isTrue();

		// Verify citation structure for all citations
		for (Citation citation : citations) {
			assertThat(citation.getType()).as("Citation type should be CHAR_LOCATION for plain text")
				.isEqualTo(Citation.LocationType.CHAR_LOCATION);
			assertThat(citation.getCitedText()).as("Cited text should not be blank").isNotBlank();
			assertThat(citation.getDocumentIndex()).as("Document index should be 0 or 1").isIn(0, 1);
			assertThat(citation.getDocumentTitle()).as("Document title should be one of the provided titles")
				.isIn("Paris Information", "Eiffel Tower History");
			assertThat(citation.getStartCharIndex()).as("Start char index should be non-negative")
				.isGreaterThanOrEqualTo(0);
			assertThat(citation.getEndCharIndex()).as("End char index should be greater than start")
				.isGreaterThan(citation.getStartCharIndex());
		}
	}

	@Test
	void testCustomContentCitation() {
		// Create a citation document with custom content blocks for fine-grained citation
		// control
		CitationDocument document = CitationDocument.builder()
			.customContent("The Great Wall of China is approximately 21,196 kilometers long.",
					"It was built over many centuries, starting in the 7th century BC.",
					"The wall was constructed to protect Chinese states from invasions.")
			.title("Great Wall Facts")
			.citationsEnabled(true)
			.build();

		UserMessage userMessage = new UserMessage(
				"Based solely on the provided document, how long is the Great Wall of China and when was it started?");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getName())
			.maxTokens(1024)
			.temperature(0.0)
			.citationDocuments(document)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response and citations
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();

		Object citationsObj = response.getMetadata().get("citations");
		assertThat(citationsObj).as("Citations should be present in response metadata").isNotNull();

		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) citationsObj;
		assertThat(citations).as("Citation list should not be empty").isNotEmpty();

		// For custom content, citations should use CONTENT_BLOCK_LOCATION type
		for (Citation citation : citations) {
			assertThat(citation.getType()).as("Citation type should be CONTENT_BLOCK_LOCATION for custom content")
				.isEqualTo(Citation.LocationType.CONTENT_BLOCK_LOCATION);
			assertThat(citation.getCitedText()).as("Cited text should not be blank").isNotBlank();
			assertThat(citation.getDocumentIndex()).as("Document index should be 0").isEqualTo(0);
			assertThat(citation.getDocumentTitle()).as("Document title should match").isEqualTo("Great Wall Facts");
			// For content block citations, we have start/end block indices instead of
			// char indices
			assertThat(citation.getStartBlockIndex()).as("Start block index should be non-negative")
				.isGreaterThanOrEqualTo(0);
			assertThat(citation.getEndBlockIndex()).as("End block index should be >= start")
				.isGreaterThanOrEqualTo(citation.getStartBlockIndex());
		}
	}

	@Test
	void testPdfCitation() throws IOException {
		// Load the test PDF from resources
		CitationDocument document = CitationDocument.builder()
			.pdfFile("src/test/resources/spring-ai-reference-overview.pdf")
			.title("Spring AI Reference")
			.citationsEnabled(true)
			.build();

		UserMessage userMessage = new UserMessage("Based solely on the provided document, what is Spring AI?");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getName())
			.maxTokens(1024)
			.temperature(0.0)
			.citationDocuments(document)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response and citations
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();

		Object citationsObj = response.getMetadata().get("citations");
		assertThat(citationsObj).as("Citations should be present for PDF documents").isNotNull();

		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) citationsObj;
		assertThat(citations).as("Citation list should not be empty for PDF").isNotEmpty();

		// For PDF documents, citations should use PAGE_LOCATION type
		for (Citation citation : citations) {
			assertThat(citation.getType()).as("Citation type should be PAGE_LOCATION for PDF")
				.isEqualTo(Citation.LocationType.PAGE_LOCATION);
			assertThat(citation.getCitedText()).as("Cited text should not be blank").isNotBlank();
			assertThat(citation.getDocumentIndex()).as("Document index should be 0").isEqualTo(0);
			assertThat(citation.getDocumentTitle()).as("Document title should match").isEqualTo("Spring AI Reference");
			// For page citations, we have start/end page numbers instead of char indices
			assertThat(citation.getStartPageNumber()).as("Start page number should be positive").isGreaterThan(0);
			assertThat(citation.getEndPageNumber()).as("End page number should be >= start")
				.isGreaterThanOrEqualTo(citation.getStartPageNumber());
		}
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public AnthropicApi anthropicApi() {
			return AnthropicApi.builder().apiKey(getApiKey()).build();
		}

		private String getApiKey() {
			String apiKey = System.getenv("ANTHROPIC_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key. Put it in an environment variable under the name ANTHROPIC_API_KEY");
			}
			return apiKey;
		}

		@Bean
		public AnthropicChatModel anthropicChatModel(AnthropicApi api) {
			return AnthropicChatModel.builder().anthropicApi(api).build();
		}

	}

}
