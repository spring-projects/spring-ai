/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.azure;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AzureVectorStore#parseMetadataToMutable(String)}.
 *
 * @author Jinwoo Lee
 */
class AzureVectorStoreMetadataTests {

	@Test
	void returnsMutableMapForBlankOrNull() {
		Map<String, Object> m1 = AzureVectorStore.parseMetadataToMutable(null);
		m1.put("distance", 0.1);
		assertThat(m1).containsEntry("distance", 0.1);

		Map<String, Object> m2 = AzureVectorStore.parseMetadataToMutable("");
		m2.put("distance", 0.2);
		assertThat(m2).containsEntry("distance", 0.2);

		Map<String, Object> m3 = AzureVectorStore.parseMetadataToMutable("   ");
		m3.put("distance", 0.3);
		assertThat(m3).containsEntry("distance", 0.3);
	}

	@Test
	void wrapsParsedJsonInLinkedHashMapSoItIsMutable() {
		Map<String, Object> map = AzureVectorStore.parseMetadataToMutable("{\"k\":\"v\"}");
		assertThat(map).containsEntry("k", "v");
		map.put("distance", 0.4);
		assertThat(map).containsEntry("distance", 0.4);
	}

}
