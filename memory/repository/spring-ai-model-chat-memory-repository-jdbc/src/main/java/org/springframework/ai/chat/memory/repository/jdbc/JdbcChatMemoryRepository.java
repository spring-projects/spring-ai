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

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

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
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final JdbcChatMemoryRepositoryDialect dialect;

	private JdbcChatMemoryRepository(DataSource dataSource, JdbcChatMemoryRepositoryDialect dialect,
			Optional<PlatformTransactionManager> txManager) {
		Assert.notNull(dataSource, "dataSource cannot be null");
		Assert.notNull(dialect, "dialect cannot be null");
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.dialect = dialect;
		this.transactionTemplate = new TransactionTemplate(
				txManager.orElseGet(() -> new DataSourceTransactionManager(dataSource)));
	}

	@Override
	public List<String> findConversationIds() {
		List<String> conversationIds = this.jdbcTemplate.query(dialect.getSelectConversationIdsSql(), rs -> {
			var ids = new ArrayList<String>();
			while (rs.next()) {
				ids.add(rs.getString(1));
			}
			return ids;
		});
		return conversationIds != null ? conversationIds : List.of();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.jdbcTemplate.query(dialect.getSelectMessagesSql(), new MessageRowMapper(), conversationId);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		transactionTemplate.execute(status -> {
			deleteByConversationId(conversationId);
			jdbcTemplate.batchUpdate(dialect.getInsertMessageSql(),
					new AddBatchPreparedStatement(conversationId, messages));
			return null;
		});
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.jdbcTemplate.update(dialect.getDeleteMessagesSql(), conversationId);
	}

	private record AddBatchPreparedStatement(String conversationId, List<Message> messages,
			AtomicLong instantSeq) implements BatchPreparedStatementSetter {

		private AddBatchPreparedStatement(String conversationId, List<Message> messages) {
			this(conversationId, messages, new AtomicLong(Instant.now().toEpochMilli()));
		}

		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			var message = this.messages.get(i);

			ps.setString(1, this.conversationId);
			ps.setString(2, message.getText());
			ps.setString(3, message.getMessageType().name());
			ps.setTimestamp(4, new Timestamp(instantSeq.getAndIncrement()));
		}

		@Override
		public int getBatchSize() {
			return this.messages.size();
		}
	}

	private static class MessageRowMapper implements RowMapper<Message> {

		@Override
		@Nullable
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
				case TOOL -> new ToolResponseMessage(List.of());
			};
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private JdbcTemplate jdbcTemplate;

		private JdbcChatMemoryRepositoryDialect dialect;

		private DataSource dataSource;

		private PlatformTransactionManager platformTransactionManager;

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
			return new JdbcChatMemoryRepository(effectiveDataSource, effectiveDialect,
					Optional.ofNullable(this.platformTransactionManager));
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
				try {
					return JdbcChatMemoryRepositoryDialect.from(dataSource);
				}
				catch (Exception ex) {
					throw new IllegalStateException("Could not detect dialect from datasource", ex);
				}
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
			try {
				JdbcChatMemoryRepositoryDialect detected = JdbcChatMemoryRepositoryDialect.from(dataSource);
				if (!detected.getClass().equals(explicitDialect.getClass())) {
					logger.warn("Explicitly set dialect {} will be used instead of detected dialect {} from datasource",
							explicitDialect.getClass().getSimpleName(), detected.getClass().getSimpleName());
				}
			}
			catch (Exception ex) {
				logger.debug("Could not detect dialect from datasource", ex);
			}
		}

	}

}
