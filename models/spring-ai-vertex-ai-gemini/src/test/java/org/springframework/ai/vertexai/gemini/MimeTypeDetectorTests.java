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

package org.springframework.ai.vertexai.gemini;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.io.PathResource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author YunKui Lu
 */
class MimeTypeDetectorTests {

	private static Stream<Arguments> provideMimeTypes() {
		return org.springframework.ai.vertexai.gemini.MimeTypeDetector.GEMINI_MIME_TYPES.entrySet()
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

}
