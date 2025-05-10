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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for Apache Cassandra.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public class CassandraChatMemoryRepository implements ChatMemoryRepository {

	public static final String CONVERSATION_TS = CassandraChatMemoryRepository.class.getSimpleName()
			+ "_message_timestamp";

	final CassandraChatMemoryRepositoryConfig conf;

	private final PreparedStatement allStmt;

	private final PreparedStatement addUserStmt;

	private final PreparedStatement addAssistantStmt;

	private final PreparedStatement getStmt;

	private final PreparedStatement deleteStmt;

	private CassandraChatMemoryRepository(CassandraChatMemoryRepositoryConfig conf) {
		Assert.notNull(conf, "conf cannot be null");
		this.conf = conf;
		this.conf.ensureSchemaExists();
		this.allStmt = prepareAllStatement();
		this.addUserStmt = prepareAddStmt(this.conf.userColumn);
		this.addAssistantStmt = prepareAddStmt(this.conf.assistantColumn);
		this.getStmt = prepareGetStatement();
		this.deleteStmt = prepareDeleteStmt();
	}

	public static CassandraChatMemoryRepository create(CassandraChatMemoryRepositoryConfig conf) {
		return new CassandraChatMemoryRepository(conf);
	}

	@Override
	public List<String> findConversationIds() {
		List<String> conversationIds = new ArrayList<>();
		long token = Long.MIN_VALUE;
		boolean emptyQuery = false;

		while (!emptyQuery && token < Long.MAX_VALUE) {
			BoundStatement stmt = this.allStmt.boundStatementBuilder().setLong("after_token", token).build();
			emptyQuery = true;
			for (Row r : this.conf.session.execute(stmt)) {
				emptyQuery = false;
				conversationIds.add(r.getString(CassandraChatMemoryRepositoryConfig.DEFAULT_SESSION_ID_NAME));
				token = r.getLong("t");
			}
		}
		return List.copyOf(conversationIds);
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(conversationId);
		BoundStatementBuilder builder = this.getStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			CassandraChatMemoryRepositoryConfig.SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
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
		Collections.reverse(messages);
		return messages;
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		final AtomicLong instantSeq = new AtomicLong(Instant.now().toEpochMilli());
		messages.forEach(msg -> {
			if (msg.getMetadata().containsKey(CONVERSATION_TS)) {
				msg.getMetadata().put(CONVERSATION_TS, Instant.ofEpochMilli(instantSeq.getAndIncrement()));
			}
			save(conversationId, msg);
		});
	}

	void save(String conversationId, Message msg) {

		Preconditions.checkArgument(
				!msg.getMetadata().containsKey(CONVERSATION_TS)
						|| msg.getMetadata().get(CONVERSATION_TS) instanceof Instant,
				"messages only accept metadata '%s' entries of type Instant", CONVERSATION_TS);

		msg.getMetadata().putIfAbsent(CONVERSATION_TS, Instant.now());

		PreparedStatement stmt = getStatement(msg);

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(conversationId);
		BoundStatementBuilder builder = stmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			CassandraChatMemoryRepositoryConfig.SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		Instant instant = (Instant) msg.getMetadata().get(CONVERSATION_TS);

		builder = builder.setInstant(CassandraChatMemoryRepositoryConfig.DEFAULT_EXCHANGE_ID_NAME, instant)
			.setString("message", msg.getText());

		this.conf.session.execute(builder.build());
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(conversationId);
		BoundStatementBuilder builder = this.deleteStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			CassandraChatMemoryRepositoryConfig.SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		this.conf.session.execute(builder.build());
	}

	private PreparedStatement prepareAddStmt(String column) {
		RegularInsert stmt = null;
		InsertInto stmtStart = QueryBuilder.insertInto(this.conf.schema.keyspace(), this.conf.schema.table());
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		for (var c : this.conf.schema.clusteringKeys()) {
			stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		stmt = stmt.value(column, QueryBuilder.bindMarker("message"));
		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareAllStatement() {
		Select stmt = QueryBuilder.selectFrom(this.conf.schema.keyspace(), this.conf.schema.table())
			.distinct()
			.raw(String.format("token(%s)", CassandraChatMemoryRepositoryConfig.DEFAULT_SESSION_ID_NAME))
			.as("t")
			.column(CassandraChatMemoryRepositoryConfig.DEFAULT_SESSION_ID_NAME)
			.whereToken(CassandraChatMemoryRepositoryConfig.DEFAULT_SESSION_ID_NAME)
			.isGreaterThan(QueryBuilder.bindMarker("after_token"))
			.limit(10000);

		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareGetStatement() {
		Select stmt = QueryBuilder.selectFrom(this.conf.schema.keyspace(), this.conf.schema.table()).all();
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (int i = 0; i + 1 < this.conf.schema.clusteringKeys().size(); ++i) {
			String columnName = this.conf.schema.clusteringKeys().get(i).name();
			stmt = stmt.whereColumn(columnName).isEqualTo(QueryBuilder.bindMarker(columnName));
		}
		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareDeleteStmt() {
		Delete stmt = null;
		DeleteSelection stmtStart = QueryBuilder.deleteFrom(this.conf.schema.keyspace(), this.conf.schema.table());
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (int i = 0; i + 1 < this.conf.schema.clusteringKeys().size(); ++i) {
			String columnName = this.conf.schema.clusteringKeys().get(i).name();
			stmt = stmt.whereColumn(columnName).isEqualTo(QueryBuilder.bindMarker(columnName));
		}
		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement getStatement(Message msg) {
		return switch (msg.getMessageType()) {
			case USER -> this.addUserStmt;
			case ASSISTANT -> this.addAssistantStmt;
			default -> throw new IllegalArgumentException("Cant add type " + msg);
		};
	}

}
