/*
 * Copyright 2023-2024 the original author or authors.
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

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableWithOptions;
import com.datastax.oss.driver.shaded.guava.common.annotations.VisibleForTesting;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Configuration for the Cassandra Chat Memory store.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public final class CassandraChatMemoryRepositoryConfig {

	public static final String DEFAULT_KEYSPACE_NAME = "springframework";

	public static final String DEFAULT_TABLE_NAME = "ai_chat_memory";

	// todo – make configurable
	public static final String DEFAULT_SESSION_ID_NAME = "session_id";

	// todo – make configurable
	public static final String DEFAULT_EXCHANGE_ID_NAME = "message_timestamp";

	public static final String DEFAULT_MESSAGES_COLUMN_NAME = "messages";

	private static final Logger logger = LoggerFactory.getLogger(CassandraChatMemoryRepositoryConfig.class);

	final CqlSession session;

	final Schema schema;

	final String messageUDT = "ai_chat_message";

	final String messagesColumn;

	// todo – make configurable
	final String messageUdtTimestampColumn = "msg_timestamp";

	// todo – make configurable
	final String messageUdtTypeColumn = "msg_type";

	// todo – make configurable
	final String messageUdtContentColumn = "msg_content";

	final SessionIdToPrimaryKeysTranslator primaryKeyTranslator;

	private final @Nullable Integer timeToLiveSeconds;

	private final boolean disallowSchemaChanges;

	private CassandraChatMemoryRepositoryConfig(Builder builder) {
		Assert.state(builder.session != null, "session is required");
		this.session = builder.session;
		this.schema = new Schema(builder.keyspace, builder.table, builder.partitionKeys, builder.clusteringKeys);
		this.messagesColumn = builder.messagesColumn;
		this.timeToLiveSeconds = builder.timeToLiveSeconds;
		this.disallowSchemaChanges = builder.disallowSchemaChanges;
		this.primaryKeyTranslator = builder.primaryKeyTranslator;
	}

	public static Builder builder() {
		return new Builder();
	}

	SchemaColumn getPrimaryKeyColumn(int index) {
		return index < this.schema.partitionKeys().size() ? this.schema.partitionKeys().get(index)
				: this.schema.clusteringKeys().get(index - this.schema.partitionKeys().size());
	}

	@VisibleForTesting
	void dropKeyspace() {
		Preconditions.checkState(this.schema.keyspace.startsWith("test_"), "Only test keyspaces can be dropped");
		this.session.execute(SchemaBuilder.dropKeyspace(this.schema.keyspace).ifExists().build());
	}

	void ensureSchemaExists() {
		if (!this.disallowSchemaChanges) {
			SchemaUtil.ensureKeyspaceExists(this.session, this.schema.keyspace);
			ensureMessageTypeExist();
			ensureTableExists();
			ensureTableColumnsExist();
			SchemaUtil.checkSchemaAgreement(this.session);
		}
		else {
			checkSchemaValid();
		}
	}

	void checkSchemaValid() {

		Preconditions.checkState(this.session.getMetadata().getKeyspace(this.schema.keyspace).isPresent(),
				"keyspace %s does not exist", this.schema.keyspace);

		Preconditions.checkState(this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.isPresent(), "table %s does not exist");

		Preconditions.checkState(this.session.getMetadata()
			.getKeyspace(this.schema.keyspace())
			.get()
			.getUserDefinedType(this.messageUDT)
			.isPresent(), "table %s does not exist");

		UserDefinedType udt = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace())
			.get()
			.getUserDefinedType(this.messageUDT)
			.get();

		Preconditions.checkState(udt.contains(this.messageUdtTimestampColumn), "field %s does not exist",
				this.messageUdtTimestampColumn);

		Preconditions.checkState(udt.contains(this.messageUdtTypeColumn), "field %s does not exist",
				this.messageUdtTypeColumn);

		Preconditions.checkState(udt.contains(this.messageUdtContentColumn), "field %s does not exist",
				this.messageUdtContentColumn);

		TableMetadata tableMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.get();

		Preconditions.checkState(tableMetadata.getColumn(this.messagesColumn).isPresent(), "column %s does not exist",
				this.messagesColumn);
	}

	private void ensureTableExists() {
		if (this.session.getMetadata().getKeyspace(this.schema.keyspace).get().getTable(this.schema.table).isEmpty()) {
			CreateTable createTable = null;

			CreateTableStart createTableStart = SchemaBuilder.createTable(this.schema.keyspace, this.schema.table)
				.ifNotExists();

			for (SchemaColumn partitionKey : this.schema.partitionKeys) {
				createTable = (null != createTable ? createTable : createTableStart).withPartitionKey(partitionKey.name,
						partitionKey.type);
			}
			Assert.state(createTable != null, "createTable should not be null");
			for (SchemaColumn clusteringKey : this.schema.clusteringKeys) {
				createTable = createTable.withClusteringColumn(clusteringKey.name, clusteringKey.type);
			}

			String lastClusteringColumn = this.schema.clusteringKeys.get(this.schema.clusteringKeys.size() - 1).name();

			CreateTableWithOptions createTableWithOptions = createTable
				.withColumn(this.messagesColumn, DataTypes.frozenListOf(SchemaBuilder.udt(this.messageUDT, true)))
				.withClusteringOrder(lastClusteringColumn, ClusteringOrder.DESC)
				// TODO replace w/ SchemaBuilder.unifiedCompactionStrategy() when
				// available
				.withOption("compaction", Map.of("class", "UnifiedCompactionStrategy"));

			if (null != this.timeToLiveSeconds) {
				createTableWithOptions = createTableWithOptions.withDefaultTimeToLiveSeconds(this.timeToLiveSeconds);
			}
			this.session.execute(createTableWithOptions.build());
		}
	}

	private void ensureMessageTypeExist() {

		SimpleStatement stmt = SchemaBuilder.createType(this.messageUDT)
			.ifNotExists()
			.withField(this.messageUdtTimestampColumn, DataTypes.TIMESTAMP)
			.withField(this.messageUdtTypeColumn, DataTypes.TEXT)
			.withField(this.messageUdtContentColumn, DataTypes.TEXT)
			.build();

		this.session.execute(stmt.setKeyspace(this.schema.keyspace));
	}

	private void ensureTableColumnsExist() {

		TableMetadata tableMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace())
			.get()
			.getTable(this.schema.table())
			.get();

		if (tableMetadata.getColumn(this.messagesColumn).isEmpty()) {

			SimpleStatement stmt = SchemaBuilder.alterTable(this.schema.keyspace(), this.schema.table())
				.addColumn(this.messagesColumn, DataTypes.frozenListOf(SchemaBuilder.udt(this.messageUDT, true)))
				.build();

			logger.debug("Executing {}", stmt.getQuery());
			this.session.execute(stmt);
		}
	}

	/** Given a string sessionId, return the value for each primary key column. */
	public interface SessionIdToPrimaryKeysTranslator extends Function<String, List<Object>> {

	}

	record Schema(String keyspace, String table, List<SchemaColumn> partitionKeys, List<SchemaColumn> clusteringKeys) {

	}

	public record SchemaColumn(String name, DataType type) {

		public GenericType<Object> javaType() {
			return CodecRegistry.DEFAULT.codecFor(this.type).getJavaType();
		}

	}

	public static final class Builder {

		private @Nullable CqlSession session = null;

		private @Nullable CqlSessionBuilder sessionBuilder = null;

		private String keyspace = DEFAULT_KEYSPACE_NAME;

		private String table = DEFAULT_TABLE_NAME;

		private List<SchemaColumn> partitionKeys = List.of(new SchemaColumn(DEFAULT_SESSION_ID_NAME, DataTypes.TEXT));

		private List<SchemaColumn> clusteringKeys = List
			.of(new SchemaColumn(DEFAULT_EXCHANGE_ID_NAME, DataTypes.TIMESTAMP));

		private String messagesColumn = DEFAULT_MESSAGES_COLUMN_NAME;

		private @Nullable Integer timeToLiveSeconds = null;

		private boolean disallowSchemaChanges = false;

		private SessionIdToPrimaryKeysTranslator primaryKeyTranslator = List::of;

		private Builder() {
		}

		public Builder withCqlSession(CqlSession session) {
			Preconditions.checkState(null == this.sessionBuilder,
					"Cannot call withContactPoint(..) or withLocalDatacenter(..) and this method");

			this.session = session;
			return this;
		}

		public Builder addContactPoint(InetSocketAddress contactPoint) {
			Preconditions.checkState(null == this.session, "Cannot call withCqlSession(..) and this method");
			if (null == this.sessionBuilder) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.addContactPoint(contactPoint);
			return this;
		}

		public Builder withLocalDatacenter(String localDC) {
			Preconditions.checkState(null == this.session, "Cannot call withCqlSession(..) and this method");
			if (null == this.sessionBuilder) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.withLocalDatacenter(localDC);
			return this;
		}

		public Builder withKeyspaceName(String keyspace) {
			this.keyspace = keyspace;
			return this;
		}

		public Builder withTableName(String table) {
			this.table = table;
			return this;
		}

		public Builder withPartitionKeys(List<SchemaColumn> partitionKeys) {
			Preconditions.checkArgument(!partitionKeys.isEmpty());
			this.partitionKeys = partitionKeys;
			return this;
		}

		public Builder withClusteringKeys(List<SchemaColumn> clusteringKeys) {
			Preconditions.checkArgument(!clusteringKeys.isEmpty());
			this.clusteringKeys = clusteringKeys;
			return this;
		}

		public Builder withMessagesColumnName(String name) {
			this.messagesColumn = name;
			return this;
		}

		/** How long are messages kept for */
		public Builder withTimeToLive(Duration timeToLive) {
			Preconditions.checkArgument(0 < timeToLive.getSeconds());
			this.timeToLiveSeconds = (int) timeToLive.toSeconds();
			return this;
		}

		public Builder disallowSchemaChanges() {
			this.disallowSchemaChanges = true;
			return this;
		}

		public Builder withChatExchangeToPrimaryKeyTranslator(SessionIdToPrimaryKeysTranslator primaryKeyTranslator) {
			this.primaryKeyTranslator = primaryKeyTranslator;
			return this;
		}

		public CassandraChatMemoryRepositoryConfig build() {

			int primaryKeyColumns = this.partitionKeys.size() + this.clusteringKeys.size();
			int primaryKeysToBind = this.primaryKeyTranslator.apply(UUID.randomUUID().toString()).size();

			Preconditions.checkArgument(primaryKeyColumns == primaryKeysToBind + 1,
					"The primaryKeyTranslator must always return one less element than the number of primary keys in total. The last clustering key remains undefined, expecting to be the timestamp for messages within sessionId. The sessionId can map to any primary key column (though it should map to a partition key column).");

			Preconditions.checkArgument(
					this.clusteringKeys.get(this.clusteringKeys.size() - 1).name().equals(DEFAULT_EXCHANGE_ID_NAME),
					"last clustering key must be the exchangeIdColumn");

			return new CassandraChatMemoryRepositoryConfig(this);
		}

	}

}
