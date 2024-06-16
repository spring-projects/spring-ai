/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.memory;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;

import org.springframework.ai.chat.memory.CassandraChatMemoryConfig.SchemaColumn;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public final class CassandraChatMemory implements ChatMemory {

	public static final String CONVERSATION_TS = CassandraChatMemory.class.getSimpleName() + "_message_timestamp";

	private final CassandraChatMemoryConfig conf;

	public static CassandraChatMemory create(CassandraChatMemoryConfig conf) {
		return new CassandraChatMemory(conf);
	}

	public CassandraChatMemory(CassandraChatMemoryConfig config) {
		this.conf = config;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		messages.forEach((message) -> add(conversationId, message));
	}

	@Override
	public void add(String sessionId, Message message) {

		Preconditions.checkArgument(
				message.getMetadata().containsKey(CONVERSATION_TS)
						&& message.getMetadata().get(CONVERSATION_TS) instanceof Instant,
				"CassandraChatMemory implementation requires Message's to have a '%s' entry in their metadata, of type Instant, representing the timestamp of the message.",
				CONVERSATION_TS);

		PreparedStatement stmt;
		switch (message.getMessageType()) {
			case USER -> stmt = conf.addUserStmt;
			case ASSISTANT -> stmt = conf.addAssistantStmt;
			default -> throw new IllegalArgumentException("Cant add type " + message);
		}

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = stmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		Instant instant = (Instant) message.getMetadata().get(CONVERSATION_TS);

		builder = builder.setInstant(CassandraChatMemoryConfig.DEFAULT_EXCHANGE_ID_NAME, instant)
			.setString("message", message.getContent());

		this.conf.session.execute(builder.build());
	}

	@Override
	public void clear(String sessionId) {

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = conf.deleteStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		this.conf.session.execute(builder.build());
	}

	@Override
	public List<Message> get(String sessionId, int lastN) {

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = conf.getStmt.boundStatementBuilder().setInt("lastN", lastN);

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		List<Message> messages = new ArrayList<>();
		for (Row r : this.conf.session.execute(builder.build())) {
			String assistant = r.getString(this.conf.assistantColumn);
			String user = r.getString(this.conf.userColumn);
			if (null != assistant) {
				messages.add(new AssistantMessage(assistant));
			}
			if (null != user) {
				messages.add(new UserMessage(user));
			}
		}
		return messages;
	}

}
