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
package org.springframework.ai.vectorstore;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.CqlVector;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import static java.lang.String.format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumn;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;

/**
 * The CassandraVectorStore is for managing and querying vector data in an Apache
 * Cassandra db. It offers functionalities like adding, deleting, and performing
 * similarity searches on documents.
 *
 * The store utilizes CQL to index and search vector data. It allows for custom metadata
 * fields in the documents to be stored alongside the vector and content data.
 *
 * This class requires a CassandraVectorStoreConfig configuration object for
 * initialization, which includes settings like connection details, index name, field
 * names, etc. It also requires an EmbeddingClient to convert documents into embeddings
 * before storing them.
 *
 * A schema matching the configuration is automatically created if it doesn't exist.
 * Missing columns and indexes in existing tables will also be automatically created.
 * Disable this with the disallowSchemaCreation.
 *
 * This class is designed to work with brand new tables that it creates for you, or on top
 * of existing Cassandra tables. The latter is appropriate when wanting to keep data in
 * place, creating embeddings next to it, and performing vector similarity searches
 * in-situ.
 *
 * Instances of this class are not dynamic against server-side schema changes. If you
 * change the schema server-side you need a new CassandraVectorStore instance.
 *
 * @author Mick Semb Wever
 * @see VectorStore
 * @see CassandraVectorStoreConfig
 * @see EmbeddingClient
 */
public final class CassandraVectorStore implements VectorStore, InitializingBean, AutoCloseable {

	/**
	 * Indexes are automatically created with COSINE. This can be changed manually via
	 * cqlsh
	 */
	public enum Similarity {

		COSINE, DOT_PRODUCT, EUCLIDEAN;

	}

	private static final String QUERY_FORMAT = "select %s,%s,%s,%s from %s.%s ? order by %s ann of ? limit ?";

	public static final String SIMILARITY_FIELD_NAME = "similarity_score";

	private static final Logger logger = LoggerFactory.getLogger(CassandraVectorStore.class);

	private final CassandraVectorStoreConfig conf;

	private final EmbeddingClient embeddingClient;

	private final FilterExpressionConverter filterExpressionConverter;

	private final Map<Set<String>, PreparedStatement> addStmts = new HashMap<>();

	private final PreparedStatement deleteStmt;

	private final String similarityStmt;

	private final Similarity similarity;

	public CassandraVectorStore(CassandraVectorStoreConfig conf, EmbeddingClient embeddingClient) {

		Preconditions.checkArgument(null != conf, "Config must not be null");
		Preconditions.checkArgument(null != embeddingClient, "Embedding client must not be null");

		this.conf = conf;
		this.embeddingClient = embeddingClient;
		conf.ensureSchemaExists(embeddingClient.dimensions());
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
	}

	@Override
	public void add(List<Document> documents) {
		CompletableFuture[] futures = new CompletableFuture[documents.size()];
		short i = 0;
		for (Document d : documents) {
			List<Object> primaryKeyValues = conf.documentIdTranslator.apply(d.getId());
			var embedding = embeddingClient.embed(d).stream().map(Double::floatValue).toList();

			BoundStatementBuilder builder = prepareAddStatement(d.getMetadata().keySet()).boundStatementBuilder();
			for (int k = 0; k < primaryKeyValues.size(); ++k) {
				SchemaColumn keyColumn = conf.getPrimaryKeyColumn(k);
				builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
			}

			builder = builder.setString(conf.schema.content(), d.getContent())
				.setVector(conf.schema.embedding(), CqlVector.newInstance(embedding), Float.class);

			for (var docField : d.getMetadata().entrySet()) {

				var metadata = conf.schema.metadataFields()
					.stream()
					.filter((m) -> m.name().equals(docField.getKey()))
					.findFirst()
					.get();

				builder = builder.set(docField.getKey(), docField.getValue(), metadata.javaType());
			}
			futures[i++] = conf.session.executeAsync(builder.build()).toCompletableFuture();
		}
		CompletableFuture.allOf(futures).join();
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		CompletableFuture[] futures = new CompletableFuture[idList.size()];
		short i = 0;
		for (String id : idList) {
			List<Object> primaryKeyValues = conf.documentIdTranslator.apply(id);
			BoundStatement s = deleteStmt.bind(primaryKeyValues.toArray());
			futures[i++] = conf.session.executeAsync(s).toCompletableFuture();
		}
		CompletableFuture.allOf(futures).join();
		return Optional.of(Boolean.TRUE);
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		Preconditions.checkArgument(request.getTopK() <= 1000);
		var embedding = embeddingClient.embed(request.getQuery()).stream().map(Double::floatValue).toList();
		CqlVector<Float> cqlVector = CqlVector.newInstance(embedding);

		String whereClause = "";
		if (request.hasFilterExpression()) {
			String expression = filterExpressionConverter.convertExpression(request.getFilterExpression());
			if (!expression.isBlank()) {
				whereClause = format("where %s", expression);
			}
		}

		String query = format(similarityStmt, cqlVector, whereClause, cqlVector, request.getTopK());
		List<Document> documents = new ArrayList<>();
		logger.trace("Executing {}", query);

		for (Row row : conf.session.execute(query)) {
			float score = row.getFloat(0);
			if (score < request.getSimilarityThreshold()) {
				break;
			}
			Map<String, Object> docFields = new HashMap<>();
			docFields.put(SIMILARITY_FIELD_NAME, score);
			for (var metadata : conf.schema.metadataFields()) {
				var value = row.get(metadata.name(), metadata.javaType());
				if (null != value) {
					docFields.put(metadata.name(), value);
				}
			}

			documents.add(new Document(getDocumentId(row), row.getString(conf.schema.content()), docFields));
		}
		return documents;
	}

	@Override
	public void afterPropertiesSet() {
	}

	@Override
	public void close() throws Exception {
		conf.close();
	}

	void checkSchemaValid() {
		conf.checkSchemaValid(embeddingClient.dimensions());
	}

	private Similarity getIndexSimilarity(TableMetadata metadata) {

		return Similarity.valueOf(metadata.getIndex(conf.schema.index())
			.get()
			.getOptions()
			.getOrDefault("similarity_function", "COSINE")
			.toUpperCase());
	}

	private PreparedStatement prepareDeleteStatement() {
		Delete stmt = null;
		DeleteSelection stmtStart = QueryBuilder.deleteFrom(conf.schema.keyspace(), conf.schema.table());

		for (var c : conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (var c : conf.schema.clusteringKeys()) {
			stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}

		return conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareAddStatement(Set<String> metadataFields) {
		if (!addStmts.containsKey(metadataFields)) {

			RegularInsert stmt = null;
			InsertInto stmtStart = QueryBuilder.insertInto(conf.schema.keyspace(), conf.schema.table());

			for (var c : conf.schema.partitionKeys()) {
				stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
			}
			for (var c : conf.schema.clusteringKeys()) {
				stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
			}

			stmt = stmt.value(conf.schema.content(), QueryBuilder.bindMarker(conf.schema.content()))
				.value(conf.schema.embedding(), QueryBuilder.bindMarker(conf.schema.embedding()));

			for (String metadataField : metadataFields) {
				stmt = stmt.value(metadataField, QueryBuilder.bindMarker(metadataField));
			}
			addStmts.putIfAbsent(metadataFields, conf.session.prepare(stmt.build()));
		}
		return addStmts.get(metadataFields);
	}

	private String similaritySearchStatement() {
		StringBuilder ids = new StringBuilder();
		for (var m : conf.schema.partitionKeys()) {
			ids.append(m.name()).append(',');
		}
		for (var m : conf.schema.clusteringKeys()) {
			ids.append(m.name()).append(',');
		}
		ids.deleteCharAt(ids.length() - 1);

		String similarityFunction = new StringBuilder("similarity_").append(similarity.toString().toLowerCase())
			.append('(')
			.append(conf.schema.embedding())
			.append(",?)")
			.toString();

		StringBuilder extraSelectFields = new StringBuilder();
		for (var m : conf.schema.metadataFields()) {
			extraSelectFields.append(m.name()).append(',');
		}
		if (0 < extraSelectFields.length()) {
			extraSelectFields.deleteCharAt(extraSelectFields.length() - 1);
		}

		// java-driver-query-builder doesn't support orderByAnnOf yet
		String query = format(QUERY_FORMAT, similarityFunction, ids.toString(), conf.schema.content(),
				extraSelectFields.toString(), conf.schema.keyspace(), conf.schema.table(), conf.schema.embedding());

		query = query.replace("?", "%s");
		logger.debug("preparing {}", query);
		return query;
	}

	private String getDocumentId(Row row) {
		List<Object> primaryKeyValues = new ArrayList<>();
		for (var m : conf.schema.partitionKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		for (var m : conf.schema.clusteringKeys()) {
			primaryKeyValues.add(row.get(m.name(), m.javaType()));
		}
		return conf.primaryKeyTranslator.apply(primaryKeyValues);
	}

}
