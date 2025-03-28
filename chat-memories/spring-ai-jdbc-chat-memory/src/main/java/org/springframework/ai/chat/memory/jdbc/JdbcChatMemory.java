/*
 * Copyright 2024-2025 the original author or authors.
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * An implementation of {@link ChatMemory} for JDBC. Creating an instance of
 * JdbcChatMemory example:
 * <code>JdbcChatMemory.create(JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build());</code>
 *
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
public class JdbcChatMemory implements ChatMemory {

	private static final String QUERY_ADD = """
			INSERT INTO ai_chat_memory (conversation_id, content, type) VALUES (?, ?, ?)""";

	private static final String QUERY_GET = """
			SELECT content, type FROM ai_chat_memory WHERE conversation_id = ? ORDER BY "timestamp" DESC LIMIT ?""";

	private static final String QUERY_CLEAR = "DELETE FROM ai_chat_memory WHERE conversation_id = ?";

	private final JdbcTemplate jdbcTemplate;

	public JdbcChatMemory(JdbcChatMemoryConfig config) {
		this.jdbcTemplate = config.getJdbcTemplate();
	}

	public static JdbcChatMemory create(JdbcChatMemoryConfig config) {
		return new JdbcChatMemory(config);
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		this.jdbcTemplate.batchUpdate(QUERY_ADD, new AddBatchPreparedStatement(conversationId, messages));
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		return this.jdbcTemplate.query(QUERY_GET, new MessageRowMapper(), conversationId, lastN);
	}

	@Override
	public void clear(String conversationId) {
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
		public Message mapRow(ResultSet rs, int i) throws SQLException {
			var content = rs.getString(1);
			var type = MessageType.valueOf(rs.getString(2));

			return switch (type) {
				case USER -> new UserMessage(content);
				case ASSISTANT -> new AssistantMessage(content);
				case SYSTEM -> new SystemMessage(content);
				default -> null;
			};
		}

	}

}
