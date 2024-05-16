package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.ai.vectorstore.TypesenseVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Pablo Sanchidrian Herrera
 */
@ConfigurationProperties(TypesenseVectorStoreProperties.CONFIG_PREFIX)
public class TypesenseVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.typesense";

	/**
	 * Typesense collection name to store the vectors.
	 */
	private String collectionName = TypesenseVectorStore.DEFAULT_COLLECTION_NAME;

	/**
	 * The dimension of the vectors to be stored in the Typesense collection.
	 */
	private int embeddingDimension = TypesenseVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

	public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public void setEmbeddingDimension(int embeddingDimension) {
		this.embeddingDimension = embeddingDimension;
	}

}
