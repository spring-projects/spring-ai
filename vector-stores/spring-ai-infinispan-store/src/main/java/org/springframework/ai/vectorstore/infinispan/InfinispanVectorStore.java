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

package org.springframework.ai.vectorstore.infinispan;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.configuration.StringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.schema.Field;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

public class InfinispanVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	/**
	 * Default Store Name
	 */
	public static final String DEFAULT_STORE_NAME = "defaultStore";

	/**
	 * Default Cache Config
	 */
	public static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\">\n"
			+ "<indexing storage=\"local-heap\">\n" + "<indexed-entities>\n"
			+ "<indexed-entity>SPRING_AI_ITEM</indexed-entity>\n" + "</indexed-entities>\n" + "</indexing>\n"
			+ "</distributed-cache>";

	/**
	 * Default package of the schema
	 */
	public static final String DEFAULT_PACKAGE = "dev.spring_ai";

	/**
	 * Default name of the protobuf springAi item. Size will be added
	 */
	public static final String DEFAULT_ITEM_NAME = "SpringAiItem";

	/**
	 * Default name of the protobuf metadata item. Size will be added
	 */
	public static final String DEFAULT_METADATA_ITEM = "SpringAiMetadata";

	/**
	 * The default distance to for the search
	 */
	public static final int DEFAULT_DISTANCE = 3;

	/**
	 * Default vector similarity
	 */
	public static final String DEFAULT_SIMILARITY = VectorStoreSimilarityMetric.COSINE.value();

	private final RemoteCacheManager infinispanClient;

	private final String storeName;

	private final @Nullable String storeConfig;

	private final int distance;

	private final int dimension;

	private final String similarity;

	private final String schemaFileName;

	private final String packageName;

	private final String itemName;

	private final String metadataItemName;

	private final boolean registerSchema;

	private final boolean createStore;

	private final String itemFullName;

	private final String metadataFullName;

	private @Nullable RemoteCache<String, SpringAiInfinispanItem> remoteCache;

	protected InfinispanVectorStore(Builder builder) {
		super(builder);
		Assert.notNull(builder.infinispanClient, "infinispanClientBuilder must not be null");
		Assert.notNull(builder.dimension, "dimension must not be null");
		Assert.isTrue(builder.distance == null || (builder.distance != null && builder.distance > 0),
				"provided distance must be greater than 0");
		this.infinispanClient = builder.infinispanClient;
		this.dimension = builder.dimension;
		this.storeConfig = builder.storeConfig;
		this.createStore = builder.createStore == null ? true : builder.createStore;
		this.storeName = builder.storeName == null ? DEFAULT_STORE_NAME : builder.storeName;
		this.distance = builder.distance == null ? DEFAULT_DISTANCE : builder.distance;
		this.similarity = builder.similarity == null ? DEFAULT_SIMILARITY : builder.similarity;
		this.packageName = builder.packageName == null ? DEFAULT_PACKAGE : builder.packageName;
		this.itemName = builder.itemName == null ? DEFAULT_ITEM_NAME : builder.itemName;
		this.metadataItemName = builder.metadataItemName == null ? DEFAULT_METADATA_ITEM : builder.metadataItemName;
		this.registerSchema = builder.registerSchema == null ? true : builder.registerSchema;
		this.schemaFileName = getSchemaFileName(builder);
		this.itemFullName = computeProtoFullName(this.itemName);
		this.metadataFullName = computeProtoFullName(this.metadataItemName);
	}

	private String getSchemaFileName(Builder builder) {
		if (builder.schemaFileName != null) {
			return builder.schemaFileName;
		}
		return builder.packageName + "." + "dimension." + builder.dimension + ".proto";
	}

	private String computeProtoFullName(String name) {
		return this.packageName + "." + name;
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(this.remoteCache, "remoteCache must not be null");
		Map<String, SpringAiInfinispanItem> elements = new HashMap<>(documents.size());
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		for (int i = 0; i < embeddings.size(); i++) {
			Document document = documents.get(i);
			float[] vector = embeddings.get(i);
			Set<SpringAiMetadata> metadataSet = document.getMetadata()
				.entrySet()
				.stream()
				.map(e -> new SpringAiMetadata(e.getKey(), e.getValue()))
				.collect(Collectors.toSet());
			elements.put(document.getId(), new SpringAiInfinispanItem(document.getId(), document.getText(), metadataSet,
					vector, document.getMetadata()));
		}

		this.remoteCache.putAll(elements);
	}

	@Override
	public void doDelete(List<String> idList) {
		Assert.notNull(this.remoteCache, "remoteCache must not be null");
		if (idList == null || idList.isEmpty()) {
			throw new IllegalArgumentException("ids cannot be null or empty");
		}

		for (String id : idList) {
			this.remoteCache.remove(id);
		}
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(this.remoteCache, "remoteCache must not be null");
		InfinispanFilterExpressionConverter filterExpressionConverter = new InfinispanFilterExpressionConverter();
		String filteringPart = filterExpressionConverter.convertExpression(filterExpression);
		String joinPart = filterExpressionConverter.doJoin();
		String deleteQuery = "DELETE FROM " + this.itemFullName + " i " + joinPart + " where " + filteringPart;
		Query<SpringAiInfinispanItem> query = this.remoteCache.query(deleteQuery);
		query.execute();
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(this.remoteCache, "remoteCache must not be null");
		String joinPart = "";
		String filteringPart = "";

		if (searchRequest.hasFilterExpression() && searchRequest.getFilterExpression() != null) {
			InfinispanFilterExpressionConverter filterExpressionConverter = new InfinispanFilterExpressionConverter();
			filteringPart = "filtering("
					+ filterExpressionConverter.convertExpression(searchRequest.getFilterExpression()) + ")";
			joinPart = filterExpressionConverter.doJoin();
		}

		var embedding = this.embeddingModel.embed(searchRequest.getQuery());
		String vectorQuery = "select i, score(i) from " + this.itemFullName + " i " + joinPart
				+ " where i.embedding <-> " + Arrays.toString(embedding) + "~" + this.distance + " " + filteringPart;

		Query<Object[]> query = this.remoteCache.query(vectorQuery);
		List<Object[]> hits = query.maxResults(searchRequest.getTopK()).list();

		return hits.stream().map(obj -> {
			SpringAiInfinispanItem item = (SpringAiInfinispanItem) obj[0];
			Float score = (Float) obj[1];
			if (score.doubleValue() < searchRequest.getSimilarityThreshold()) {
				return null;
			}

			return Document.builder()
				.id(item.id())
				.text(item.text())
				.metadata(item.metadataMap())
				.score(score.doubleValue())
				.build();
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Override
	public void afterPropertiesSet() {
		Schema schema = buildSchema();
		// Register the schema and marshaller on client side
		ProtoStreamMarshaller marshaller = (ProtoStreamMarshaller) this.infinispanClient.getMarshallerRegistry()
			.getMarshaller(ProtoStreamMarshaller.class);
		if (marshaller == null) {
			throw new IllegalStateException("ProtoStreamMarshaller not found");
		}
		marshaller.register(schema, new SpringAiMetadataMarshaller(this.metadataFullName),
				new SpringAiItemMarshaller(this.itemFullName));

		// Uploads the schema to the server, if necessary
		if (this.registerSchema) {
			this.infinispanClient.administration().schemas().createOrUpdate(schema);
		}

		// Check if the schema is present
		if (this.infinispanClient.administration().schemas().get(this.schemaFileName).isEmpty()) {
			throw new IllegalStateException("SpringAI Schema '" + this.schemaFileName + "' not found");
		}
		ProtoStreamMarshaller finalMarshaller = marshaller;
		// Make sure the marshaller is Protostream on the client side
		this.infinispanClient.getConfiguration().addRemoteCache(this.storeName, c -> c.marshaller(finalMarshaller));

		// Get the underlying infinispan remote cache where the embeddings are stored
		this.remoteCache = this.infinispanClient.getCache(this.storeName);
		if (this.remoteCache == null && this.createStore) {
			String infinispanCacheConfig = this.storeConfig;
			if (infinispanCacheConfig == null) {
				infinispanCacheConfig = DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", this.storeName)
					.replace("SPRING_AI_ITEM", this.itemFullName);
			}
			this.remoteCache = this.infinispanClient.administration()
				.getOrCreateCache(this.storeName, new StringConfiguration(infinispanCacheConfig));
		}

		if (this.remoteCache == null) {
			throw new IllegalStateException("Infinispan Cache '" + this.storeName + "' not found");
		}
	}

	private Schema buildSchema() {
		Field.Builder schemaBuilder = new Schema.Builder(this.schemaFileName).packageName(this.packageName)
			// Medata Item
			.addMessage(this.metadataItemName)
			.addComment("@Indexed")
			.addField(Type.Scalar.STRING, "name", 1)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.STRING, "value", 2)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.INT64, "value_int", 3)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.DOUBLE, "value_float", 4)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.BOOL, "value_bool", 5)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.FIXED64, "value_date", 6)
			.addComment("@Basic(projectable=true)")
			// SpringAi item
			.addMessage(this.itemName)
			.addComment("@Indexed")
			.addField(Type.Scalar.STRING, "id", 1)
			.addComment("@Basic(projectable=true)")
			.addField(Type.Scalar.STRING, "text", 2)
			.addComment("@Basic(projectable=true)");

		// Add metadata field
		schemaBuilder.addRepeatedField(Type.create(this.metadataItemName), "metadata", 3).addComment("@Embedded");

		// Add embedding
		schemaBuilder.addRepeatedField(Type.Scalar.FLOAT, "embedding", 4)
			.addComment(String.format("@Vector(dimension=%d, similarity=%s)", this.dimension,
					this.similarity.toUpperCase()));
		return schemaBuilder.build();
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.INFINISPAN.value(), operationName)
			.collectionName(this.storeName)
			.dimensions(this.embeddingModel.dimensions())
			.similarityMetric(this.similarity);
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.remoteCache;
		return Optional.ofNullable(client);
	}

	/**
	 * Creates a new builder instance for InfinispanVectorStore.
	 * @return a new InfinispanBuilder instance
	 */
	public static Builder builder(RemoteCacheManager infinispanClient, EmbeddingModel embeddingModel) {
		return new Builder(infinispanClient, embeddingModel);
	}

	public void clear() {
		Assert.notNull(this.remoteCache, "remoteCache must not be null");
		this.remoteCache.clear();
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private RemoteCacheManager infinispanClient;

		@Nullable private final Integer dimension;

		@Nullable private Boolean createStore;

		@Nullable private String storeName;

		@Nullable private String storeConfig;

		@Nullable private Integer distance;

		@Nullable private String similarity;

		// Schema properties
		@Nullable private String schemaFileName;

		@Nullable private String packageName;

		@Nullable private String itemName;

		@Nullable private String metadataItemName;

		@Nullable private Boolean registerSchema;

		/**
		 * Infinispan store name to be used, will be created on first access
		 */
		public Builder storeName(String name) {
			this.storeName = name;
			return this;
		}

		/**
		 * Infinispan cache config to be used, will be created on first access
		 */
		public Builder storeConfig(String storeConfig) {
			this.storeConfig = storeConfig;
			return this;
		}

		/**
		 * Infinispan distance for knn query
		 */
		public Builder distance(Integer distance) {
			this.distance = distance;
			return this;
		}

		/**
		 * Infinispan similarity for the embedding definition
		 */
		public Builder similarity(String similarity) {
			this.similarity = similarity;
			return this;
		}

		/**
		 * Infinispan schema package name
		 */
		public Builder packageName(String packageName) {
			this.packageName = packageName;
			return this;
		}

		/**
		 * Infinispan schema itemName
		 */
		public Builder springAiItemName(String itemName) {
			this.itemName = itemName;
			return this;
		}

		/**
		 * Infinispan schema metadataItemName
		 */
		public Builder metadataItemName(String metadataItemName) {
			this.metadataItemName = metadataItemName;
			return this;
		}

		/**
		 * Register Langchain schema in the server
		 */
		public Builder registerSchema(Boolean registerSchema) {
			this.registerSchema = registerSchema;
			return this;
		}

		/**
		 * Create store in the server
		 */
		public Builder createStore(Boolean create) {
			this.createStore = create;
			return this;
		}

		/**
		 * Schema file name in the server
		 */
		public Builder schemaFileName(String schemaFileName) {
			this.schemaFileName = schemaFileName;
			return this;
		}

		/**
		 * Sets the Infinispan Hot Rod client.
		 * @param infinispanClient infinispan client
		 * @param embeddingModel the Embedding Model to be used
		 */
		public Builder(RemoteCacheManager infinispanClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(infinispanClient, "infinispanClient must not be null");
			this.infinispanClient = infinispanClient;
			this.dimension = embeddingModel.dimensions();
		}

		/**
		 * Builds the InfinispanVectorStore instance.
		 * @return a new InfinispanVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		@Override
		public InfinispanVectorStore build() {
			return new InfinispanVectorStore(this);
		}

	}

}
