package org.springframework.ai.model;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
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

		Media media = new Media.Builder().mimeType(mimeType)
			.data(new ByteArrayResource(data))
			.id(id)
			.name(name)
			.build();

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

		Media media = new Media.Builder().mimeType(mimeType).data(url).id(id).name(name).build();

		assertThat(media.getMimeType()).isEqualTo(mimeType);
		assertThat(media.getData()).isInstanceOf(String.class);
		assertThat(media.getData()).isEqualTo(url.toString());
		assertThat(media.getId()).isEqualTo(id);
		assertThat(media.getName()).isEqualTo(name);
	}

	@Test
	void testMediaBuilderWithNullMimeType() {
		assertThatThrownBy(() -> new Media.Builder().mimeType(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MimeType must not be null");
	}

	@Test
	void testMediaBuilderWithNullId() {
		assertThatThrownBy(() -> new Media.Builder().mimeType(MimeType.valueOf("image/png")).id(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Id must not be null");
	}

	@Test
	void testMediaBuilderWithNullData() {
		assertThatThrownBy(
				() -> new Media.Builder().mimeType(MimeType.valueOf("image/png")).data((Object) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Data must not be null");
	}

	@Test
	void testGetDataAsByteArrayWithInvalidData() {
		Media media = new Media.Builder().mimeType(MimeType.valueOf("image/png"))
			.data("invalid data")
			.id("123")
			.build();

		assertThatThrownBy(media::getDataAsByteArray).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Media data is not a byte[]");
	}

}
