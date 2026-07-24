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

package org.springframework.ai.vectorstore.pinecone;

import java.util.List;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link PineconeVectorStore}.
 *
 * @author Timur Rakhmatullin
 */
class PineconeVectorStoreTests {

	private static final String INDEX_NAME = "test-index";

	private EmbeddingModel embeddingModel;

	private Pinecone pinecone;

	private Index index;

	private PineconeVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		this.embeddingModel = mock(EmbeddingModel.class);
		this.pinecone = mock(Pinecone.class);
		this.index = mock(Index.class);

		this.vectorStore = PineconeVectorStore.builder(this.embeddingModel)
			.apiKey("test-api-key")
			.indexName(INDEX_NAME)
			.build();

		ReflectionTestUtils.setField(this.vectorStore, "pinecone", this.pinecone);
		given(this.pinecone.getIndexConnection(INDEX_NAME)).willReturn(this.index);
	}

	@Test
	void reusesIndexConnectionAcrossOperations() {
		given(this.embeddingModel.embed(anyList(), any(), any())).willReturn(List.of(new float[] { 0.1f, 0.2f }));
		given(this.embeddingModel.embed(anyString())).willReturn(new float[] { 0.1f, 0.2f });

		QueryResponseWithUnsignedIndices queryResponse = mock(QueryResponseWithUnsignedIndices.class);
		given(this.index.queryByVector(anyInt(), anyList(), anyString(), isNull(), anyBoolean(), anyBoolean()))
			.willReturn(queryResponse);
		given(queryResponse.getMatchesList()).willReturn(List.of());

		this.vectorStore.doAdd(List.of(new Document("content")));
		this.vectorStore.doDelete(List.of("id-1"));
		this.vectorStore.doDelete(List.of("id-2"));
		this.vectorStore.doSimilaritySearch(SearchRequest.builder().query("query").build());

		then(this.pinecone).should(times(1)).getIndexConnection(INDEX_NAME);
		then(this.index).should(times(1)).upsert(anyList(), eq(""));
		then(this.index).should(times(2)).delete(anyList(), eq(false), eq(""), isNull());
		then(this.index).should(times(1)).queryByVector(anyInt(), anyList(), eq(""), isNull(), eq(false), eq(true));
	}

	@Test
	void resolvesIndexConnectionLazily() {
		then(this.pinecone).shouldHaveNoInteractions();

		this.vectorStore.doDelete(List.of("id-1"));

		then(this.pinecone).should(times(1)).getIndexConnection(INDEX_NAME);
	}

}
