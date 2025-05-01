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

package org.springframework.ai.chroma.vectorstore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.DeleteEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.Embedding;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ChromaVectorStore} is a concrete implementation of the {@link VectorStore}
 * interface. It is responsible for adding, deleting, and searching documents based on
 * their similarity to a query, using the {@link ChromaApi} and {@link EmbeddingModel} for
 * embedding calculations. For more information about how it does this, see the official
 * <a href="https://www.trychroma.com/">Chroma website</a>.
 *
 * @author Christian Tzolov
 * @author Fu Cheng
 * @author Sebastien Deleuze
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Jonghoon Park
 */
public class ChromaVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private final ChromaApi chromaApi;

	private final String tenantName;

	private final String databaseName;

	private final String collectionName;

	private FilterExpressionConverter filterExpressionConverter;

	@Nullable
	private String collectionId;

	private final boolean initializeSchema;

	private final ObjectMapper objectMapper;

	private boolean initialized = false;

	private static final Logger logger = LoggerFactory.getLogger(ChromaVectorStore.class);

	/**
	 * @param builder {@link VectorStore.Builder} for chroma vector store
	 */
	protected ChromaVectorStore(Builder builder) {
		super(builder);

		this.chromaApi = builder.chromaApi;
		this.tenantName = builder.tenantName;
		this.databaseName = builder.databaseName;
		this.collectionName = builder.collectionName;
		this.initializeSchema = builder.initializeSchema;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();

		if (builder.initializeImmediately) {
			try {
				afterPropertiesSet();
			}
			catch (Exception e) {
				throw new IllegalStateException("Failed to initialize ChromaVectorStore", e);
			}
		}
	}

	public static Builder builder(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
		return new Builder(chromaApi, embeddingModel);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.initialized) {
			var collection = this.chromaApi.getCollection(this.tenantName, this.databaseName, this.collectionName);
			if (collection == null) {
				if (this.initializeSchema) {
					var tenant = this.chromaApi.getTenant(this.tenantName);
					if (tenant == null) {
						this.chromaApi.createTenant(this.tenantName);
					}

					var database = this.chromaApi.getDatabase(this.tenantName, this.databaseName);
					if (database == null) {
						this.chromaApi.createDatabase(this.tenantName, this.databaseName);
					}

					collection = this.chromaApi.createCollection(this.tenantName, this.databaseName,
							new ChromaApi.CreateCollectionRequest(this.collectionName));
				}
				else {
					throw new RuntimeException("Collection " + this.collectionName
							+ " doesn't exist and won't be created as the initializeSchema is set to false.");
				}
			}
			if (collection != null) {
				this.collectionId = collection.id();
			}
			this.initialized = true;
		}
	}

	@Override
	public void doAdd(@NonNull List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");
		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<String> ids = new ArrayList<>();
		List<Map<String, Object>> metadatas = new ArrayList<>();
		List<String> contents = new ArrayList<>();
		List<float[]> embeddings = new ArrayList<>();

		List<float[]> documentEmbeddings = this.embeddingModel.embed(documents,
				EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);

		for (Document document : documents) {
			ids.add(document.getId());
			metadatas.add(document.getMetadata());
			contents.add(document.getText());
			embeddings.add(documentEmbeddings.get(documents.indexOf(document)));
		}

		this.chromaApi.upsertEmbeddings(this.tenantName, this.databaseName, this.collectionId,
				new AddEmbeddingsRequest(ids, embeddings, metadatas, contents));
	}

	@Override
	public void doDelete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");
		this.chromaApi.deleteEmbeddings(this.tenantName, this.databaseName, this.collectionId,
				new DeleteEmbeddingsRequest(idList));
	}

	@Override
	protected void doDelete(Filter.Expression expression) {
		Assert.notNull(expression, "Filter expression must not be null");

		try {
			ChromaFilterExpressionConverter converter = new ChromaFilterExpressionConverter();
			String whereClauseStr = converter.convertExpression(expression);

			Map<String, Object> whereClause = this.chromaApi.where(whereClauseStr);

			logger.debug("Deleting with where clause: " + whereClause);

			DeleteEmbeddingsRequest deleteRequest = new DeleteEmbeddingsRequest(null, whereClause);
			this.chromaApi.deleteEmbeddings(this.tenantName, this.databaseName, this.collectionId, deleteRequest);
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	@NonNull
	public List<Document> doSimilaritySearch(@NonNull SearchRequest request) {

		String query = request.getQuery();
		Assert.notNull(query, "Query string must not be null");

		float[] embedding = this.embeddingModel.embed(query);

		Map<String, Object> where = (request.getFilterExpression() != null)
				? jsonToMap(this.filterExpressionConverter.convertExpression(request.getFilterExpression())) : null;

		var queryRequest = new ChromaApi.QueryRequest(embedding, request.getTopK(), where);
		var queryResponse = this.chromaApi.queryCollection(this.tenantName, this.databaseName, this.collectionId,
				queryRequest);
		var embeddings = this.chromaApi.toEmbeddingResponseList(queryResponse);

		List<Document> responseDocuments = new ArrayList<>();

		for (Embedding chromaEmbedding : embeddings) {
			float distance = chromaEmbedding.distances().floatValue();
			if ((1 - distance) >= request.getSimilarityThreshold()) {
				String id = chromaEmbedding.id();
				String content = chromaEmbedding.document();
				Map<String, Object> metadata = chromaEmbedding.metadata();
				if (metadata == null) {
					metadata = new HashMap<>();
				}

				metadata.put(DocumentMetadata.DISTANCE.value(), distance);
				Document document = Document.builder()
					.id(id)
					.text(content)
					.metadata(metadata)
					.score(1.0 - distance)
					.build();
				responseDocuments.add(document);
			}
		}

		return responseDocuments;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> jsonToMap(String jsonText) {
		try {
			return (Map<String, Object>) this.objectMapper.readValue(jsonText, Map.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.CHROMA.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.collectionName + ":" + this.collectionId);
	}

	// used by the test
	void createCollection() {
		var collection = this.chromaApi.createCollection(this.tenantName, this.databaseName,
				new ChromaApi.CreateCollectionRequest(this.collectionName));
		if (collection != null) {
			this.collectionId = collection.id();
		}
	}

	// used by the test
	void deleteCollection() {
		this.chromaApi.deleteCollection(this.tenantName, this.databaseName, this.collectionName);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final ChromaApi chromaApi;

		private String tenantName = ChromaApiConstants.DEFAULT_TENANT_NAME;

		private String databaseName = ChromaApiConstants.DEFAULT_DATABASE_NAME;

		private String collectionName = ChromaApiConstants.DEFAULT_COLLECTION_NAME;

		private boolean initializeSchema = false;

		private FilterExpressionConverter filterExpressionConverter = new ChromaFilterExpressionConverter();

		private boolean initializeImmediately = false;

		private Builder(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(chromaApi, "ChromaApi must not be null");
			this.chromaApi = chromaApi;
		}

		/**
		 * Sets the tenant name.
		 * @param tenantName the name of the tenant
		 * @return the builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder tenantName(String tenantName) {
			Assert.hasText(tenantName, "tenantName must not be null or empty");
			this.tenantName = tenantName;
			return this;
		}

		/**
		 * Sets the database name.
		 * @param databaseName the name of the database
		 * @return the builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder databaseName(String databaseName) {
			Assert.hasText(databaseName, "databaseName must not be null or empty");
			this.databaseName = databaseName;
			return this;
		}

		/**
		 * Sets the collection name.
		 * @param collectionName the name of the collection
		 * @return the builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "collectionName must not be null or empty");
			this.collectionName = collectionName;
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
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Sets whether to initialize immediately.
		 * @param initialize true to initialize immediately, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeImmediately(boolean initialize) {
			this.initializeImmediately = initialize;
			return this;
		}

		/**
		 * Builds the {@link ChromaVectorStore} instance.
		 * @return a new ChromaVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		public ChromaVectorStore build() {
			return new ChromaVectorStore(this);
		}

	}

}
