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

package org.springframework.ai.vectorstore.typesense;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.typesense.api.Client;
import org.typesense.api.MultiSearch;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchResultItem;
import org.typesense.model.MultiSearchSearchesParameter;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TypesenseVectorStore}.
 *
 * @author Soby Chacko
 */
class TypesenseVectorStoreTests {

	private final Client client = mock(Client.class);

	private final MultiSearch multiSearch = mock(MultiSearch.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	TypesenseVectorStoreTests() {
		this.client.multiSearch = this.multiSearch;
	}

	@Test
	void aSearchAsksForAsManyHitsAsTopK() throws Exception {
		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 1f, 0f, 0f, 0f });
		MultiSearchResultItem noHits = new MultiSearchResultItem();
		noHits.hits(List.of());
		when(this.multiSearch.perform(any(MultiSearchSearchesParameter.class), anyMap()))
			.thenReturn(new MultiSearchResult().addResultsItem(noHits));

		store().similaritySearch(SearchRequest.builder().query("query").topK(25).build());

		ArgumentCaptor<MultiSearchSearchesParameter> searches = ArgumentCaptor
			.forClass(MultiSearchSearchesParameter.class);
		verify(this.multiSearch).perform(searches.capture(), anyMap());
		// Typesense returns 10 hits per page unless asked for more
		assertThat(searches.getValue().getSearches().get(0).getPerPage()).isEqualTo(25);
	}

	private TypesenseVectorStore store() {
		return TypesenseVectorStore.builder(this.client, this.embeddingModel).build();
	}

}
