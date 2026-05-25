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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Integration tests for Anthropic Skills API support via the Java SDK.
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
	private AnthropicClient anthropicClient;

	@Test
	void shouldGenerateExcelWithXlsxSkill(@TempDir Path tempDir) throws IOException {
		UserMessage userMessage = new UserMessage(
				"Please create an Excel file (.xlsx) with 3 columns: Name, Age, City. "
						+ "Add 5 sample rows of data. Generate the actual file using the xlsx skill.");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_5)
			.maxTokens(4096)
			.skill(AnthropicSkill.XLSX)
			.toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()))
			.internalToolExecutionEnabled(false)
			.build();

		Prompt prompt = new Prompt(List.of(userMessage), options);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText).as("Response text should not be blank").isNotBlank();
		logger.info("XLSX Skill Response: {}", responseText);

		assertThat(responseText.toLowerCase()).as("Response should mention spreadsheet or Excel")
			.containsAnyOf("spreadsheet", "excel", "xlsx", "created", "file");

		List<String> fileIds = AnthropicSkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).as("Skills response should contain at least one file ID").isNotEmpty();
		logger.info("Extracted {} file ID(s): {}", fileIds.size(), fileIds);

		List<Path> downloadedFiles = AnthropicSkillsResponseHelper.downloadAllFiles(response, this.anthropicClient,
				tempDir);
		assertThat(downloadedFiles).as("Should download at least one file").isNotEmpty();

		for (Path filePath : downloadedFiles) {
			assertThat(filePath).exists();
			assertThat(Files.size(filePath)).as("Downloaded file should not be empty").isGreaterThan(0);
			logger.info("Downloaded file: {} ({} bytes)", filePath.getFileName(), Files.size(filePath));
		}

		boolean hasXlsxFile = downloadedFiles.stream()
			.anyMatch(path -> path.toString().toLowerCase().endsWith(".xlsx"));
		assertThat(hasXlsxFile).as("At least one .xlsx file should be downloaded").isTrue();
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public AnthropicClient anthropicClient() {
			String apiKey = System.getenv("ANTHROPIC_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key. Put it in an environment variable under the name ANTHROPIC_API_KEY");
			}
			return AnthropicSetup.setupSyncClient(null, apiKey, null, null, null, null);
		}

		@Bean
		public AnthropicChatModel anthropicChatModel(AnthropicClient client) {
			return AnthropicChatModel.builder().anthropicClient(client).build();
		}

	}

}
