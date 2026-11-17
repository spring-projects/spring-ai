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

package org.springframework.ai.tokenizer;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.knuddels.jtokkit.api.EncodingType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JTokkitTokenCountEstimator}.
 */
class JTokkitTokenCountEstimatorTests {

	private final JTokkitTokenCountEstimator estimator = new JTokkitTokenCountEstimator();

	@ParameterizedTest
	@NullAndEmptySource
	void emptyTextHasNoTokens(@Nullable String text) {
		assertThat(this.estimator.estimate(text)).isZero();
	}

	@Test
	void defaultEncodingIsCl100kBase() {
		assertThat(this.estimator.estimate("antidisestablishmentarianism")).isEqualTo(6);
	}

	@ParameterizedTest
	@CsvSource({ "CL100K_BASE, 6", "R50K_BASE, 5", "P50K_BASE, 5", "O200K_BASE, 6" })
	void usesConfiguredEncoding(EncodingType encodingType, int expectedTokens) {
		var configuredEstimator = new JTokkitTokenCountEstimator(encodingType);

		assertThat(configuredEstimator.estimate("antidisestablishmentarianism")).isEqualTo(expectedTokens);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void emptyContentHasNoTokens(@Nullable String text) {
		assertThat(this.estimator.estimate(new TestMediaContent(text, List.of()))).isZero();
	}

	@Test
	void textOnlyContentCountsText() {
		assertThat(this.estimator.estimate(new TestMediaContent("Hello, world!", List.of()))).isEqualTo(4);
	}

	@Test
	void mediaOnlyContentCountsMimeTypeAndUri() {
		String uri = "https://example.com/image.png";
		Media media = new Media(MimeTypeUtils.IMAGE_PNG, URI.create(uri));

		assertThat(this.estimator.estimate(new TestMediaContent(null, List.of(media))))
			.isEqualTo(this.estimator.estimate("image/png") + this.estimator.estimate(uri));
	}

	@Test
	void binaryMediaCountsBase64EncodedData() {
		byte[] bytes = { 0, 1, 2, 3, (byte) 0xff };
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(bytes).build();

		assertThat(this.estimator.estimate(new TestMediaContent(null, List.of(media))))
			.isEqualTo(this.estimator.estimate("image/png") + this.estimator.estimate("AAECA/8="));
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://example.com/image.png", "http://example.com/image%20name.png?size=large#preview",
			"file:/images/image.png" })
	void urlMediaCountsMimeTypeAndAddress(String address) throws MalformedURLException {
		URI uri = URI.create(address);
		var url = uri.toURL();
		Media urlMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(url).build();
		Media uriMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(uri).build();

		assertThat(this.estimator.estimate(new TestMediaContent(null, List.of(urlMedia))))
			.isEqualTo(this.estimator.estimate("image/png") + this.estimator.estimate(address))
			.isEqualTo(this.estimator.estimate(new TestMediaContent(null, List.of(uriMedia))));
		assertThat(urlMedia.getData()).isSameAs(url);
	}

	@Test
	void countsTextAndAllMedia() {
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/image.png").build();
		Media text = Media.builder().mimeType(MimeTypeUtils.TEXT_PLAIN).data(new byte[] { 0, 1, 2 }).build();
		var content = new TestMediaContent("Describe these attachments", List.of(image, text));

		assertThat(this.estimator.estimate(content)).isEqualTo(this.estimator.estimate("Describe these attachments")
				+ this.estimator.estimate("image/png") + this.estimator.estimate("https://example.com/image.png")
				+ this.estimator.estimate("text/plain") + this.estimator.estimate("AAEC"));
	}

	@Test
	void emptyIterableHasNoTokens() {
		assertThat(this.estimator.estimate(List.<MediaContent>of())).isZero();
	}

	@Test
	void iterableCountsEachContent() {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("AAEC").build();
		List<MediaContent> contents = List.of(new TestMediaContent("Hello, world!", List.of()),
				new TestMediaContent(null, List.of(media)), new TestMediaContent(null, List.of()));
		Iterable<MediaContent> iterable = contents::iterator;

		assertThat(this.estimator.estimate(iterable))
			.isEqualTo(4 + this.estimator.estimate("image/png") + this.estimator.estimate("AAEC"));
	}

	private record TestMediaContent(@Nullable String text, List<Media> media) implements MediaContent {

		@Override
		public @Nullable String getText() {
			return this.text;
		}

		@Override
		public List<Media> getMedia() {
			return this.media;
		}

		@Override
		public Map<String, Object> getMetadata() {
			return Map.of();
		}

	}

}
