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
 * @author Mrxiao03
 */
class ConverseApiUtilsTests {

	@Test
	void convertObjectToDocumentPreservesMapIterationOrder() {
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("alpha", 1);
		nested.put("beta", 2);

		Map<String, Object> value = new LinkedHashMap<>();
		value.put("first", nested);
		value.put("second", "value");
		value.put("third", true);

		Document document = ConverseApiUtils.convertObjectToDocument(value);

		assertThat(document.asMap().keySet()).containsExactly("first", "second", "third");
		assertThat(document.asMap().get("first").asMap().keySet()).containsExactly("alpha", "beta");
	}

}
