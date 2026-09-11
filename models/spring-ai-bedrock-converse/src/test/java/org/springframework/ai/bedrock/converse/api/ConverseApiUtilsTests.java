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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConverseApiUtils#convertDocumentToObject(Document)}.
 */
class ConverseApiUtilsTests {

	private final JsonHelper jsonHelper = new JsonHelper();

	@Test
	void convertDocumentToObjectReturnsNullForNullOrNullDocument() {
		assertThat(ConverseApiUtils.convertDocumentToObject(null)).isNull();
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromNull())).isNull();
	}

	@Test
	void convertDocumentToObjectMapsScalarValues() {
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromString("hello"))).isEqualTo("hello");
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromBoolean(true))).isEqualTo(true);
		assertThat(ConverseApiUtils.convertDocumentToObject(Document.fromNumber(42))).isEqualTo(new BigDecimal(42));
	}

	@Test
	void convertDocumentToObjectMapsListsAndMapsRecursively() {
		Document document = Document
			.fromMap(Map.of("items", Document.fromList(List.of(Document.fromString("a"), Document.fromNumber(1)))));

		Object converted = ConverseApiUtils.convertDocumentToObject(document);

		assertThat(converted).isEqualTo(Map.of("items", List.of("a", new BigDecimal(1))));
	}

	@Test
	void documentRoundTripPreservesControlCharactersInStringValues() {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("text", "line1\nline2\twith tab");
		input.put("nested", Map.of("list", List.of("a\nb", "c")));

		Document document = ConverseApiUtils.convertObjectToDocument(input);
		Object converted = ConverseApiUtils.convertDocumentToObject(document);

		assertThat(converted).isEqualTo(input);

		// The serialized form must be valid JSON: control characters escaped, not raw.
		String json = this.jsonHelper.toJson(converted);
		assertThat(json).doesNotContain("\n").doesNotContain("\t");
		assertThat(this.jsonHelper.fromJsonToMap(json)).isEqualTo(input);
	}

}
