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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConverseApiUtils}.
 *
 * @author Seyed Hosseini
 */
class ConverseApiUtilsTests {

	@Test
	void convertsScalarValues() {
		assertThat(ConverseApiUtils.convertObjectToDocument("low")).isEqualTo(Document.fromString("low"));
		assertThat(ConverseApiUtils.convertObjectToDocument(true)).isEqualTo(Document.fromBoolean(true));
		assertThat(ConverseApiUtils.convertObjectToDocument(null)).isEqualTo(Document.fromNull());
	}

	@Test
	void convertsNumericValues() {
		assertThat(ConverseApiUtils.convertObjectToDocument(42)).isEqualTo(Document.fromNumber(42));
		assertThat(ConverseApiUtils.convertObjectToDocument(42L)).isEqualTo(Document.fromNumber(42L));
		assertThat(ConverseApiUtils.convertObjectToDocument(4.2f)).isEqualTo(Document.fromNumber(4.2f));
		assertThat(ConverseApiUtils.convertObjectToDocument(4.2)).isEqualTo(Document.fromNumber(4.2));
		assertThat(ConverseApiUtils.convertObjectToDocument(BigDecimal.valueOf(4.2)))
			.isEqualTo(Document.fromNumber(BigDecimal.valueOf(4.2)));
		assertThat(ConverseApiUtils.convertObjectToDocument(BigInteger.valueOf(42)))
			.isEqualTo(Document.fromNumber(BigInteger.valueOf(42)));
	}

	@Test
	void convertsNestedMapValue() {
		Document document = ConverseApiUtils.convertObjectToDocument(Map.of("output_config", Map.of("effort", "low")));

		assertThat(document.isMap()).isTrue();
		Document outputConfig = document.asMap().get("output_config");
		assertThat(outputConfig.isMap()).isTrue();
		assertThat(outputConfig.asMap().get("effort")).isEqualTo(Document.fromString("low"));
	}

	@Test
	void convertsEmptyNestedMapValue() {
		Document document = ConverseApiUtils.convertObjectToDocument(Map.of("output_config", Map.of()));

		Document outputConfig = document.asMap().get("output_config");
		assertThat(outputConfig.isMap()).isTrue();
		assertThat(outputConfig.asMap()).isEmpty();
	}

	@Test
	void convertsNullValueInsideNestedMap() {
		Map<String, Object> withNullValue = new HashMap<>();
		withNullValue.put("effort", null);

		Document document = ConverseApiUtils.convertObjectToDocument(Map.of("output_config", withNullValue));

		assertThat(document.asMap().get("output_config").asMap().get("effort")).isEqualTo(Document.fromNull());
	}

	@Test
	void convertsListOfMultipleNestedMapsValue() {
		Document document = ConverseApiUtils
			.convertObjectToDocument(List.of(Map.of("effort", "low"), Map.of("effort", "high")));

		assertThat(document.isList()).isTrue();
		assertThat(document.asList()).hasSize(2);
		assertThat(document.asList().get(0).asMap().get("effort")).isEqualTo(Document.fromString("low"));
		assertThat(document.asList().get(1).asMap().get("effort")).isEqualTo(Document.fromString("high"));
	}

	@Test
	void convertsThreeLevelsDeepMapListMapStructure() {
		Document document = ConverseApiUtils
			.convertObjectToDocument(Map.of("level1", Map.of("level2", List.of(Map.of("level3", "value")))));

		Document level2List = document.asMap().get("level1").asMap().get("level2");
		assertThat(level2List.isList()).isTrue();
		assertThat(level2List.asList().get(0).asMap().get("level3")).isEqualTo(Document.fromString("value"));
	}

}
