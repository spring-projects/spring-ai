/*
 * Copyright 2023-2024 the original author or authors.
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.converter.PineconeFilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

/**
 * A VectorStore implementation backed by Pinecone, a cloud-based vector database. This
 * store supports creating, updating, deleting, and similarity searching of documents in a
 * Pinecone index.
 *
 * @author Christian Tzolov
 * @author Adam Bchouti
 * @author Soby Chacko
 */
public class PineconeVectorStore extends AbstractObservationVectorStore {

	public static final String CONTENT_FIELD_NAME = "document_content";

	public static final String DISTANCE_METADATA_FIELD_NAME = "distance";

	public final FilterExpressionConverter filterExpressionConverter = new PineconeFilterExpressionConverter();

	private final EmbeddingModel embeddingModel;

	private final PineconeConnection pineconeConnection;

	private final String pineconeNamespace;

	private final String pineconeIndexName;

	private final String pineconeContentFieldName;

	private final String pineconeDistanceMetadataFieldName;

	private final ObjectMapper objectMapper;

	private final BatchingStrategy batchingStrategy;

	/**
	 * Configuration class for the PineconeVectorStore.
	 */
	public static final class PineconeVectorStoreConfig {

		// The free tier (gcp-starter) doesn't support Namespaces.
		// Leave the namespace empty (e.g. "") for the free tier.
		private final String namespace;

		private final String contentFieldName;

		private final String distanceMetadataFieldName;

		private final PineconeConnectionConfig connectionConfig;

		private final PineconeClientConfig clientConfig;

		// private final int defaultSimilarityTopK;

		/**
		 * Constructor using the builder.
		 * @param builder The configuration builder.
		 */
		/**
		 * Constructor using the builder.
		 * @param builder The configuration builder.
		 */
		public PineconeVectorStoreConfig(Builder builder) {
			this.namespace = builder.namespace;
			this.contentFieldName = builder.contentFieldName;
			this.distanceMetadataFieldName = builder.distanceMetadataFieldName;

			// this.defaultSimilarityTopK = builder.defaultSimilarityTopK;
			this.connectionConfig = new PineconeConnectionConfig().withIndexName(builder.indexName);
			this.clientConfig = new PineconeClientConfig().withApiKey(builder.apiKey)
				.withEnvironment(builder.environment)
				.withProjectName(builder.projectId)
				.withApiKey(builder.apiKey)
				.withServerSideTimeoutSec((int) builder.serverSideTimeout.toSeconds());
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * {@return the default config}
		 */
		public static PineconeVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		public static class Builder {

			private String apiKey;

			private String projectId;

			private String environment;

			private String indexName;

			// The free-tier (gcp-starter) doesn't support Namespaces!
			private String namespace = "";

			private String contentFieldName = CONTENT_FIELD_NAME;

			private String distanceMetadataFieldName = DISTANCE_METADATA_FIELD_NAME;

			/**
			 * Optional server-side timeout in seconds for all operations. Default: 20
			 * seconds.
			 */
			private Duration serverSideTimeout = Duration.ofSeconds(20);

			private Builder() {
			}

			/**
			 * Pinecone api key.
			 * @param apiKey key to use.
			 * @return this builder.
			 */
			public Builder withApiKey(String apiKey) {
				this.apiKey = apiKey;
				return this;
			}

			/**
			 * Pinecone project id.
			 * @param projectId Project id to use.
			 * @return this builder.
			 */
			public Builder withProjectId(String projectId) {
				this.projectId = projectId;
				return this;
			}

			/**
			 * Pinecone environment name.
			 * @param environment Environment name (e.g. gcp-starter).
			 * @return this builder.
			 */
			public Builder withEnvironment(String environment) {
				this.environment = environment;
				return this;
			}

			/**
			 * Pinecone index name.
			 * @param indexName Pinecone index name to use.
			 * @return this builder.
			 */
			public Builder withIndexName(String indexName) {
				this.indexName = indexName;
				return this;
			}

			/**
			 * Pinecone Namespace. The free-tier (gcp-starter) doesn't support Namespaces.
			 * For free-tier leave the namespace empty.
			 * @param namespace Pinecone namespace to use.
			 * @return this builder.
			 */
			public Builder withNamespace(String namespace) {
				this.namespace = namespace;
				return this;
			}

			/**
			 * Content field name.
			 * @param contentFieldName content field name to use.
			 * @return this builder.
			 */
			public Builder withContentFieldName(String contentFieldName) {
				this.contentFieldName = contentFieldName;
				return this;
			}

			/**
			 * Distance metadata field name.
			 * @param distanceMetadataFieldName distance metadata field name to use.
			 * @return this builder.
			 */
			public Builder withDistanceMetadataFieldName(String distanceMetadataFieldName) {
				this.distanceMetadataFieldName = distanceMetadataFieldName;
				return this;
			}

			/**
			 * Pinecone server side timeout.
			 * @param serverSideTimeout server timeout to use.
			 * @return this builder.
			 */
			public Builder withServerSideTimeout(Duration serverSideTimeout) {
				this.serverSideTimeout = serverSideTimeout;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 */
			public PineconeVectorStoreConfig build() {
				return new PineconeVectorStoreConfig(this);
			}

		}

	}

	/**
	 * Constructs a new PineconeVectorStore.
	 * @param config The configuration for the store.
	 * @param embeddingModel The client for embedding operations.
	 */
	public PineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel) {
		this(config, embeddingModel, ObservationRegistry.NOOP, null, new TokenCountBatchingStrategy());
	}

	/**
	 * Constructs a new PineconeVectorStore.
	 * @param config The configuration for the store.
	 * @param embeddingModel The client for embedding operations.
	 * @param observationRegistry The registry for observations.
	 * @param customObservationConvention The custom observation convention.
	 */
	public PineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel,
			ObservationRegistry observationRegistry, VectorStoreObservationConvention customObservationConvention,
			BatchingStrategy batchingStrategy) {
		super(observationRegistry, customObservationConvention);
		Assert.notNull(config, "PineconeVectorStoreConfig must not be null");
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");

		this.embeddingModel = embeddingModel;
		this.pineconeNamespace = config.namespace;
		this.pineconeIndexName = config.connectionConfig.getIndexName();
		this.pineconeContentFieldName = config.contentFieldName;
		this.pineconeDistanceMetadataFieldName = config.distanceMetadataFieldName;
		this.pineconeConnection = new PineconeClient(config.clientConfig).connect(config.connectionConfig);
		this.objectMapper = new ObjectMapper();
		this.batchingStrategy = batchingStrategy;
	}

	/**
	 * Adds a list of documents to the vector store based on the namespace.
	 * @param documents The list of documents to be added.
	 * @param namespace The namespace to add the documents to
	 */
	public void add(List<Document> documents, String namespace) {
		this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);
		List<Vector> upsertVectors = documents.stream()
			.map(document -> Vector.newBuilder()
				.setId(document.getId())
				.addAllValues(EmbeddingUtils.toList(document.getEmbedding()))
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
		return Value.newBuilder().setStringValue(document.getContent()).build();
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
				return new Document(id, content, metadata);
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
			var filterStruct = structBuilder.build();
			return filterStruct;
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
			.withCollectionName(this.pineconeIndexName)
			.withDimensions(this.embeddingModel.dimensions())
			.withNamespace(this.pineconeNamespace)
			.withFieldName(this.pineconeContentFieldName);
	}

}
