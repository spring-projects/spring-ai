/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaTests {

	@Test
	void testMediaBuilderWithByteArrayResource() {
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] data = new byte[] { 1, 2, 3 };
		String id = "123";
		String name = "test-media";

		Media media = Media.builder().mimeType(mimeType).data(new ByteArrayResource(data)).id(id).name(name).build();

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(byte[].class);
		assertThat(media.getDataAsByteArray()).isEqualTo(data);
		assertThat(media.getId()).isEqualTo(id);
		assertThat(media.getName()).isEqualTo(name);
	}

	@Test
	void testMediaBuilderWithURL() throws MalformedURLException {
		MimeType mimeType = MimeType.valueOf("image/png");
		URL url = new URL("http://example.com/image.png");
		String id = "123";
		String name = "test-media";

		Media media = Media.builder().mimeType(mimeType).data(url).id(id).name(name).build();

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(String.class);
		assertThat(media.getData()).isEqualTo(url.toString());
		assertThat(media.getId()).isEqualTo(id);
		assertThat(media.getName()).isEqualTo(name);
	}

	@Test
	void testMediaBuilderWithNullMimeType() {
		assertThatThrownBy(() -> Media.builder().mimeType(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MimeType must not be null");
	}

	@Test
	void testMediaBuilderWithNullData() {
		assertThatThrownBy(() -> Media.builder().mimeType(MimeType.valueOf("image/png")).data((Object) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Data must not be null");
	}

	@Test
	void testGetDataAsByteArrayWithInvalidData() {
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data("invalid data")
			.id("123")
			.name("test-media")
			.build();

		assertThatThrownBy(media::getDataAsByteArray).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Media data is not a byte[]");
	}

	@Test
	void testMediaBuilderWithNullURL() {
		assertThatThrownBy(() -> Media.builder().mimeType(MimeType.valueOf("image/png")).data((URL) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URL must not be null");
	}

	@Test
	void testMediaBuilderWithNullResource() {
		assertThatThrownBy(() -> Media.builder().mimeType(MimeType.valueOf("image/png")).data((Resource) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Data must not be null");
	}

	@Test
	void testMediaBuilderWithOptionalId() {
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] data = new byte[] { 1, 2, 3 };

		Media media = Media.builder().mimeType(mimeType).data(data).name("test-media").build();

		assertThat(media.getId()).isNull();
		assertThat(media.getName()).isEqualTo("test-media");
	}

	@Test
	void testMediaBuilderWithDefaultName() {
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] data = new byte[] { 1, 2, 3 };

		Media media = Media.builder().mimeType(mimeType).data(data).build();

		assertValidMediaName(media.getName(), "png");
	}

	@Test
	void testMediaBuilderWithFailingResource() {
		Resource failingResource = new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			// Implement other methods...
			@Override
			public byte[] getContentAsByteArray() throws IOException {
				throw new IOException("Simulated failure");
			}
		};

		assertThatThrownBy(() -> Media.builder().mimeType(MimeType.valueOf("image/png")).data(failingResource).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void testMediaBuilderWithDifferentMimeTypes() {
		byte[] data = new byte[] { 1, 2, 3 };

		Media jpegMedia = Media.builder().mimeType(Media.Format.IMAGE_JPEG).data(data).build();
		assertValidMediaName(jpegMedia.getName(), "jpeg");

		Media pdfMedia = Media.builder().mimeType(Media.Format.DOC_PDF).data(data).build();
		assertValidMediaName(pdfMedia.getName(), "pdf");
	}

	@Test
	void testLastDataMethodWins() throws MalformedURLException {
		URL url = new URL("http://example.com/image.png");
		byte[] bytes = new byte[] { 1, 2, 3 };

		Media media = Media.builder().mimeType(Media.Format.IMAGE_PNG).data(url).data(bytes).build();

		assertThat(media.getData()).isSameAs(bytes);
	}

	@Test
	void testMediaConstructorWithUrl() throws MalformedURLException {
		MimeType mimeType = MimeType.valueOf("image/png");
		URL url = new URL("http://example.com/image.png");

		Media media = new Media(mimeType, url);

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(String.class);
		assertThat(media.getData()).isEqualTo(url.toString());
		assertThat(media.getId()).isNull();
		String name = media.getName();
		assertValidMediaName(media.getName(), "png");
	}

	private void assertValidMediaName(String name, String expectedMimeSubtype) {
		// Split name into parts (media-subtype-uuid)
		String[] parts = name.split("-", 3);

		// Verify we have all three parts
		assertThat(parts).hasSize(3);

		// Verify the prefix is "media"
		assertThat(parts[0]).isEqualTo("media");

		// Verify the subtype matches expected
		assertThat(parts[1]).isEqualTo(expectedMimeSubtype);

		// Validate the UUID portion
		assertThat(UUID.fromString(parts[2])).isNotNull();
	}

	@Test
	void testMediaConstructorWithResource() throws IOException {
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] data = new byte[] { 1, 2, 3 };
		Resource resource = new ByteArrayResource(data);

		Media media = new Media(mimeType, resource);

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(byte[].class);
		assertThat(media.getDataAsByteArray()).isEqualTo(data);
		assertThat(media.getId()).isNull();
		assertValidMediaName(media.getName(), "png");
	}

	@Test
	void testMediaConstructorWithResourceAndId() throws IOException {
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] data = new byte[] { 1, 2, 3 };
		Resource resource = new ByteArrayResource(data);
		String id = "123";

		Media media = new Media(mimeType, resource, id);

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(byte[].class);
		assertThat(media.getDataAsByteArray()).isEqualTo(data);
		assertThat(media.getId()).isEqualTo(id);
		assertValidMediaName(media.getName(), "png");
	}

	@Test
	void testMediaConstructorWithFailingResource() {
		Resource failingResource = new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public byte[] getContentAsByteArray() throws IOException {
				throw new IOException("Simulated failure");
			}
		};

		assertThatThrownBy(() -> new Media(Media.Format.IMAGE_PNG, failingResource))
			.isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void testMediaConstructorWithFailingResourceAndId() {
		Resource failingResource = new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public byte[] getContentAsByteArray() throws IOException {
				throw new IOException("Simulated failure");
			}
		};

		assertThatThrownBy(() -> new Media(Media.Format.IMAGE_PNG, failingResource, "123"))
			.isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(IOException.class);
	}

	/// Tests to ensure two arg ctors behave identically to the builder

	@Test
	void testURLConstructorMatchesBuilder() throws MalformedURLException {
		// Given
		MimeType mimeType = MimeType.valueOf("image/png");
		URL url = new URL("http://example.com/image.png");

		// When
		Media mediaFromCtor = new Media(mimeType, url);
		Media mediaFromBuilder = Media.builder().mimeType(mimeType).data(url).build();

		// Then - verify all properties match
		assertThat(mediaFromCtor.getMimeType()).isEqualTo(mediaFromBuilder.getMimeType());
		assertThat(mediaFromCtor.getData()).isEqualTo(mediaFromBuilder.getData());
		assertThat(mediaFromCtor.getId()).isEqualTo(mediaFromBuilder.getId());

		// Verify name structure for both instances
		assertValidMediaName(mediaFromCtor.getName(), "png");
		assertValidMediaName(mediaFromBuilder.getName(), "png");

		// Data type consistency
		assertThat(mediaFromCtor.getData()).isInstanceOf(String.class);
		assertThat(mediaFromBuilder.getData()).isInstanceOf(String.class);
	}

	@Test
	void testResourceConstructorMatchesBuilder() throws IOException {
		// Given
		MimeType mimeType = MimeType.valueOf("image/png");
		byte[] content = new byte[] { 1, 2, 3, 4, 5 };
		Resource resource = new ByteArrayResource(content);

		// When
		Media mediaFromCtor = new Media(mimeType, resource);
		Media mediaFromBuilder = Media.builder().mimeType(mimeType).data(resource).build();

		// Then - verify all properties match
		assertThat(mediaFromCtor.getMimeType()).isEqualTo(mediaFromBuilder.getMimeType());
		assertThat(mediaFromCtor.getDataAsByteArray()).isEqualTo(mediaFromBuilder.getDataAsByteArray());
		assertThat(mediaFromCtor.getId()).isEqualTo(mediaFromBuilder.getId());

		// Verify name structure for both instances
		assertValidMediaName(mediaFromCtor.getName(), "png");
		assertValidMediaName(mediaFromBuilder.getName(), "png");

		// Data type consistency
		assertThat(mediaFromCtor.getData()).isInstanceOf(byte[].class);
		assertThat(mediaFromBuilder.getData()).isInstanceOf(byte[].class);
	}

	@Test
	void testURLConstructorNullValidation() {
		MimeType mimeType = MimeType.valueOf("image/png");

		// Test null mimeType
		assertThatThrownBy(() -> new Media(null, new URL("http://example.com/image.png")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MimeType must not be null");

		// Test null URL
		assertThatThrownBy(() -> new Media(mimeType, (URL) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("URL must not be null");

		// Compare with builder validation
		assertThatThrownBy(() -> Media.builder().mimeType(null).data(new URL("http://example.com/image.png")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MimeType must not be null");

		assertThatThrownBy(() -> Media.builder().mimeType(mimeType).data((URL) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("URL must not be null");
	}

	@Test
	void testResourceConstructorNullValidation() {
		MimeType mimeType = MimeType.valueOf("image/png");

		// Test null mimeType
		assertThatThrownBy(() -> new Media(null, new ByteArrayResource(new byte[] { 1, 2, 3 })))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MimeType must not be null");

		// Test null resource
		assertThatThrownBy(() -> new Media(mimeType, (Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Data must not be null");

		// Compare with builder validation
		assertThatThrownBy(
				() -> Media.builder().mimeType(null).data(new ByteArrayResource(new byte[] { 1, 2, 3 })).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MimeType must not be null");

		assertThatThrownBy(() -> Media.builder().mimeType(mimeType).data((Resource) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Data must not be null");
	}

	@Test
	void testResourceIOExceptionHandling() {
		MimeType mimeType = MimeType.valueOf("image/png");
		Resource failingResource = new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public byte[] getContentAsByteArray() throws IOException {
				throw new IOException("Simulated failure");
			}
		};

		// Test constructor exception handling
		assertThatThrownBy(() -> new Media(mimeType, failingResource)).isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(IOException.class)
			.hasMessageContaining("Simulated failure");

		// Compare with builder exception handling
		assertThatThrownBy(() -> Media.builder().mimeType(mimeType).data(failingResource).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasCauseInstanceOf(IOException.class)
			.hasMessageContaining("Simulated failure");
	}

	@Test
	void testDifferentMimeTypesNameFormat() throws IOException {
		// Test constructor name generation
		Media jpegMediaCtor = new Media(Media.Format.IMAGE_JPEG, new ByteArrayResource(new byte[] { 1, 2, 3 }));
		assertValidMediaName(jpegMediaCtor.getName(), "jpeg");

		Media pngMediaCtor = new Media(Media.Format.IMAGE_PNG, new ByteArrayResource(new byte[] { 1, 2, 3 }));
		assertValidMediaName(pngMediaCtor.getName(), "png");

		// Compare with builder name generation
		Media jpegMediaBuilder = Media.builder()
			.mimeType(Media.Format.IMAGE_JPEG)
			.data(new ByteArrayResource(new byte[] { 1, 2, 3 }))
			.build();
		assertValidMediaName(jpegMediaBuilder.getName(), "jpeg");

		Media pngMediaBuilder = Media.builder()
			.mimeType(Media.Format.IMAGE_PNG)
			.data(new ByteArrayResource(new byte[] { 1, 2, 3 }))
			.build();
		assertValidMediaName(pngMediaBuilder.getName(), "png");
	}

}
