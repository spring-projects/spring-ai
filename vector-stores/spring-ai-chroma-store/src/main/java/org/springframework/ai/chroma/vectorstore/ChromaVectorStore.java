/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.DeleteEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.Embedding;
import org.springframework.ai.chroma.vectorstore.ChromaApi.GetEmbeddingResponse;
import org.springframework.ai.chroma.vectorstore.ChromaApi.GetEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest.Include;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
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
 * @author chabinhwang
 */
public class ChromaVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private final ChromaApi chromaApi;

	private final String tenantName;

	private final String databaseName;

	private final String collectionName;

	private final FilterExpressionConverter filterExpressionConverter;

	private @Nullable String collectionId;

	private final boolean initializeSchema;

	private final JsonMapper jsonMapper;

	private boolean initialized = false;

	// Cache for vectorDimensions(); only a known size is stored.
	private volatile int resolvedVectorDimensions = -1;

	private static final Log logger = LogFactory.getLog(ChromaVectorStore.class);

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
		this.jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();

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
					throw new RuntimeException("Collection " + this.collectionName + " with the tenant: "
							+ this.tenantName + " and the database: " + this.databaseName
							+ " doesn't exist and won't be created as the initializeSchema is set to false.");
				}
			}
			Assert.state(collection != null, "collection should pre-exist or have been initialized.");
			this.collectionId = collection.id();
			this.initialized = true;
		}
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");
		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<String> ids = new ArrayList<>();
		List<Map<String, Object>> metadatas = new ArrayList<>();
		List<String> contents = new ArrayList<>();
		List<float[]> embeddings = new ArrayList<>();

		List<float[]> documentEmbeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			ids.add(document.getId());
			metadatas.add(document.getMetadata());
			contents.add(document.getText());
			embeddings.add(documentEmbeddings.get(i));
		}

		this.chromaApi.upsertEmbeddings(this.tenantName, this.databaseName, this.requireCollectionId(),
				new AddEmbeddingsRequest(ids, embeddings, metadatas, contents));
	}

	@Override
	protected boolean supportsUpsert() {
		return true;
	}

	@Override
	protected void doUpsert(List<EmbeddedDocument> entries) {
		// Whole-batch dimension pre-check before any write, so a mismatched vector fails
		// fast and cannot partially write. Non-empty and finiteness are already enforced
		// by the EmbeddedDocument constructor.
		int expected = vectorDimensions();
		for (int i = 0; i < entries.size(); i++) {
			checkDimensions("Embedding at index " + i, entries.get(i).embedding().length, expected);
		}

		List<String> ids = entries.stream().map(entry -> entry.document().getId()).toList();
		// Chroma has no atomic replace, so the stored keys are read before the write. A
		// concurrent upsert of the same id between the two can leave its added keys in
		// place; this is documented rather than locked, as a JVM-local lock cannot
		// coordinate writers in other processes.
		Map<String, Set<String>> storedKeys = storedMetadataKeys(ids);

		List<Map<String, Object>> metadatas = new ArrayList<>(entries.size());
		List<String> contents = new ArrayList<>(entries.size());
		List<float[]> embeddings = new ArrayList<>(entries.size());
		for (EmbeddedDocument entry : entries) {
			// Pair positionally: the document and its embedding come from the same entry.
			Document document = entry.document();
			// Chroma merges the metadata of an upsert into what is stored, and removes a
			// key only when it is sent with a null value. Null out the stored keys that
			// the new metadata leaves out, so the document is replaced in one write.
			Map<String, Object> metadata = new HashMap<>(document.getMetadata());
			for (String key : storedKeys.getOrDefault(document.getId(), Set.of())) {
				metadata.putIfAbsent(key, null);
			}
			metadatas.add(metadata);
			contents.add(document.getText());
			embeddings.add(entry.embedding());
		}

		this.chromaApi.upsertEmbeddings(this.tenantName, this.databaseName, this.requireCollectionId(),
				new AddEmbeddingsRequest(ids, embeddings, metadatas, contents));
	}

	@Override
	public void doDelete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");
		this.chromaApi.deleteEmbeddings(this.tenantName, this.databaseName, this.requireCollectionId(),
				new DeleteEmbeddingsRequest(idList));
	}

	@Override
	protected void doDelete(Filter.Expression expression) {
		Assert.notNull(expression, "Filter expression must not be null");

		try {
			ChromaFilterExpressionConverter converter = new ChromaFilterExpressionConverter();
			String whereClauseStr = converter.convertExpression(expression);

			Map<String, Object> whereClause = this.chromaApi.where(whereClauseStr);

			if (logger.isDebugEnabled()) {
				logger.debug("Deleting with where clause: " + whereClause);
			}

			DeleteEmbeddingsRequest deleteRequest = new DeleteEmbeddingsRequest(null, whereClause);
			this.chromaApi.deleteEmbeddings(this.tenantName, this.databaseName, this.requireCollectionId(),
					deleteRequest);
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to delete documents by filter: " + e.getMessage(), e);
			}
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		String query = request.getQuery();
		Assert.notNull(query, "Query string must not be null");

		return search(this.embeddingModel.embed(query), request);
	}

	/**
	 * The metadata keys stored for each of the given ids that already exist.
	 */
	private Map<String, Set<String>> storedMetadataKeys(List<String> ids) {
		GetEmbeddingResponse response = this.chromaApi.getEmbeddings(this.tenantName, this.databaseName,
				this.requireCollectionId(),
				new GetEmbeddingsRequest(ids, null, ids.size(), 0, List.of(Include.METADATAS)));
		Map<String, Set<String>> keys = new HashMap<>();
		if (response == null || response.metadata() == null) {
			return keys;
		}
		for (int i = 0; i < response.ids().size(); i++) {
			Map<String, String> metadata = response.metadata().get(i);
			if (metadata != null) {
				keys.put(response.ids().get(i), metadata.keySet());
			}
		}
		return keys;
	}

	@Override
	protected boolean supportsQueryEmbeddingSearch() {
		return true;
	}

	@Override
	protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
		checkDimensions("Query embedding", queryEmbedding.length, vectorDimensions());
		return search(queryEmbedding, request);
	}

	private List<Document> search(float[] embedding, SearchRequest request) {
		Map<String, Object> where = (request.getFilterExpression() != null)
				? jsonToMap(this.filterExpressionConverter.convertExpression(request.getFilterExpression())) : null;

		var queryRequest = new ChromaApi.QueryRequest(embedding, request.getTopK(), where);
		var queryResponse = this.chromaApi.queryCollection(this.tenantName, this.databaseName,
				this.requireCollectionId(), queryRequest);
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
		return (Map<String, Object>) this.jsonMapper.readValue(jsonText, Map.class);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		VectorStoreObservationContext.Builder builder = VectorStoreObservationContext
			.builder(VectorStoreProvider.CHROMA.value(), operationName)
			.collectionName(this.collectionName + ":" + this.requireCollectionId());
		int vectorDimensions = vectorDimensions();
		if (vectorDimensions > 0) {
			builder.dimensions(vectorDimensions);
		}
		return builder;
	}

	/**
	 * The vector size the collection accepts, or -1 when it is not known.
	 * <p>
	 * Chroma creates a collection without a size and fixes it from the first embedding
	 * written, so the size is read from the collection. The embedding model is never
	 * asked, because its width says nothing about the width of a collection that already
	 * holds embeddings. Until something is written, or when the collection cannot be
	 * read, the size stays unknown and reading it is tried again on the next call; Chroma
	 * itself then rejects an embedding of the wrong size. Only a known size is cached, so
	 * a store has to be rebuilt after the collection is recreated with a different size.
	 * @return the known dimension, or -1
	 */
	private int vectorDimensions() {
		int cached = this.resolvedVectorDimensions;
		if (cached > 0) {
			return cached;
		}
		int resolved = resolveVectorDimensions();
		if (resolved > 0) {
			this.resolvedVectorDimensions = resolved;
		}
		return resolved;
	}

	private int resolveVectorDimensions() {
		try {
			Integer dimension = this.chromaApi.getCollectionDimension(this.tenantName, this.databaseName,
					this.collectionName);
			return (dimension != null) ? dimension : -1;
		}
		catch (Exception ex) {
			logger.debug("Could not read the vector size of collection " + this.collectionName, ex);
			return -1;
		}
	}

	private String requireCollectionId() {
		Assert.notNull(this.collectionId, "collectionId should not be null");
		return this.collectionId;
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
