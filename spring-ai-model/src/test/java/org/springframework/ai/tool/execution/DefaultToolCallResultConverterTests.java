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
		ToolCallResult result = this.converter.convert(null, null);
		assertThat(result.content()).isEqualTo("null");
	}

	@Test
	void convertVoidReturnTypeShouldReturnDoneJson() {
		ToolCallResult result = this.converter.convert(null, void.class);
		assertThat(result.content()).isEqualTo("\"Done\"");
	}

	@Test
	void convertStringReturnTypeShouldReturnJson() {
		ToolCallResult result = this.converter.convert("test", String.class);
		assertThat(result.content()).isEqualTo("\"test\"");
	}

	@Test
	void convertNullReturnValueShouldReturnNullJson() {
		ToolCallResult result = this.converter.convert(null, String.class);
		assertThat(result.content()).isEqualTo("null");
	}

	@Test
	void convertObjectReturnTypeShouldReturnJson() {
		TestObject testObject = new TestObject("test", 42);
		ToolCallResult result = this.converter.convert(testObject, TestObject.class);
		assertThat(result.content()).containsIgnoringWhitespaces("""
				"name": "test"
				""").containsIgnoringWhitespaces("""
				"value": 42
				""");
	}

	@Test
	void convertCollectionReturnTypeShouldReturnJson() {
		List<String> testList = List.of("one", "two", "three");
		ToolCallResult result = this.converter.convert(testList, List.class);
		assertThat(result.content()).isEqualTo("""
				["one","two","three"]
				""".trim());
	}

	@Test
	void convertMapReturnTypeShouldReturnJson() {
		Map<String, Integer> testMap = Map.of("one", 1, "two", 2);
		ToolCallResult result = this.converter.convert(testMap, Map.class);
		assertThat(result.content()).containsIgnoringWhitespaces("""
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
		ToolCallResult result = this.converter.convert(img, BufferedImage.class);

		var b64Struct = JsonParser.fromJson(result.content(), Base64Wrapper.class);
		assertThat(b64Struct.mimeType).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(b64Struct.data).isNotNull();

		var imgData = Base64.getDecoder().decode(b64Struct.data);
		assertThat(imgData.length).isNotZero();

		var imgRes = ImageIO.read(new ByteArrayInputStream(imgData));
		assertThat(imgRes.getWidth()).isEqualTo(64);
		assertThat(imgRes.getHeight()).isEqualTo(64);
		assertThat(imgRes.getRGB(0, 0)).isEqualTo(img.getRGB(0, 0));
	}

	@Test
	void convertEmptyCollectionsShouldReturnEmptyJson() {
		assertThat(this.converter.convert(List.of(), List.class).content()).isEqualTo("[]");
		assertThat(this.converter.convert(Map.of(), Map.class).content()).isEqualTo("{}");
		assertThat(this.converter.convert(new String[0], String[].class).content()).isEqualTo("[]");
	}

	@Test
	void convertRecordReturnTypeShouldReturnJson() {
		TestRecord record = new TestRecord("recordName", 1);
		ToolCallResult result = this.converter.convert(record, TestRecord.class);

		assertThat(result.content()).containsIgnoringWhitespaces("\"recordName\"");
		assertThat(result.content()).containsIgnoringWhitespaces("1");
	}

	@Test
	void convertSpecialCharactersInStringsShouldEscapeJson() {
		String specialChars = "Test with \"quotes\", newlines\n, tabs\t, and backslashes\\";
		ToolCallResult result = this.converter.convert(specialChars, String.class);

		// Should properly escape JSON special characters
		assertThat(result.content()).contains("\\\"quotes\\\"");
		assertThat(result.content()).contains("\\n");
		assertThat(result.content()).contains("\\t");
		assertThat(result.content()).contains("\\\\");
	}

	record TestRecord(String name, int value) {
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
