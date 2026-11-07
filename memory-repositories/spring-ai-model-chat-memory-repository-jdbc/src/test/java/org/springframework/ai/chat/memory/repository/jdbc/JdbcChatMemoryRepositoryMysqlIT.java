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

package org.springframework.ai.chat.memory.repository.jdbc;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcChatMemoryRepository} with MySQL.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Mark Pollack
 * @author Yanming Zhou
 * @author Henning Pöttker
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:mysql:8.0.42:///")
@Sql(scripts = "classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-mysql.sql")
class JdbcChatMemoryRepositoryMysqlIT extends AbstractJdbcChatMemoryRepositoryIT {

	@Test
	void savesDifferentConversationsConcurrently() throws Exception {
		this.jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
		var dataSource = Objects.requireNonNull(this.jdbcTemplate.getDataSource());
		var conversationIds = List.of("conversation-1", "conversation-2", "conversation-3", "conversation-4");
		var repository = JdbcChatMemoryRepository.builder()
			.jdbcTemplate(new DeleteBarrierJdbcTemplate(dataSource, conversationIds.size()))
			.build();
		var executor = Executors.newFixedThreadPool(conversationIds.size());

		try {
			var saves = conversationIds.stream()
				.map(conversationId -> executor
					.submit(() -> repository.saveAll(conversationId, List.of(new UserMessage(conversationId)))))
				.toList();
			for (var save : saves) {
				save.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(conversationIds)
			.allSatisfy(conversationId -> assertThat(repository.findByConversationId(conversationId)).hasSize(1));
	}

	private static final class DeleteBarrierJdbcTemplate extends JdbcTemplate {

		private final CyclicBarrier barrier;

		private DeleteBarrierJdbcTemplate(DataSource dataSource, int concurrentSaves) {
			super(dataSource);
			this.barrier = new CyclicBarrier(concurrentSaves);
		}

		@Override
		public int update(String sql, Object... args) {
			int updatedRows = super.update(sql, args);
			if (sql.startsWith("DELETE FROM SPRING_AI_CHAT_MEMORY")) {
				awaitDeleteBarrier();
			}
			return updatedRows;
		}

		private void awaitDeleteBarrier() {
			try {
				this.barrier.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for concurrent deletes", ex);
			}
			catch (BrokenBarrierException | TimeoutException ex) {
				throw new IllegalStateException("Concurrent deletes did not reach the barrier", ex);
			}
		}

	}
}
