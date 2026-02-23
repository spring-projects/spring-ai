/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.reader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class TextReaderTests {

	@Test
	void loadText() {
		Resource resource = new DefaultResourceLoader().getResource("classpath:text_source.txt");
		assertThat(resource).isNotNull();
		TextReader textReader = new TextReader(resource);
		textReader.getCustomMetadata().put("customKey", "Value");

		List<Document> documents0 = textReader.get();

		List<Document> documents = TokenTextSplitter.builder().build().apply(documents0);

		assertThat(documents.size()).isEqualTo(54);

		for (Document document : documents) {
			assertThat(document.getMetadata().get("customKey")).isEqualTo("Value");
			assertThat(document.getMetadata().get(TextReader.SOURCE_METADATA)).isEqualTo("text_source.txt");
			assertThat(document.getMetadata().get(TextReader.CHARSET_METADATA)).isEqualTo("UTF-8");
			assertThat(document.getText()).isNotEmpty();
		}
	}

	@Test
	void loadTextFromByteArrayResource() {
		// Test with default constructor
		Resource defaultByteArrayResource = new ByteArrayResource("Test content".getBytes(StandardCharsets.UTF_8));
		assertThat(defaultByteArrayResource).isNotNull();
		TextReader defaultTextReader = new TextReader(defaultByteArrayResource);
		defaultTextReader.getCustomMetadata().put("customKey", "DefaultValue");

		List<Document> defaultDocuments = defaultTextReader.get();

		assertThat(defaultDocuments).hasSize(1);

		Document defaultDocument = defaultDocuments.get(0);
		assertThat(defaultDocument.getMetadata()).containsEntry("customKey", "DefaultValue")
			.containsEntry(TextReader.CHARSET_METADATA, "UTF-8");

		// Assert on the SOURCE_METADATA for default ByteArrayResource
		assertThat(defaultDocument.getMetadata().get(TextReader.SOURCE_METADATA))
			.isEqualTo("Byte array resource [resource loaded from byte array]");

		assertThat(defaultDocument.getText()).isEqualTo("Test content");

		// Test with custom description constructor
		String customDescription = "Custom byte array resource";
		Resource customByteArrayResource = new ByteArrayResource(
				"Another test content".getBytes(StandardCharsets.UTF_8), customDescription);
		assertThat(customByteArrayResource).isNotNull();
		TextReader customTextReader = new TextReader(customByteArrayResource);
		customTextReader.getCustomMetadata().put("customKey", "CustomValue");

		List<Document> customDocuments = customTextReader.get();

		assertThat(customDocuments).hasSize(1);

		Document customDocument = customDocuments.get(0);
		assertThat(customDocument.getMetadata()).containsEntry("customKey", "CustomValue")
			.containsEntry(TextReader.CHARSET_METADATA, "UTF-8");

		// Assert on the SOURCE_METADATA for custom ByteArrayResource
		assertThat(customDocument.getMetadata().get(TextReader.SOURCE_METADATA))
			.isEqualTo("Byte array resource [Custom byte array resource]");

		assertThat(customDocument.getText()).isEqualTo("Another test content");
	}

	@Test
	void loadEmptyText() {
		Resource emptyResource = new ByteArrayResource("".getBytes(StandardCharsets.UTF_8));
		TextReader textReader = new TextReader(emptyResource);

		List<Document> documents = textReader.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEmpty();
		assertThat(documents.get(0).getMetadata().get(TextReader.CHARSET_METADATA)).isEqualTo("UTF-8");
	}

	@Test
	void loadTextWithOnlyWhitespace() {
		Resource whitespaceResource = new ByteArrayResource("   \n\t\r\n   ".getBytes(StandardCharsets.UTF_8));
		TextReader textReader = new TextReader(whitespaceResource);

		List<Document> documents = textReader.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEqualTo("   \n\t\r\n   ");
	}

	@Test
	void loadTextWithMultipleNewlines() {
		String content = "Line 1\n\n\nLine 4\r\nLine 5\r\n\r\nLine 7";
		Resource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
		TextReader textReader = new TextReader(resource);

		List<Document> documents = textReader.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEqualTo(content);
	}

	@Test
	void customMetadataIsPreserved() {
		Resource resource = new ByteArrayResource("Test".getBytes(StandardCharsets.UTF_8));
		TextReader textReader = new TextReader(resource);

		// Add multiple custom metadata entries
		textReader.getCustomMetadata().put("author", "Author");
		textReader.getCustomMetadata().put("version", "1.0");
		textReader.getCustomMetadata().put("category", "test");

		List<Document> documents = textReader.get();

		assertThat(documents).hasSize(1);
		Document document = documents.get(0);
		assertThat(document.getMetadata()).containsEntry("author", "Author");
		assertThat(document.getMetadata()).containsEntry("version", "1.0");
		assertThat(document.getMetadata()).containsEntry("category", "test");
	}

	@Test
	void resourceDescriptionHandling(@TempDir File tempDir) throws IOException {
		// Test with file resource
		File testFile = new File(tempDir, "test-file.txt");
		try (FileWriter writer = new FileWriter(testFile, StandardCharsets.UTF_8)) {
			writer.write("File content");
		}

		TextReader fileReader = new TextReader(new FileSystemResource(testFile));
		List<Document> documents = fileReader.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getMetadata().get(TextReader.SOURCE_METADATA)).isEqualTo("test-file.txt");
	}

	@Test
	void multipleCallsToGetReturnSameResult() {
		Resource resource = new ByteArrayResource("Consistent content".getBytes(StandardCharsets.UTF_8));
		TextReader textReader = new TextReader(resource);
		textReader.getCustomMetadata().put("test", "value");

		List<Document> firstCall = textReader.get();
		List<Document> secondCall = textReader.get();

		assertThat(firstCall).hasSize(1);
		assertThat(secondCall).hasSize(1);
		assertThat(firstCall.get(0).getText()).isEqualTo(secondCall.get(0).getText());
		assertThat(firstCall.get(0).getMetadata()).isEqualTo(secondCall.get(0).getMetadata());
	}

	@Test
	void resourceWithoutExtension(@TempDir File tempDir) throws IOException {
		// Test file without extension
		File noExtFile = new File(tempDir, "no-extension-file");
		try (FileWriter writer = new FileWriter(noExtFile, StandardCharsets.UTF_8)) {
			writer.write("Content without extension");
		}

		TextReader textReader = new TextReader(new FileSystemResource(noExtFile));
		List<Document> documents = textReader.get();

		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEqualTo("Content without extension");
		assertThat(documents.get(0).getMetadata().get(TextReader.SOURCE_METADATA)).isEqualTo("no-extension-file");
	}

}
