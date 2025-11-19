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

package org.springframework.ai.image;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageResponseTests {

	@Test
	void getResultAsBytesReturnsFirstDecodedImage() {
		byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
		String base64 = Base64.getEncoder().encodeToString(payload);
		Image image = new Image("https://example.test/image.png", base64);
		ImageResponse response = new ImageResponse(List.of(new ImageGeneration(image)));

		assertThat(response.getResultAsBytes()).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(payload));
	}

	@Test
	void getResultsAsBytesSkipsEntriesWithoutPayload() {
		byte[] payload = new byte[] { 1, 2, 3 };
		String base64 = Base64.getEncoder().encodeToString(payload);
		Image imageWithPayload = new Image(null, base64);
		Image imageWithoutPayload = new Image("https://example.test/image.png", null);
		ImageResponse response = new ImageResponse(
				List.of(new ImageGeneration(imageWithPayload), new ImageGeneration(imageWithoutPayload)));

		assertThat(response.getResultsAsBytes()).hasSize(1)
			.first()
			.satisfies(bytes -> assertThat(bytes).isEqualTo(payload));
	}

	@Test
	void imageProvidesOptionalStreamForBase64Payload() throws IOException {
		byte[] payload = { 42, 43, 44 };
		String base64 = Base64.getEncoder().encodeToString(payload);
		Image image = new Image(null, base64);

		assertThat(image.getB64JsonAsBytes()).contains(payload);
		assertThat(image.getB64JsonAsInputStream()).hasValueSatisfying(stream -> {
			try (stream) {
				assertThat(stream.readAllBytes()).isEqualTo(payload);
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});
	}

	@Test
	void helpersReturnEmptyWhenPayloadMissing() {
		Image image = new Image("https://example.test/image.png", null);
		ImageResponse response = new ImageResponse(List.of(new ImageGeneration(image)));

		assertThat(image.getB64JsonAsBytes()).isEmpty();
		assertThat(image.getB64JsonAsInputStream()).isEmpty();
		assertThat(response.getResultAsBytes()).isEmpty();
		assertThat(response.getResultsAsBytes()).isEmpty();
	}

}
