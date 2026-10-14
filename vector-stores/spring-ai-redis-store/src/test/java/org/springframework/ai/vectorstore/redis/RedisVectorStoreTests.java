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

package org.springframework.ai.vectorstore.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchProtocol.SearchCommand;
import redis.clients.jedis.search.SearchResult;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Taewoong Kim
 */
class RedisVectorStoreTests {

	@Test
	void searchesDoNotReturnEmbeddingField() {
		RedisClient jedisClient = mock(RedisClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		SearchResult searchResult = mock(SearchResult.class);
		when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1.0f, 0.0f, 0.0f });
		when(searchResult.getDocuments()).thenReturn(List.of());
		when(jedisClient.ftSearch(anyString(), any(Query.class))).thenReturn(searchResult);
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel)
			.embeddingFieldName("custom_embedding")
			.build();

		vectorStore.similaritySearch(SearchRequest.builder().query("query").topK(1).build());
		vectorStore.searchByText("query", RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME, 1);
		vectorStore.searchByRange("query", 0.75);

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(jedisClient, times(3)).ftSearch(eq(RedisVectorStore.DEFAULT_INDEX_NAME), queryCaptor.capture());
		assertThat(queryCaptor.getAllValues()).allSatisfy(query -> assertThat(returnFields(query))
			.contains(RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME, RedisVectorStore.DISTANCE_FIELD_NAME)
			.doesNotContain("custom_embedding"));
	}

	@Test
	void failedIndexReadRefusesTheWriteAndDoesNotPoisonLaterCalls() {
		RedisClient jedisClient = mock(RedisClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(jedisClient.ftInfo(anyString())).thenThrow(new JedisConnectionException("connection lost"))
			.thenReturn(Map.of("attributes", List.of(Map.of("attribute", "embedding", "dim", 4))));
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel).build();
		List<EmbeddedDocument> entries = List
			.of(new EmbeddedDocument(new Document("foo"), new float[] { 0.1f, 0.2f, 0.3f }));

		// Redis would accept a wrong-sized vector and then never find it, so a size that
		// could not be read must not be replaced by the model's
		assertThatIllegalStateException().isThrownBy(() -> vectorStore.doUpsert(entries))
			.withMessageContaining("vector size");

		// Once the index can be read again the real size applies
		assertThatIllegalArgumentException().isThrownBy(() -> vectorStore.doUpsert(entries))
			.withMessageContaining("dimension 3")
			.withMessageContaining("dimension 4");
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void knownIndexSizeIsReadOnce() {
		RedisClient jedisClient = mock(RedisClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(jedisClient.ftInfo(anyString()))
			.thenReturn(Map.of("attributes", List.of(Map.of("attribute", "embedding", "dim", 4))));
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel).build();

		assertThat(vectorStore.createObservationContextBuilder("test").build().getDimensions()).isEqualTo(4);
		assertThat(vectorStore.createObservationContextBuilder("test").build().getDimensions()).isEqualTo(4);
		verify(jedisClient, times(1)).ftInfo(anyString());
	}

	@Test
	void initializingOverAnExistingIndexDoesNotAskTheModel() {
		RedisClient jedisClient = mock(RedisClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(jedisClient.ftList()).thenReturn(Set.of(RedisVectorStore.DEFAULT_INDEX_NAME));
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel)
			.initializeSchema(true)
			.build();

		vectorStore.afterPropertiesSet();

		verify(jedisClient, never()).ftCreate(anyString(), any(FTCreateParams.class), any(Iterable.class));
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void createdIndexKeepsTheSizeItWasCreatedWith() {
		RedisClient jedisClient = mock(RedisClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(jedisClient.ftList()).thenReturn(Set.of());
		when(jedisClient.ftCreate(anyString(), any(FTCreateParams.class), any(Iterable.class))).thenReturn("OK");
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel)
			.initializeSchema(true)
			.build();

		vectorStore.afterPropertiesSet();

		assertThat(vectorStore.createObservationContextBuilder("test").build().getDimensions()).isEqualTo(3);
		verify(jedisClient, never()).ftInfo(anyString());
	}

	private static List<String> returnFields(Query query) {
		CommandArguments commandArguments = new CommandArguments(SearchCommand.SEARCH);
		query.addParams(commandArguments);
		List<String> arguments = StreamSupport.stream(commandArguments.spliterator(), false)
			.map(argument -> new String(argument.getRaw(), StandardCharsets.UTF_8))
			.toList();
		int returnIndex = arguments.indexOf("RETURN");
		int returnFieldCount = Integer.parseInt(arguments.get(returnIndex + 1));
		return arguments.subList(returnIndex + 2, returnIndex + 2 + returnFieldCount);
	}

}
