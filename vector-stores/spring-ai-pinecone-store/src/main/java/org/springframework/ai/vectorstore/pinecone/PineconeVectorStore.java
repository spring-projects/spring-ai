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

package org.springframework.ai.vectorstore.pinecone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.QueryRequest;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.converter.PineconeFilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A VectorStore implementation backed by Pinecone, a cloud-based vector database. This
 * store supports creating, updating, deleting, and similarity searching of documents in a
 * Pinecone index.
 *
 * @author Christian Tzolov
 * @author Adam Bchouti
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author chabinhwang
 */
public class PineconeVectorStore extends AbstractObservationVectorStore {

	public static final String CONTENT_FIELD_NAME = "document_content";

	public final FilterExpressionConverter filterExpressionConverter = new PineconeFilterExpressionConverter();

	private final String pineconeNamespace;

	private final String pineconeIndexName;

	private final String pineconeContentFieldName;

	private final String pineconeDistanceMetadataFieldName;

	private final Pinecone pinecone;

	private static final Logger logger = LoggerFactory.getLogger(PineconeVectorStore.class);

	/**
	 * Creates a new PineconeVectorStore using the builder pattern.
	 * @param builder The configured builder instance
	 */
	protected PineconeVectorStore(Builder builder) {
		super(builder);

		Assert.hasText(builder.apiKey, "ApiKey must not be null or empty");
		Assert.hasText(builder.indexName, "IndexName must not be null or empty");

		this.pineconeNamespace = builder.namespace;
		this.pineconeIndexName = builder.indexName;
		this.pineconeContentFieldName = builder.contentFieldName;
		this.pineconeDistanceMetadataFieldName = builder.distanceMetadataFieldName;

		this.pinecone = new Pinecone.Builder(builder.apiKey).build();
	}

	/**
	 * Creates a new builder for constructing a PineconeVectorStore instance. This builder
	 * implements a type-safe step pattern that guides users through the required
	 * configuration fields in a specific order, followed by optional configurations.
	 *
	 * Required fields must be provided in this sequence:
	 * <ol>
	 * <li>embeddingModel (provided to this method)</li>
	 * <li>apiKey</li>
	 * <li>indexName</li>
	 * </ol>
	 *
	 * After all required fields are set, optional configurations can be added using the
	 * fluent builder pattern.
	 *
	 * Example usage: <pre>{@code
	 * PineconeVectorStore store = PineconeVectorStore.builder(embeddingModel)
	 *     .apiKey("your-api-key")
	 *     .indexName("your-index")
	 *     .namespace("optional")  // optional configuration
	 *     .build();
	 * }</pre>
	 * @param embeddingModel the embedding model to use for vector transformations
	 * @return the first step of the builder requiring API key configuration
	 * @throws IllegalArgumentException if embeddingModel is null
	 */
	public static Builder.BuilderWithApiKey builder(EmbeddingModel embeddingModel) {
		return Builder.StepBuilder.start(embeddingModel);
	}

	/**
	 * Adds a list of documents to the vector store based on the namespace.
	 * @param documents The list of documents to be added.
	 * @param namespace The namespace to add the documents to
	 */
	public void add(List<Document> documents, String namespace) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		List<VectorWithUnsignedIndices> upsertVectors = new ArrayList<>();
		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			upsertVectors.add(io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices(document.getId(),
					EmbeddingUtils.toList(embeddings.get(i)), null, null, metadataToStruct(document)));
		}
		this.pinecone.getIndexConnection(this.pineconeIndexName).upsert(upsertVectors, namespace);
	}

	/**
	 * Adds a list of documents to the vector store.
	 * @param documents The list of documents to be added.
	 */
	@Override
	public void doAdd(List<Document> documents) {
		add(documents, this.pineconeNamespace);
	}

	/**
	 * Converts the document metadata to a Protobuf Struct.
	 * @param document The document containing metadata.
	 * @return The metadata as a Protobuf Struct.
	 */
	private Struct metadataToStruct(Document document) {
		try {
			var structBuilder = Struct.newBuilder();
			JsonFormat.parser()
				.ignoringUnknownFields()
				.merge(JsonMapper.shared().writeValueAsString(document.getMetadata()), structBuilder);
			structBuilder.putFields(this.pineconeContentFieldName, contentValue(document));
			return structBuilder.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Retrieves the content value of a document.
	 * @param document The document.
	 * @return The content value.
	 */
	private Value contentValue(Document document) {
		return Value.newBuilder().setStringValue(Objects.requireNonNullElse(document.getText(), "")).build();
	}

	/**
	 * Deletes a list of documents by their IDs based on the namespace.
	 * @param documentIds The list of document IDs to be deleted.
	 * @param namespace The namespace of the document IDs.
	 */
	public void delete(List<String> documentIds, String namespace) {
		this.pinecone.getIndexConnection(this.pineconeIndexName).delete(documentIds, false, namespace, null);
	}

	/**
	 * Deletes a list of documents by their IDs.
	 * @param documentIds The list of document IDs to be deleted.
	 */
	@Override
	public void doDelete(List<String> documentIds) {
		delete(documentIds, this.pineconeNamespace);
	}

	public List<Document> similaritySearch(SearchRequest request, String namespace) {

		String nativeExpressionFilters = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());

		var queryRequestBuilder = QueryRequest.newBuilder()
			.addAllVector(EmbeddingUtils.toList(queryEmbedding))
			.setTopK(request.getTopK())
			.setIncludeMetadata(true)
			.setNamespace(namespace);

		if (StringUtils.hasText(nativeExpressionFilters)) {
			queryRequestBuilder.setFilter(metadataFiltersToStruct(nativeExpressionFilters));
		}

		QueryResponseWithUnsignedIndices queryResponse = this.pinecone.getIndexConnection(this.pineconeIndexName)
			.queryByVector(request.getTopK(), EmbeddingUtils.toList(queryEmbedding), namespace,
					metadataFiltersToStruct(nativeExpressionFilters), false, true);

		return queryResponse.getMatchesList()
			.stream()
			.filter(scoredVector -> scoredVector.getScore() >= request.getSimilarityThreshold())
			.map(scoredVector -> {
				var id = scoredVector.getId();
				Struct metadataStruct = scoredVector.getMetadata();
				var content = metadataStruct.getFieldsOrThrow(this.pineconeContentFieldName).getStringValue();
				Map<String, Object> metadata = extractMetadata(metadataStruct);
				metadata.put(this.pineconeDistanceMetadataFieldName, 1 - scoredVector.getScore());
				return Document.builder()
					.id(id)
					.text(content)
					.metadata(metadata)
					.score((double) scoredVector.getScore())
					.build();
			})
			.toList();
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			// Direct filter based deletion is not working in pinecone, so we are
			// retrieving the documents
			// by doing a similarity search with an empty query and then passing the ID's
			// of the documents to the delete(Id) API method.
			SearchRequest searchRequest = SearchRequest.builder()
				.query("") // empty query since we only want filter matches
				.filterExpression(filterExpression)
				.topK(10000) // large enough to get all matches
				.similarityThresholdAll()
				.build();

			List<Document> matchingDocs = similaritySearch(searchRequest, this.pineconeNamespace);

			if (!matchingDocs.isEmpty()) {
				// Then delete those documents by ID
				List<String> idsToDelete = matchingDocs.stream().map(Document::getId).collect(Collectors.toList());
				delete(idsToDelete, this.pineconeNamespace);
				logger.debug("Deleted {} documents matching filter expression", idsToDelete.size());
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter", e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		return similaritySearch(request, this.pineconeNamespace);
	}

	private Struct metadataFiltersToStruct(String metadataFilters) {
		try {
			var structBuilder = Struct.newBuilder();
			JsonFormat.parser().ignoringUnknownFields().merge(metadataFilters, structBuilder);
			return structBuilder.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Extracts metadata from a Protobuf Struct.
	 * @param metadataStruct The Protobuf Struct containing metadata.
	 * @return The metadata as a map.
	 */
	private Map<String, Object> extractMetadata(Struct metadataStruct) {
		try {
			String json = JsonFormat.printer().print(metadataStruct);
			Map<String, Object> metadata = JsonMapper.shared().readValue(json, new TypeReference<>() {

			});
			metadata.remove(this.pineconeContentFieldName);
			return metadata;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.PINECONE.value(), operationName)
			.collectionName(this.pineconeIndexName)
			.dimensions(this.embeddingModel.dimensions())
			.namespace(this.pineconeNamespace)
			.fieldName(this.pineconeContentFieldName);
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.pinecone;
		return Optional.of(client);
	}

	/**
	 * Builder class for creating {@link PineconeVectorStore} instances. This implements a
	 * type-safe step builder pattern to ensure all required fields are provided in a
	 * specific order before optional configuration.
	 *
	 * The required fields must be provided in this sequence: 1. embeddingModel (via
	 * builder method) 2. apiKey 3. indexName
	 *
	 * After all required fields are set, optional configurations can be provided using
	 * the fluent builder pattern.
	 *
	 * Example usage: <pre>{@code
	 * PineconeVectorStore store = PineconeVectorStore.builder(embeddingModel)
	 *     .apiKey("your-api-key")
	 *     .indexName("your-index")
	 *     .namespace("optional")  // optional configuration
	 *     .build();
	 * }</pre>
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		/** Required field for Pinecone API authentication */
		private final String apiKey;

		/** Required field specifying the Pinecone index name */
		private final String indexName;

		// Optional fields with default values
		private String namespace = "";

		private String contentFieldName = CONTENT_FIELD_NAME;

		private String distanceMetadataFieldName = DocumentMetadata.DISTANCE.value();

		private Builder(EmbeddingModel embeddingModel, String apiKey, String indexName) {
			super(embeddingModel);
			this.apiKey = apiKey;
			this.indexName = indexName;
		}

		/**
		 * Sets the Pinecone namespace. Note: The free-tier (gcp-starter) doesn't support
		 * Namespaces.
		 * @param namespace The namespace to use (leave empty for free tier)
		 * @return The builder instance
		 */
		public Builder namespace(@Nullable String namespace) {
			this.namespace = namespace != null ? namespace : "";
			return this;
		}

		/**
		 * Sets the content field name.
		 * @param contentFieldName The content field name to use
		 * @return The builder instance
		 */
		public Builder contentFieldName(@Nullable String contentFieldName) {
			this.contentFieldName = contentFieldName != null ? contentFieldName : CONTENT_FIELD_NAME;
			return this;
		}

		/**
		 * Sets the distance metadata field name.
		 * @param distanceMetadataFieldName The distance metadata field name to use
		 * @return The builder instance
		 */
		public Builder distanceMetadataFieldName(@Nullable String distanceMetadataFieldName) {
			this.distanceMetadataFieldName = distanceMetadataFieldName != null ? distanceMetadataFieldName
					: DocumentMetadata.DISTANCE.value();
			return this;
		}

		/**
		 * Builds a new PineconeVectorStore instance with the configured properties.
		 * @return A new PineconeVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		@Override
		public PineconeVectorStore build() {
			return new PineconeVectorStore(this);
		}

		/**
		 * First step interface requiring API key configuration.
		 */
		public interface BuilderWithApiKey {

			/**
			 * Sets the Pinecone API key and moves to index name configuration.
			 * @param apiKey The Pinecone API key
			 * @return The next builder step for index name
			 * @throws IllegalArgumentException if apiKey is null or empty
			 */
			BuilderWithIndexName apiKey(String apiKey);

		}

		/**
		 * Final step interface requiring index name configuration.
		 */
		public interface BuilderWithIndexName {

			/**
			 * Sets the index name and returns the builder for optional configuration.
			 * @param indexName The Pinecone index name
			 * @return The builder for optional configurations
			 * @throws IllegalArgumentException if indexName is null or empty
			 */
			Builder indexName(String indexName);

		}

		/**
		 * Internal implementation of the step builder pattern using records for
		 * immutability. Each step maintains the state from previous steps and implements
		 * the corresponding interface to ensure type safety and proper sequencing of the
		 * build steps.
		 */
		public static class StepBuilder {

			/**
			 * Initiates the step builder sequence with the embedding model.
			 * @param embeddingModel The embedding model to use
			 * @return The first step for API key configuration
			 * @throws IllegalArgumentException if embeddingModel is null
			 */
			static BuilderWithApiKey start(EmbeddingModel embeddingModel) {
				Assert.notNull(embeddingModel, "EmbeddingModel must not be null");
				return new ApiKeyStep(embeddingModel);
			}

			private record ApiKeyStep(EmbeddingModel embeddingModel) implements BuilderWithApiKey {
				@Override
				public BuilderWithIndexName apiKey(String apiKey) {
					Assert.hasText(apiKey, "ApiKey must not be null or empty");
					return new IndexNameStep(this.embeddingModel, apiKey);
				}
			}

			private record IndexNameStep(EmbeddingModel embeddingModel, String apiKey) implements BuilderWithIndexName {
				@Override
				public Builder indexName(String indexName) {
					Assert.hasText(indexName, "IndexName must not be null or empty");
					return new Builder(this.embeddingModel, this.apiKey, indexName);
				}
			}

		}

	}

}
