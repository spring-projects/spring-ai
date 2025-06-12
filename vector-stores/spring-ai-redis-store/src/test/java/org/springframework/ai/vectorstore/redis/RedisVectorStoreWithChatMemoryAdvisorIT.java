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

package org.springframework.ai.vectorstore.redis;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for RedisVectorStore using Redis Stack TestContainer.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisVectorStoreWithChatMemoryAdvisorIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	float[] embed = { 0.003961659F, -0.0073295482F, 0.02663665F };

	@Test
	@DisplayName("Advised chat should have similar messages from vector store")
	void advisedChatShouldHaveSimilarMessagesFromVectorStore() throws Exception {
		// Mock chat model
		ChatModel chatModel = chatModelAlwaysReturnsTheSameReply();
		// Mock embedding model
		EmbeddingModel embeddingModel = embeddingModelShouldAlwaysReturnFakedEmbed();

		// Create Redis store with dimensions matching our fake embeddings
		RedisVectorStore store = RedisVectorStore
			.builder(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()), embeddingModel)
			.metadataFields(MetadataField.tag("conversationId"), MetadataField.tag("messageType"))
			.initializeSchema(true)
			.build();

		store.afterPropertiesSet();

		// Initialize store with test data
		store.add(List.of(new Document("Tell me a good joke", Map.of("conversationId", "default")),
				new Document("Tell me a bad joke", Map.of("conversationId", "default", "messageType", "USER"))));

		// Run chat with advisor
		ChatClient.builder(chatModel)
			.build()
			.prompt()
			.user("joke")
			.advisors(VectorStoreChatMemoryAdvisor.builder(store).build())
			.call()
			.chatResponse();

		verifyRequestHasBeenAdvisedWithMessagesFromVectorStore(chatModel);
	}

	private static ChatModel chatModelAlwaysReturnsTheSameReply() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				Why don't scientists trust atoms?
				Because they make up everything!"""))));
		given(chatModel.call(argumentCaptor.capture())).willReturn(chatResponse);
		return chatModel;
	}

	private static void verifyRequestHasBeenAdvisedWithMessagesFromVectorStore(ChatModel chatModel) {
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(argumentCaptor.capture());
		List<SystemMessage> systemMessages = argumentCaptor.getValue()
			.getInstructions()
			.stream()
			.filter(message -> message instanceof SystemMessage)
			.map(message -> (SystemMessage) message)
			.toList();
		assertThat(systemMessages).hasSize(1);
		SystemMessage systemMessage = systemMessages.get(0);
		assertThat(systemMessage.getText()).contains("Tell me a good joke");
		assertThat(systemMessage.getText()).contains("Tell me a bad joke");
	}

	private EmbeddingModel embeddingModelShouldAlwaysReturnFakedEmbed() {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		given(embeddingModel.embed(any(String.class))).willReturn(embed);
		given(embeddingModel.dimensions()).willReturn(embed.length);

		// Mock the list version of embed method to return a list of embeddings
		given(embeddingModel.embed(Mockito.anyList(), Mockito.any(), Mockito.any())).willAnswer(invocation -> {
			List<Document> docs = invocation.getArgument(0);
			List<float[]> embeddings = new java.util.ArrayList<>();
			for (int i = 0; i < docs.size(); i++) {
				embeddings.add(embed);
			}
			return embeddings;
		});

		return embeddingModel;
	}

}