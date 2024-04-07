package org.springframework.ai.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Pablo Sanchidrian Herrera
 */
public class TypesenseVectorStore implements VectorStore, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(TypesenseVectorStore.class);

	public static final String DOC_ID_FIELD_NAME = "doc_id";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String EMBEDDING_FIELD_NAME = "embedding";

	private final Client client;

	private final EmbeddingClient embeddingClient;

	private final TypesenseConfig config;

	public static class TypesenseConfig {

		private final String collectionName;

		private final int embeddingDimension;

		public TypesenseConfig(String collectionName, int embeddingDimension) {
			this.collectionName = collectionName;
			this.embeddingDimension = embeddingDimension;
		}

		/**
		 * {@return the default config}
		 */
		public static TypesenseConfig defaultConfig() {
			return builder().build();
		}

		private TypesenseConfig(Builder builder) {
			this.collectionName = builder.collectionName;
			this.embeddingDimension = builder.embeddingDimension;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {

			return new Builder();
		}

		public static class Builder {

			private String collectionName;

			private int embeddingDimension;

			/**
			 * Set the collection name.
			 * @param collectionName The collection name.
			 * @return The builder.
			 */
			public Builder withCollectionName(String collectionName) {
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Set the embedding dimension.
			 * @param embeddingDimension The embedding dimension.
			 * @return The builder.
			 */
			public Builder withEmbeddingDimension(int embeddingDimension) {
				this.embeddingDimension = embeddingDimension;
				return this;
			}

			/**
			 * Build the configuration.
			 * @return The configuration.
			 */
			public TypesenseConfig build() {
				return new TypesenseConfig(this);
			}

		}

	}

	public TypesenseVectorStore(Client client, EmbeddingClient embeddingClient) {
		this(client, embeddingClient, TypesenseConfig.defaultConfig());
	}

	public TypesenseVectorStore(Client client, EmbeddingClient embeddingClient, TypesenseConfig config) {
		Assert.notNull(client, "Typesense must not be null");
		Assert.notNull(embeddingClient, "EmbeddingClient must not be null");

		this.client = client;
		this.embeddingClient = embeddingClient;
		this.config = config;
	}

	@Override
	public void add(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");

		List<HashMap<String, Object>> documentList = documents.stream().map(document -> {
			HashMap<String, Object> typesenseDoc = new HashMap<>();
			typesenseDoc.put(DOC_ID_FIELD_NAME, document.getId());
			typesenseDoc.put(CONTENT_FIELD_NAME, document.getContent());
			typesenseDoc.put(METADATA_FIELD_NAME, document.getMetadata());
			List<Double> embedding = this.embeddingClient.embed(document.getContent());
			typesenseDoc.put(EMBEDDING_FIELD_NAME, embedding);

			return typesenseDoc;
		}).toList();

		ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
		importDocumentsParameters.action("upsert");

		try {
			this.client.collections(this.config.collectionName)
				.documents()
				.import_(documentList, importDocumentsParameters);
		}
		catch (Exception e) {
			logger.error("Failed to add documents", e);
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
		deleteDocumentsParameters.filterBy(DOC_ID_FIELD_NAME + ":[" + String.join(",", idList) + "]");

		try {
			int deletedDocst = (Integer) this.client.collections(this.config.collectionName)
				.documents()
				.delete(deleteDocumentsParameters)
				.getOrDefault("num_deleted", 0);
			return Optional.of(deletedDocst > 0);
		}
		catch (Exception e) {
			logger.error("Failed to delete documents", e);
			return Optional.of(Boolean.FALSE);
		}
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		Assert.notNull(request.getQuery(), "Query string must not be null");

		List<Double> embedding = this.embeddingClient.embed(request.getQuery());

		HashMap<String, String> search = new HashMap<>();
		search.put("collection", this.config.collectionName);
		search.put("q", "*");
		search.put("vector_query", String.join(",", embedding.stream().map(String::valueOf).toList()));

		SearchParameters searchParameters = new SearchParameters()
				.q("*")
				.vectorQuery("vec:([" + String.join(",", embedding.stream().map(String::valueOf).toList()) + "])");

		try {
			SearchResult searchResult = client.collections(this.config.collectionName).documents().search(searchParameters);
			return List.of();
		} catch (Exception e) {
			logger.error("Failed to search documents", e);
			return List.of();
		}
	}
	// ---------------------------------------------------------------------------------
	// Initialization
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() throws Exception {
		this.createCollection();
	}

	private boolean hasCollection() {
		try {
			this.client.collections(this.config.collectionName).retrieve();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	void createCollection() {
		if (this.hasCollection()) {
			logger.info("Collection {} already exists", this.config.collectionName);
			return;
		}

		CollectionSchema collectionSchema = new CollectionSchema();

		collectionSchema.name(this.config.collectionName)
			.addFieldsItem(new Field().name(DOC_ID_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(CONTENT_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(METADATA_FIELD_NAME).type(FieldTypes.OBJECT).optional(true))
			.addFieldsItem(new Field().name(EMBEDDING_FIELD_NAME)
				.type(FieldTypes.FLOAT_ARRAY)
				.numDim(this.embeddingClient.dimensions())
				.optional(false));

		try {
			this.client.collections().create(collectionSchema);
			logger.info("Collection {} created", this.config.collectionName);
		}
		catch (Exception e) {
			logger.error("Failed to create collection {}", this.config.collectionName, e);
		}
	}

}
