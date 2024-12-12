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

package org.springframework.ai.vectorstore.cassandra;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.api.querybuilder.BuildableQuery;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumn;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumnEnd;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.datastax.oss.driver.shaded.guava.common.annotations.VisibleForTesting;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.cassandra.SchemaUtil;
import org.springframework.lang.Nullable;

/**
 * Configuration for the Cassandra vector store.
 *
 * All metadata columns configured to the store will be fetched and added to all queried
 * documents.
 *
 * To filter expression search against a metadata column configure it with
 * SchemaColumnTags.INDEXED
 *
 * The Cassandra Java Driver is configured via the application.conf resource found in the
 * classpath. See
 * https://github.com/apache/cassandra-java-driver/tree/4.x/manual/core/configuration
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 * @deprecated since 1.0.0-M5, use {@link CassandraVectorStore#builder()} instead
 */
@Deprecated(since = "1.0.0-M5", forRemoval = true)
public final class CassandraVectorStoreConfig implements AutoCloseable {

	public static final String DEFAULT_KEYSPACE_NAME = "springframework";

	public static final String DEFAULT_TABLE_NAME = "ai_vector_store";

	public static final String DEFAULT_ID_NAME = "id";

	public static final String DEFAULT_INDEX_SUFFIX = "idx";

	public static final String DEFAULT_CONTENT_COLUMN_NAME = "content";

	public static final String DEFAULT_EMBEDDING_COLUMN_NAME = "embedding";

	public static final int DEFAULT_ADD_CONCURRENCY = 16;

	private static final Logger logger = LoggerFactory.getLogger(CassandraVectorStoreConfig.class);

	final CqlSession session;

	final Schema schema;

	final boolean disallowSchemaChanges;

	// TODO: Remove this flag as the document no longer holds embeddings.
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	final boolean returnEmbeddings;

	final DocumentIdTranslator documentIdTranslator;

	final PrimaryKeyTranslator primaryKeyTranslator;

	final Executor executor;

	private final boolean closeSessionOnClose;

	private CassandraVectorStoreConfig(Builder builder) {
		this.session = null != builder.session ? builder.session : builder.sessionBuilder.build();
		this.closeSessionOnClose = null == builder.session;

		this.schema = new Schema(builder.keyspace, builder.table, builder.partitionKeys, builder.clusteringKeys,
				builder.contentColumnName, builder.embeddingColumnName, builder.indexName, builder.metadataColumns);

		this.disallowSchemaChanges = builder.disallowSchemaChanges;
		this.returnEmbeddings = builder.returnEmbeddings;
		this.documentIdTranslator = builder.documentIdTranslator;
		this.primaryKeyTranslator = builder.primaryKeyTranslator;
		this.executor = Executors.newFixedThreadPool(builder.fixedThreadPoolExecutorSize);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public void close() throws Exception {
		if (this.closeSessionOnClose) {
			this.session.close();
		}
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

	void ensureSchemaExists(int vectorDimension) {
		if (!this.disallowSchemaChanges) {
			SchemaUtil.ensureKeyspaceExists(this.session, this.schema.keyspace);
			ensureTableExists(vectorDimension);
			ensureTableColumnsExist(vectorDimension);
			ensureIndexesExists();
			SchemaUtil.checkSchemaAgreement(this.session);
		}
		else {
			checkSchemaValid(vectorDimension);
		}
	}

	void checkSchemaValid(int vectorDimension) {

		Preconditions.checkState(this.session.getMetadata().getKeyspace(this.schema.keyspace).isPresent(),
				"keyspace %s does not exist", this.schema.keyspace);

		Preconditions.checkState(this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.isPresent(), "table %s does not exist");

		TableMetadata tableMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.get();

		Preconditions.checkState(tableMetadata.getColumn(this.schema.content).isPresent(), "column %s does not exist",
				this.schema.content);

		Preconditions.checkState(tableMetadata.getColumn(this.schema.embedding).isPresent(), "column %s does not exist",
				this.schema.embedding);

		for (SchemaColumn m : this.schema.metadataColumns) {
			Optional<ColumnMetadata> column = tableMetadata.getColumn(m.name());
			Preconditions.checkState(column.isPresent(), "column %s does not exist", m.name());

			Preconditions.checkArgument(column.get().getType().equals(m.type()),
					"Mismatching type on metadata column %s of %s vs %s", m.name(), column.get().getType(), m.type());

			if (m.indexed()) {
				Preconditions.checkState(
						tableMetadata.getIndexes().values().stream().anyMatch(i -> i.getTarget().equals(m.name())),
						"index %s does not exist", m.name());
			}
		}

	}

	private void ensureIndexesExists() {

		SimpleStatement indexStmt = SchemaBuilder.createIndex(this.schema.index)
			.ifNotExists()
			.custom("StorageAttachedIndex")
			.onTable(this.schema.keyspace, this.schema.table)
			.andColumn(this.schema.embedding)
			.build();

		logger.debug("Executing {}", indexStmt.getQuery());
		this.session.execute(indexStmt);

		Stream
			.concat(this.schema.partitionKeys.stream(),
					Stream.concat(this.schema.clusteringKeys.stream(), this.schema.metadataColumns.stream()))
			.filter(cs -> cs.indexed())
			.forEach(metadata -> {

				SimpleStatement indexStatement = SchemaBuilder.createIndex(String.format("%s_idx", metadata.name()))
					.ifNotExists()
					.custom("StorageAttachedIndex")
					.onTable(this.schema.keyspace, this.schema.table)
					.andColumn(metadata.name())
					.build();

				logger.debug("Executing {}", indexStatement.getQuery());
				this.session.execute(indexStatement);
			});
	}

	private void ensureTableExists(int vectorDimension) {
		if (this.session.getMetadata().getKeyspace(this.schema.keyspace).get().getTable(this.schema.table).isEmpty()) {

			CreateTable createTable = null;

			CreateTableStart createTableStart = SchemaBuilder.createTable(this.schema.keyspace, this.schema.table)
				.ifNotExists();

			for (SchemaColumn partitionKey : this.schema.partitionKeys) {
				createTable = (null != createTable ? createTable : createTableStart).withPartitionKey(partitionKey.name,
						partitionKey.type);
			}
			for (SchemaColumn clusteringKey : this.schema.clusteringKeys) {
				createTable = createTable.withClusteringColumn(clusteringKey.name, clusteringKey.type);
			}

			createTable = createTable.withColumn(this.schema.content, DataTypes.TEXT);

			for (SchemaColumn metadata : this.schema.metadataColumns) {
				createTable = createTable.withColumn(metadata.name(), metadata.type());
			}

			// https://datastax-oss.atlassian.net/browse/JAVA-3118
			// .withColumn(config.embedding, new DefaultVectorType(DataTypes.FLOAT,
			// vectorDimension));

			StringBuilder tableStmt = new StringBuilder(createTable.asCql());
			tableStmt.setLength(tableStmt.length() - 1);
			tableStmt.append(',')
				.append(this.schema.embedding)
				.append(" vector<float,")
				.append(vectorDimension)
				.append(">)");
			logger.debug("Executing {}", tableStmt.toString());
			this.session.execute(tableStmt.toString());
		}
	}

	private void ensureTableColumnsExist(int vectorDimension) {

		TableMetadata tableMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.get();

		Set<SchemaColumn> newColumns = new HashSet<>();
		boolean addContent = tableMetadata.getColumn(this.schema.content).isEmpty();
		boolean addEmbedding = tableMetadata.getColumn(this.schema.embedding).isEmpty();

		for (SchemaColumn metadata : this.schema.metadataColumns) {
			Optional<ColumnMetadata> column = tableMetadata.getColumn(metadata.name());
			if (column.isPresent()) {

				Preconditions.checkArgument(column.get().getType().equals(metadata.type()),
						"Cannot change type on metadata column %s from %s to %s", metadata.name(),
						column.get().getType(), metadata.type());
			}
			else {
				newColumns.add(metadata);
			}
		}

		if (!newColumns.isEmpty() || addContent || addEmbedding) {
			AlterTableAddColumn alterTable = SchemaBuilder.alterTable(this.schema.keyspace, this.schema.table);
			for (SchemaColumn metadata : newColumns) {
				alterTable = alterTable.addColumn(metadata.name(), metadata.type());
			}
			if (addContent) {
				alterTable = alterTable.addColumn(this.schema.content, DataTypes.TEXT);
			}
			if (addEmbedding) {
				// special case for embedding column, bc JAVA-3118, as above
				StringBuilder alterTableStmt = new StringBuilder(((BuildableQuery) alterTable).asCql());
				if (newColumns.isEmpty() && !addContent) {
					alterTableStmt.append(" ADD (");
				}
				else {
					alterTableStmt.setLength(alterTableStmt.length() - 1);
					alterTableStmt.append(',');
				}
				alterTableStmt.append(this.schema.embedding)
					.append(" vector<float,")
					.append(vectorDimension)
					.append(">)");

				logger.debug("Executing {}", alterTableStmt.toString());
				this.session.execute(alterTableStmt.toString());
			}
			else {
				SimpleStatement stmt = ((AlterTableAddColumnEnd) alterTable).build();
				logger.debug("Executing {}", stmt.getQuery());
				this.session.execute(stmt);
			}
		}
	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public enum SchemaColumnTags {

		INDEXED

	}

	/**
	 * Given a string document id, return the value for each primary key column.
	 *
	 * It is a requirement that an empty {@code List<Object>} returns an example formatted
	 * id
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public interface DocumentIdTranslator extends Function<String, List<Object>> {

	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	/** Given a list of primary key column values, return the document id. */
	public interface PrimaryKeyTranslator extends Function<List<Object>, String> {

	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	record Schema(String keyspace, String table, List<SchemaColumn> partitionKeys, List<SchemaColumn> clusteringKeys,
			String content, String embedding, String index, Set<SchemaColumn> metadataColumns) {

	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public record SchemaColumn(String name, DataType type, SchemaColumnTags... tags) {

		public SchemaColumn(String name, DataType type) {
			this(name, type, new SchemaColumnTags[0]);
		}

		public GenericType<Object> javaType() {
			return CodecRegistry.DEFAULT.codecFor(this.type).getJavaType();
		}

		public boolean indexed() {
			for (SchemaColumnTags t : this.tags) {
				if (SchemaColumnTags.INDEXED == t) {
					return true;
				}
			}
			return false;
		}

	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public static final class Builder {

		private CqlSession session = null;

		private CqlSessionBuilder sessionBuilder = null;

		private String keyspace = DEFAULT_KEYSPACE_NAME;

		private String table = DEFAULT_TABLE_NAME;

		private List<SchemaColumn> partitionKeys = List.of(new SchemaColumn(DEFAULT_ID_NAME, DataTypes.TEXT));

		private List<SchemaColumn> clusteringKeys = List.of();

		private String indexName = null;

		private String contentColumnName = DEFAULT_CONTENT_COLUMN_NAME;

		private String embeddingColumnName = DEFAULT_EMBEDDING_COLUMN_NAME;

		private Set<SchemaColumn> metadataColumns = new HashSet<>();

		private boolean disallowSchemaChanges = false;

		private boolean returnEmbeddings = false;

		private int fixedThreadPoolExecutorSize = DEFAULT_ADD_CONCURRENCY;

		private DocumentIdTranslator documentIdTranslator = (String id) -> List.of(id);

		private PrimaryKeyTranslator primaryKeyTranslator = (List<Object> primaryKeyColumns) -> {
			if (primaryKeyColumns.isEmpty()) {
				return "test";
			}
			Preconditions.checkArgument(1 == primaryKeyColumns.size());
			return (String) primaryKeyColumns.get(0);
		};

		private Builder() {
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withCqlSession(CqlSession session) {
			Preconditions.checkState(null == this.sessionBuilder,
					"Cannot call withContactPoint(..) or withLocalDatacenter(..) and this method");

			this.session = session;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder addContactPoint(InetSocketAddress contactPoint) {
			Preconditions.checkState(null == this.session, "Cannot call withCqlSession(..) and this method");
			if (null == this.sessionBuilder) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.addContactPoint(contactPoint);
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withLocalDatacenter(String localDC) {
			Preconditions.checkState(null == this.session, "Cannot call withCqlSession(..) and this method");
			if (null == this.sessionBuilder) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.withLocalDatacenter(localDC);
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withKeyspaceName(String keyspace) {
			this.keyspace = keyspace;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withTableName(String table) {
			this.table = table;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withPartitionKeys(List<SchemaColumn> partitionKeys) {
			Preconditions.checkArgument(!partitionKeys.isEmpty());
			this.partitionKeys = partitionKeys;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withClusteringKeys(List<SchemaColumn> clusteringKeys) {
			this.clusteringKeys = clusteringKeys;
			return this;
		}

		/**
		 * defaults (if null) to '&lt;table_name&gt;_&lt;embedding_column_name&gt;_idx'
		 **/
		@Nullable
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withIndexName(String name) {
			this.indexName = name;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withContentColumnName(String name) {
			this.contentColumnName = name;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withEmbeddingColumnName(String name) {
			this.embeddingColumnName = name;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder addMetadataColumns(SchemaColumn... columns) {
			Builder builder = this;
			for (SchemaColumn f : columns) {
				builder = builder.addMetadataColumn(f);
			}
			return builder;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder addMetadataColumns(List<SchemaColumn> columns) {
			Builder builder = this;
			this.metadataColumns.addAll(columns);
			return builder;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder addMetadataColumn(SchemaColumn column) {

			Preconditions.checkArgument(this.metadataColumns.stream().noneMatch(sc -> sc.name().equals(column.name())),
					"A metadata column with name %s has already been added", column.name());

			this.metadataColumns.add(column);
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder disallowSchemaChanges() {
			this.disallowSchemaChanges = true;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder returnEmbeddings() {
			this.returnEmbeddings = true;
			return this;
		}

		/**
		 * Executor to use when adding documents. The hotspot is the call to the
		 * embeddingModel. For remote transformers you probably want a higher value to
		 * utilize network. For local transformers you probably want a lower value to
		 * avoid saturation.
		 **/
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withFixedThreadPoolExecutorSize(int threads) {
			Preconditions.checkArgument(0 < threads);
			this.fixedThreadPoolExecutorSize = threads;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withDocumentIdTranslator(DocumentIdTranslator documentIdTranslator) {
			this.documentIdTranslator = documentIdTranslator;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public Builder withPrimaryKeyTranslator(PrimaryKeyTranslator primaryKeyTranslator) {
			this.primaryKeyTranslator = primaryKeyTranslator;
			return this;
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public CassandraVectorStoreConfig build() {
			if (null == this.indexName) {
				this.indexName = String.format("%s_%s_%s", this.table, this.embeddingColumnName, DEFAULT_INDEX_SUFFIX);
			}
			for (SchemaColumn metadata : this.metadataColumns) {

				Preconditions.checkArgument(
						!this.partitionKeys.stream().anyMatch(c -> c.name().equals(metadata.name())),
						"metadataColumn %s cannot have same name as a partition key", metadata.name());

				Preconditions.checkArgument(
						!this.clusteringKeys.stream().anyMatch(c -> c.name().equals(metadata.name())),
						"metadataColumn %s cannot have same name as a clustering key", metadata.name());

				Preconditions.checkArgument(!metadata.name().equals(this.contentColumnName),
						"metadataColumn %s cannot have same name as content column name", this.contentColumnName);

				Preconditions.checkArgument(!metadata.name().equals(this.embeddingColumnName),
						"metadataColumn %s cannot have same name as embedding column name", this.embeddingColumnName);

			}

			int primaryKeyColumnsCount = this.partitionKeys.size() + this.clusteringKeys.size();
			String exampleId = this.primaryKeyTranslator.apply(Collections.emptyList());
			List<Object> testIdTranslation = this.documentIdTranslator.apply(exampleId);

			Preconditions.checkArgument(testIdTranslation.size() == primaryKeyColumnsCount,
					"documentIdTranslator results length %s doesn't match number of primary key columns %s",
					String.valueOf(testIdTranslation.size()), String.valueOf(primaryKeyColumnsCount));

			Preconditions.checkArgument(
					exampleId.equals(this.primaryKeyTranslator.apply(this.documentIdTranslator.apply(exampleId))),
					"primaryKeyTranslator is not an inverse function to documentIdTranslator");

			return new CassandraVectorStoreConfig(this);
		}

	}

}
