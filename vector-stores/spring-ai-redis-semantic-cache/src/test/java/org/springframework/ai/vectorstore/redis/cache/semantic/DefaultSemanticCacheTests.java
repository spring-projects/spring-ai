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

package org.springframework.ai.vectorstore.redis.cache.semantic;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DefaultSemanticCache}.
 *
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class DefaultSemanticCacheTests {

	@Mock
	private VectorStore vectorStore;

	@Test
	void setWithContextHashAndTtlRequiresRedisVectorStore() {
		DefaultSemanticCache cache = DefaultSemanticCache.builder().vectorStore(this.vectorStore).build();

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> cache.set("query", createChatResponse(), "hash", Duration.ofMinutes(1)));

		// Nothing is stored, rather than an entry that never expires
		verifyNoInteractions(this.vectorStore);
	}

	@Test
	void setWithContextHashAndTtlRejectsNonPositiveTtl() {
		DefaultSemanticCache cache = DefaultSemanticCache.builder().vectorStore(this.vectorStore).build();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> cache.set("query", createChatResponse(), "hash", Duration.ZERO))
			.withMessage("TTL must be positive");
		verifyNoInteractions(this.vectorStore);
	}

	private static ChatResponse createChatResponse() {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage("response")))).build();
	}

}
