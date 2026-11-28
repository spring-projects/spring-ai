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

package org.springframework.ai.vectorstore.weaviate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionConfig;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.data.DeleteManyResponse;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse;
import io.weaviate.client6.v1.api.collections.query.ConsistencyLevel;
import io.weaviate.client6.v1.api.collections.query.Metadata;
import io.weaviate.client6.v1.api.collections.query.QueryMetadata;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import io.weaviate.client6.v1.api.collections.vectorindex.Distance;
import io.weaviate.client6.v1.api.collections.vectorindex.Hnsw;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * A vector store implementation that stores and retrieves vectors in a Weaviate database.
 *
 * <p>
 * This implementation is built on the Weaviate Java {@code client6} API and uses gRPC for
 * similarity search, writes, and deletes. It does not require GraphQL to be enabled on
 * the Weaviate cluster.
 *
 * <p>
 * Scoring relies on Weaviate's {@code certainty}, which is only meaningful for the cosine
 * distance metric. The store therefore creates its collection with cosine distance and a
 * self-provided (bring-your-own-vector) index. When pointed at a pre-existing collection
 * that does not use cosine distance, similarity search fails fast rather than returning
 * silently incorrect scores.
 *
 * Note: You can assign arbitrary metadata fields with your Documents. These will be
 * persisted and managed as Document fields. But only the metadata keys listed in
 * {@link WeaviateVectorStore#filterMetadataFields} can be used for similarity search
 * expression filters.
 *
 * <p>
 * Example usage with builder:
 * </p>
 * <pre>{@code
 * // weaviateClient is an io.weaviate.client6.v1.api.WeaviateClient
 * WeaviateVectorStore vectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
 *     .options(options)                     	  // Optional: use custom options
 *     .consistencyLevel(ConsistentLevel.QUORUM)  // Optional: Set consistency level (default: ONE)
 *     .filterMetadataFields(List.of(             // Optional: Configure filterable metadata fields
 *         MetadataField.text("country"),
 *         MetadataField.number("year")
 *     ))
 *     .build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @since 1.0.0
 */
public class WeaviateVectorStore extends AbstractObservationVectorStore {

	private static final Log logger = LogFactory.getLog(WeaviateVectorStore.class);

	private static final String METADATA_FIELD_NAME = "metadata";

	private final WeaviateClient weaviateClient;

	private final WeaviateVectorStoreOptions options;

	private final ConsistentLevel consistencyLevel;

	/**
	 * List of metadata fields (as field name and type) that can be used in similarity
	 * search query filter expressions. The {@link Document#getMetadata()} can contain
	 * arbitrary number of metadata entries, but only the fields listed here can be used
	 * in the search filter expressions.
	 *
	 * If new entries are added ot the filterMetadataFields the affected documents must be
	 * (re)updated.
	 */
	private final List<MetadataField> filterMetadataFields;

	/**
	 * Converts the generic {@link Filter.Expression} into native, client6, Weaviate
	 * {@link io.weaviate.client6.v1.api.collections.query.Filter} objects.
	 */
	private final WeaviateFilterExpressionConverter filterExpressionConverter;

	private final Object collectionInitLock = new Object();

	private volatile boolean collectionInitialized;

	/**
	 * Protected constructor for creating a WeaviateVectorStore instance using the builder
	 * pattern. This constructor initializes the vector store with the configured settings
	 * from the builder and performs necessary validations.
	 * @param builder the {@link Builder} containing all configuration settings
	 * @throws IllegalArgumentException if the weaviateClient is null
	 * @see Builder
	 * @since 1.0.0
	 */
	protected WeaviateVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.weaviateClient, "WeaviateClient must not be null");

		this.options = builder.options;

		this.weaviateClient = builder.weaviateClient;
		this.consistencyLevel = builder.consistencyLevel;
		this.filterMetadataFields = builder.filterMetadataFields;
		this.filterExpressionConverter = new WeaviateFilterExpressionConverter(
				this.filterMetadataFields.stream().map(MetadataField::name).toList(),
				this.options.getMetaFieldPrefix());

		// https://github.com/weaviate/java-client/issues/641: in client6 6.3.0 both
		// QUORUM and ALL are mapped to the proto value for ONE. Warn once here (rather
		// than per operation) so the degradation is not mistaken for a Spring AI bug.
		if (this.consistencyLevel == ConsistentLevel.QUORUM || this.consistencyLevel == ConsistentLevel.ALL) {
			logger.warn("Weaviate client6 currently maps consistency level " + this.consistencyLevel
					+ " to ONE due to https://github.com/weaviate/java-client/issues/641. "
					+ "Writes and deletes will behave as consistency level ONE until the client is upgraded "
					+ "to a release that fixes the issue.");
		}
	}

	/**
	 * Creates a new WeaviateBuilder instance. This is the recommended way to instantiate
	 * a WeaviateVectorStore.
	 * @return a new WeaviateBuilder instance
	 */
	public static Builder builder(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
		return new Builder(weaviateClient, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {

		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		List<WeaviateObject<Map<String, Object>>> weaviateObjects = new ArrayList<>(documents.size());
		for (int i = 0; i < documents.size(); i++) {
			weaviateObjects.add(toWeaviateObject(documents.get(i), embeddings.get(i)));
		}

		InsertManyResponse response = writeHandle().data.insertMany(weaviateObjects);

		Set<String> errorMessages = collectInsertErrors(response);
		if (!errorMessages.isEmpty()) {
			throw new RuntimeException("Failed to add documents because: \n" + errorMessages);
		}
	}

	static Set<String> collectInsertErrors(InsertManyResponse response) {
		Set<String> errorMessages = new LinkedHashSet<>();
		if (response.errors() != null) {
			errorMessages.addAll(response.errors());
		}
		if (response.responses() != null) {
			for (InsertManyResponse.InsertObject insertObject : response.responses()) {
				if (!insertObject.successful() && StringUtils.hasText(insertObject.error())) {
					errorMessages.add(insertObject.error());
				}
			}
		}
		return errorMessages;
	}

	private WeaviateObject<Map<String, Object>> toWeaviateObject(Document document, float[] embedding) {

		// https://weaviate.io/developers/weaviate/config-refs/datatypes
		Map<String, Object> fields = new HashMap<>();
		fields.put(this.options.getContentFieldName(), document.getText());
		try {
			String metadataString = JsonMapper.shared().writeValueAsString(document.getMetadata());
			fields.put(METADATA_FIELD_NAME, metadataString);
		}
		catch (JacksonException e) {
			throw new RuntimeException("Failed to serialize the Document metadata: " + document.getText());
		}

		// Add the filterable metadata fields as top level fields, allowing filter
		// expressions on them.
		for (MetadataField mf : this.filterMetadataFields) {
			if (document.getMetadata().containsKey(mf.name())) {
				fields.put(this.options.getMetaFieldPrefix() + mf.name(), document.getMetadata().get(mf.name()));
			}
		}

		return WeaviateObject.<Map<String, Object>>of(
				builder -> builder.uuid(document.getId()).properties(fields).vectors(Vectors.of(embedding)));
	}

	@Override
	public void doDelete(List<String> documentIds) {
		Assert.notNull(documentIds, "documentIds must not be null");

		DeleteManyResponse response = writeHandle().data.deleteMany(documentIds.toArray(new String[0]));

		throwOnDeleteFailure(response);
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		// Native filter delete removes the need to route filter-deletes through a
		// similarity search with an empty query.
		io.weaviate.client6.v1.api.collections.query.Filter filter = this.filterExpressionConverter
			.convert(filterExpression);

		DeleteManyResponse response = writeHandle().data.deleteMany(filter);

		throwOnDeleteFailure(response);
	}

	static void throwOnDeleteFailure(DeleteManyResponse response) {
		Set<String> errorMessages = new LinkedHashSet<>();
		if (response.objects() != null) {
			for (DeleteManyResponse.DeletedObject deletedObject : response.objects()) {
				if (!deletedObject.successful() && StringUtils.hasText(deletedObject.error())) {
					errorMessages.add(deletedObject.error());
				}
			}
		}

		if (response.failed() > 0 || !errorMessages.isEmpty()) {
			throw new RuntimeException("Failed to delete documents because: \n"
					+ (errorMessages.isEmpty() ? response.failed() + " object(s) failed to delete" : errorMessages));
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		float[] embedding = this.embeddingModel.embed(request.getQuery());

		CollectionHandle<Map<String, Object>> handle = collectionHandle();

		QueryResponse<Map<String, Object>> response = handle.query.nearVector(embedding, builder -> {
			builder.limit(request.getTopK())
				// Cosine certainty threshold; see the cosine-distance requirement above.
				.certainty((float) request.getSimilarityThreshold())
				.returnProperties(this.options.getContentFieldName(), METADATA_FIELD_NAME)
				.returnMetadata(Metadata.CERTAINTY)
				// Subject to https://github.com/weaviate/java-client/issues/641 (QUORUM
				// and ALL currently behave as ONE).
				.consistencyLevel(mapConsistencyLevel());
			if (request.hasFilterExpression()) {
				Filter.Expression filterExpression = request.getFilterExpression();
				Assert.state(filterExpression != null, "Filter expression must not be null");
				builder.filters(this.filterExpressionConverter.convert(filterExpression));
			}
			return builder;
		});

		if (CollectionUtils.isEmpty(response.objects())) {
			return List.of();
		}

		return response.objects().stream().map(this::toDocument).toList();
	}

	Document toDocument(WeaviateObject<Map<String, Object>> object) {

		String id = object.uuid();
		Assert.state(StringUtils.hasText(id), "Weaviate object UUID must not be null or empty");

		Map<String, Object> properties = object.properties();

		// Content
		String content = (properties != null) ? (String) properties.get(this.options.getContentFieldName()) : null;

		// Metadata
		Map<String, Object> metadata = new HashMap<>();
		Object metadataJson = (properties != null) ? properties.get(METADATA_FIELD_NAME) : null;
		if (metadataJson instanceof String metadataString && StringUtils.hasText(metadataString)) {
			metadata.putAll(JsonMapper.shared().readValue(metadataString, Map.class));
		}

		// Scoring: certainty is only populated for cosine-distance collections.
		QueryMetadata queryMetadata = object.queryMetadata();
		Float certainty = (queryMetadata != null) ? queryMetadata.certainty() : null;
		Assert.state(certainty != null,
				"Weaviate did not return a certainty for the matched object. Certainty is only available when the "
						+ "collection uses cosine distance; non-cosine collections are not supported for scoring.");

		metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - certainty);

		// @formatter:off
		return Document.builder()
			.id(id)
			.text(content)
			.metadata(metadata)
			.score(certainty.doubleValue())
			.build(); // @formatter:on
	}

	/**
	 * Returns a collection handle configured with the store's consistency level for write
	 * and delete operations.
	 */
	private CollectionHandle<Map<String, Object>> writeHandle() {
		// https://github.com/weaviate/java-client/issues/641: QUORUM/ALL currently
		// behave as ONE. The user-facing WARN is emitted once in the constructor.
		return collectionHandle().withConsistencyLevel(mapConsistencyLevel());
	}

	private CollectionHandle<Map<String, Object>> collectionHandle() {
		initCollectionIfNecessary();
		return this.weaviateClient.collections.use(this.options.getObjectClass());
	}

	private ConsistencyLevel mapConsistencyLevel() {
		return ConsistencyLevel.valueOf(this.consistencyLevel.name());
	}

	/**
	 * Idempotently ensures the backing collection exists with a self-provided
	 * (vectorizer: none) cosine-distance vector index. An existing collection is reused
	 * without destructive reconfiguration.
	 */
	private void initCollectionIfNecessary() {
		if (this.collectionInitialized) {
			return;
		}
		synchronized (this.collectionInitLock) {
			if (this.collectionInitialized) {
				return;
			}
			String objectClass = this.options.getObjectClass();
			try {
				if (!this.weaviateClient.collections.exists(objectClass)) {
					this.weaviateClient.collections.create(buildCollectionConfig(objectClass));
				}
				this.collectionInitialized = true;
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to initialize Weaviate collection: " + objectClass, e);
			}
		}
	}

	private CollectionConfig buildCollectionConfig(String objectClass) {
		List<Property> properties = new ArrayList<>();
		properties.add(Property.text(this.options.getContentFieldName()));
		properties.add(Property.text(METADATA_FIELD_NAME));
		for (MetadataField mf : this.filterMetadataFields) {
			String name = this.options.getMetaFieldPrefix() + mf.name();
			Property property = switch (mf.type()) {
				case TEXT -> Property.text(name);
				case NUMBER -> Property.number(name);
				case BOOLEAN -> Property.bool(name);
			};
			properties.add(property);
		}

		return CollectionConfig.of(objectClass,
				builder -> builder.properties(properties.toArray(new Property[0]))
					.vectorConfig(VectorConfig.selfProvided(
							vectorizer -> vectorizer.vectorIndex(Hnsw.of(hnsw -> hnsw.distance(Distance.COSINE))))));
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.WEAVIATE.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.options.getObjectClass());
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.weaviateClient;
		return Optional.of(client);
	}

	/**
	 * Defines the consistency levels for Weaviate operations.
	 *
	 * @see <a href=
	 * "https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-consistency-strategies">Weaviate
	 * Consistency Strategies</a>
	 */
	public enum ConsistentLevel {

		/**
		 * Write must receive an acknowledgement from at least one replica node. This is
		 * the fastest (most available), but least consistent option.
		 */
		ONE,

		/**
		 * Write must receive an acknowledgement from at least QUORUM replica nodes.
		 * QUORUM is calculated as n / 2 + 1, where n is the number of replicas.
		 *
		 * <p>
		 * <strong>Note:</strong> due to <a href=
		 * "https://github.com/weaviate/java-client/issues/641">weaviate/java-client#641</a>,
		 * the client6 {@code 6.3.0} driver currently maps this level to {@code ONE}.
		 */
		QUORUM,

		/**
		 * Write must receive an acknowledgement from all replica nodes. This is the most
		 * consistent, but 'slowest'.
		 *
		 * <p>
		 * <strong>Note:</strong> due to <a href=
		 * "https://github.com/weaviate/java-client/issues/641">weaviate/java-client#641</a>,
		 * the client6 {@code 6.3.0} driver currently maps this level to {@code ONE}.
		 */
		ALL

	}

	/**
	 * Represents a metadata field configuration for Weaviate vector store.
	 *
	 * @param name the name of the metadata field
	 * @param type the type of the metadata field
	 */
	public record MetadataField(String name, Type type) {

		/**
		 * Creates a metadata field of type TEXT.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type TEXT
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField text(String name) {
			Assert.hasText(name, "Text field must not be empty");
			return new MetadataField(name, Type.TEXT);
		}

		/**
		 * Creates a metadata field of type NUMBER.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type NUMBER
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField number(String name) {
			Assert.hasText(name, "Number field must not be empty");
			return new MetadataField(name, Type.NUMBER);
		}

		/**
		 * Creates a metadata field of type BOOLEAN.
		 * @param name the name of the field
		 * @return a new MetadataField instance of type BOOLEAN
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public static MetadataField bool(String name) {
			Assert.hasText(name, "Boolean field name must not be empty");
			return new MetadataField(name, Type.BOOLEAN);
		}

		/**
		 * Defines the supported types for metadata fields.
		 */
		public enum Type {

			TEXT, NUMBER, BOOLEAN

		}
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		private ConsistentLevel consistencyLevel = ConsistentLevel.ONE;

		private List<MetadataField> filterMetadataFields = List.of();

		private final WeaviateClient weaviateClient;

		/**
		 * Constructs a new WeaviateBuilder instance.
		 * @param weaviateClient The Weaviate client instance used for database
		 * operations. Must not be null.
		 * @param embeddingModel The embedding model used for vector transformations.
		 * @throws IllegalArgumentException if weaviateClient is null
		 */
		private Builder(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(weaviateClient, "WeaviateClient must not be null");
			this.weaviateClient = weaviateClient;
		}

		/**
		 * Configures the Weaviate vector store option.
		 * @param options the vector store options to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if options is null or empty
		 * @since 1.1.0
		 */
		public Builder options(WeaviateVectorStoreOptions options) {
			Assert.notNull(options, "options must not be empty");
			this.options = options;
			return this;
		}

		/**
		 * Configures the consistency level for Weaviate operations.
		 * @param consistencyLevel the consistency level to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if consistencyLevel is null
		 */
		public Builder consistencyLevel(ConsistentLevel consistencyLevel) {
			Assert.notNull(consistencyLevel, "consistencyLevel must not be null");
			this.consistencyLevel = consistencyLevel;
			return this;
		}

		/**
		 * Configures the filterable metadata fields.
		 * @param filterMetadataFields list of metadata fields that can be used in filters
		 * @return this builder instance
		 * @throws IllegalArgumentException if filterMetadataFields is null
		 */
		public Builder filterMetadataFields(List<MetadataField> filterMetadataFields) {
			Assert.notNull(filterMetadataFields, "filterMetadataFields must not be null");
			this.filterMetadataFields = filterMetadataFields;
			return this;
		}

		/**
		 * Builds and returns a new WeaviateVectorStore instance with the configured
		 * settings.
		 * @return a new WeaviateVectorStore instance
		 * @throws IllegalStateException if the builder configuration is invalid
		 */
		@Override
		public WeaviateVectorStore build() {
			return new WeaviateVectorStore(this);
		}

	}

}
