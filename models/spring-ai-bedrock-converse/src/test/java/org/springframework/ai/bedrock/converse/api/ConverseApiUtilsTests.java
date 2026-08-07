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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConverseApiUtils}.
 *
 * @author yqz
 */
class ConverseApiUtilsTests {

	@Test
	void convertMapPreservesKeyOrder() {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("evidenceText", "text");
		source.put("justification", "reason");
		source.put("valid", true);

		Document document = ConverseApiUtils.convertObjectToDocument(source);

		assertThat(document.asMap().keySet()).containsExactly("evidenceText", "justification", "valid");
	}

	@Test
	void convertNestedMapPreservesKeyOrder() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("zebra", "z");
		properties.put("apple", "a");
		properties.put("mango", "m");

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);

		Document document = ConverseApiUtils.convertObjectToDocument(schema);

		assertThat(document.asMap().keySet()).containsExactly("type", "properties");
		assertThat(document.asMap().get("properties").asMap().keySet()).containsExactly("zebra", "apple", "mango");
	}

}
