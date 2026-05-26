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

package org.springframework.ai.chat.memory.repository.oracle;

import java.util.List;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.OracleChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * An implementation of {@link ChatMemoryRepository} for Oracle.
 *
 * @since 2.0.0
 */
public final class OracleChatMemoryRepository implements ChatMemoryRepository {

	/** Delegate repository implementation. */
	private final JdbcChatMemoryRepository delegate;

	private OracleChatMemoryRepository(final JdbcChatMemoryRepository delegateRepository) {
		this.delegate = delegateRepository;
	}

	@Override
	public List<String> findConversationIds() {
		return this.delegate.findConversationIds();
	}

	@Override
	public List<Message> findByConversationId(final String conversationId) {
		return this.delegate.findByConversationId(conversationId);
	}

	@Override
	public void saveAll(final String conversationId, final List<Message> messages) {
		this.delegate.saveAll(conversationId, messages);
	}

	@Override
	public void deleteByConversationId(final String conversationId) {
		this.delegate.deleteByConversationId(conversationId);
	}

	/**
	 * Create a builder for {@link OracleChatMemoryRepository}.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link OracleChatMemoryRepository}.
	 */
	public static final class Builder {

		/** Data source used by the underlying JDBC repository. */
		private @Nullable DataSource dataSource;

		/** JDBC template used by the underlying JDBC repository. */
		private @Nullable JdbcTemplate jdbcTemplate;

		/** Optional transaction manager used by the repository. */
		private @Nullable PlatformTransactionManager transactionManager;

		private Builder() {
		}

		/**
		 * Set the data source.
		 * @param dataSourceToUse data source to use
		 * @return this builder
		 */
		public Builder dataSource(final DataSource dataSourceToUse) {
			this.dataSource = dataSourceToUse;
			return this;
		}

		/**
		 * Set the JDBC template.
		 * @param jdbcTemplateToUse JDBC template to use
		 * @return this builder
		 */
		public Builder jdbcTemplate(final JdbcTemplate jdbcTemplateToUse) {
			this.jdbcTemplate = jdbcTemplateToUse;
			return this;
		}

		/**
		 * Set the transaction manager.
		 * @param transactionManagerToUse transaction manager to use
		 * @return this builder
		 */
		public Builder transactionManager(final PlatformTransactionManager transactionManagerToUse) {
			this.transactionManager = transactionManagerToUse;
			return this;
		}

		/**
		 * Build the Oracle chat memory repository.
		 * @return a new Oracle chat memory repository
		 */
		public OracleChatMemoryRepository build() {
			var repository = JdbcChatMemoryRepository.builder().dialect(new OracleChatMemoryRepositoryDialect());

			if (this.dataSource != null) {
				repository.dataSource(this.dataSource);
			}

			if (this.jdbcTemplate != null) {
				repository.jdbcTemplate(this.jdbcTemplate);
			}

			if (this.transactionManager != null) {
				repository.transactionManager(this.transactionManager);
			}

			return new OracleChatMemoryRepository(repository.build());
		}

	}

}
