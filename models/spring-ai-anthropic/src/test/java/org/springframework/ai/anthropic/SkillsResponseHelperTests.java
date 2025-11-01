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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SkillsResponseHelper}.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class SkillsResponseHelperTests {

	@Test
	void shouldReturnEmptyListForNullResponse() {
		List<String> fileIds = SkillsResponseHelper.extractFileIds(null);
		assertThat(fileIds).isEmpty();
	}

	@Test
	void shouldReturnEmptyListForResponseWithoutMetadata() {
		ChatResponse response = new ChatResponse(List.of());
		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).isEmpty();
	}

	@Test
	void shouldReturnEmptyListWhenNoFileContentBlocks() {
		// Create a response with text content but no files
		ContentBlock textBlock = new ContentBlock("Sample text response");
		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null, List.of(textBlock),
				"claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).isEmpty();
	}

	@Test
	void shouldExtractSingleFileId() {
		// Create a file content block
		ContentBlock fileBlock = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, "file_abc123", "report.xlsx");

		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null, List.of(fileBlock),
				"claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);

		assertThat(fileIds).hasSize(1);
		assertThat(fileIds).containsExactly("file_abc123");
	}

	@Test
	void shouldExtractMultipleFileIds() {
		// Create multiple file content blocks
		ContentBlock file1 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_123", "report.xlsx");

		ContentBlock file2 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_456", "presentation.pptx");

		ContentBlock file3 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_789", "document.docx");

		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null,
				List.of(file1, file2, file3), "claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);

		assertThat(fileIds).hasSize(3);
		assertThat(fileIds).containsExactly("file_123", "file_456", "file_789");
	}

	@Test
	void shouldExtractFileIdsFromMixedContent() {
		// Mix of text and file content blocks
		ContentBlock textBlock = new ContentBlock("I've created the files you requested");

		ContentBlock file1 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_excel", "data.xlsx");

		ContentBlock file2 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_pdf", "summary.pdf");

		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null,
				List.of(textBlock, file1, file2), "claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);

		assertThat(fileIds).hasSize(2);
		assertThat(fileIds).containsExactly("file_excel", "file_pdf");
	}

	@Test
	void shouldReturnNullContainerIdForNullResponse() {
		String containerId = SkillsResponseHelper.extractContainerId(null);
		assertThat(containerId).isNull();
	}

	@Test
	void shouldReturnNullContainerIdForResponseWithoutMetadata() {
		ChatResponse response = new ChatResponse(List.of());
		String containerId = SkillsResponseHelper.extractContainerId(response);
		assertThat(containerId).isNull();
	}

	@Test
	void shouldReturnNullContainerIdWhenNotPresent() {
		ContentBlock textBlock = new ContentBlock("Response without container");
		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null, List.of(textBlock),
				"claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		String containerId = SkillsResponseHelper.extractContainerId(response);
		assertThat(containerId).isNull();
	}

	@Test
	void shouldExtractContainerId() {
		ContentBlock textBlock = new ContentBlock("Response with container");
		ChatCompletionResponse.Container container = new ChatCompletionResponse.Container("container_xyz789");
		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null, List.of(textBlock),
				"claude-sonnet-4-5", null, null, null, container);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		String containerId = SkillsResponseHelper.extractContainerId(response);
		assertThat(containerId).isEqualTo("container_xyz789");
	}

	@Test
	void shouldHandleMultipleFileBlocks() {
		// Response with multiple file blocks in content
		ContentBlock file1 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_1", "file1.xlsx");
		ContentBlock file2 = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, "file_2", "file2.pptx");
		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_1", "message", null, List.of(file1, file2),
				"claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).hasSize(2);
		assertThat(fileIds).containsExactly("file_1", "file_2");
	}

	@Test
	void shouldIgnoreFileBlocksWithoutFileId() {
		// File block with null fileId should be ignored
		ContentBlock invalidFileBlock = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, "file.xlsx");

		ContentBlock validFileBlock = new ContentBlock(ContentBlock.Type.FILE, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, "file_valid", "valid.xlsx");

		ChatCompletionResponse apiResponse = new ChatCompletionResponse("msg_123", "message", null,
				List.of(invalidFileBlock, validFileBlock), "claude-sonnet-4-5", null, null, null, null);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.keyValue("anthropic-response", apiResponse)
			.build();

		ChatResponse response = new ChatResponse(List.of(), metadata);

		List<String> fileIds = SkillsResponseHelper.extractFileIds(response);

		// Should only extract the valid file ID
		assertThat(fileIds).hasSize(1);
		assertThat(fileIds).containsExactly("file_valid");
	}

}
