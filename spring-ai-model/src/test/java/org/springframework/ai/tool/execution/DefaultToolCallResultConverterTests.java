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

package org.springframework.ai.tool.execution;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolCallResultConverter}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallResultConverterTests {

	private final DefaultToolCallResultConverter converter = new DefaultToolCallResultConverter();

	@Test
	void convertWithNullReturnTypeShouldReturn() {
		String result = this.converter.convert(null, null);
		assertThat(result).isEqualTo("null");
	}

	@Test
	void convertVoidReturnTypeShouldReturnDoneJson() {
		String result = this.converter.convert(null, void.class);
		assertThat(result).isEqualTo("\"Done\"");
	}

	@Test
	void convertStringReturnTypeShouldReturnJson() {
		String result = this.converter.convert("test", String.class);
		assertThat(result).isEqualTo("\"test\"");
	}

	@Test
	void convertNullReturnValueShouldReturnNullJson() {
		String result = this.converter.convert(null, String.class);
		assertThat(result).isEqualTo("null");
	}

	@Test
	void convertObjectReturnTypeShouldReturnJson() {
		TestObject testObject = new TestObject("test", 42);
		String result = this.converter.convert(testObject, TestObject.class);
		assertThat(result).containsIgnoringWhitespaces("""
				"name": "test"
				""").containsIgnoringWhitespaces("""
				"value": 42
				""");
	}

	@Test
	void convertCollectionReturnTypeShouldReturnJson() {
		List<String> testList = List.of("one", "two", "three");
		String result = this.converter.convert(testList, List.class);
		assertThat(result).isEqualTo("""
				["one","two","three"]
				""".trim());
	}

	@Test
	void convertMapReturnTypeShouldReturnJson() {
		Map<String, Integer> testMap = Map.of("one", 1, "two", 2);
		String result = this.converter.convert(testMap, Map.class);
		assertThat(result).containsIgnoringWhitespaces("""
				"one": 1
				""").containsIgnoringWhitespaces("""
				"two": 2
				""");
	}

	@Test
	void convertImageShouldReturnBase64Image() throws IOException {
		// We don't want any AWT windows.
		System.setProperty("java.awt.headless", "true");

		var img = new BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR);
		var g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 64, 64);
		g.dispose();
		String result = this.converter.convert(img, BufferedImage.class);

		var b64Struct = JsonParser.fromJson(result, Base64Wrapper.class);
		assertThat(b64Struct.mimeType).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(b64Struct.data).isNotNull();

		var imgData = Base64.getDecoder().decode(b64Struct.data);
		assertThat(imgData.length).isNotZero();

		var imgRes = ImageIO.read(new ByteArrayInputStream(imgData));
		assertThat(imgRes.getWidth()).isEqualTo(64);
		assertThat(imgRes.getHeight()).isEqualTo(64);
		assertThat(imgRes.getRGB(0, 0)).isEqualTo(img.getRGB(0, 0));
	}

	record Base64Wrapper(MimeType mimeType, String data) {
	}

	static class TestObject {

		private final String name;

		private final int value;

		TestObject(String name, int value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return this.name;
		}

		public int getValue() {
			return this.value;
		}

	}

}
