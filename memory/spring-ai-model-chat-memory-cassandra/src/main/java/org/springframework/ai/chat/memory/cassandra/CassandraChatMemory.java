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

package org.springframework.ai.chat.memory.cassandra;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

/**
 * @deprecated Use CassandraChatMemoryRepository
 *
 * Create a CassandraChatMemory like <code>
 CassandraChatMemory.create(CassandraChatMemoryConfig.builder().withTimeToLive(Duration.ofDays(1)).build());
 </code>
 *
 * For example @see org.springframework.ai.chat.memory.cassandra.CassandraChatMemory
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Deprecated
public final class CassandraChatMemory implements ChatMemory {

	final CassandraChatMemoryConfig conf;

	final CassandraChatMemoryRepository repo;

	public CassandraChatMemory(CassandraChatMemoryConfig config) {
		this.conf = config;
		repo = CassandraChatMemoryRepository.create(conf);
	}

	public static CassandraChatMemory create(CassandraChatMemoryConfig conf) {
		return new CassandraChatMemory(conf);
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		repo.saveAll(conversationId, messages);
	}

	@Override
	public void add(String sessionId, Message msg) {
		repo.save(sessionId, msg);
	}

	@Override
	public void clear(String sessionId) {
		repo.deleteByConversationId(sessionId);
	}

	@Override
	public List<Message> get(String sessionId, int lastN) {
		return repo.findByConversationId(sessionId).subList(0, lastN);
	}

}
