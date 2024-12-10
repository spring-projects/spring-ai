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

package org.springframework.ai.rag.orchestration.routing;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AllRetrieversQueryRouter}.
 *
 * @author Thomas Vitale
 */
class AllRetrieversQueryRouterTests {

	@Test
	void whenDocumentRetrieversIsNullThenThrow() {
		assertThatThrownBy(
				() -> AllRetrieversQueryRouter.builder().documentRetrievers((List<DocumentRetriever>) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentRetrievers cannot be null or empty");
	}

	@Test
	void whenDocumentRetrieversIsEmptyThenThrow() {
		assertThatThrownBy(() -> AllRetrieversQueryRouter.builder().documentRetrievers(List.of()).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentRetrievers cannot be null or empty");
	}

	@Test
	void whenDocumentRetrieversContainsNullKeysThenThrow() {
		var documentRetrievers = new ArrayList<DocumentRetriever>();
		documentRetrievers.add(null);
		assertThatThrownBy(() -> AllRetrieversQueryRouter.builder().documentRetrievers(documentRetrievers).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentRetrievers cannot contain null elements");
	}

	@Test
	void whenQueryIsNullThenThrow() {
		DocumentRetriever documentRetriever = mock(DocumentRetriever.class);
		QueryRouter queryRouter = AllRetrieversQueryRouter.builder().documentRetrievers(documentRetriever).build();
		assertThatThrownBy(() -> queryRouter.route(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void routeToAllRetrievers() {
		DocumentRetriever documentRetriever1 = mock(DocumentRetriever.class);
		DocumentRetriever documentRetriever2 = mock(DocumentRetriever.class);
		QueryRouter queryRouter = AllRetrieversQueryRouter.builder()
			.documentRetrievers(documentRetriever1, documentRetriever2)
			.build();
		List<DocumentRetriever> selectedDocumentRetrievers = queryRouter.route(new Query("test"));
		assertThat(selectedDocumentRetrievers).containsAll(List.of(documentRetriever1, documentRetriever2));
	}

}
