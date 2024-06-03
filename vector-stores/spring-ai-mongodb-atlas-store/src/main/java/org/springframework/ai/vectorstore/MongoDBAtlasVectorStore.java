/*
 * Copyright 2023 - 2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mongodb.BasicDBObject;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * @author Chris Smith
 * @since 1.0.0
 */
public class MongoDBAtlasVectorStore implements VectorStore, InitializingBean {

	public static final String ID_FIELD_NAME = "_id";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String SCORE_FIELD_NAME = "score";

	private static final String DEFAULT_VECTOR_COLLECTION_NAME = "vector_store";

	private static final String DEFAULT_VECTOR_INDEX_NAME = "vector_index";

	private static final String DEFAULT_PATH_NAME = "embedding";

	private static final int DEFAULT_NUM_CANDIDATES = 200;

	private final MongoTemplate mongoTemplate;

	private final EmbeddingModel embeddingModel;

	private final MongoDBVectorStoreConfig config;

	private final MongoDBAtlasFilterExpressionConverter filterExpressionConverter = new MongoDBAtlasFilterExpressionConverter();

	private final boolean initializeSchema;

	public MongoDBAtlasVectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		this(mongoTemplate, embeddingModel, MongoDBVectorStoreConfig.defaultConfig(), initializeSchema);
	}

	public MongoDBAtlasVectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel,
			MongoDBVectorStoreConfig config, boolean initializeSchema) {
		this.mongoTemplate = mongoTemplate;
		this.embeddingModel = embeddingModel;
		this.config = config;

		this.initializeSchema = initializeSchema;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.initializeSchema) {
			return;
		}

		// Create the collection if it does not exist
		if (!mongoTemplate.collectionExists(this.config.collectionName)) {
			mongoTemplate.createCollection(this.config.collectionName);
		}
		// Create search index, command doesn't do anything if already existing
		mongoTemplate.executeCommand(createSearchIndex());
	}

	/**
	 * Provides the Definition for the search index
	 */
	private org.bson.Document createSearchIndex() {
		List<org.bson.Document> vectorFields = new ArrayList<>();

		vectorFields.add(new org.bson.Document().append("type", "vector")
			.append("path", this.config.pathName)
			.append("numDimensions", this.embeddingModel.dimensions())
			.append("similarity", "cosine"));

		vectorFields.addAll(this.config.metadataFieldsToFilter.stream()
			.map(fieldName -> new org.bson.Document().append("type", "filter").append("path", "metadata." + fieldName))
			.toList());

		return new org.bson.Document().append("createSearchIndexes", this.config.collectionName)
			.append("indexes",
					List.of(new org.bson.Document().append("name", this.config.vectorIndexName)
						.append("type", "vectorSearch")
						.append("definition", new org.bson.Document("fields", vectorFields))));
	}

	/**
	 * Maps a BasicDBObject to a Spring AI Document
	 * @param basicDBObject the basicDBObject to map to a spring ai document
	 * @return the spring ai document
	 */
	@SuppressWarnings("unchecked")
	private Document mapBasicDbObject(BasicDBObject basicDBObject) {
		String id = basicDBObject.getString(ID_FIELD_NAME);
		String content = basicDBObject.getString(CONTENT_FIELD_NAME);
		Map<String, Object> metadata = (Map<String, Object>) basicDBObject.get(METADATA_FIELD_NAME);
		List<Double> embedding = (List<Double>) basicDBObject.get(this.config.pathName);

		Document document = new Document(id, content, metadata);
		document.setEmbedding(embedding);

		return document;
	}

	@Override
	public void add(List<Document> documents) {
		for (Document document : documents) {
			List<Double> embedding = this.embeddingModel.embed(document);
			document.setEmbedding(embedding);
			this.mongoTemplate.save(document, this.config.collectionName);
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		Query query = new Query(where(ID_FIELD_NAME).in(idList));

		var deleteRes = this.mongoTemplate.remove(query, this.config.collectionName);
		long deleteCount = deleteRes.getDeletedCount();

		return Optional.of(deleteCount == idList.size());
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(SearchRequest.query(query));
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		String nativeFilterExpressions = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		List<Double> queryEmbedding = this.embeddingModel.embed(request.getQuery());
		var vectorSearch = new VectorSearchAggregation(queryEmbedding, this.config.pathName, this.config.numCandidates,
				this.config.vectorIndexName, request.getTopK(), nativeFilterExpressions);

		Aggregation aggregation = Aggregation.newAggregation(vectorSearch,
				Aggregation.addFields()
					.addField(SCORE_FIELD_NAME)
					.withValueOfExpression("{\"$meta\":\"vectorSearchScore\"}")
					.build(),
				Aggregation.match(new Criteria(SCORE_FIELD_NAME).gte(request.getSimilarityThreshold())));

		return this.mongoTemplate.aggregate(aggregation, this.config.collectionName, BasicDBObject.class)
			.getMappedResults()
			.stream()
			.map(this::mapBasicDbObject)
			.toList();
	}

	public static class MongoDBVectorStoreConfig {

		private final String collectionName;

		private final String vectorIndexName;

		private final String pathName;

		private final List<String> metadataFieldsToFilter;

		private final int numCandidates;

		private MongoDBVectorStoreConfig(Builder builder) {
			this.collectionName = builder.collectionName;
			this.vectorIndexName = builder.vectorIndexName;
			this.pathName = builder.pathName;
			this.numCandidates = builder.numCandidates;
			this.metadataFieldsToFilter = builder.metadataFieldsToFilter;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static MongoDBVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		public static class Builder {

			private String collectionName = DEFAULT_VECTOR_COLLECTION_NAME;

			private String vectorIndexName = DEFAULT_VECTOR_INDEX_NAME;

			private String pathName = DEFAULT_PATH_NAME;

			private int numCandidates = DEFAULT_NUM_CANDIDATES;

			private List<String> metadataFieldsToFilter = Collections.emptyList();

			private Builder() {
			}

			/**
			 * Configures the collection to use This must match the name of the collection
			 * for the Vector Search Index in Atlas
			 * @param collectionName
			 * @return this builder
			 */
			public Builder withCollectionName(String collectionName) {
				Assert.notNull(collectionName, "Collection Name must not be null");
				Assert.notNull(collectionName, "Collection Name must not be empty");
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Configures the vector index name. This must match the name of the Vector
			 * Search Index Name in Atlas
			 * @param vectorIndexName
			 * @return this builder
			 */
			public Builder withVectorIndexName(String vectorIndexName) {
				Assert.notNull(vectorIndexName, "Vector Index Name must not be null");
				Assert.notNull(vectorIndexName, "Vector Index Name must not be empty");
				this.vectorIndexName = vectorIndexName;
				return this;
			}

			/**
			 * Configures the path name. This must match the name of the field indexed for
			 * the Vector Search Index in Atlas
			 * @param pathName
			 * @return this builder
			 */
			public Builder withPathName(String pathName) {
				Assert.notNull(pathName, "Path Name must not be null");
				Assert.notNull(pathName, "Path Name must not be empty");
				this.pathName = pathName;
				return this;
			}

			public Builder withMetadataFieldsToFilter(List<String> metadataFieldsToFilter) {
				Assert.notEmpty(metadataFieldsToFilter, "Fields list must not be empty");
				this.metadataFieldsToFilter = metadataFieldsToFilter;
				return this;
			}

			public MongoDBVectorStoreConfig build() {
				return new MongoDBVectorStoreConfig(this);
			}

		}

	}

}