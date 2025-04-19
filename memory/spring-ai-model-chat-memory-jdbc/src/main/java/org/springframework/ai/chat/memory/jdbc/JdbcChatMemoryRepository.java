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

package org.springframework.ai.chat.memory.jdbc;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * An implementation of {@link ChatMemoryRepository} for JDBC.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

	private static final String QUERY_ADD = """
			INSERT INTO ai_chat_memory (conversation_id, content, type) VALUES (?, ?, ?)""";

	private static final String QUERY_GET = """
			SELECT content, type FROM ai_chat_memory WHERE conversation_id = ? ORDER BY "timestamp" DESC""";

	private static final String QUERY_CLEAR = "DELETE FROM ai_chat_memory WHERE conversation_id = ?";

	private final JdbcTemplate jdbcTemplate;

	private JdbcChatMemoryRepository(JdbcChatMemoryConfig config) {
		Assert.notNull(config, "config cannot be null");
		this.jdbcTemplate = config.getJdbcTemplate();
	}

	public static JdbcChatMemoryRepository create(JdbcChatMemoryConfig config) {
		return new JdbcChatMemoryRepository(config);
	}

	@Override
	public List<Message> findById(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.jdbcTemplate.query(QUERY_GET, new MessageRowMapper(), conversationId);
	}

	@Override
	public void save(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");
		this.jdbcTemplate.batchUpdate(QUERY_ADD, new AddBatchPreparedStatement(conversationId, messages));
	}

	@Override
	public void deleteById(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.jdbcTemplate.update(QUERY_CLEAR, conversationId);
	}

	private record AddBatchPreparedStatement(String conversationId,
			List<Message> messages) implements BatchPreparedStatementSetter {
		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			var message = this.messages.get(i);

			ps.setString(1, this.conversationId);
			ps.setString(2, message.getText());
			ps.setString(3, message.getMessageType().name());
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
				case TOOL -> null;
			};
		}

	}

}
