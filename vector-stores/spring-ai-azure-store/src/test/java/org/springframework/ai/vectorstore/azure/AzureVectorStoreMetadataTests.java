package org.springframework.ai.vectorstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

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
