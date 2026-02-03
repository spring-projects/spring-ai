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

package org.springframework.ai.chat.cache.semantic;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemanticCacheAdvisor}.
 *
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
class SemanticCacheAdvisorTests {

	@Mock
	private SemanticCache mockCache;

	@Mock
	private CallAdvisorChain mockChain;

	private SemanticCacheAdvisor advisor;

	@BeforeEach
	void setUp() {
		this.advisor = SemanticCacheAdvisor.builder().cache(this.mockCache).build();
	}

	@Test
	void adviseCallWithSystemPromptUsesContextHash() {
		String systemPromptText = "You are a helpful assistant.";
		String userText = "What is AI?";

		Prompt prompt = new Prompt(List.of(new SystemMessage(systemPromptText), new UserMessage(userText)));

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		// Cache miss
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		this.advisor.adviseCall(request, this.mockChain);

		// Assert - verify cache.get was called with user text and a non-null context hash
		ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockCache).get(queryCaptor.capture(), hashCaptor.capture());

		assertThat(queryCaptor.getValue()).isEqualTo(userText);
		assertThat(hashCaptor.getValue()).isNotNull().hasSize(8); // 8 hex chars

		// Assert - verify cache.set was called with same parameters
		verify(this.mockCache).set(eq(userText), eq(chatResponse), hashCaptor.capture());
		assertThat(hashCaptor.getValue()).isNotNull().hasSize(8);
	}

	@Test
	void adviseCallWithoutSystemPromptUsesNullContextHash() {
		String userText = "What is AI?";

		Prompt prompt = new Prompt(List.of(new UserMessage(userText)));

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		// Cache miss
		when(this.mockCache.get(eq(userText), isNull())).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		this.advisor.adviseCall(request, this.mockChain);

		// Assert - verify cache.get was called with null context hash
		verify(this.mockCache).get(userText, null);

		// Assert - verify cache.set was called with null context hash
		verify(this.mockCache).set(eq(userText), eq(chatResponse), (String) isNull());
	}

	@Test
	void adviseCallReturnsCachedResponseOnHit() {
		String userText = "What is AI?";

		Prompt prompt = new Prompt(List.of(new UserMessage(userText)));

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatResponse cachedResponse = createMockChatResponse("Cached response about AI.");

		// Cache hit
		when(this.mockCache.get(userText, null)).thenReturn(Optional.of(cachedResponse));

		ChatClientResponse result = this.advisor.adviseCall(request, this.mockChain);

		// Assert - should return cached response without calling the chain
		assertThat(result.chatResponse()).isEqualTo(cachedResponse);
		verify(this.mockChain, never()).nextCall(any());
		verify(this.mockCache, never()).set(any(String.class), any(ChatResponse.class), any(String.class));
	}

	@Test
	void sameSystemPromptProducesSameContextHash() {
		String systemPromptText = "You are a pirate.";
		String userText1 = "Hello";
		String userText2 = "Goodbye";

		Prompt prompt1 = new Prompt(List.of(new SystemMessage(systemPromptText), new UserMessage(userText1)));

		Prompt prompt2 = new Prompt(List.of(new SystemMessage(systemPromptText), new UserMessage(userText2)));

		ChatClientRequest request1 = ChatClientRequest.builder().prompt(prompt1).build();

		ChatClientRequest request2 = ChatClientRequest.builder().prompt(prompt2).build();

		ChatResponse chatResponse = createMockChatResponse("Response");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		when(this.mockCache.get(any(), any())).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(any())).thenReturn(clientResponse);

		this.advisor.adviseCall(request1, this.mockChain);
		this.advisor.adviseCall(request2, this.mockChain);

		// Assert - capture both context hashes and verify they're the same
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockCache).get(eq(userText1), hashCaptor.capture());
		String hash1 = hashCaptor.getValue();

		verify(this.mockCache).get(eq(userText2), hashCaptor.capture());
		String hash2 = hashCaptor.getValue();

		assertThat(hash1).isEqualTo(hash2);
	}

	@Test
	void differentSystemPromptsProduceDifferentContextHashes() {
		String userText = "Hello";

		Prompt prompt1 = new Prompt(List.of(new SystemMessage("You are a pirate."), new UserMessage(userText)));

		Prompt prompt2 = new Prompt(List.of(new SystemMessage("You are a teacher."), new UserMessage(userText)));

		ChatClientRequest request1 = ChatClientRequest.builder().prompt(prompt1).build();

		ChatClientRequest request2 = ChatClientRequest.builder().prompt(prompt2).build();

		ChatResponse chatResponse = createMockChatResponse("Response");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		when(this.mockCache.get(any(), any(String.class))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(any())).thenReturn(clientResponse);

		this.advisor.adviseCall(request1, this.mockChain);
		this.advisor.adviseCall(request2, this.mockChain);

		// Assert - capture both context hashes and verify they're different
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockCache, org.mockito.Mockito.times(2)).set(eq(userText), any(ChatResponse.class),
				hashCaptor.capture());

		List<String> capturedHashes = hashCaptor.getAllValues();
		assertThat(capturedHashes).hasSize(2);
		assertThat(capturedHashes.get(0)).isNotEqualTo(capturedHashes.get(1));
	}

	@Test
	void emptySystemPromptTreatedAsNoSystemPrompt() {
		String userText = "What is AI?";

		Prompt prompt = new Prompt(List.of(new SystemMessage(""), new UserMessage(userText)));

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		// Cache miss
		when(this.mockCache.get(eq(userText), isNull())).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		this.advisor.adviseCall(request, this.mockChain);

		// Assert - empty system prompt should result in null context hash
		verify(this.mockCache).get(eq(userText), (String) isNull());
		verify(this.mockCache).set(eq(userText), eq(chatResponse), (String) isNull());
	}

	private ChatResponse createMockChatResponse(String text) {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text)))).build();
	}

}
