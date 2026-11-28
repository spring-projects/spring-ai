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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.DeleteResponse;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PineconeVectorStoreTests {

	private static final String INDEX_NAME = "test-index";

	private static final String NAMESPACE = "test-namespace";

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final Index index = mock(Index.class);

	@Test
	void filterDeleteRemovesMatchesBeyondQueryLimit() {
		Map<String, String> documents = new LinkedHashMap<>();
		for (int i = 0; i < 10001; i++) {
			documents.put("obsolete-" + i, "obsolete");
		}
		documents.put("retained", "current");

		when(this.embeddingModel.embed("")).thenReturn(new float[] { 0.1f, 0.2f });
		when(this.index.queryByVector(anyInt(), anyList(), eq(NAMESPACE), any(Struct.class), eq(false), eq(true)))
			.thenAnswer(invocation -> {
				int topK = invocation.getArgument(0);
				var response = QueryResponse.newBuilder();
				documents.entrySet()
					.stream()
					.filter(entry -> entry.getValue().equals("obsolete"))
					.limit(topK)
					.forEach(entry -> response.addMatches(ScoredVector.newBuilder()
						.setId(entry.getKey())
						.setScore(1)
						.setMetadata(Struct.newBuilder()
							.putFields(PineconeVectorStore.CONTENT_FIELD_NAME,
									Value.newBuilder().setStringValue("content").build())
							.build())));
				return new QueryResponseWithUnsignedIndices(response.build());
			});
		doAnswer(invocation -> {
			List<String> ids = invocation.getArgument(0);
			ids.forEach(documents::remove);
			return DeleteResponse.getDefaultInstance();
		}).when(this.index).delete(anyList(), eq(false), eq(NAMESPACE), eq(null));
		when(this.index.deleteByFilter(any(Struct.class), eq(NAMESPACE))).thenAnswer(invocation -> {
			Struct filter = invocation.getArgument(0);
			String category = filter.getFieldsOrThrow("category")
				.getStructValue()
				.getFieldsOrThrow("$eq")
				.getStringValue();
			documents.entrySet().removeIf(entry -> entry.getValue().equals(category));
			return DeleteResponse.getDefaultInstance();
		});

		PineconeVectorStore vectorStore = createVectorStore(NAMESPACE);
		vectorStore.delete(new FilterExpressionBuilder().eq("category", "obsolete").build());

		assertThat(documents).containsExactly(Map.entry("retained", "current"));
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeletePreservesNamespaceAndMetadataTypes() {
		PineconeVectorStore vectorStore = createVectorStore(NAMESPACE);
		var builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.and(builder.eq("active", false), builder.gte("year", 2020)).build();

		vectorStore.delete(filter);

		var filterCaptor = ArgumentCaptor.forClass(Struct.class);
		verify(this.index).deleteByFilter(filterCaptor.capture(), eq(NAMESPACE));
		List<Value> conditions = filterCaptor.getValue().getFieldsOrThrow("$and").getListValue().getValuesList();
		assertThat(conditions).hasSize(2);
		assertThat(
				conditions.get(0).getStructValue().getFieldsOrThrow("active").getStructValue().getFieldsOrThrow("$eq"))
			.isEqualTo(Value.newBuilder().setBoolValue(false).build());
		assertThat(
				conditions.get(1).getStructValue().getFieldsOrThrow("year").getStructValue().getFieldsOrThrow("$gte"))
			.isEqualTo(Value.newBuilder().setNumberValue(2020).build());
		verifyNoMoreInteractions(this.index);
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeleteUsesDefaultNamespace() {
		PineconeVectorStore vectorStore = createVectorStore(null);

		vectorStore.delete(new FilterExpressionBuilder().eq("category", "obsolete").build());

		verify(this.index).deleteByFilter(any(Struct.class), eq(""));
		verifyNoMoreInteractions(this.index);
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeletePropagatesServiceFailure() {
		PineconeVectorStore vectorStore = createVectorStore(NAMESPACE);
		var failure = new IllegalArgumentException("Delete rejected");
		when(this.index.deleteByFilter(any(Struct.class), eq(NAMESPACE))).thenThrow(failure);

		assertThatThrownBy(() -> vectorStore.delete(new FilterExpressionBuilder().eq("category", "obsolete").build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to delete documents by filter")
			.hasCause(failure);
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeleteRejectsNullExpression() {
		PineconeVectorStore vectorStore = createVectorStore(NAMESPACE);

		assertThatThrownBy(() -> vectorStore.delete((Filter.Expression) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Filter expression must not be null");
		verifyNoInteractions(this.index);
		verify(this.embeddingModel, never()).embed(anyString());
	}

	private PineconeVectorStore createVectorStore(String namespace) {
		try (MockedConstruction<Pinecone> ignored = mockConstruction(Pinecone.class,
				(client, context) -> when(client.getIndexConnection(INDEX_NAME)).thenReturn(this.index))) {
			return PineconeVectorStore.builder(this.embeddingModel)
				.apiKey("test-api-key")
				.indexName(INDEX_NAME)
				.namespace(namespace)
				.build();
		}
	}

}
