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

package org.springframework.ai.vectorstore.typesense;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.api.exceptions.ObjectAlreadyExists;
import org.typesense.api.exceptions.ObjectNotFound;
import org.typesense.model.CollectionResponse;
import org.typesense.model.CollectionSchema;
import org.typesense.model.DeleteDocumentsParameters;
import org.typesense.model.Field;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.model.IndexAction;
import org.typesense.model.MultiSearchCollectionParameters;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchResultItem;
import org.typesense.model.MultiSearchSearchesParameter;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * A vector store implementation that uses Typesense as the backend. This implementation
 * supports storing and searching document embeddings using Typesense's vector search
 * capabilities.
 *
 * <p>
 * Example usage: <pre>{@code
 * TypesenseVectorStore vectorStore = TypesenseVectorStore.builder(client, embeddingModel)
 *     .collectionName("my_collection")
 *     .embeddingDimension(1536)
 *     .initializeSchema(true)
 *     .build();
 * }</pre>
 *
 * @author Dhanush Anumula
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Mark Pollack
 * @author Soby Chacko
 * @author chabinhwang
 * @see org.springframework.ai.vectorstore.VectorStore
 * @see org.springframework.ai.embedding.EmbeddingModel
 */
public class TypesenseVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	/**
	 * The name of the field that contains the document ID. It is mandatory to set "id" as
	 * the field name because that is the name that typesense is going to look for.
	 */
	public static final String DOC_ID_FIELD_NAME = "id";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String EMBEDDING_FIELD_NAME = "embedding";

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final String DEFAULT_COLLECTION_NAME = "vector_store";

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	private static final Log logger = LogFactory.getLog(TypesenseVectorStore.class);

	public final FilterExpressionConverter filterExpressionConverter = new TypesenseFilterExpressionConverter();

	private final Client client;

	private final boolean initializeSchema;

	private final String collectionName;

	private final int embeddingDimension;

	// Cache for vectorDimensions(); only a known size is stored.
	private volatile int resolvedVectorDimensions = INVALID_EMBEDDING_DIMENSION;

	/**
	 * Protected constructor for creating a TypesenseVectorStore instance using the
	 * builder pattern. This constructor initializes the vector store with the configured
	 * settings from the builder and performs necessary validations.
	 * @param builder the {@link Builder} containing all configuration settings
	 * @throws IllegalArgumentException if the client is null
	 * @throws IllegalArgumentException if the embeddingModel is null
	 * @see Builder
	 * @since 1.0.0
	 */
	protected TypesenseVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.client, "Typesense must not be null");

		this.client = builder.client;
		this.initializeSchema = builder.initializeSchema;
		this.collectionName = builder.collectionName;
		this.embeddingDimension = builder.embeddingDimension;
	}

	/**
	 * Creates a new TypesenseBuilder instance. This is the recommended way to instantiate
	 * a TypesenseVectorStore.
	 * @return a new TypesenseBuilder instance
	 */
	public static Builder builder(Client client, EmbeddingModel embeddingModel) {
		return new Builder(client, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		List<HashMap<String, Object>> documentList = IntStream.range(0, documents.size())
			.mapToObj(i -> toTypesenseDocument(documents.get(i), embeddings.get(i)))
			.toList();

		ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
		importDocumentsParameters.action(IndexAction.UPSERT);

		try {
			this.client.collections(this.collectionName).documents().import_(documentList, importDocumentsParameters);

			if (logger.isInfoEnabled()) {
				logger.info("Added " + documentList.size() + " documents");
			}
		}
		catch (Exception e) {
			logger.error("Failed to add documents", e);
		}
	}

	private static HashMap<String, Object> toTypesenseDocument(Document document, float[] embedding) {
		HashMap<String, Object> typesenseDoc = new HashMap<>();
		typesenseDoc.put(DOC_ID_FIELD_NAME, document.getId());
		typesenseDoc.put(CONTENT_FIELD_NAME, document.getText());
		typesenseDoc.put(METADATA_FIELD_NAME, document.getMetadata());
		typesenseDoc.put(EMBEDDING_FIELD_NAME, embedding);
		return typesenseDoc;
	}

	@Override
	protected boolean supportsUpsert() {
		return true;
	}

	@Override
	protected void doUpsert(List<EmbeddedDocument> entries) {
		// Typesense writes a batch document by document, so the whole batch is checked
		// before anything is written. When the size is not known the check is left to
		// Typesense, which rejects a vector of the wrong size.
		int expected = vectorDimensions();
		for (int i = 0; i < entries.size(); i++) {
			checkDimensions("Embedding at index " + i, entries.get(i).embedding().length, expected);
		}

		// The upsert action replaces a whole document with the same id, so omitted
		// metadata keys are dropped.
		List<HashMap<String, Object>> documentList = entries.stream()
			.map(entry -> toTypesenseDocument(entry.document(), entry.embedding()))
			.toList();
		ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
		importDocumentsParameters.action(IndexAction.UPSERT);
		String results;
		try {
			results = this.client.collections(this.collectionName)
				.documents()
				.import_(documentList, importDocumentsParameters);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to upsert documents into collection " + this.collectionName, ex);
		}
		checkImportResults(results);
	}

	/**
	 * Typesense reports a rejected document in the import results, one JSON line per
	 * document, rather than by failing the request.
	 */
	private void checkImportResults(String results) {
		String[] lines = results.split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				continue;
			}
			JsonNode result = JacksonUtils.getDefaultJsonMapper().readTree(lines[i]);
			if (!result.path("success").asBoolean(false)) {
				throw new IllegalStateException("Typesense rejected the document at index " + i + ": "
						+ result.path("error").asString("no error given"));
			}
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
		// Typesense filter_by for the id field expects bare string values, not quoted
		// literals — e.g. id: [id1,id2]. Typesense document IDs are restricted to
		// URL-safe characters (no commas, brackets, or quotes), so raw string joining
		// is safe here. The operator is `: ` (not `:=`) per the Typesense recommendation
		// for multi-value id filters.
		deleteDocumentsParameters.filterBy(DOC_ID_FIELD_NAME + ": [" + String.join(",", idList) + "]");

		try {
			int deletedDocs = (Integer) this.client.collections(this.collectionName)
				.documents()
				.delete(deleteDocumentsParameters)
				.getOrDefault("num_deleted", 0);

			if (deletedDocs < idList.size()) {
				logger.warn("Failed to delete all documents");
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents", e);
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String filterStr = this.filterExpressionConverter.convertExpression(filterExpression);
			DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
			deleteDocumentsParameters.filterBy(filterStr);

			Map<String, Object> response = this.client.collections(this.collectionName)
				.documents()
				.delete(deleteDocumentsParameters);

			int deletedDocs = (Integer) response.getOrDefault("num_deleted", 0);
			if (deletedDocs == 0) {
				logger.warn("No documents were deleted matching filter expression");
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Deleted " + deletedDocs + " documents matching filter expression");
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter", e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Assert.notNull(request.getQuery(), "Query string must not be null");

		// Convert the filter first, so an invalid one fails before the model is called
		String nativeFilterExpressions = nativeFilterExpression(request);

		float[] embedding = this.embeddingModel.embed(request.getQuery());

		try {
			return searchByEmbedding(embedding, nativeFilterExpressions, request);
		}
		catch (RuntimeException ex) {
			logger.error("Failed to search documents", ex);
			return List.of();
		}
	}

	@Override
	protected boolean supportsQueryEmbeddingSearch() {
		return true;
	}

	@Override
	protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
		// Typesense rejects a query vector of the wrong size; checking here gives the
		// same error before the request whenever the size is known.
		checkDimensions("Query embedding", queryEmbedding.length, vectorDimensions());
		return searchByEmbedding(queryEmbedding, nativeFilterExpression(request), request);
	}

	private String nativeFilterExpression(SearchRequest request) {
		String nativeFilterExpressions = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		if (logger.isInfoEnabled()) {
			logger.info("Filter expression: " + nativeFilterExpressions);
		}
		return nativeFilterExpressions;
	}

	private List<Document> searchByEmbedding(float[] embedding, String nativeFilterExpressions, SearchRequest request) {
		MultiSearchCollectionParameters multiSearchCollectionParameters = new MultiSearchCollectionParameters();
		multiSearchCollectionParameters.collection(this.collectionName);
		multiSearchCollectionParameters.q("*");
		// Typesense returns 10 hits per page unless asked for more
		multiSearchCollectionParameters.perPage(request.getTopK());

		Stream<Float> floatStream = IntStream.range(0, embedding.length).mapToObj(i -> embedding[i]);
		// typesense uses only cosine similarity
		String vectorQuery = EMBEDDING_FIELD_NAME + ":(" + "["
				+ String.join(",", floatStream.map(String::valueOf).toList()) + "], " + "k: " + request.getTopK() + ", "
				+ "distance_threshold: " + (1 - request.getSimilarityThreshold()) + ")";

		multiSearchCollectionParameters.vectorQuery(vectorQuery);
		multiSearchCollectionParameters.filterBy(nativeFilterExpressions);

		MultiSearchSearchesParameter multiSearchesParameter = new MultiSearchSearchesParameter()
			.addSearchesItem(multiSearchCollectionParameters);

		MultiSearchResult result;
		try {
			result = this.client.multiSearch.perform(multiSearchesParameter, Map.of("query_by", EMBEDDING_FIELD_NAME));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to search collection " + this.collectionName, ex);
		}
		// Typesense reports a rejected search, such as a query vector of the wrong size,
		// in the result instead of failing the request
		for (MultiSearchResultItem searchResult : result.getResults()) {
			if (searchResult.getError() != null) {
				throw new IllegalStateException("Typesense rejected the search: " + searchResult.getError());
			}
		}

		List<Document> documents = result.getResults()
			.stream()
			.flatMap(searchResult -> searchResult.getHits().stream().map(hit -> {
				Map<String, Object> rawDocument = hit.getDocument();
				String docId = (String) rawDocument.get(DOC_ID_FIELD_NAME);
				Assert.state(docId != null, "document id must not be null");
				String content = (String) rawDocument.getOrDefault(CONTENT_FIELD_NAME, "");
				Map<String, Object> metadata = rawDocument.get(METADATA_FIELD_NAME) instanceof Map
						? (Map<String, Object>) rawDocument.get(METADATA_FIELD_NAME) : Map.of();
				metadata.put(DocumentMetadata.DISTANCE.value(), hit.getVectorDistance());
				return Document.builder()
					.id(docId)
					.text(content)
					.metadata(metadata)
					.score(1.0 - hit.getVectorDistance())
					.build();
			}))
			.toList();

		if (logger.isInfoEnabled()) {
			logger.info("Found " + documents.size() + " documents");
		}
		return documents;
	}

	int embeddingDimensions() {
		if (this.embeddingDimension != INVALID_EMBEDDING_DIMENSION) {
			return this.embeddingDimension;
		}
		try {
			int embeddingDimensions = this.embeddingModel.dimensions();
			if (embeddingDimensions > 0) {
				return embeddingDimensions;
			}
		}
		catch (Exception e) {
			if (logger.isWarnEnabled()) {
				logger.warn(
						"Failed to obtain the embedding dimensions from the embedding model and fall backs to default: "
								+ this.embeddingDimension,
						e);
			}
		}
		return OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

	// ---------------------------------------------------------------------------------
	// Initialization
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() {
		if (this.initializeSchema) {
			this.createCollection();
		}
	}

	private boolean hasCollection() {
		try {
			this.client.collections(this.collectionName).retrieve();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	void createCollection() {
		// Ask the model for the size only when the collection is known to be missing.
		// Any other lookup failure fails startup, since the collection may well exist.
		try {
			cacheDimensions(this.client.collections(this.collectionName).retrieve());
			if (logger.isInfoEnabled()) {
				logger.info("Collection " + this.collectionName + " already exists");
			}
			return;
		}
		catch (ObjectNotFound ex) {
			// Missing, so it is created below
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not check whether collection " + this.collectionName + " exists",
					ex);
		}

		CollectionSchema collectionSchema = new CollectionSchema();

		collectionSchema.name(this.collectionName)
			.addFieldsItem(new Field().name(DOC_ID_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(CONTENT_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(METADATA_FIELD_NAME).type(FieldTypes.OBJECT).optional(true))
			.addFieldsItem(new Field().name(EMBEDDING_FIELD_NAME)
				.type(FieldTypes.FLOAT_ARRAY)
				.numDim(this.embeddingDimensions())
				.optional(false))
			.enableNestedFields(true);

		try {
			this.client.collections().create(collectionSchema);
			if (logger.isInfoEnabled()) {
				logger.info("Collection " + this.collectionName + " created");
			}
		}
		catch (ObjectAlreadyExists ex) {
			// Another process created it first
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to create collection " + this.collectionName, ex);
		}
		// Another process may have created the collection first with a different size,
		// so the size is read back instead of assumed.
		vectorDimensions();
	}

	/**
	 * The size of the stored vectors.
	 * <p>
	 * The size comes from the configured {@code embeddingDimension} if set, otherwise
	 * from the {@code num_dim} of the embedding field in the collection schema, including
	 * a collection this store just created. A failed lookup leaves the size unknown and
	 * is tried again on the next call. Only a known size is cached, so a store has to be
	 * rebuilt after the collection is recreated with a different size.
	 * @return the known dimension, or -1
	 */
	private int vectorDimensions() {
		if (this.embeddingDimension > 0) {
			return this.embeddingDimension;
		}
		int cached = this.resolvedVectorDimensions;
		if (cached > 0) {
			return cached;
		}
		try {
			return cacheDimensions(this.client.collections(this.collectionName).retrieve());
		}
		catch (Exception ex) {
			logger.debug("Could not read the vector dimensions from the collection schema", ex);
		}
		return INVALID_EMBEDDING_DIMENSION;
	}

	private int cacheDimensions(CollectionResponse collection) {
		List<Field> fields = collection.getFields();
		if (fields != null) {
			for (Field field : fields) {
				Integer numDim = field.getNumDim();
				if (EMBEDDING_FIELD_NAME.equals(field.getName()) && numDim != null && numDim > 0) {
					this.resolvedVectorDimensions = numDim;
					return numDim;
				}
			}
		}
		return INVALID_EMBEDDING_DIMENSION;
	}

	void dropCollection() {
		if (!this.hasCollection()) {
			if (logger.isInfoEnabled()) {
				logger.info("Collection " + this.collectionName + " does not exist");
			}
			return;
		}

		try {
			this.client.collections(this.collectionName).delete();
			if (logger.isInfoEnabled()) {
				logger.info("Collection " + this.collectionName + " dropped");
			}
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to drop collection " + this.collectionName, e);
			}
		}
	}

	@Nullable Map<String, Object> getCollectionInfo() {
		try {
			CollectionResponse retrievedCollection = this.client.collections(this.collectionName).retrieve();
			return Map.of("name", retrievedCollection.getName(), "num_documents",
					retrievedCollection.getNumDocuments());
		}
		catch (Exception e) {
			logger.error("Failed to retrieve collection info", e);
			return null;
		}

	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		VectorStoreObservationContext.Builder builder = VectorStoreObservationContext
			.builder(VectorStoreProvider.TYPESENSE.value(), operationName)
			.collectionName(this.collectionName)
			.fieldName(EMBEDDING_FIELD_NAME)
			.similarityMetric(VectorStoreSimilarityMetric.COSINE.value());
		int dimensions = vectorDimensions();
		if (dimensions > 0) {
			builder.dimensions(dimensions);
		}
		return builder;
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.client;
		return Optional.of(client);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private String collectionName = DEFAULT_COLLECTION_NAME;

		private int embeddingDimension = INVALID_EMBEDDING_DIMENSION;

		private final Client client;

		private boolean initializeSchema = false;

		/**
		 * Constructs a new TypesenseBuilder instance.
		 * @param client The Typesense client instance used for database operations. Must
		 * not be null.
		 * @param embeddingModel The embedding model used for vector transformations.
		 * @throws IllegalArgumentException if client is null
		 */
		public Builder(Client client, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(client, "client must not be null");
			this.client = client;
		}

		/**
		 * Configures the collection name.
		 * @param collectionName the collection name to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "collectionName must not be empty");
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the dimension size of the embedding vectors.
		 * @param embeddingDimension The dimension of the embedding
		 * @return this builder instance
		 * @throws IllegalArgumentException if dimension is invalid
		 */
		public Builder embeddingDimension(int embeddingDimension) {
			Assert.isTrue(embeddingDimension > 0, "Embedding dimension must be greater than 0");
			this.embeddingDimension = embeddingDimension;
			return this;
		}

		/**
		 * Configures whether to initialize the collection schema automatically.
		 * @param initializeSchema true to initialize schema automatically
		 * @return this builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		@Override
		public TypesenseVectorStore build() {
			return new TypesenseVectorStore(this);
		}

	}

}
