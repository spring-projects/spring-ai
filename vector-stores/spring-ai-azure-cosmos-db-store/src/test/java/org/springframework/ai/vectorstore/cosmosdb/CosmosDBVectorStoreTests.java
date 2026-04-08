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

package org.springframework.ai.vectorstore.cosmosdb;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for similarity-search document mapping in the Cosmos DB vector store.
 *
 * @author Yaklede
 * @since 2.0.0
 */
class CosmosDBVectorStoreTests {

	@Test
	void mapSimilaritySearchDocumentsShouldKeepMetadataScopedToEachDocument() {
		ObjectNode first = JsonMapper.shared().createObjectNode();
		first.put("id", "1");
		first.put("content", "First");
		first.set("metadata", metadataNode("country", "UK", "year", 2021, "active", true, "score", List.of(1, 2)));

		ObjectNode second = JsonMapper.shared().createObjectNode();
		second.put("id", "2");
		second.put("content", "Second");
		second.set("metadata",
				metadataNode("country", "NL", "year", 2022, "active", false, "score", Map.of("value", 7)));

		List<Document> documents = CosmosDBVectorStore.mapSimilaritySearchDocuments(List.of(first, second));

		assertThat(documents).hasSize(2);
		assertThat(documents.get(0).getMetadata()).containsExactlyInAnyOrderEntriesOf(
				Map.of("country", "UK", "year", 2021, "active", true, "score", "[1,2]"));
		assertThat(documents.get(1).getMetadata()).containsExactlyInAnyOrderEntriesOf(
				Map.of("country", "NL", "year", 2022, "active", false, "score", "{\"value\":7}"));
	}

	@Test
	void mapSimilaritySearchDocumentsShouldHandleMissingMetadataNode() {
		ObjectNode doc = JsonMapper.shared().createObjectNode();
		doc.put("id", "1");
		doc.put("content", "First");

		List<Document> documents = CosmosDBVectorStore.mapSimilaritySearchDocuments(List.of(doc));

		assertThat(documents).singleElement().extracting(Document::getMetadata).isEqualTo(Map.of());
	}

	private static ObjectNode metadataNode(String countryKey, String country, String yearKey, int year,
			String activeKey, boolean active, String scoreKey, Object score) {
		ObjectNode metadata = JsonMapper.shared().createObjectNode();
		metadata.put(countryKey, country);
		metadata.put(yearKey, year);
		metadata.put(activeKey, active);
		metadata.set(scoreKey, JsonMapper.shared().valueToTree(score));
		return metadata;
	}

}
