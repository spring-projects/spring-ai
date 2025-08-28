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

package org.springframework.ai.google.genai;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.io.PathResource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author YunKui Lu
 */
class MimeTypeDetectorTests {

	private static Stream<Arguments> provideMimeTypes() {
		return org.springframework.ai.google.genai.MimeTypeDetector.GEMINI_MIME_TYPES.entrySet()
			.stream()
			.map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByURLPath(String extension, MimeType expectedMimeType) throws MalformedURLException {
		String path = "https://testhost/test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(URI.create(path).toURL());
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByURI(String extension, MimeType expectedMimeType) {
		String path = "https://testhost/test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(URI.create(path));
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByFile(String extension, MimeType expectedMimeType) {
		String path = "test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(new File(path));
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByPath(String extension, MimeType expectedMimeType) {
		String path = "test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(Path.of(path));
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByResource(String extension, MimeType expectedMimeType) {
		String path = "test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(new PathResource(path));
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@MethodSource("provideMimeTypes")
	void getMimeTypeByString(String extension, MimeType expectedMimeType) {
		String path = "test." + extension;
		MimeType mimeType = MimeTypeDetector.getMimeType(path);
		assertThat(mimeType).isEqualTo(expectedMimeType);
	}

	@ParameterizedTest
	@ValueSource(strings = { " ", "\t", "\n" })
	void getMimeTypeByStringWithInvalidInputShouldThrowException(String invalidPath) {
		assertThatThrownBy(() -> MimeTypeDetector.getMimeType(invalidPath)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unable to detect the MIME type");
	}

	@ParameterizedTest
	@ValueSource(strings = { "JPG", "PNG", "GIF" })
	void getMimeTypeByStringWithUppercaseExtensionsShouldWork(String uppercaseExt) {
		String upperFileName = "test." + uppercaseExt;
		String lowerFileName = "test." + uppercaseExt.toLowerCase();

		// Should throw for uppercase (not in map) but work for lowercase
		assertThatThrownBy(() -> MimeTypeDetector.getMimeType(upperFileName))
			.isInstanceOf(IllegalArgumentException.class);

		// Lowercase should work if it's a supported extension
		if (org.springframework.ai.google.genai.MimeTypeDetector.GEMINI_MIME_TYPES
			.containsKey(uppercaseExt.toLowerCase())) {
			assertThatCode(() -> MimeTypeDetector.getMimeType(lowerFileName)).doesNotThrowAnyException();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "test.jpg", "test.png", "test.gif" })
	void getMimeTypeSupportedFileAcrossDifferentMethodsShouldBeConsistent(String fileName) {
		MimeType stringResult = MimeTypeDetector.getMimeType(fileName);
		MimeType fileResult = MimeTypeDetector.getMimeType(new File(fileName));
		MimeType pathResult = MimeTypeDetector.getMimeType(Path.of(fileName));

		// All methods should return the same result for supported extensions
		assertThat(stringResult).isEqualTo(fileResult);
		assertThat(stringResult).isEqualTo(pathResult);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://example.com/documents/file.pdf", "https://example.com/data/file.json",
			"https://example.com/files/document.txt" })
	void getMimeTypeByURIWithUnsupportedExtensionsShouldThrowException(String url) {
		URI uri = URI.create(url);

		assertThatThrownBy(() -> MimeTypeDetector.getMimeType(uri)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unable to detect the MIME type");
	}

}
