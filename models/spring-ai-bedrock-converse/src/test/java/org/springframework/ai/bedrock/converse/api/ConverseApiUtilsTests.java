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

package org.springframework.ai.bedrock.converse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link ConverseApiUtils#convertDocumentToObject(Document)}.
 * <p>
 * The key guarantee is that a tool-use input {@link Document} is turned into a plain Java
 * object that serializes to valid JSON, so that arguments containing control characters
 * (such as newlines) survive the strict JSON parsing performed on tool-call arguments.
 * This is the inverse of {@link ConverseApiUtils#convertObjectToDocument(Object)}.
 */
class ConverseApiUtilsTests {

	private final JsonHelper jsonHelper = new JsonHelper();

	@Test
	void convertsScalarNodeTypes() {
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromNull())).isNull();
		assertThat(ConverseApiUtils.convertDocumentToObject(null)).isNull();
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromString("hello"))).isEqualTo("hello");
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromBoolean(true))).isEqualTo(true);
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromNumber(42))).isEqualTo(new BigDecimal("42"));
	}

	@Test
	void convertsNestedMapsAndLists() {
		Document document = Document.mapBuilder()
			.putString("channel", "VIBER_BM")
			.putList("tags", List.of(Document.fromString("a"), Document.fromString("b")))
			.putDocument("nested", Document.mapBuilder().putNumber("count", 2).build())
			.build();

		Object result = ConverseApiUtils.convertDocumentToObject(document);

		assertThat(result).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).containsEntry("channel", "VIBER_BM");
		assertThat(map.get("tags")).isEqualTo(List.of("a", "b"));
		assertThat(map.get("nested")).isEqualTo(Map.of("count", new BigDecimal("2")));
	}

	@Test
	void isInverseOfConvertObjectToDocument() {
		Map<String, Object> original = Map.of("channel", "SMS", "to", "385911234567", "retries", new BigDecimal("3"));

		Object roundTripped = ConverseApiUtils
			.convertDocumentToObject(ConverseApiUtils.convertObjectToDocument(original));

		assertThat(roundTripped).isEqualTo(original);
	}

	/**
	 * Regression: a tool argument whose string value contains a newline and emoji must
	 * serialize to valid JSON. Previously {@code Document.toString()} left the control
	 * character unescaped, which broke the strict JSON parsing of tool-call arguments.
	 */
	@Test
	void producesValidJsonForStringValuesWithControlCharacters() {
		String multilineText = "Line one\nLine two 🌊\tafter tab";
		Document input = Document.mapBuilder()
			.putString("channel", "VIBER_BM")
			.putString("to", "385911234567")
			.putString("text", multilineText)
			.build();

		String json = this.jsonHelper.toJson(ConverseApiUtils.convertDocumentToObject(input));

		// The raw control characters must not appear unescaped in the JSON string.
		assertThat(json).doesNotContain("\n").doesNotContain("\t").contains("\\n").contains("\\t");

		// And the JSON must parse back cleanly, preserving the original text.
		assertThatCode(() -> {
			Map<String, Object> parsed = this.jsonHelper.fromJsonToMap(json);
			assertThat(parsed).containsEntry("text", multilineText).containsEntry("channel", "VIBER_BM");
		}).doesNotThrowAnyException();
	}

}
