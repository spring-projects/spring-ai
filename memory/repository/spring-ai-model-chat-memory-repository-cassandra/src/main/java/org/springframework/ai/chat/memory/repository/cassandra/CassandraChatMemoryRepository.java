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

package org.springframework.ai.chat.memory.repository.cassandra;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for Apache Cassandra.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public final class CassandraChatMemoryRepository implements ChatMemoryRepository {

	public static final String CONVERSATION_TS = CassandraChatMemoryRepository.class.getSimpleName()
			+ "_message_timestamp";

	final CassandraChatMemoryRepositoryConfig conf;

	private final PreparedStatement allStmt;

	private final PreparedStatement addStmt;

	private final PreparedStatement getStmt;

	private CassandraChatMemoryRepository(CassandraChatMemoryRepositoryConfig conf) {
		Assert.notNull(conf, "conf cannot be null");
		this.conf = conf;
		this.conf.ensureSchemaExists();
		this.allStmt = prepareAllStatement();
		this.addStmt = prepareAddStmt();
		this.getStmt = prepareGetStatement();
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
		return findByConversationIdWithLimit(conversationId, 1);
	}

	List<Message> findByConversationIdWithLimit(String conversationId, int limit) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(conversationId);
		BoundStatementBuilder builder = this.getStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			CassandraChatMemoryRepositoryConfig.SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}
		builder = builder.setInt("legacy_limit", limit);

		List<Message> messages = new ArrayList<>();
		for (Row r : this.conf.session.execute(builder.build())) {
			for (UdtValue udt : Objects.requireNonNullElse(r.getList(this.conf.messagesColumn, UdtValue.class),
					List.<UdtValue>of())) {
				messages.add(getMessage(udt));
			}
		}
		return messages;
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		Instant instant = Instant.now();
		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(conversationId);
		BoundStatementBuilder builder = this.addStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			CassandraChatMemoryRepositoryConfig.SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		List<UdtValue> msgs = new ArrayList<>();
		for (Message msg : messages) {

			Preconditions.checkArgument(
					!msg.getMetadata().containsKey(CONVERSATION_TS)
							|| msg.getMetadata().get(CONVERSATION_TS) instanceof Instant,
					"messages only accept metadata '%s' entries of type Instant", CONVERSATION_TS);

			msg.getMetadata().putIfAbsent(CONVERSATION_TS, instant);

			UdtValue udt = this.conf.session.getMetadata()
				.getKeyspace(this.conf.schema.keyspace())
				.get()
				.getUserDefinedType(this.conf.messageUDT)
				.get()
				.newValue()
				.setInstant(this.conf.messageUdtTimestampColumn, (Instant) msg.getMetadata().get(CONVERSATION_TS))
				.setString(this.conf.messageUdtTypeColumn, msg.getMessageType().name())
				.setString(this.conf.messageUdtContentColumn, msg.getText());

			msgs.add(udt);
		}
		builder = builder.setInstant(CassandraChatMemoryRepositoryConfig.DEFAULT_EXCHANGE_ID_NAME, instant)
			.setList("msgs", msgs, UdtValue.class);

		this.conf.session.execute(builder.build());
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		saveAll(conversationId, List.of());
	}

	@Override
	public void refresh(String conversationId, List<Message> deletes, List<Message> adds) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(deletes, "deletes cannot be null");
		Assert.notNull(adds, "adds cannot be null");

		// RMW (Read-Modify-Write) is the only way with the current schema.
		// This is not efficient, but it is correct.
		List<Message> currentMessages = new ArrayList<>(this.findByConversationId(conversationId));
		currentMessages.removeAll(deletes);
		currentMessages.addAll(adds);
		this.saveAll(conversationId, currentMessages);
	}

	private PreparedStatement prepareAddStmt() {
		RegularInsert stmt = null;
		InsertInto stmtStart = QueryBuilder.insertInto(this.conf.schema.keyspace(), this.conf.schema.table());
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		Assert.notNull(stmt, "stmt shouldn't be null");
		for (var c : this.conf.schema.clusteringKeys()) {
			stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		stmt = stmt.value(this.conf.messagesColumn, QueryBuilder.bindMarker("msgs"));
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
		stmt = stmt.limit(QueryBuilder.bindMarker("legacy_limit"));
		return this.conf.session.prepare(stmt.build());
	}

	private Message getMessage(UdtValue udt) {
		String content = Objects.requireNonNullElse(udt.getString(this.conf.messageUdtContentColumn), "");
		Map<String, Object> props = Map.of(CONVERSATION_TS, udt.getInstant(this.conf.messageUdtTimestampColumn));
		String type = udt.getString(this.conf.messageUdtTypeColumn);
		Assert.state(type != null, "message type shouldn't be null");
		return switch (MessageType.valueOf(type)) {
			case ASSISTANT -> AssistantMessage.builder().content(content).properties(props).build();
			case USER -> UserMessage.builder().text(content).metadata(props).build();
			case SYSTEM -> SystemMessage.builder().text(content).metadata(props).build();
			case TOOL ->
				// todo – persist ToolResponse somehow
				ToolResponseMessage.builder().responses(List.of()).metadata(props).build();
			default -> throw new IllegalStateException(String.format("unknown message type %s", type));
		};
	}

}
