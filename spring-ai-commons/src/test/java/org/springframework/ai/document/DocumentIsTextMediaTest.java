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

package org.springframework.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Issue #5600 - {@code Document.isText()} ignores MimeType of Media field.
 *
 * <p>
 * A {@link Document} that carries a {@link Media} with a text-based MIME type must be
 * considered textual so {@code AbstractObservationVectorStore} does not throw an
 * {@code IllegalArgumentException} when embedding such documents.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5600">Issue #5600</a>
 */
class DocumentIsTextMediaTest {

	// Helper method to create Document with Media

	private static Document documentWithMedia(MimeType mimeType) {
		Media media = Media.builder().mimeType(mimeType).data(URI.create("https://example.com/file")).build();
		return Document.builder().media(media).build();
	}

	// text/* MIME types must return true

	@Test
	@DisplayName("isText() returns true for Media with text/plain (Issue #5600)")
	void isText_textPlain() {
		assertThat(documentWithMedia(MimeTypeUtils.TEXT_PLAIN).isText()).isTrue();
	}

	@Test
	@DisplayName("isText() returns true for Media with text/xml (Issue #5600)")
	void isText_textXml() {
		assertThat(documentWithMedia(MimeType.valueOf("text/xml")).isText()).isTrue();
	}

	@Test
	@DisplayName("isText() returns true for Media with text/csv (Issue #5600)")
	void isText_textCsv() {
		assertThat(documentWithMedia(MimeType.valueOf("text/csv")).isText()).isTrue();
	}

	@Test
	@DisplayName("isText() returns true for Media with text/html (Issue #5600)")
	void isText_textHtml() {
		assertThat(documentWithMedia(MimeTypeUtils.TEXT_HTML).isText()).isTrue();
	}

	// non-text MIME types must return false

	@Test
	@DisplayName("isText() returns false for Media with image/png")
	void isText_imagePng() {
		assertThat(documentWithMedia(MimeTypeUtils.IMAGE_PNG).isText()).isFalse();
	}

	@Test
	@DisplayName("isText() returns false for Media with application/pdf")
	void isText_applicationPdf() {
		assertThat(documentWithMedia(MimeType.valueOf("application/pdf")).isText()).isFalse();
	}

	// regression: original String-based behaviour must be unchanged

	@Test
	@DisplayName("isText() still returns true for plain-text String documents (regression)")
	void isText_plainStringDocument() {
		Document doc = Document.builder().text("Hello world").build();
		assertThat(doc.isText()).isTrue();
	}

}
