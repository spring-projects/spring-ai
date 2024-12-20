/*
 * Copyright 2023-2024 the original author or authors.
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.micrometer.observation.ObservationRegistry;
import io.pinecone.PineconeClient;
import io.pinecone.PineconeClientConfig;
import io.pinecone.PineconeConnection;
import io.pinecone.PineconeConnectionConfig;
import io.pinecone.proto.DeleteRequest;
import io.pinecone.proto.QueryRequest;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.UpsertRequest;
import io.pinecone.proto.Vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.converter.PineconeFilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;
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
 */
public class PineconeVectorStore extends AbstractObservationVectorStore {

	public static final String CONTENT_FIELD_NAME = "document_content";

	public final FilterExpressionConverter filterExpressionConverter = new PineconeFilterExpressionConverter();

	private final PineconeConnection pineconeConnection;

	private final String pineconeNamespace;

	private final String pineconeIndexName;

	private final String pineconeContentFieldName;

	private final String pineconeDistanceMetadataFieldName;

	private final ObjectMapper objectMapper;

	private final BatchingStrategy batchingStrategy;

	/**
	 * Constructs a new PineconeVectorStore.
	 * @deprecated Use {@link #builder(EmbeddingModel, String, String, String, String)}
	 * ()} instead
	 * @param config The configuration for the store
	 * @param embeddingModel The client for embedding operations
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public PineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel) {
		this(config, embeddingModel, ObservationRegistry.NOOP, null, new TokenCountBatchingStrategy());
	}

	/**
	 * Constructs a new PineconeVectorStore.
	 * @deprecated Use {@link #builder(EmbeddingModel, String, String, String, String)}
	 * ()} instead
	 * @param config The configuration for the store
	 * @param embeddingModel The client for embedding operations
	 * @param observationRegistry The registry for observations
	 * @param customObservationConvention The custom observation convention
	 * @param batchingStrategy The strategy for batching operations
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public PineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel,
			ObservationRegistry observationRegistry, VectorStoreObservationConvention customObservationConvention,
			BatchingStrategy batchingStrategy) {
		this(builder(embeddingModel, config.clientConfig.getApiKey(), config.clientConfig.getProjectName(),
				config.clientConfig.getEnvironment(), config.connectionConfig.getIndexName())
			.namespace(config.namespace)
			.contentFieldName(config.contentFieldName)
			.distanceMetadataFieldName(config.distanceMetadataFieldName)
			.serverSideTimeout(Duration.ofSeconds(config.clientConfig.getServerSideTimeoutSec()))
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention)
			.batchingStrategy(batchingStrategy));
	}

	/**
	 * Creates a new PineconeVectorStore using the builder pattern.
	 * @param builder The configured builder instance
	 */
	protected PineconeVectorStore(PineconeBuilder builder) {
		super(builder);

		Assert.hasText(builder.apiKey, "ApiKey must not be null or empty");
		Assert.hasText(builder.projectId, "ProjectId must not be null or empty");
		Assert.hasText(builder.environment, "Environment must not be null or empty");
		Assert.hasText(builder.indexName, "IndexName must not be null or empty");

		this.pineconeNamespace = builder.namespace;
		this.pineconeIndexName = builder.indexName;
		this.pineconeContentFieldName = builder.contentFieldName;
		this.pineconeDistanceMetadataFieldName = builder.distanceMetadataFieldName;

		PineconeClientConfig clientConfig = new PineconeClientConfig().withApiKey(builder.apiKey)
			.withEnvironment(builder.environment)
			.withProjectName(builder.projectId)
			.withServerSideTimeoutSec((int) builder.serverSideTimeout.toSeconds());

		PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig().withIndexName(builder.indexName);

		this.pineconeConnection = new PineconeClient(clientConfig).connect(connectionConfig);
		this.objectMapper = new ObjectMapper();
		this.batchingStrategy = builder.batchingStrategy;
	}

	/**
	 * Creates a new builder instance for configuring a PineconeVectorStore.
	 * @return A new PineconeBuilder instance
	 */
	public static PineconeBuilder builder(EmbeddingModel embeddingModel, String apiKey, String projectId,
			String environment, String indexName) {
		return new PineconeBuilder(embeddingModel, apiKey, projectId, environment, indexName);
	}

	/**
	 * Adds a list of documents to the vector store based on the namespace.
	 * @param documents The list of documents to be added.
	 * @param namespace The namespace to add the documents to
	 */
	public void add(List<Document> documents, String namespace) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);
		List<Vector> upsertVectors = documents.stream()
			.map(document -> Vector.newBuilder()
				.setId(document.getId())
				.addAllValues(EmbeddingUtils.toList(embeddings.get(documents.indexOf(document))))
				.setMetadata(metadataToStruct(document))
				.build())
			.toList();

		UpsertRequest upsertRequest = UpsertRequest.newBuilder()
			.addAllVectors(upsertVectors)
			.setNamespace(namespace)
			.build();

		this.pineconeConnection.getBlockingStub().upsert(upsertRequest);
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
				.merge(this.objectMapper.writeValueAsString(document.getMetadata()), structBuilder);
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
		return Value.newBuilder().setStringValue(document.getText()).build();
	}

	/**
	 * Deletes a list of documents by their IDs based on the namespace.
	 * @param documentIds The list of document IDs to be deleted.
	 * @param namespace The namespace of the document IDs.
	 * @return An optional boolean indicating the deletion status.
	 */
	public Optional<Boolean> delete(List<String> documentIds, String namespace) {

		DeleteRequest deleteRequest = DeleteRequest.newBuilder()
			.setNamespace(namespace) // ignored for free tier.
			.addAllIds(documentIds)
			.setDeleteAll(false)
			.build();

		this.pineconeConnection.getBlockingStub().delete(deleteRequest);

		// The Pinecone delete API does not provide deletion status info.
		return Optional.of(true);
	}

	/**
	 * Deletes a list of documents by their IDs.
	 * @param documentIds The list of document IDs to be deleted.
	 * @return An optional boolean indicating the deletion status.
	 */
	@Override
	public Optional<Boolean> doDelete(List<String> documentIds) {
		return delete(documentIds, this.pineconeNamespace);
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

		QueryResponse queryResponse = this.pineconeConnection.getBlockingStub().query(queryRequestBuilder.build());

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
			Map<String, Object> metadata = this.objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {

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

	/**
	 * Builder class for creating PineconeVectorStore instances.
	 */
	public static class PineconeBuilder extends AbstractVectorStoreBuilder<PineconeBuilder> {

		private final String apiKey;

		private final String projectId;

		private final String environment;

		private final String indexName;

		private String namespace = "";

		private String contentFieldName = CONTENT_FIELD_NAME;

		private String distanceMetadataFieldName = DocumentMetadata.DISTANCE.value();

		private Duration serverSideTimeout = Duration.ofSeconds(20);

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		private PineconeBuilder(EmbeddingModel embeddingModel, String apiKey, String projectId, String environment,
				String indexName) {
			super(embeddingModel);

			Assert.hasText(apiKey, "ApiKey must not be null or empty");
			Assert.hasText(projectId, "ProjectId must not be null or empty");
			Assert.hasText(environment, "Environment must not be null or empty");
			Assert.hasText(indexName, "IndexName must not be null or empty");

			this.apiKey = apiKey;
			this.projectId = projectId;
			this.environment = environment;
			this.indexName = indexName;
		}

		/**
		 * Sets the Pinecone namespace. Note: The free-tier (gcp-starter) doesn't support
		 * Namespaces.
		 * @param namespace The namespace to use (leave empty for free tier)
		 * @return The builder instance
		 */
		public PineconeBuilder namespace(@Nullable String namespace) {
			this.namespace = namespace != null ? namespace : "";
			return this;
		}

		/**
		 * Sets the content field name.
		 * @param contentFieldName The content field name to use
		 * @return The builder instance
		 */
		public PineconeBuilder contentFieldName(@Nullable String contentFieldName) {
			this.contentFieldName = contentFieldName != null ? contentFieldName : CONTENT_FIELD_NAME;
			return this;
		}

		/**
		 * Sets the distance metadata field name.
		 * @param distanceMetadataFieldName The distance metadata field name to use
		 * @return The builder instance
		 */
		public PineconeBuilder distanceMetadataFieldName(@Nullable String distanceMetadataFieldName) {
			this.distanceMetadataFieldName = distanceMetadataFieldName != null ? distanceMetadataFieldName
					: DocumentMetadata.DISTANCE.value();
			return this;
		}

		/**
		 * Sets the server-side timeout.
		 * @param serverSideTimeout The timeout duration to use
		 * @return The builder instance
		 */
		public PineconeBuilder serverSideTimeout(@Nullable Duration serverSideTimeout) {
			this.serverSideTimeout = serverSideTimeout != null ? serverSideTimeout : Duration.ofSeconds(20);
			return this;
		}

		/**
		 * Sets the batching strategy.
		 * @param batchingStrategy The batching strategy to use
		 * @return The builder instance
		 * @throws IllegalArgumentException if batchingStrategy is null
		 */
		public PineconeBuilder batchingStrategy(BatchingStrategy batchingStrategy) {
			Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
			this.batchingStrategy = batchingStrategy;
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

	}

	/**
	 * Configuration for PineconeVectorStore.
	 *
	 * @deprecated Use
	 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
	 * ()} instead. This class will be removed in a future release as part of the
	 * migration to the builder pattern.
	 * @since 1.0.0
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public static final class PineconeVectorStoreConfig {

		// The free tier (gcp-starter) doesn't support Namespaces.
		// Leave the namespace empty (e.g. "") for the free tier.
		private final String namespace;

		private final String contentFieldName;

		// TODO: Why is this field configurable? Can we remove this after standardizing
		// the key?
		private final String distanceMetadataFieldName;

		private final PineconeConnectionConfig connectionConfig;

		private final PineconeClientConfig clientConfig;

		/**
		 * Constructor using the builder.
		 * @param builder The configuration builder
		 * @deprecated Use
		 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
		 * ()} instead
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public PineconeVectorStoreConfig(Builder builder) {
			this.namespace = builder.namespace;
			this.contentFieldName = builder.contentFieldName;
			this.distanceMetadataFieldName = builder.distanceMetadataFieldName;

			this.connectionConfig = new PineconeConnectionConfig().withIndexName(builder.indexName);
			this.clientConfig = new PineconeClientConfig().withApiKey(builder.apiKey)
				.withEnvironment(builder.environment)
				.withProjectName(builder.projectId)
				.withServerSideTimeoutSec((int) builder.serverSideTimeout.toSeconds());
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration
		 * @deprecated Use
		 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
		 * ()} instead
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * {@return the default config}
		 * @deprecated Use
		 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
		 * ()} instead
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static PineconeVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		/**
		 * Builder for PineconeVectorStoreConfig.
		 *
		 * @deprecated Use
		 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
		 * ()} instead. This class will be removed in a future release as part of the
		 * migration to the builder pattern.
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static final class Builder {

			private String apiKey;

			private String projectId;

			private String environment;

			private String indexName;

			// The free-tier (gcp-starter) doesn't support Namespaces!
			private String namespace = "";

			private String contentFieldName = CONTENT_FIELD_NAME;

			private String distanceMetadataFieldName = DocumentMetadata.DISTANCE.value();

			/**
			 * Optional server-side timeout in seconds for all operations. Default: 20
			 * seconds.
			 */
			private Duration serverSideTimeout = Duration.ofSeconds(20);

			private Builder() {
			}

			/**
			 * Pinecone api key.
			 * @param apiKey key to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withApiKey(String apiKey) {
				this.apiKey = apiKey;
				return this;
			}

			/**
			 * Pinecone project id.
			 * @param projectId Project id to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withProjectId(String projectId) {
				this.projectId = projectId;
				return this;
			}

			/**
			 * Pinecone environment name.
			 * @param environment Environment name (e.g. gcp-starter)
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withEnvironment(String environment) {
				this.environment = environment;
				return this;
			}

			/**
			 * Pinecone index name.
			 * @param indexName Pinecone index name to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withIndexName(String indexName) {
				this.indexName = indexName;
				return this;
			}

			/**
			 * Pinecone Namespace. The free-tier (gcp-starter) doesn't support Namespaces.
			 * For free-tier leave the namespace empty.
			 * @param namespace Pinecone namespace to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withNamespace(String namespace) {
				this.namespace = namespace;
				return this;
			}

			/**
			 * Content field name.
			 * @param contentFieldName content field name to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withContentFieldName(String contentFieldName) {
				this.contentFieldName = contentFieldName;
				return this;
			}

			/**
			 * Distance metadata field name.
			 * @param distanceMetadataFieldName distance metadata field name to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withDistanceMetadataFieldName(String distanceMetadataFieldName) {
				this.distanceMetadataFieldName = distanceMetadataFieldName;
				return this;
			}

			/**
			 * Pinecone server side timeout.
			 * @param serverSideTimeout server timeout to use
			 * @return this builder
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public Builder withServerSideTimeout(Duration serverSideTimeout) {
				this.serverSideTimeout = serverSideTimeout;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 * @deprecated Use
			 * {@link PineconeVectorStore#builder(EmbeddingModel, String, String, String, String)}
			 * ()} instead
			 */
			@Deprecated(since = "1.0.0-M5", forRemoval = true)
			public PineconeVectorStoreConfig build() {
				return new PineconeVectorStoreConfig(this);
			}

		}

	}

}
