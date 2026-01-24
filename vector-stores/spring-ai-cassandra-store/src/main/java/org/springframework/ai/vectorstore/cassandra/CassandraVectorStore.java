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

package org.springframework.ai.vectorstore.cassandra;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlVector;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.IndexMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumn;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumnEnd;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.select.Selector;
import com.datastax.oss.driver.shaded.guava.common.annotations.VisibleForTesting;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;

/**
 * The CassandraVectorStore is for managing and querying vector data in an Apache
 * Cassandra db. It offers functionalities like adding, deleting, and performing
 * similarity searches on documents.
 *
 * The store utilizes CQL to index and search vector data. It allows for custom metadata
 * fields in the documents to be stored alongside the vector and content data.
 *
 * This class requires a CassandraVectorStore#CassandraBuilder configuration object for
 * initialization, which includes settings like connection details, index name, column
 * names, etc. It also requires an EmbeddingModel to convert documents into embeddings
 * before storing them.
 *
 * A schema matching the configuration is automatically created if it doesn't exist.
 * Missing columns and indexes in existing tables will also be automatically created.
 * Disable this with the CassandraBuilder#initializeSchema(boolean) method().
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * CassandraVectorStore vectorStore = CassandraVectorStore.builder(embeddingModel)
 *     .session(cqlSession)
 *     .keyspace("my_keyspace")
 *     .table("my_vectors")
 *     .build();
 *
 * // Add documents
 * vectorStore.add(List.of(
 *     new Document("1", "content1", Map.of("key1", "value1")),
 *     new Document("2", "content2", Map.of("key2", "value2"))
 * ));
 *
 * // Search with filters
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.query("search text")
 *         .withTopK(5)
 *         .withSimilarityThreshold(0.7)
 *         .withFilterExpression("metadata.key1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * CassandraVectorStore vectorStore = CassandraVectorStore.builder(embeddingModel)
 *     .session(cqlSession)
 *     .keyspace("my_keyspace")
 *     .table("my_vectors")
 *     .partitionKeys(List.of(new SchemaColumn("id", DataTypes.TEXT)))
 *     .clusteringKeys(List.of(new SchemaColumn("timestamp", DataTypes.TIMESTAMP)))
 *     .addMetadataColumns(
 *         new SchemaColumn("category", DataTypes.TEXT, SchemaColumnTags.INDEXED),
 *         new SchemaColumn("score", DataTypes.DOUBLE)
 *     )
 *     .contentColumnName("text")
 *     .embeddingColumnName("vector")
 *     .fixedThreadPoolExecutorSize(32)
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .build();
 * }</pre>
 *
 * This class is designed to work with brand new tables that it creates for you, or on top
 * of existing Cassandra tables. The latter is appropriate when wanting to keep data in
 * place, creating embeddings next to it, and performing vector similarity searches
 * in-situ.
 *
 * Instances of this class are not dynamic against server-side schema changes. If you
 * change the schema server-side you need a new CassandraVectorStore instance.
 *
 * When adding documents with the method {@link #add(List<Document>)} it first calls
 * embeddingModel to create the embeddings. This is slow. Configure
 * {@link Builder#fixedThreadPoolExecutorSize(int)} accordingly to improve performance so
 * embeddings are created and the documents are added concurrently. The default
 * concurrency is 16 ({@link Builder#DEFAULT_ADD_CONCURRENCY}). Remote transformers
 * probably want higher concurrency, and local transformers may need lower concurrency.
 * This concurrency limit does not need to be higher than the max parallel calls made to
 * the {@link #add(List<Document>)} method multiplied by the list size. This setting can
 * also serve as a protecting throttle against your embedding model.
 *
 * @author Mick Semb Wever
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author chabinhwang
 * @see VectorStore
 * @see EmbeddingModel
 * @since 1.0.0
 */
public class CassandraVectorStore extends AbstractObservationVectorStore implements AutoCloseable {

	public static final String DEFAULT_KEYSPACE_NAME = "springframework";

	public static final String DEFAULT_TABLE_NAME = "ai_vector_store";

	public static final String DEFAULT_ID_NAME = "id";

	public static final String DEFAULT_INDEX_SUFFIX = "idx";

	public static final String DEFAULT_CONTENT_COLUMN_NAME = "content";

	public static final String DEFAULT_EMBEDDING_COLUMN_NAME = "embedding";

	public static final int DEFAULT_ADD_CONCURRENCY = 16;

	public static final String DRIVER_PROFILE_UPDATES = "spring-ai-updates";

	public static final String DRIVER_PROFILE_SEARCH = "spring-ai-search";

	private static final Logger logger = LoggerFactory.getLogger(CassandraVectorStore.class);

	private static final Map<Similarity, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			Similarity.COSINE, VectorStoreSimilarityMetric.COSINE, Similarity.EUCLIDEAN,
			VectorStoreSimilarityMetric.EUCLIDEAN, Similarity.DOT_PRODUCT, VectorStoreSimilarityMetric.DOT);

	private final CqlSession session;

	private final Schema schema;

	private final boolean initializeSchema;

	private final FilterExpressionConverter filterExpressionConverter;

	private final DocumentIdTranslator documentIdTranslator;

	private final PrimaryKeyTranslator primaryKeyTranslator;

	private final Executor executor;

	private final boolean closeSessionOnClose;

	private final ConcurrentMap<Set<String>, PreparedStatement> addStmts = new ConcurrentHashMap<>();

	private final PreparedStatement deleteStmt;

	private final Similarity similarity;

	protected CassandraVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.session, "Session must not be null");

		this.session = builder.session;
		this.schema = builder.buildSchema();
		this.initializeSchema = builder.initializeSchema;
		this.documentIdTranslator = builder.documentIdTranslator;
		this.primaryKeyTranslator = builder.primaryKeyTranslator;
		this.executor = Executors.newFixedThreadPool(builder.fixedThreadPoolExecutorSize);
		this.closeSessionOnClose = builder.closeSessionOnClose;

		ensureSchemaExists(this.embeddingModel.dimensions());
		prepareAddStatement(Set.of());
		this.deleteStmt = prepareDeleteStatement();

		TableMetadata cassandraMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace())
			.get()
			.getTable(this.schema.table())
			.get();

		this.similarity = getIndexSimilarity(cassandraMetadata);

		this.filterExpressionConverter = builder.filterExpressionConverter != null ? builder.filterExpressionConverter
				: new CassandraFilterExpressionConverter(cassandraMetadata.getColumns().values());
	}

	public static Builder builder(EmbeddingModel embeddingModel) {
		return new Builder(embeddingModel);
	}

	private static Float[] toFloatArray(float[] embedding) {
		Float[] embeddingFloat = new Float[embedding.length];
		int i = 0;
		for (Float d : embedding) {
			embeddingFloat[i++] = d.floatValue();
		}
		return embeddingFloat;
	}

	@Override
	public void doAdd(List<Document> documents) {
		var futures = new CompletableFuture[documents.size()];

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		for (int i = 0; i < documents.size(); i++) {
			final int index = i;
			Document d = documents.get(i);
			futures[i] = CompletableFuture.runAsync(() -> {
				List<Object> primaryKeyValues = this.documentIdTranslator.apply(d.getId());

				BoundStatementBuilder builder = prepareAddStatement(d.getMetadata().keySet()).boundStatementBuilder();
				for (int k = 0; k < primaryKeyValues.size(); ++k) {
					SchemaColumn keyColumn = this.getPrimaryKeyColumn(k);
					builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
				}

				builder = builder.setString(this.schema.content(), d.getText())
					.setVector(this.schema.embedding(),
							CqlVector.newInstance(EmbeddingUtils.toList(embeddings.get(index))), Float.class);

				for (var metadataColumn : this.schema.metadataColumns()
					.stream()
					.filter(mc -> d.getMetadata().containsKey(mc.name()))
					.toList()) {

					builder = builder.set(metadataColumn.name(), d.getMetadata().get(metadataColumn.name()),
							metadataColumn.javaType());
				}
				BoundStatement s = builder.build().setExecutionProfileName(DRIVER_PROFILE_UPDATES);
				this.session.execute(s);
			}, this.executor);
		}
		CompletableFuture.allOf(futures).join();
	}

	@Override
	public void doDelete(List<String> idList) {
		CompletableFuture[] futures = new CompletableFuture[idList.size()];
		int i = 0;
		for (String id : idList) {
			List<Object> primaryKeyValues = this.documentIdTranslator.apply(id);
			BoundStatement s = this.deleteStmt.bind(primaryKeyValues.toArray());
			futures[i++] = this.session.executeAsync(s).toCompletableFuture();
		}
		CompletableFuture.allOf(futures).join();
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			// TODO - Investigate why we can't do a direct filter based delete in
			// Cassandra
			// This SO thread seems to indicate that this is not possible in Cassandra
			// https://stackoverflow.com/questions/70953262/unable-to-delete-multiple-rows-getting-some-partition-key-parts-are-missing-i
			// Needs more research into this matter.
			SearchRequest searchRequest = SearchRequest.builder()
				.query("") // empty query since we only want filter matches
				.filterExpression(filterExpression)
				.topK(1000) // large enough to get all matches
				.similarityThresholdAll()
				.build();

			List<Document> matchingDocs = similaritySearch(searchRequest);

			if (!matchingDocs.isEmpty()) {
				// Then delete those documents by ID
				List<String> idsToDelete = matchingDocs.stream().map(Document::getId).collect(Collectors.toList());
				delete(idsToDelete);
				logger.debug("Deleted {} documents matching filter expression", idsToDelete.size());
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter", e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Preconditions.checkArgument(request.getTopK() <= 1000);
		var embedding = toFloatArray(this.embeddingModel.embed(request.getQuery()));
		CqlVector<Float> cqlVector = CqlVector.newInstance(embedding);
		String cql = createSimilaritySearchCql(request, cqlVector, request.getTopK());

		List<Document> documents = new ArrayList<>();
		ResultSet result = this.session
			.execute(SimpleStatement.newInstance(cql).setExecutionProfileName(DRIVER_PROFILE_SEARCH));

		for (Row row : result) {
			float score = row.getFloat(0);
			if (score < request.getSimilarityThreshold()) {
				break;
			}
			Map<String, Object> docFields = new HashMap<>();
			docFields.put(DocumentMetadata.DISTANCE.value(), 1 - score);
			for (var metadata : this.schema.metadataColumns()) {
				var value = row.get(metadata.name(), metadata.javaType());
				if (null != value) {
					docFields.put(metadata.name(), value);
				}
			}
			Document doc = Document.builder()
				.id(getDocumentId(row))
				.text(row.getString(this.schema.content()))
				.metadata(docFields)
				.score((double) score)
				.build();

			documents.add(doc);
		}
		return documents;
	}

	void checkSchemaValid() {
		this.checkSchemaValid(this.embeddingModel.dimensions());
	}

	private Similarity getIndexSimilarity(TableMetadata metadata) {

		Optional<IndexMetadata> indexMetadata = metadata.getIndex(this.schema.index());

		if (indexMetadata.isEmpty()) {
			throw new IllegalStateException(
					String.format("Index %s does not exist in table %s", this.schema.index(), this.schema.table));
		}

		return Similarity
			.valueOf(indexMetadata.get().getOptions().getOrDefault("similarity_function", "COSINE").toUpperCase());

	}

	private PreparedStatement prepareDeleteStatement() {
		Delete stmt = null;
		DeleteSelection stmtStart = QueryBuilder.deleteFrom(this.schema.keyspace(), this.schema.table());

		for (var c : this.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		Assert.state(stmt != null, "stmt should not be null by now");
		for (var c : this.schema.clusteringKeys()) {
			stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}

		return this.session.prepare(stmt.build());
	}

	private PreparedStatement prepareAddStatement(Set<String> metadataFields) {

		// metadata fields that are not configured as metadata columns are not added
		Set<String> fieldsThatAreColumns = new HashSet<>(this.schema.metadataColumns()
			.stream()
			.map(mc -> mc.name())
			.filter(mc -> metadataFields.contains(mc))
			.toList());

		return this.addStmts.computeIfAbsent(fieldsThatAreColumns, fields -> {

			RegularInsert stmt = null;
			InsertInto stmtStart = QueryBuilder.insertInto(this.schema.keyspace(), this.schema.table());

			for (var c : this.schema.partitionKeys()) {
				stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
			}
			Assert.state(stmt != null, "stmt should not be null by now");
			for (var c : this.schema.clusteringKeys()) {
				stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
			}

			stmt = stmt.value(this.schema.content(), QueryBuilder.bindMarker(this.schema.content()))
				.value(this.schema.embedding(), QueryBuilder.bindMarker(this.schema.embedding()));

			for (String metadataField : fields) {
				stmt = stmt.value(metadataField, QueryBuilder.bindMarker(metadataField));
			}
			return this.session.prepare(stmt.build());
		});
	}

	private String createSimilaritySearchCql(SearchRequest request, CqlVector<Float> cqlVector, int topK) {

		Select stmt = QueryBuilder.selectFrom(this.schema.keyspace(), this.schema.table())
			.function("similarity_" + this.similarity.toString().toLowerCase(),
					Selector.column(this.schema.embedding()), QueryBuilder.literal(cqlVector));

		for (var c : this.schema.partitionKeys()) {
			stmt = stmt.column(c.name());
		}
		for (var c : this.schema.clusteringKeys()) {
			stmt = stmt.column(c.name());
		}
		stmt = stmt.column(this.schema.content());
		for (var m : this.schema.metadataColumns()) {
			stmt = stmt.column(m.name());
		}
		stmt = stmt.column(this.schema.embedding());

		// the filterExpression is a string so we go back to building a CQL string
		String whereClause = "";
		if (request.hasFilterExpression()) {
			Assert.state(request.getFilterExpression() != null, "filter expression assumed to be non-null");
			String expression = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
			if (!expression.isBlank()) {
				whereClause = String.format(" WHERE %s", expression);
			}
		}
		String cql = stmt.orderByAnnOf(this.schema.embedding(), cqlVector).limit(topK).asCql();
		return cql.replace(" ORDER ", whereClause + " ORDER ");
	}

	private String getDocumentId(Row row) {
		List<Object> primaryKeyValues = new ArrayList<>();
		for (var m : this.schema.partitionKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		for (var m : this.schema.clusteringKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		return this.primaryKeyTranslator.apply(primaryKeyValues);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.CASSANDRA.value(), operationName)
			.collectionName(this.schema.table())
			.dimensions(this.embeddingModel.dimensions())
			.namespace(this.schema.keyspace())
			.similarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.similarity)) {
			return this.similarity.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.similarity).value();
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
	static void dropKeyspace(Builder builder) {
		Preconditions.checkState(builder.keyspace.startsWith("test_"), "Only test keyspaces can be dropped");
		Assert.state(builder.session != null, "builder.session should not be null");
		builder.session.execute(SchemaBuilder.dropKeyspace(builder.keyspace).ifExists().build());
	}

	void ensureSchemaExists(int vectorDimension) {
		if (this.initializeSchema) {
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
			.isPresent(), "table %s does not exist", this.schema.table);

		TableMetadata tableMetadata = this.session.getMetadata()
			.getKeyspace(this.schema.keyspace)
			.get()
			.getTable(this.schema.table)
			.get();

		Preconditions.checkState(tableMetadata.getIndex(this.schema.index()).isPresent(), "index %s does not exist",
				this.schema.index());

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
			Assert.state(createTable != null, "createTable should be non-null by now");
			for (SchemaColumn clusteringKey : this.schema.clusteringKeys) {
				createTable = createTable.withClusteringColumn(clusteringKey.name, clusteringKey.type);
			}

			createTable = createTable.withColumn(this.schema.content, DataTypes.TEXT)
				.withColumn(this.schema.embedding, DataTypes.vectorOf(DataTypes.FLOAT, vectorDimension));

			for (SchemaColumn metadata : this.schema.metadataColumns) {
				createTable = createTable.withColumn(metadata.name(), metadata.type());
			}

			logger.debug("Executing {}", createTable.asCql());
			this.session.execute(createTable.build());
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
				alterTable = alterTable.addColumn(this.schema.embedding,
						DataTypes.vectorOf(DataTypes.FLOAT, vectorDimension));
			}
			SimpleStatement stmt = ((AlterTableAddColumnEnd) alterTable).build();
			logger.debug("Executing {}", stmt.getQuery());
			this.session.execute(stmt);
		}
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.session;
		return Optional.of(client);
	}

	/**
	 * Indexes are automatically created with COSINE. This can be changed manually via
	 * cqlsh
	 */
	public enum Similarity {

		COSINE, DOT_PRODUCT, EUCLIDEAN

	}

	public enum SchemaColumnTags {

		INDEXED

	}

	/**
	 * Given a string document id, return the value for each primary key column.
	 *
	 * It is a requirement that an empty {@code List<Object>} returns an example formatted
	 * id
	 */
	public interface DocumentIdTranslator extends Function<String, List<Object>> {

	}

	/** Given a list of primary key column values, return the document id. */
	public interface PrimaryKeyTranslator extends Function<List<Object>, String> {

	}

	record Schema(String keyspace, String table, List<SchemaColumn> partitionKeys, List<SchemaColumn> clusteringKeys,
			String content, String embedding, String index, Set<SchemaColumn> metadataColumns) {

	}

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

	/**
	 * Builder for the Cassandra vector store.
	 *
	 * All metadata columns configured to the store will be fetched and added to all
	 * queried documents.
	 *
	 * To filter expression search against a metadata column configure it with
	 * SchemaColumnTags.INDEXED
	 *
	 * The Cassandra Java Driver is configured via the application.conf resource found in
	 * the classpath. See
	 * https://github.com/apache/cassandra-java-driver/tree/4.x/manual/core/configuration
	 *
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private @Nullable CqlSession session;

		private @Nullable CqlSessionBuilder sessionBuilder;

		private boolean closeSessionOnClose;

		private String keyspace = DEFAULT_KEYSPACE_NAME;

		private String table = DEFAULT_TABLE_NAME;

		private List<SchemaColumn> partitionKeys = List.of(new SchemaColumn(DEFAULT_ID_NAME, DataTypes.TEXT));

		private List<SchemaColumn> clusteringKeys = List.of();

		private @Nullable String indexName;

		private String contentColumnName = DEFAULT_CONTENT_COLUMN_NAME;

		private String embeddingColumnName = DEFAULT_EMBEDDING_COLUMN_NAME;

		private final Set<SchemaColumn> metadataColumns = new HashSet<>();

		private boolean initializeSchema = true;

		private int fixedThreadPoolExecutorSize = DEFAULT_ADD_CONCURRENCY;

		private @Nullable FilterExpressionConverter filterExpressionConverter;

		private DocumentIdTranslator documentIdTranslator = (String id) -> List.of(id);

		private PrimaryKeyTranslator primaryKeyTranslator = (List<Object> primaryKeyColumns) -> {
			if (primaryKeyColumns.isEmpty()) {
				return "test";
			}
			Preconditions.checkArgument(1 == primaryKeyColumns.size());
			return (String) primaryKeyColumns.get(0);
		};

		private Builder(EmbeddingModel embeddingModel) {
			super(embeddingModel);
		}

		/**
		 * Sets the CQL session.
		 * @param session the CQL session to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if session is null
		 */
		public Builder session(CqlSession session) {
			Assert.notNull(session, "Session must not be null");
			this.session = session;
			return this;
		}

		/**
		 * Executor to use when adding documents. The hotspot is the call to the
		 * embeddingModel. For remote transformers you probably want a higher value to
		 * utilize network. For local transformers you probably want a lower value to
		 * avoid saturation.
		 **/
		public Builder fixedThreadPoolExecutorSize(int threads) {
			Preconditions.checkArgument(0 < threads);
			this.fixedThreadPoolExecutorSize = threads;
			return this;
		}

		/**
		 * Sets the keyspace name.
		 * @param keyspace the keyspace name
		 * @return the builder instance
		 * @throws IllegalArgumentException if keyspace is null or empty
		 */
		public Builder keyspace(String keyspace) {
			Assert.hasText(keyspace, "Keyspace must not be null or empty");
			this.keyspace = keyspace;
			return this;
		}

		/**
		 * Adds a contact point to the session builder.
		 * @param contactPoint the contact point to add
		 * @return the builder instance
		 * @throws IllegalStateException if session is already set
		 */
		public Builder contactPoint(InetSocketAddress contactPoint) {
			Assert.state(this.session == null, "Cannot call addContactPoint(..) when session is already set");
			if (this.sessionBuilder == null) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.addContactPoint(contactPoint);
			return this;
		}

		/**
		 * Sets the local datacenter for the session builder.
		 * @param localDatacenter the local datacenter name
		 * @return the builder instance
		 * @throws IllegalStateException if session is already set
		 */
		public Builder localDatacenter(String localDatacenter) {
			Assert.state(this.session == null, "Cannot call withLocalDatacenter(..) when session is already set");
			if (this.sessionBuilder == null) {
				this.sessionBuilder = new CqlSessionBuilder();
			}
			this.sessionBuilder.withLocalDatacenter(localDatacenter);
			return this;
		}

		/**
		 * Sets the table name.
		 * @param table the table name
		 * @return the builder instance
		 * @throws IllegalArgumentException if table is null or empty
		 */
		public Builder table(String table) {
			Assert.hasText(table, "Table must not be null or empty");
			this.table = table;
			return this;
		}

		/**
		 * Sets the partition keys.
		 * @param partitionKeys the partition keys
		 * @return the builder instance
		 * @throws IllegalArgumentException if partitionKeys is null or empty
		 */
		public Builder partitionKeys(List<SchemaColumn> partitionKeys) {
			Assert.notEmpty(partitionKeys, "Partition keys must not be null or empty");
			this.partitionKeys = partitionKeys;
			return this;
		}

		/**
		 * Sets the clustering keys.
		 * @param clusteringKeys the clustering keys
		 * @return the builder instance
		 */
		public Builder clusteringKeys(List<SchemaColumn> clusteringKeys) {
			this.clusteringKeys = clusteringKeys != null ? clusteringKeys : List.of();
			return this;
		}

		/**
		 * Sets the index name.
		 * @param indexName the index name
		 * @return the builder instance
		 */
		public Builder indexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the filter expression converter.
		 * @param converter the filter expression converter to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if converter is null
		 */
		public Builder filterExpressionConverter(FilterExpressionConverter converter) {
			Assert.notNull(converter, "FilterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Sets the document ID translator.
		 * @param translator the document ID translator to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if translator is null
		 */
		public Builder documentIdTranslator(DocumentIdTranslator translator) {
			Assert.notNull(translator, "DocumentIdTranslator must not be null");
			this.documentIdTranslator = translator;
			return this;
		}

		public Builder contentColumnName(String contentColumnName) {
			this.contentColumnName = contentColumnName;
			return this;
		}

		public Builder embeddingColumnName(String embeddingColumnName) {
			this.embeddingColumnName = embeddingColumnName;
			return this;
		}

		public Builder addMetadataColumns(SchemaColumn... columns) {
			Builder builder = this;
			for (SchemaColumn f : columns) {
				builder = builder.addMetadataColumn(f);
			}
			return builder;
		}

		public Builder addMetadataColumns(List<SchemaColumn> columns) {
			Builder builder = this;
			this.metadataColumns.addAll(columns);
			return builder;
		}

		public Builder addMetadataColumn(SchemaColumn column) {

			Preconditions.checkArgument(this.metadataColumns.stream().noneMatch(sc -> sc.name().equals(column.name())),
					"A metadata column with name %s has already been added", column.name());

			this.metadataColumns.add(column);
			return this;
		}

		/**
		 * Sets the primary key translator.
		 * @param translator the primary key translator to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if translator is null
		 */
		public Builder primaryKeyTranslator(PrimaryKeyTranslator translator) {
			Assert.notNull(translator, "PrimaryKeyTranslator must not be null");
			this.primaryKeyTranslator = translator;
			return this;
		}

		Schema buildSchema() {
			if (this.indexName == null) {
				this.indexName = String.format("%s_%s_%s", this.table, this.embeddingColumnName, DEFAULT_INDEX_SUFFIX);
			}

			validateSchema();

			return new Schema(this.keyspace, this.table, this.partitionKeys, this.clusteringKeys,
					this.contentColumnName, this.embeddingColumnName, this.indexName, this.metadataColumns);
		}

		private void validateSchema() {
			for (SchemaColumn metadata : this.metadataColumns) {
				Assert.isTrue(!this.partitionKeys.stream().anyMatch(c -> c.name().equals(metadata.name())),
						"metadataColumn " + metadata.name() + " cannot have same name as a partition key");

				Assert.isTrue(!this.clusteringKeys.stream().anyMatch(c -> c.name().equals(metadata.name())),
						"metadataColumn " + metadata.name() + " cannot have same name as a clustering key");

				Assert.isTrue(!metadata.name().equals(this.contentColumnName),
						"metadataColumn " + metadata.name() + " cannot have same name as content column name");

				Assert.isTrue(!metadata.name().equals(this.embeddingColumnName),
						"metadataColumn " + metadata.name() + " cannot have same name as embedding column name");
			}

			int primaryKeyColumnsCount = this.partitionKeys.size() + this.clusteringKeys.size();
			String exampleId = this.primaryKeyTranslator.apply(Collections.emptyList());
			List<Object> testIdTranslation = this.documentIdTranslator.apply(exampleId);

			Assert.isTrue(testIdTranslation.size() == primaryKeyColumnsCount,
					"documentIdTranslator results length " + testIdTranslation.size()
							+ " doesn't match number of primary key columns " + primaryKeyColumnsCount);

			Assert.isTrue(exampleId.equals(this.primaryKeyTranslator.apply(this.documentIdTranslator.apply(exampleId))),
					"primaryKeyTranslator is not an inverse function to documentIdTranslator");
		}

		@Override
		public CassandraVectorStore build() {
			if (this.session == null && this.sessionBuilder != null) {
				this.session = this.sessionBuilder.build();
				this.closeSessionOnClose = true;
			}
			Assert.notNull(this.session, "Either session must be set directly or configured via sessionBuilder");
			return new CassandraVectorStore(this);
		}

	}

}
