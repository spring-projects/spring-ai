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

package org.springframework.ai.chat.cache.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.GuardrailRefusal;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemanticCacheAdvisor}.
 *
 * @author Soby Chacko
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class SemanticCacheAdvisorTests {

	private static final String SCOPE = "tenant-a";

	private static final String SCOPE_PARAM = "tenantId";

	@Mock
	private SemanticCache mockCache;

	@Mock
	private CallAdvisorChain mockChain;

	@Mock
	private StreamAdvisorChain mockStreamChain;

	private SemanticCacheAdvisor advisor;

	@BeforeEach
	void setUp() {
		this.advisor = SemanticCacheAdvisor.builder()
			.cache(this.mockCache)
			.scopeResolver(request -> (String) request.context().get(SCOPE_PARAM))
			.build();
	}

	@Test
	void builderRequiresScopeResolver() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SemanticCacheAdvisor.builder().cache(this.mockCache).build())
			.withMessage("Scope resolver must not be null");
	}

	@Test
	void constructorRequiresScopeResolver() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SemanticCacheAdvisor(this.mockCache, null))
			.withMessage("Scope resolver must not be null");
	}

	@Test
	void builderRejectsNonPositiveTtl() {
		SemanticCacheAdvisor.Builder builder = SemanticCacheAdvisor.builder()
			.cache(this.mockCache)
			.scopeResolver(request -> SCOPE);

		assertThatIllegalArgumentException().isThrownBy(() -> builder.ttl(Duration.ZERO).build())
			.withMessage("TTL must be positive");
		assertThatIllegalArgumentException().isThrownBy(() -> builder.ttl(Duration.ofSeconds(-1)).build())
			.withMessage("TTL must be positive");
	}

	@Test
	void adviseCallWithTtlCachesResponseWithTtl() {
		Duration ttl = Duration.ofMinutes(10);
		SemanticCacheAdvisor ttlAdvisor = SemanticCacheAdvisor.builder()
			.cache(this.mockCache)
			.scopeResolver(request -> SCOPE)
			.ttl(ttl)
			.build();
		String userText = "What is AI?";

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(any()))
			.thenReturn(ChatClientResponse.builder().chatResponse(chatResponse).build());

		ttlAdvisor.adviseCall(createRequest(SCOPE, null, userText), this.mockChain);

		verify(this.mockCache).set(userText, chatResponse, sha256(SCOPE), ttl);
		verify(this.mockCache, never()).set(any(String.class), any(ChatResponse.class), any(String.class));
	}

	@Test
	void adviseCallWithoutTtlCachesResponseWithoutTtl() {
		String userText = "What is AI?";

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(any()))
			.thenReturn(ChatClientResponse.builder().chatResponse(chatResponse).build());

		this.advisor.adviseCall(createRequest(SCOPE, null, userText), this.mockChain);

		verify(this.mockCache).set(userText, chatResponse, sha256(SCOPE));
		verify(this.mockCache, never()).set(any(String.class), any(ChatResponse.class), any(String.class),
				any(Duration.class));
	}

	@Test
	void adviseStreamWithTtlCachesAggregatedResponseWithTtl() {
		Duration ttl = Duration.ofMinutes(10);
		SemanticCacheAdvisor ttlAdvisor = SemanticCacheAdvisor.builder()
			.cache(this.mockCache)
			.scopeResolver(request -> SCOPE)
			.ttl(ttl)
			.build();
		String userText = "What is AI?";
		ChatClientRequest request = createRequest(SCOPE, null, userText);

		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockStreamChain.nextStream(request)).thenReturn(Flux.just(ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("AI is artificial intelligence."))
			.build()));

		ttlAdvisor.adviseStream(request, this.mockStreamChain).blockLast();

		verify(this.mockCache).set(eq(userText), any(ChatResponse.class), eq(sha256(SCOPE)), eq(ttl));
	}

	@Test
	void adviseCallWithSystemPromptUsesContextHash() {
		String systemPromptText = "You are a helpful assistant.";
		String userText = "What is AI?";

		ChatClientRequest request = createRequest(SCOPE, systemPromptText, userText);

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
		assertThat(hashCaptor.getValue()).isNotNull().hasSize(64); // 64 hex chars (32
																	// bytes)

		// Assert - verify cache.set was called with same parameters
		verify(this.mockCache).set(eq(userText), eq(chatResponse), hashCaptor.capture());
		assertThat(hashCaptor.getValue()).isNotNull().hasSize(64);
	}

	@Test
	void adviseCallWithoutSystemPromptUsesScopeHash() {
		String userText = "What is AI?";

		ChatClientRequest request = createRequest(SCOPE, null, userText);

		ChatResponse chatResponse = createMockChatResponse("AI is artificial intelligence.");
		ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).build();

		String expectedHash = sha256(SCOPE);

		// Cache miss
		when(this.mockCache.get(userText, expectedHash)).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		this.advisor.adviseCall(request, this.mockChain);

		// Assert - verify cache.get and cache.set were called with the scope hash
		verify(this.mockCache).get(userText, expectedHash);
		verify(this.mockCache).set(userText, chatResponse, expectedHash);
	}

	@Test
	void adviseCallReturnsCachedResponseOnHit() {
		String userText = "What is AI?";

		ChatClientRequest request = createRequest(SCOPE, null, userText);

		ChatResponse cachedResponse = createMockChatResponse("Cached response about AI.");

		// Cache hit
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.of(cachedResponse));

		ChatClientResponse result = this.advisor.adviseCall(request, this.mockChain);

		// Assert - should return cached response without calling the chain
		assertThat(result.chatResponse()).isEqualTo(cachedResponse);
		verify(this.mockChain, never()).nextCall(any());
		verify(this.mockCache, never()).set(any(String.class), any(ChatResponse.class), any(String.class));
	}

	@Test
	void adviseCallDoesNotCacheTheRefusalOfAGuardrail() {
		String userText = "Tell me something dangerous";
		ChatClientRequest request = createRequest(SCOPE, null, userText);
		ChatClientResponse refusal = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(GuardrailRefusal.generation("I can't help with that."))))
			.build();

		when(this.mockCache.get(userText, sha256(SCOPE))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(request)).thenReturn(refusal);

		ChatClientResponse result = this.advisor.adviseCall(request, this.mockChain);

		// The cache keeps the text of a response and not its finish reason: a cached
		// refusal would come back as an answer of the model.
		assertThat(result).isSameAs(refusal);
		verify(this.mockCache).get(userText, sha256(SCOPE));
		verifyNoMoreInteractions(this.mockCache);
	}

	@Test
	void adviseStreamDoesNotCacheTheRefusalOfAGuardrail() {
		String userText = "Tell me something dangerous";
		ChatClientRequest request = createRequest(SCOPE, null, userText);
		ChatClientResponse refusal = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(GuardrailRefusal.generation("I can't help with that."))))
			.build();
		StreamAdvisorChain streamChain = mock(StreamAdvisorChain.class);

		when(this.mockCache.get(userText, sha256(SCOPE))).thenReturn(Optional.empty());
		when(streamChain.nextStream(request)).thenReturn(Flux.just(refusal));

		List<ChatClientResponse> result = this.advisor.adviseStream(request, streamChain).collectList().block();

		assertThat(result).hasSize(1);
		assertThat(GuardrailRefusal.isRefusal(result.get(0).chatResponse())).isTrue();
		verify(this.mockCache).get(userText, sha256(SCOPE));
		verifyNoMoreInteractions(this.mockCache);
	}

	@Test
	void sameSystemPromptProducesSameContextHash() {
		String systemPromptText = "You are a pirate.";
		String userText1 = "Hello";
		String userText2 = "Goodbye";

		ChatClientRequest request1 = createRequest(SCOPE, systemPromptText, userText1);
		ChatClientRequest request2 = createRequest(SCOPE, systemPromptText, userText2);

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
	void differentSystemPromptsShareContextHashWithinScope() {
		String userText = "Hello";

		ChatClientRequest request1 = createRequest(SCOPE, "You are a pirate.", userText);
		ChatClientRequest request2 = createRequest(SCOPE, "You are a teacher.", userText);
		ChatClientRequest request3 = createRequest(SCOPE, null, userText);

		assertThat(captureContextHashes(request1, request2, request3)).containsOnly(sha256(SCOPE));
	}

	@Test
	void scopeResolverIncludingSystemPromptIsolatesSystemPrompts() {
		SemanticCacheAdvisor personaAdvisor = new SemanticCacheAdvisor(this.mockCache,
				request -> SCOPE + "|" + request.prompt().getSystemMessage().getText());
		String userText = "Hello";

		ChatClientResponse clientResponse = ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("Response"))
			.build();
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockChain.nextCall(any())).thenReturn(clientResponse);

		personaAdvisor.adviseCall(createRequest(SCOPE, "You are a pirate.", userText), this.mockChain);
		personaAdvisor.adviseCall(createRequest(SCOPE, "You are a teacher.", userText), this.mockChain);

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockCache, times(2)).get(eq(userText), hashCaptor.capture());
		assertThat(hashCaptor.getAllValues()).doesNotHaveDuplicates();
	}

	@Test
	void differentScopesProduceDifferentContextHashes() {
		String systemPromptText = "You are a helpful assistant.";
		String userText = "Hello";

		ChatClientRequest request1 = createRequest("tenant-a", systemPromptText, userText);
		ChatClientRequest request2 = createRequest("tenant-b", systemPromptText, userText);

		assertThat(captureContextHashes(request1, request2)).doesNotHaveDuplicates();
	}

	@Test
	void contextHashMatchesFullSha256Digest() {
		String systemPromptText = "You are a helpful assistant.";
		String userText = "What is AI?";

		ChatClientRequest request = createRequest(SCOPE, systemPromptText, userText);

		String expectedHex = sha256(SCOPE);

		assertThat(captureContextHashes(request)).containsExactly(expectedHex);
		assertThat(expectedHex).matches("^[0-9a-f]{64}$");
	}

	@Test
	void adviseCallWithoutScopeBypassesCache() {
		ChatClientRequest request = createRequest(null, "You are a helpful assistant.", "What is AI?");

		ChatClientResponse clientResponse = ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("AI is artificial intelligence."))
			.build();
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		ChatClientResponse result = this.advisor.adviseCall(request, this.mockChain);

		assertThat(result).isSameAs(clientResponse);
		verifyNoInteractions(this.mockCache);
	}

	@Test
	void adviseCallWithBlankScopeBypassesCache() {
		ChatClientRequest request = createRequest("  ", null, "What is AI?");

		ChatClientResponse clientResponse = ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("AI is artificial intelligence."))
			.build();
		when(this.mockChain.nextCall(request)).thenReturn(clientResponse);

		ChatClientResponse result = this.advisor.adviseCall(request, this.mockChain);

		assertThat(result).isSameAs(clientResponse);
		verifyNoInteractions(this.mockCache);
	}

	@Test
	void adviseStreamWithoutScopeBypassesCache() {
		ChatClientRequest request = createRequest(null, null, "What is AI?");

		ChatClientResponse clientResponse = ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("AI is artificial intelligence."))
			.build();
		when(this.mockStreamChain.nextStream(request)).thenReturn(Flux.just(clientResponse));

		List<ChatClientResponse> result = this.advisor.adviseStream(request, this.mockStreamChain)
			.collectList()
			.block();

		assertThat(result).containsExactly(clientResponse);
		verifyNoInteractions(this.mockCache);
	}

	@Test
	void adviseStreamWithScopeCachesAggregatedResponse() {
		String userText = "What is AI?";
		ChatClientRequest request = createRequest(SCOPE, null, userText);

		ChatClientResponse clientResponse = ChatClientResponse.builder()
			.chatResponse(createMockChatResponse("AI is artificial intelligence."))
			.build();
		when(this.mockCache.get(eq(userText), any(String.class))).thenReturn(Optional.empty());
		when(this.mockStreamChain.nextStream(request)).thenReturn(Flux.just(clientResponse));

		this.advisor.adviseStream(request, this.mockStreamChain).blockLast();

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.mockCache).get(eq(userText), hashCaptor.capture());
		verify(this.mockCache).set(eq(userText), any(ChatResponse.class), eq(hashCaptor.getValue()));
	}

	private List<String> captureContextHashes(ChatClientRequest... requests) {
		SemanticCache cache = mock(SemanticCache.class);
		SemanticCacheAdvisor advisor = new SemanticCacheAdvisor(cache,
				request -> (String) request.context().get(SCOPE_PARAM));
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(cache.get(any(), any())).thenReturn(Optional.empty());
		when(chain.nextCall(any()))
			.thenReturn(ChatClientResponse.builder().chatResponse(createMockChatResponse("Response")).build());

		for (ChatClientRequest request : requests) {
			advisor.adviseCall(request, chain);
		}

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(cache, times(requests.length)).get(any(), hashCaptor.capture());
		return hashCaptor.getAllValues();
	}

	private static ChatClientRequest createRequest(String scope, String systemText, String userText) {
		Prompt prompt = (systemText != null)
				? new Prompt(List.of(new SystemMessage(systemText), new UserMessage(userText)))
				: new Prompt(List.of(new UserMessage(userText)));
		ChatClientRequest.Builder builder = ChatClientRequest.builder().prompt(prompt);
		if (scope != null) {
			builder.context(SCOPE_PARAM, scope);
		}
		return builder.build();
	}

	private static String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private ChatResponse createMockChatResponse(String text) {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text)))).build();
	}

}
