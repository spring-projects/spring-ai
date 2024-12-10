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

package org.springframework.ai.vectorstore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlVector;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumn;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Builder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;

/**
 * The CassandraVectorStore is for managing and querying vector data in an Apache
 * Cassandra db. It offers functionalities like adding, deleting, and performing
 * similarity searches on documents.
 *
 * The store utilizes CQL to index and search vector data. It allows for custom metadata
 * fields in the documents to be stored alongside the vector and content data.
 *
 * This class requires a CassandraVectorStoreConfig configuration object for
 * initialization, which includes settings like connection details, index name, column
 * names, etc. It also requires an EmbeddingModel to convert documents into embeddings
 * before storing them.
 *
 * A schema matching the configuration is automatically created if it doesn't exist.
 * Missing columns and indexes in existing tables will also be automatically created.
 * Disable this with the CassandraVectorStoreConfig#disallowSchemaChanges().
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
 * {@link CassandraVectorStoreConfig.Builder#withFixedThreadPoolExecutorSize(int)}
 * accordingly to improve performance so embeddings are created and the documents are
 * added concurrently. The default concurrency is 16
 * ({@link CassandraVectorStoreConfig#DEFAULT_ADD_CONCURRENCY}). Remote transformers
 * probably want higher concurrency, and local transformers may need lower concurrency.
 * This concurrency limit does not need to be higher than the max parallel calls made to
 * the {@link #add(List<Document>)} method multiplied by the list size. This setting can
 * also serve as a protecting throttle against your embedding model.
 *
 * @author Mick Semb Wever
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 * @see VectorStore
 * @see org.springframework.ai.vectorstore.CassandraVectorStoreConfig
 * @see EmbeddingModel
 * @since 1.0.0
 */
public class CassandraVectorStore extends AbstractObservationVectorStore implements AutoCloseable {

	public static final String DRIVER_PROFILE_UPDATES = "spring-ai-updates";

	public static final String DRIVER_PROFILE_SEARCH = "spring-ai-search";

	private static final String QUERY_FORMAT = "select %s,%s,%s%s from %s.%s ? order by %s ann of ? limit ?";

	private static final Logger logger = LoggerFactory.getLogger(CassandraVectorStore.class);

	private static Map<Similarity, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(Similarity.COSINE,
			VectorStoreSimilarityMetric.COSINE, Similarity.EUCLIDEAN, VectorStoreSimilarityMetric.EUCLIDEAN,
			Similarity.DOT_PRODUCT, VectorStoreSimilarityMetric.DOT);

	private final CassandraVectorStoreConfig conf;

	private final EmbeddingModel embeddingModel;

	private final FilterExpressionConverter filterExpressionConverter;

	private final ConcurrentMap<Set<String>, PreparedStatement> addStmts = new ConcurrentHashMap<>();

	private final PreparedStatement deleteStmt;

	private final String similarityStmt;

	private final Similarity similarity;

	private final BatchingStrategy batchingStrategy;

	public CassandraVectorStore(CassandraVectorStoreConfig conf, EmbeddingModel embeddingModel) {
		this(conf, embeddingModel, ObservationRegistry.NOOP, null, new TokenCountBatchingStrategy());
	}

	public CassandraVectorStore(CassandraVectorStoreConfig conf, EmbeddingModel embeddingModel,
			ObservationRegistry observationRegistry, VectorStoreObservationConvention customObservationConvention,
			BatchingStrategy batchingStrategy) {

		super(observationRegistry, customObservationConvention);

		Preconditions.checkArgument(null != conf, "Config must not be null");
		Preconditions.checkArgument(null != embeddingModel, "Embedding model must not be null");

		this.conf = conf;
		this.embeddingModel = embeddingModel;
		conf.ensureSchemaExists(embeddingModel.dimensions());
		prepareAddStatement(Set.of());
		this.deleteStmt = prepareDeleteStatement();

		TableMetadata cassandraMetadata = conf.session.getMetadata()
			.getKeyspace(conf.schema.keyspace())
			.get()
			.getTable(conf.schema.table())
			.get();

		this.similarity = getIndexSimilarity(cassandraMetadata);
		this.similarityStmt = similaritySearchStatement();

		this.filterExpressionConverter = new CassandraFilterExpressionConverter(
				cassandraMetadata.getColumns().values());
		this.batchingStrategy = batchingStrategy;
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

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		int i = 0;
		for (Document d : documents) {
			futures[i++] = CompletableFuture.runAsync(() -> {
				List<Object> primaryKeyValues = this.conf.documentIdTranslator.apply(d.getId());

				BoundStatementBuilder builder = prepareAddStatement(d.getMetadata().keySet()).boundStatementBuilder();
				for (int k = 0; k < primaryKeyValues.size(); ++k) {
					SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
					builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
				}

				builder = builder.setString(this.conf.schema.content(), d.getContent())
					.setVector(this.conf.schema.embedding(),
							CqlVector.newInstance(EmbeddingUtils.toList(embeddings.get(documents.indexOf(d)))),
							Float.class);

				for (var metadataColumn : this.conf.schema.metadataColumns()
					.stream()
					.filter(mc -> d.getMetadata().containsKey(mc.name()))
					.toList()) {

					builder = builder.set(metadataColumn.name(), d.getMetadata().get(metadataColumn.name()),
							metadataColumn.javaType());
				}
				BoundStatement s = builder.build().setExecutionProfileName(DRIVER_PROFILE_UPDATES);
				this.conf.session.execute(s);
			}, this.conf.executor);
		}
		CompletableFuture.allOf(futures).join();
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		CompletableFuture[] futures = new CompletableFuture[idList.size()];
		int i = 0;
		for (String id : idList) {
			List<Object> primaryKeyValues = this.conf.documentIdTranslator.apply(id);
			BoundStatement s = this.deleteStmt.bind(primaryKeyValues.toArray());
			futures[i++] = this.conf.session.executeAsync(s).toCompletableFuture();
		}
		CompletableFuture.allOf(futures).join();
		return Optional.of(Boolean.TRUE);
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Preconditions.checkArgument(request.getTopK() <= 1000);
		var embedding = toFloatArray(this.embeddingModel.embed(request.getQuery()));
		CqlVector<Float> cqlVector = CqlVector.newInstance(embedding);

		String whereClause = "";
		if (request.hasFilterExpression()) {
			String expression = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
			if (!expression.isBlank()) {
				whereClause = String.format("where %s", expression);
			}
		}

		String query = String.format(this.similarityStmt, cqlVector, whereClause, cqlVector, request.getTopK());
		List<Document> documents = new ArrayList<>();
		logger.trace("Executing {}", query);
		SimpleStatement s = SimpleStatement.newInstance(query).setExecutionProfileName(DRIVER_PROFILE_SEARCH);

		for (Row row : this.conf.session.execute(s)) {
			float score = row.getFloat(0);
			if (score < request.getSimilarityThreshold()) {
				break;
			}
			Map<String, Object> docFields = new HashMap<>();
			docFields.put(DocumentMetadata.DISTANCE.value(), 1 - score);
			for (var metadata : this.conf.schema.metadataColumns()) {
				var value = row.get(metadata.name(), metadata.javaType());
				if (null != value) {
					docFields.put(metadata.name(), value);
				}
			}
			Document doc = Document.builder()
				.id(getDocumentId(row))
				.text(row.getString(this.conf.schema.content()))
				.metadata(docFields)
				.score((double) score)
				.build();

			documents.add(doc);
		}
		return documents;
	}

	@Override
	public void close() throws Exception {
		this.conf.close();
	}

	void checkSchemaValid() {
		this.conf.checkSchemaValid(this.embeddingModel.dimensions());
	}

	private Similarity getIndexSimilarity(TableMetadata metadata) {

		return Similarity.valueOf(metadata.getIndex(this.conf.schema.index())
			.get()
			.getOptions()
			.getOrDefault("similarity_function", "COSINE")
			.toUpperCase());
	}

	private PreparedStatement prepareDeleteStatement() {
		Delete stmt = null;
		DeleteSelection stmtStart = QueryBuilder.deleteFrom(this.conf.schema.keyspace(), this.conf.schema.table());

		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (var c : this.conf.schema.clusteringKeys()) {
			stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}

		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareAddStatement(Set<String> metadataFields) {

		// metadata fields that are not configured as metadata columns are not added
		Set<String> fieldsThatAreColumns = new HashSet<>(this.conf.schema.metadataColumns()
			.stream()
			.map(mc -> mc.name())
			.filter(mc -> metadataFields.contains(mc))
			.toList());

		return this.addStmts.computeIfAbsent(fieldsThatAreColumns, fields -> {

			RegularInsert stmt = null;
			InsertInto stmtStart = QueryBuilder.insertInto(this.conf.schema.keyspace(), this.conf.schema.table());

			for (var c : this.conf.schema.partitionKeys()) {
				stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
			}
			for (var c : this.conf.schema.clusteringKeys()) {
				stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
			}

			stmt = stmt.value(this.conf.schema.content(), QueryBuilder.bindMarker(this.conf.schema.content()))
				.value(this.conf.schema.embedding(), QueryBuilder.bindMarker(this.conf.schema.embedding()));

			for (String metadataField : fields) {
				stmt = stmt.value(metadataField, QueryBuilder.bindMarker(metadataField));
			}
			return this.conf.session.prepare(stmt.build());
		});
	}

	private String similaritySearchStatement() {
		StringBuilder ids = new StringBuilder();
		for (var m : this.conf.schema.partitionKeys()) {
			ids.append(m.name()).append(',');
		}
		for (var m : this.conf.schema.clusteringKeys()) {
			ids.append(m.name()).append(',');
		}
		ids.deleteCharAt(ids.length() - 1);

		String similarityFunction = new StringBuilder("similarity_").append(this.similarity.toString().toLowerCase())
			.append('(')
			.append(this.conf.schema.embedding())
			.append(",?)")
			.toString();

		StringBuilder extraSelectFields = new StringBuilder();
		for (var m : this.conf.schema.metadataColumns()) {
			extraSelectFields.append(',').append(m.name());
		}
		if (this.conf.returnEmbeddings) {
			extraSelectFields.append(',').append(this.conf.schema.embedding());
		}

		// java-driver-query-builder doesn't support orderByAnnOf yet
		String query = String.format(QUERY_FORMAT, similarityFunction, ids.toString(), this.conf.schema.content(),
				extraSelectFields.toString(), this.conf.schema.keyspace(), this.conf.schema.table(),
				this.conf.schema.embedding());

		query = query.replace("?", "%s");
		logger.debug("preparing {}", query);
		return query;
	}

	private String getDocumentId(Row row) {
		List<Object> primaryKeyValues = new ArrayList<>();
		for (var m : this.conf.schema.partitionKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		for (var m : this.conf.schema.clusteringKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		return this.conf.primaryKeyTranslator.apply(primaryKeyValues);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.CASSANDRA.value(), operationName)
			.withCollectionName(this.conf.schema.table())
			.withDimensions(this.embeddingModel.dimensions())
			.withNamespace(this.conf.schema.keyspace())
			.withSimilarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.similarity)) {
			return this.similarity.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.similarity).value();
	}

	/**
	 * Indexes are automatically created with COSINE. This can be changed manually via
	 * cqlsh
	 */
	public enum Similarity {

		COSINE, DOT_PRODUCT, EUCLIDEAN

	}

}
