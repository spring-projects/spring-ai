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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

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

	/**
	 * Metadata key under which each message's creation timestamp (an {@link Instant}) is
	 * exposed when messages are read back from the repository. Messages carrying this key
	 * retain their original timestamp when the conversation is saved again.
	 *
	 * @since 2.0.0
	 */
	public static final String CONVERSATION_TS = JdbcChatMemoryRepository.class.getSimpleName() + "_message_timestamp";

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final JdbcChatMemoryRepositoryDialect dialect;

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
	public List<String> findConversationIds() {
		return this.jdbcTemplate.query(this.dialect.getSelectConversationIdsSql(),
				(rs, rowNum) -> Objects.requireNonNull(rs.getString(1)));
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.jdbcTemplate.query(this.dialect.getSelectMessagesSql(), new MessageRowMapper(), conversationId)
			.stream()
			.filter(Objects::nonNull)
			.toList();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		List<Message> persistableMessages = messages.stream()
			.filter(m -> !(m instanceof ToolResponseMessage)
					&& !(m instanceof AssistantMessage am && am.hasToolCalls()))
			.toList();

		this.transactionTemplate.executeWithoutResult(status -> {
			deleteByConversationId(conversationId);
			this.jdbcTemplate.batchUpdate(this.dialect.getInsertMessageSql(),
					new AddBatchPreparedStatement(conversationId, persistableMessages));
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

	private record AddBatchPreparedStatement(String conversationId,
			List<Message> messages) implements BatchPreparedStatementSetter {

		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			var message = this.messages.get(i);

			ps.setString(1, this.conversationId);
			ps.setString(2, message.getText());
			ps.setString(3, message.getMessageType().name());
			// Preserve the original creation time across the delete-and-reinsert
			// performed by saveAll(): reuse the timestamp carried in the message
			// metadata if present, otherwise stamp the current time for a new message.
			Object messageTs = message.getMetadata().get(CONVERSATION_TS);
			Instant timestamp = (messageTs instanceof Instant instant) ? instant : Instant.now();
			ps.setTimestamp(4, Timestamp.from(timestamp));
			// The sequence_id is the message's position within the conversation. Since
			// saveAll() deletes and reinserts the whole conversation in a single batch,
			// the batch index is a stable, database-portable ordering key.
			ps.setLong(5, i);
		}

		@Override
		public int getBatchSize() {
			return this.messages.size();
		}
	}

	private static class MessageRowMapper implements RowMapper<@Nullable Message> {

		@Override
		public @Nullable Message mapRow(ResultSet rs, int i) throws SQLException {
			var content = rs.getString(1);
			var type = MessageType.valueOf(rs.getString(2));
			Timestamp timestamp = rs.getTimestamp(3);
			Map<String, Object> metadata = (timestamp != null) ? Map.of(CONVERSATION_TS, timestamp.toInstant())
					: Map.of();

			return switch (type) {
				case USER -> UserMessage.builder().text(content).metadata(metadata).build();
				case ASSISTANT -> AssistantMessage.builder().content(content).properties(metadata).build();
				case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
				// this implementation doesn't support tool calls message persistence, so
				// TOOL rows are filtered out by the caller
				case TOOL -> null;
			};
		}

	}

	public static final class Builder {

		private @Nullable JdbcTemplate jdbcTemplate;

		private @Nullable JdbcChatMemoryRepositoryDialect dialect;

		private @Nullable DataSource dataSource;

		private @Nullable PlatformTransactionManager platformTransactionManager;

		private static final Log logger = LogFactory.getLog(Builder.class);

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
				if (logger.isWarnEnabled()) {
					logger.warn("Explicitly set dialect " + explicitDialect.getClass().getSimpleName()
							+ " will be used instead of detected dialect " + detected.getClass().getSimpleName()
							+ " from datasource");
				}
			}
		}

	}

}
