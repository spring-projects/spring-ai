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

package org.springframework.ai.chat.memory.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for JDBC.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Linar Abzaltdinov
 * @author Mark Pollack
 * @author Yanming Zhou
 * @since 1.0.0
 */
public final class JdbcChatMemoryRepository implements ChatMemoryRepository {

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final JdbcChatMemoryRepositoryDialect dialect;

	private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryRepository.class);

	private JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, JdbcChatMemoryRepositoryDialect dialect,
			@Nullable PlatformTransactionManager txManager) {
		Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
		Assert.notNull(dialect, "dialect cannot be null");
		this.jdbcTemplate = jdbcTemplate;
		this.dialect = dialect;
		if (txManager == null) {
			Assert.state(jdbcTemplate.getDataSource() != null, "jdbcTemplate dataSource cannot be null");
			txManager = new DataSourceTransactionManager(jdbcTemplate.getDataSource());
		}
		this.transactionTemplate = new TransactionTemplate(txManager);
	}

	@Override
	@SuppressWarnings("NullAway") // Assume query can't return null rows
	public List<String> findConversationIds() {
		return this.jdbcTemplate.queryForList(this.dialect.getSelectConversationIdsSql(), String.class);
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.jdbcTemplate.query(this.dialect.getSelectMessagesSql(), new MessageRowMapper(), conversationId);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		this.transactionTemplate.execute(status -> {
			deleteByConversationId(conversationId);
			this.jdbcTemplate.batchUpdate(this.dialect.getInsertMessageSql(),
					new AddBatchPreparedStatement(conversationId, messages));
			return null;
		});
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.jdbcTemplate.update(this.dialect.getDeleteMessagesSql(), conversationId);
	}

	public static Builder builder() {
		return new Builder();
	}

	private record AddBatchPreparedStatement(String conversationId, List<Message> messages,
			AtomicLong sequenceId) implements BatchPreparedStatementSetter {

		private AddBatchPreparedStatement(String conversationId, List<Message> messages) {
			// Use second-level granularity to ensure compatibility with all database
			// timestamp precisions. The timestamp serves as a sequence number for
			// message ordering, not as a precise temporal record.
			this(conversationId, messages, new AtomicLong(Instant.now().getEpochSecond()));
		}

		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			var message = this.messages.get(i);

			ps.setString(1, this.conversationId);
			ps.setString(2, message.getText());
			ps.setString(3, message.getMessageType().name());
			// Convert seconds to milliseconds for Timestamp constructor.
			// Each message gets a unique second value, ensuring proper ordering.
			ps.setTimestamp(4, new Timestamp(this.sequenceId.getAndIncrement() * 1000L));
		}

		@Override
		public int getBatchSize() {
			return this.messages.size();
		}
	}

	private static class MessageRowMapper implements RowMapper<Message> {

		@Override
		public Message mapRow(ResultSet rs, int i) throws SQLException {
			var content = rs.getString(1);
			var type = MessageType.valueOf(rs.getString(2));

			return switch (type) {
				case USER -> new UserMessage(content);
				case ASSISTANT -> new AssistantMessage(content);
				case SYSTEM -> new SystemMessage(content);
				// The content is always stored empty for ToolResponseMessages.
				// If we want to capture the actual content, we need to extend
				// AddBatchPreparedStatement to support it.
				case TOOL -> ToolResponseMessage.builder().responses(List.of()).build();
			};
		}

	}

	public static final class Builder {

		private @Nullable JdbcTemplate jdbcTemplate;

		private @Nullable JdbcChatMemoryRepositoryDialect dialect;

		private @Nullable DataSource dataSource;

		private @Nullable PlatformTransactionManager platformTransactionManager;

		private static final Logger logger = LoggerFactory.getLogger(Builder.class);

		private Builder() {
		}

		public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		public Builder dialect(JdbcChatMemoryRepositoryDialect dialect) {
			this.dialect = dialect;
			return this;
		}

		public Builder dataSource(DataSource dataSource) {
			this.dataSource = dataSource;
			return this;
		}

		public Builder transactionManager(PlatformTransactionManager txManager) {
			this.platformTransactionManager = txManager;
			return this;
		}

		public JdbcChatMemoryRepository build() {
			DataSource effectiveDataSource = resolveDataSource();
			JdbcChatMemoryRepositoryDialect effectiveDialect = resolveDialect(effectiveDataSource);
			return new JdbcChatMemoryRepository(resolveJdbcTemplate(), effectiveDialect,
					this.platformTransactionManager);
		}

		private JdbcTemplate resolveJdbcTemplate() {
			if (this.jdbcTemplate != null) {
				return this.jdbcTemplate;
			}
			if (this.dataSource != null) {
				return new JdbcTemplate(this.dataSource);
			}
			throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
		}

		private DataSource resolveDataSource() {
			if (this.dataSource != null) {
				return this.dataSource;
			}
			if (this.jdbcTemplate != null && this.jdbcTemplate.getDataSource() != null) {
				return this.jdbcTemplate.getDataSource();
			}
			throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
		}

		private JdbcChatMemoryRepositoryDialect resolveDialect(DataSource dataSource) {
			if (this.dialect == null) {
				return JdbcChatMemoryRepositoryDialect.from(dataSource);
			}
			else {
				warnIfDialectMismatch(dataSource, this.dialect);
				return this.dialect;
			}
		}

		/**
		 * Logs a warning if the explicitly set dialect differs from the dialect detected
		 * from the DataSource.
		 */
		private void warnIfDialectMismatch(DataSource dataSource, JdbcChatMemoryRepositoryDialect explicitDialect) {
			JdbcChatMemoryRepositoryDialect detected = JdbcChatMemoryRepositoryDialect.from(dataSource);
			if (!detected.getClass().equals(explicitDialect.getClass())) {
				logger.warn("Explicitly set dialect {} will be used instead of detected dialect {} from datasource",
						explicitDialect.getClass().getSimpleName(), detected.getClass().getSimpleName());
			}
		}

	}

}
