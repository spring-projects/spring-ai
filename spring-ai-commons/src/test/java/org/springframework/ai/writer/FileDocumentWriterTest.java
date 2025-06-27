/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jemin Huh
 */
public class FileDocumentWriterTest {

	@TempDir
	Path tempDir;

	private String testFileName;

	private List<Document> testDocuments;

	@BeforeEach
	void setUp() {
		this.testFileName = this.tempDir.resolve("file-document-test-output.txt").toString();
		this.testDocuments = List.of(
				Document.builder()
					.text("Document one introduces the core functionality of Spring AI.")
					.metadata("page_number", "1")
					.metadata("end_page_number", "2")
					.metadata("source", "intro.pdf")
					.metadata("title", "Spring AI Overview")
					.metadata("author", "QA Team")
					.build(),
				Document.builder()
					.text("Document two illustrates multi-line handling and line breaks.\nEnsure preservation of formatting.")
					.metadata("page_number", "3")
					.metadata("end_page_number", "4")
					.metadata("source", "formatting.pdf")
					.build(),
				Document.builder()
					.text("Document three checks metadata inclusion and output formatting behavior.")
					.metadata("page_number", "5")
					.metadata("end_page_number", "6")
					.metadata("version", "v1.2")
					.build());
	}

	@Test
	void testBasicWrite() throws IOException {
		var writer = new FileDocumentWriter(this.testFileName);
		writer.accept(this.testDocuments);

		List<String> lines = Files.readAllLines(Path.of(this.testFileName));
		assertEquals("", lines.get(0));
		assertEquals("", lines.get(1));
		assertEquals("Document one introduces the core functionality of Spring AI.", lines.get(2));
		assertEquals("", lines.get(3));
		assertEquals("Document two illustrates multi-line handling and line breaks.", lines.get(4));
		assertEquals("Ensure preservation of formatting.", lines.get(5));
		assertEquals("", lines.get(6));
		assertEquals("Document three checks metadata inclusion and output formatting behavior.", lines.get(7));
	}

	@Test
	void testWriteWithDocumentMarkers() throws IOException {
		var writer = new FileDocumentWriter(this.testFileName, true, MetadataMode.NONE, false);
		writer.accept(this.testDocuments);

		List<String> lines = Files.readAllLines(Path.of(this.testFileName));
		assertEquals("", lines.get(0));
		assertEquals("### Doc: 0, pages:[1,2]", lines.get(1));
		assertEquals("", lines.get(2));
		assertEquals("", lines.get(3));
		assertEquals("Document one introduces the core functionality of Spring AI.", lines.get(4));
		assertEquals("### Doc: 1, pages:[3,4]", lines.get(5));
		assertEquals("", lines.get(6));
		assertEquals("", lines.get(7));
		assertEquals("Document two illustrates multi-line handling and line breaks.", lines.get(8));
		assertEquals("Ensure preservation of formatting.", lines.get(9));
		assertEquals("### Doc: 2, pages:[5,6]", lines.get(10));
		assertEquals("", lines.get(11));
		assertEquals("", lines.get(12));
		assertEquals("Document three checks metadata inclusion and output formatting behavior.", lines.get(13));
	}

	@Test
	void testMetadataModeAllWithDocumentMarkers() throws IOException {
		var writer = new FileDocumentWriter(this.testFileName, true, MetadataMode.ALL, false);
		writer.accept(this.testDocuments);

		List<String> lines = Files.readAllLines(Path.of(this.testFileName));
		assertEquals("", lines.get(0));
		assertEquals("### Doc: 0, pages:[1,2]", lines.get(1));
		String subListToString = lines.subList(2, 7).toString();
		assertTrue(subListToString.contains("page_number: 1"));
		assertTrue(subListToString.contains("end_page_number: 2"));
		assertTrue(subListToString.contains("source: intro.pdf"));
		assertTrue(subListToString.contains("title: Spring AI Overview"));
		assertTrue(subListToString.contains("author: QA Team"));
		assertEquals("", lines.get(7));
		assertEquals("Document one introduces the core functionality of Spring AI.", lines.get(8));

		assertEquals("### Doc: 1, pages:[3,4]", lines.get(9));
		subListToString = lines.subList(10, 13).toString();
		assertTrue(subListToString.contains("page_number: 3"));
		assertTrue(subListToString.contains("source: formatting.pdf"));
		assertTrue(subListToString.contains("end_page_number: 4"));
		assertEquals("", lines.get(13));
		assertEquals("Document two illustrates multi-line handling and line breaks.", lines.get(14));
		assertEquals("Ensure preservation of formatting.", lines.get(15));

		assertEquals("### Doc: 2, pages:[5,6]", lines.get(16));
		subListToString = lines.subList(17, 20).toString();
		assertTrue(subListToString.contains("page_number: 5"));
		assertTrue(subListToString.contains("end_page_number: 6"));
		assertTrue(subListToString.contains("version: v1.2"));
		assertEquals("", lines.get(20));
		assertEquals("Document three checks metadata inclusion and output formatting behavior.", lines.get(21));
	}

	@Test
	void testAppendWrite() throws IOException {
		Files.writeString(Path.of(this.testFileName), "Test String\n");

		var writer = new FileDocumentWriter(this.testFileName, false, MetadataMode.NONE, true);
		writer.accept(this.testDocuments.subList(0, 2));

		List<String> lines = Files.readAllLines(Path.of(this.testFileName));
		assertEquals("Test String", lines.get(0));
		assertEquals("", lines.get(1));
		assertEquals("", lines.get(2));
		assertEquals("Document one introduces the core functionality of Spring AI.", lines.get(3));
		assertEquals("", lines.get(4));
		assertEquals("Document two illustrates multi-line handling and line breaks.", lines.get(5));
		assertEquals("Ensure preservation of formatting.", lines.get(6));
		assertEquals(7, lines.size());
	}

}
