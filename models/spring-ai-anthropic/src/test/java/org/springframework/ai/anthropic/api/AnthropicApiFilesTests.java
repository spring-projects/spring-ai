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

package org.springframework.ai.anthropic.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.FileMetadata;
import org.springframework.ai.anthropic.api.AnthropicApi.FilesListResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Files API models in {@link AnthropicApi}.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class AnthropicApiFilesTests {

	@Test
	void shouldCreateFileMetadataRecord() {
		FileMetadata metadata = new FileMetadata("file_abc123", "sales_report.xlsx", 12345L,
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "2025-11-02T12:00:00Z",
				"2025-11-03T12:00:00Z");

		assertThat(metadata.id()).isEqualTo("file_abc123");
		assertThat(metadata.filename()).isEqualTo("sales_report.xlsx");
		assertThat(metadata.size()).isEqualTo(12345L);
		assertThat(metadata.mimeType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		assertThat(metadata.createdAt()).isEqualTo("2025-11-02T12:00:00Z");
		assertThat(metadata.expiresAt()).isEqualTo("2025-11-03T12:00:00Z");
	}

	@Test
	void shouldCreateFileMetadataWithNullFields() {
		FileMetadata metadata = new FileMetadata("file_123", "test.xlsx", 100L, "application/xlsx", null, null);

		assertThat(metadata.id()).isEqualTo("file_123");
		assertThat(metadata.filename()).isEqualTo("test.xlsx");
		assertThat(metadata.size()).isEqualTo(100L);
		assertThat(metadata.mimeType()).isEqualTo("application/xlsx");
		assertThat(metadata.createdAt()).isNull();
		assertThat(metadata.expiresAt()).isNull();
	}

	@Test
	void shouldCreateFilesListResponse() {
		List<FileMetadata> files = List.of(
				new FileMetadata("file_1", "file1.xlsx", 100L, "application/xlsx", null, null),
				new FileMetadata("file_2", "file2.pptx", 200L, "application/pptx", null, null));

		FilesListResponse response = new FilesListResponse(files, true, "next_page_token");

		assertThat(response.data()).hasSize(2);
		assertThat(response.data().get(0).id()).isEqualTo("file_1");
		assertThat(response.data().get(1).id()).isEqualTo("file_2");
		assertThat(response.hasMore()).isTrue();
		assertThat(response.nextPage()).isEqualTo("next_page_token");
	}

	@Test
	void shouldCreateFilesListResponseWithEmptyList() {
		FilesListResponse response = new FilesListResponse(List.of(), false, null);

		assertThat(response.data()).isEmpty();
		assertThat(response.hasMore()).isFalse();
		assertThat(response.nextPage()).isNull();
	}

	@Test
	void shouldCreateFilesListResponseWithMultiplePages() {
		List<FileMetadata> page1 = List.of(
				new FileMetadata("file_1", "file1.xlsx", 100L, "application/xlsx", "2025-11-02T10:00:00Z",
						"2025-11-03T10:00:00Z"),
				new FileMetadata("file_2", "file2.pptx", 200L, "application/pptx", "2025-11-02T11:00:00Z",
						"2025-11-03T11:00:00Z"));

		FilesListResponse response = new FilesListResponse(page1, true, "page_2_token");

		assertThat(response.data()).hasSize(2);
		assertThat(response.hasMore()).isTrue();
		assertThat(response.nextPage()).isEqualTo("page_2_token");

		// Verify metadata details
		FileMetadata first = response.data().get(0);
		assertThat(first.filename()).isEqualTo("file1.xlsx");
		assertThat(first.size()).isEqualTo(100L);
		assertThat(first.createdAt()).isEqualTo("2025-11-02T10:00:00Z");
		assertThat(first.expiresAt()).isEqualTo("2025-11-03T10:00:00Z");
	}

	@Test
	void shouldHandleDifferentFileTypes() {
		List<FileMetadata> files = List.of(
				new FileMetadata("file_1", "report.xlsx", 5000L,
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null, null),
				new FileMetadata("file_2", "presentation.pptx", 15000L,
						"application/vnd.openxmlformats-officedocument.presentationml.presentation", null, null),
				new FileMetadata("file_3", "document.docx", 8000L,
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document", null, null),
				new FileMetadata("file_4", "output.pdf", 25000L, "application/pdf", null, null));

		FilesListResponse response = new FilesListResponse(files, false, null);

		assertThat(response.data()).hasSize(4);
		assertThat(response.data()).extracting(FileMetadata::filename)
			.containsExactly("report.xlsx", "presentation.pptx", "document.docx", "output.pdf");
		assertThat(response.data()).extracting(FileMetadata::mimeType)
			.containsExactly("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"application/vnd.openxmlformats-officedocument.presentationml.presentation",
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf");
	}

}
