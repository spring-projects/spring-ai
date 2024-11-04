/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.memory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * An implementation of {@link ChatMemory} for PgVector. Creating an instance of
 * PgVectorChatMemory example:
 * <code>PgVectorChatMemory.create(PgVectorChatMemoryConfig.builder().withJdbcTemplate(jdbcTemplate).build());</code>
 *
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
public class PgVectorChatMemory implements ChatMemory {

	private final PgVectorChatMemoryConfig config;

	private final JdbcTemplate jdbcTemplate;

	public PgVectorChatMemory(PgVectorChatMemoryConfig config) {
		this.config = config;
		this.config.initializeSchema();
		this.jdbcTemplate = this.config.getJdbcTemplate();
	}

	public static PgVectorChatMemory create(PgVectorChatMemoryConfig config) {
		return new PgVectorChatMemory(config);
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		var sql = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?)",
				this.config.getFullyQualifiedTableName(), this.config.getSessionIdColumnName(),
				this.config.getAssistantColumnName(), this.config.getUserColumnName());

		this.jdbcTemplate.batchUpdate(sql, new AddBatchPreparedStatement(conversationId, messages));
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		var sql = String.format("SELECT %s, %s FROM %s WHERE %s = ? ORDER BY %s DESC LIMIT ?",
				this.config.getAssistantColumnName(), this.config.getUserColumnName(),
				this.config.getFullyQualifiedTableName(), this.config.getSessionIdColumnName(),
				this.config.getExchangeIdColumnName());

		return this.jdbcTemplate.query(sql, new MessageRowMapper(), conversationId, lastN);
	}

	@Override
	public void clear(String conversationId) {
		var sql = String.format("DELETE FROM %s WHERE %s = ?", this.config.getFullyQualifiedTableName(),
				this.config.getSessionIdColumnName());

		this.jdbcTemplate.update(sql, conversationId);
	}

	private record AddBatchPreparedStatement(String conversationId,
			List<Message> messages) implements BatchPreparedStatementSetter {
		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			var message = this.messages.get(i);

			ps.setString(1, this.conversationId);

			switch (message.getMessageType()) {
				case ASSISTANT -> {
					ps.setString(2, message.getContent());
					ps.setNull(3, Types.VARCHAR);
				}
				case USER -> {
					ps.setNull(2, Types.VARCHAR);
					ps.setString(3, message.getContent());
				}
				default -> throw new IllegalArgumentException("Can't add type " + message);
			}
		}

		@Override
		public int getBatchSize() {
			return this.messages.size();
		}
	}

	private static class MessageRowMapper implements RowMapper<Message> {

		@Override
		public Message mapRow(ResultSet rs, int i) throws SQLException {
			var assistant = rs.getString(1);

			if (assistant != null) {
				return new AssistantMessage(assistant);
			}

			var user = rs.getString(2);

			if (user != null) {
				return new UserMessage(user);
			}

			return null;
		}

	}

}
