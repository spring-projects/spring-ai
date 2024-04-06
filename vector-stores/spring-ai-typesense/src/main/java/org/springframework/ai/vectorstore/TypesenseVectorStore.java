package org.springframework.ai.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.model.CollectionSchema;
import org.typesense.model.Field;
import org.typesense.model.ImportDocumentsParameters;

import java.util.HashMap;
import java.util.List;
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

		private String collectionName;

	}

	public TypesenseVectorStore(Client client, EmbeddingClient embeddingClient) {
		this(client, embeddingClient, new TypesenseConfig());
	}

	public TypesenseVectorStore(Client client, EmbeddingClient embeddingClient, TypesenseConfig config) {
		Assert.notNull(client, "MilvusServiceClient must not be null");
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
		return Optional.empty();
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		Assert.notNull(request.getQuery(), "Query string must not be null");
		return null;
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
