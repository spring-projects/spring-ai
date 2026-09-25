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
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchProtocol.SearchCommand;
import redis.clients.jedis.search.SearchResult;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
