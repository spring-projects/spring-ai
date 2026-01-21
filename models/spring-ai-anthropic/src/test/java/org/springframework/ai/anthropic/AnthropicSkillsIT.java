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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicSkill;
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
 * Integration tests for Anthropic Skills API support.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
@SpringBootTest(classes = AnthropicSkillsIT.Config.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicSkillsIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSkillsIT.class);

	@Autowired
	private AnthropicChatModel chatModel;

	@Autowired
	private AnthropicApi anthropicApi;

	@Test
	void shouldGenerateExcelWithXlsxSkill(@TempDir Path tempDir) throws IOException {
		// Create a prompt requesting Excel generation
		// Use explicit language to trigger skill execution
		UserMessage userMessage = new UserMessage(
				"Please create an Excel file (.xlsx) with 3 columns: Name, Age, City. Add 5 sample rows of data. "
						+ "Generate the actual file using the xlsx skill.");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(4096)
			.anthropicSkill(AnthropicSkill.XLSX)
			.toolChoice(new AnthropicApi.ToolChoiceAny())
			.internalToolExecutionEnabled(false)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Call the model
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response exists and is not empty
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();

		// Log the response for debugging
		logger.info("XLSX Skill Response: {}", responseText);

		// Log metadata for debugging
		if (response.getMetadata() != null) {
			logger.info("Response Metadata: {}", response.getMetadata());
		}

		// Verify the response mentions Excel/spreadsheet creation
		// The exact content may vary, but it should reference the created file
		assertThat(responseText.toLowerCase()).as("Response should mention spreadsheet or Excel")
			.containsAnyOf("spreadsheet", "excel", "xlsx", "created", "file");

		// Extract file IDs from the response
		List<String> fileIds = AnthropicSkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).as("Skills response should contain at least one file ID").isNotEmpty();

		logger.info("Extracted {} file ID(s): {}", fileIds.size(), fileIds);

		// Download all files
		List<Path> downloadedFiles = AnthropicSkillsResponseHelper.downloadAllFiles(response, this.anthropicApi,
				tempDir);
		assertThat(downloadedFiles).as("Should download at least one file").isNotEmpty();

		// Verify files exist and have content
		for (Path filePath : downloadedFiles) {
			assertThat(filePath).exists();
			assertThat(Files.size(filePath)).as("Downloaded file should not be empty").isGreaterThan(0);
			logger.info("Downloaded file: {} ({} bytes)", filePath.getFileName(), Files.size(filePath));
		}

		// Verify at least one Excel file was created
		boolean hasXlsxFile = downloadedFiles.stream()
			.anyMatch(path -> path.toString().toLowerCase().endsWith(".xlsx"));
		assertThat(hasXlsxFile).as("At least one .xlsx file should be downloaded").isTrue();
	}

	@Test
	void shouldGeneratePowerPointWithPptxSkill(@TempDir Path tempDir) throws IOException {
		// Create a prompt requesting PowerPoint generation
		// Use explicit language to trigger skill execution
		UserMessage userMessage = new UserMessage(
				"Please create a PowerPoint presentation file (.pptx) about Spring AI with 3 slides: "
						+ "Introduction, Features, and Conclusion. Generate the actual file using the pptx skill.");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(4096)
			.anthropicSkill(AnthropicSkill.PPTX)
			.toolChoice(new AnthropicApi.ToolChoiceAny())
			.internalToolExecutionEnabled(false)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Call the model
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response exists and is not empty
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();

		// Log the response for debugging
		logger.info("PPTX Skill Response: {}", responseText);

		// Verify the response mentions PowerPoint/presentation creation
		assertThat(responseText.toLowerCase()).as("Response should mention presentation or PowerPoint")
			.containsAnyOf("presentation", "powerpoint", "pptx", "slide", "created", "file");

		// Extract file IDs from the response
		List<String> fileIds = AnthropicSkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).as("Skills response should contain at least one file ID").isNotEmpty();

		logger.info("Extracted {} file ID(s): {}", fileIds.size(), fileIds);

		// Download all files
		List<Path> downloadedFiles = AnthropicSkillsResponseHelper.downloadAllFiles(response, this.anthropicApi,
				tempDir);
		assertThat(downloadedFiles).as("Should download at least one file").isNotEmpty();

		// Verify files exist and have content
		for (Path filePath : downloadedFiles) {
			assertThat(filePath).exists();
			assertThat(Files.size(filePath)).as("Downloaded file should not be empty").isGreaterThan(0);
			logger.info("Downloaded file: {} ({} bytes)", filePath.getFileName(), Files.size(filePath));
		}

		// Verify at least one PowerPoint file was created
		boolean hasPptxFile = downloadedFiles.stream()
			.anyMatch(path -> path.toString().toLowerCase().endsWith(".pptx"));
		assertThat(hasPptxFile).as("At least one .pptx file should be downloaded").isTrue();
	}

	@Test
	void shouldUseMultipleSkills(@TempDir Path tempDir) throws IOException {
		// Create a prompt that could use multiple skills
		// Use explicit language to trigger skill execution
		UserMessage userMessage = new UserMessage(
				"Please create two files: 1) An Excel file (.xlsx) with sample sales data (use xlsx skill), "
						+ "and 2) A PowerPoint presentation file (.pptx) summarizing the data (use pptx skill). "
						+ "Generate the actual files.");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(4096)
			.anthropicSkill(AnthropicSkill.XLSX)
			.anthropicSkill(AnthropicSkill.PPTX)
			.toolChoice(new AnthropicApi.ToolChoiceAny())
			.internalToolExecutionEnabled(false)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);

		// Call the model
		ChatResponse response = this.chatModel.call(prompt);

		// Verify response exists and is not empty
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();

		// Log the response for debugging
		logger.info("Multiple Skills Response: {}", responseText);

		// Verify the response mentions document creation
		assertThat(responseText.toLowerCase()).as("Response should mention file creation")
			.containsAnyOf("spreadsheet", "presentation", "created", "file", "xlsx", "pptx");

		// Extract file IDs from the response
		List<String> fileIds = AnthropicSkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).as("Skills response should contain at least one file ID").isNotEmpty();

		logger.info("Extracted {} file ID(s): {}", fileIds.size(), fileIds);

		// Download all files
		List<Path> downloadedFiles = AnthropicSkillsResponseHelper.downloadAllFiles(response, this.anthropicApi,
				tempDir);
		assertThat(downloadedFiles).as("Should download at least one file").isNotEmpty();
		assertThat(downloadedFiles.size()).as("Should download multiple files").isGreaterThanOrEqualTo(2);

		// Verify files exist and have content
		for (Path filePath : downloadedFiles) {
			assertThat(filePath).exists();
			assertThat(Files.size(filePath)).as("Downloaded file should not be empty").isGreaterThan(0);
			logger.info("Downloaded file: {} ({} bytes)", filePath.getFileName(), Files.size(filePath));
		}

		// Verify both file types were created
		boolean hasXlsxFile = downloadedFiles.stream()
			.anyMatch(path -> path.toString().toLowerCase().endsWith(".xlsx"));
		boolean hasPptxFile = downloadedFiles.stream()
			.anyMatch(path -> path.toString().toLowerCase().endsWith(".pptx"));

		assertThat(hasXlsxFile || hasPptxFile).as("At least one .xlsx or .pptx file should be downloaded").isTrue();
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
