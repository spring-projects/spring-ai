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
import static org.springframework.ai.vertexai.gemini.MimeTypeDetector.GEMINI_MIME_TYPES;

/**
 * @author YunKui Lu
 */
class MimeTypeDetectorTests {

	private static Stream<Arguments> provideMimeTypes() {
		return GEMINI_MIME_TYPES.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
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
