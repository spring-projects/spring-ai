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

package org.springframework.ai.reader.tika;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Christian Tzolov
 * @author Shahbaz Aamir
 * @author ikaitist
 * @author Dimitar Proynov
 */
public class TikaDocumentReaderTests {

	@ParameterizedTest
	@CsvSource({
			"classpath:/word-sample.docx,word-sample.docx,Two kinds of links are possible, those that refer to an external website",
			"classpath:/word-sample.doc,word-sample.doc,The limited permissions granted above are perpetual and will not be revoked by OASIS",
			"classpath:/sample2.pdf,sample2.pdf,Consult doc/pdftex/manual.pdf from your tetex distribution for more",
			"classpath:/sample.ppt,sample.ppt,Sed ipsum tortor, fringilla a consectetur eget, cursus posuere sem.",
			"classpath:/sample.pptx,sample.pptx,Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
			"https://github.com/spring-projects/spring-ai/,https://github.com/spring-projects/spring-ai/,An Application Framework for AI Engineering" })
	public void testDocx(String resourceUri, String resourceName, String contentSnipped) {

		var docs = new TikaDocumentReader(resourceUri).get();
		assertThat(docs).hasSize(1);

		var doc = docs.get(0);

		assertThat(doc.getMetadata()).containsKeys(TikaDocumentReader.METADATA_SOURCE);
		assertThat(doc.getMetadata().get(TikaDocumentReader.METADATA_SOURCE)).isEqualTo(resourceName);
		assertThat(doc.getText()).contains(contentSnipped);
	}

	@ParameterizedTest
	@CsvSource({
			"classpath:/word-sample.docx,word-sample.docx,This document demonstrates the ability of the calibre DOCX Input plugin",
			"classpath:/sample2.pdf,sample2.pdf,Robert Maron", "classpath:/sample.ppt,sample.ppt,Sample FILE",
			"classpath:/sample.pptx,sample.pptx,Sample FILE" })
	public void testReaderWithFormatter(String resourceUri, String resourceName, String contentSnipped) {

		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(5)
			.build();
		var docs = new TikaDocumentReader(resourceUri, formatter).get();

		assertThat(docs).hasSize(1);

		var doc = docs.get(0);

		assertThat(doc.getMetadata()).containsKeys(TikaDocumentReader.METADATA_SOURCE);
		assertThat(doc.getMetadata().get(TikaDocumentReader.METADATA_SOURCE)).isEqualTo(resourceName);
		assertFalse(doc.getText().contains(contentSnipped));
		docs = new TikaDocumentReader(resourceUri).get();
		doc = docs.get(0);
		assertThat(doc.getText()).contains(contentSnipped);
	}

	@Test
	void testGetIsReentrant() {
		var reader = new TikaDocumentReader("classpath:/word-sample.docx");

		var first = reader.get();
		var second = reader.get();

		assertThat(first).hasSize(1);
		assertThat(second).hasSize(1);
		assertThat(second.get(0).getText()).isEqualTo(first.get(0).getText());
		assertThat(second.get(0).getMetadata()).isEqualTo(first.get(0).getMetadata());
	}

	@Test
	public void testFailsWhenTextExceedsCharacterLimit(@TempDir Path tempDir) throws IOException {

		Resource resource = textResource(tempDir, 5000);
		var reader = new TikaDocumentReader(resource, 1000, ExtractedTextFormatter.defaults());

		Throwable thrown = catchThrowable(reader::get);

		assertThat(thrown).isInstanceOf(IllegalStateException.class)
			.hasMessage("Text extracted from 'large.txt' exceeds the maximum of 1000 characters");
		assertThat(WriteLimitReachedException.isWriteLimitReached(thrown)).isTrue();
	}

	@Test
	public void testFailsWhenTextExceedsTheWriteLimitOfACustomContentHandler(@TempDir Path tempDir) throws IOException {

		var reader = new TikaDocumentReader(textResource(tempDir, 5000), new BodyContentHandler(1000),
				ExtractedTextFormatter.defaults());

		assertThat(catchThrowable(reader::get)).isInstanceOf(IllegalStateException.class)
			.hasMessage("Text extracted from 'large.txt' exceeds the content handler write limit");
	}

	@Test
	public void testCharacterLimitIsEnforcedAtTheExactCharacterCount(@TempDir Path tempDir) throws IOException {

		Resource resource = textResource(tempDir, 5000);
		int extractedCharacters = extractedText(resource, -1).length();

		assertThat(extractedText(resource, extractedCharacters)).hasSize(extractedCharacters);
		assertThat(catchThrowable(() -> extractedText(resource, extractedCharacters - 1)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void testCharacterLimitCanBeDisabled(@TempDir Path tempDir) throws IOException {

		assertThat(countLetterAs(extractedText(textResource(tempDir, 5000), -1))).isEqualTo(5000);
	}

	@Test
	public void testDefaultCharacterLimitIsAppliedByDefault(@TempDir Path tempDir) throws IOException {

		var reader = new TikaDocumentReader(textResource(tempDir, 5000), ExtractedTextFormatter.defaults());

		assertThat(TikaDocumentReader.DEFAULT_MAX_CHARACTERS).isEqualTo(10_000_000);
		assertThat(countLetterAs(reader.get().get(0).getText())).isEqualTo(5000);
	}

	@Test
	public void testReaderCanBeReusedWithoutAccumulatingText(@TempDir Path tempDir) throws IOException {

		var reader = new TikaDocumentReader(textResource(tempDir, 5000), ExtractedTextFormatter.defaults());

		assertThat(countLetterAs(reader.get().get(0).getText())).isEqualTo(5000);
		assertThat(countLetterAs(reader.get().get(0).getText())).isEqualTo(5000);
	}

	@ParameterizedTest
	@ValueSource(ints = { -2, Integer.MIN_VALUE })
	public void testRejectsACharacterLimitBelowMinusOne(int maxCharacters) {

		assertThat(catchThrowable(() -> new TikaDocumentReader(new ClassPathResource("/word-sample.docx"),
				maxCharacters, ExtractedTextFormatter.defaults())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxCharacters must be -1 or greater");
	}

	@Test
	public void testACharacterLimitOfZeroRejectsAnyText(@TempDir Path tempDir) throws IOException {

		assertThat(catchThrowable(() -> extractedText(textResource(tempDir, 1), 0)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Text extracted from 'large.txt' exceeds the maximum of 0 characters");
	}

	private static Resource textResource(Path tempDir, int characters) throws IOException {
		Path file = tempDir.resolve("large.txt");
		Files.writeString(file, "A".repeat(characters));
		return new FileSystemResource(file);
	}

	private static String extractedText(Resource resource, int maxCharacters) {
		return new TikaDocumentReader(resource, maxCharacters, ExtractedTextFormatter.defaults()).get()
			.get(0)
			.getText();
	}

	private static long countLetterAs(String text) {
		return text.chars().filter(character -> character == 'A').count();
	}

}
