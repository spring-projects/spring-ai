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

package org.springframework.ai.vectorstore.s3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListOutputVector;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @author Jewoo Shin
 */
public class S3VectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final int LIST_VECTORS_MAX_RESULTS = 500;

	private static final int DELETE_VECTORS_MAX_KEYS = 500;

	private final S3VectorsClient s3VectorsClient;

	private final String vectorBucketName;

	private final String indexName;

	private final S3VectorFilterExpressionConverter filterExpressionConverter;

	private final S3VectorStoreFilterExpressionEvaluator filterExpressionEvaluator;

	/**
	 * Creates a new S3VectorStore instance with the specified builder settings.
	 * Initializes observation-related components and the embedding model.
	 * @param builder the builder containing configuration settings
	 */
	protected S3VectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.vectorBucketName, "vectorBucketName must not be null");
		Assert.notNull(builder.indexName, "indexName must not be null");
		Assert.notNull(builder.s3VectorsClient, "S3VectorsClient must not be null");

		this.s3VectorsClient = builder.s3VectorsClient;
		this.indexName = builder.indexName;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.filterExpressionEvaluator = new S3VectorStoreFilterExpressionEvaluator();
		this.vectorBucketName = builder.vectorBucketName;
	}

	private static software.amazon.awssdk.core.document.Document constructMetadata(
			Map<String, Object> originalMetadata) {
		Map<String, software.amazon.awssdk.core.document.Document> metadata = new HashMap<>(originalMetadata.size());
		originalMetadata.forEach((k, v) -> metadata.put(k, DocumentUtils.toDocument(v)));
		return software.amazon.awssdk.core.document.Document.fromMap(metadata);
	}

	private static VectorData constructVectorData(float[] embedding) {
		ArrayList<Float> float32 = new ArrayList<>(embedding.length);
		for (float v : embedding) {
			float32.add(v);
		}
		return VectorData.builder().float32(float32).build();
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "The document list should not be null.");
		this.doAdd(documents, EmbeddingOptions.builder().build());
	}

	@Override
	public void doAdd(List<Document> documents, EmbeddingOptions options) {
		Assert.notNull(documents, "The document list should not be null.");
		Assert.notNull(options, "The embedding Options should not be null.");

		List<float[]> embedding = this.embeddingModel.embed(documents, options, this.batchingStrategy);

		PutVectorsRequest.Builder requestBuilder = PutVectorsRequest.builder();
		requestBuilder.indexName(this.indexName).vectorBucketName(this.vectorBucketName);

		List<PutInputVector> vectors = new ArrayList<>(documents.size());
		for (Document document : documents) {
			float[] embs = embedding.get(documents.indexOf(document));
			VectorData vectorData = constructVectorData(embs);
			vectors.add(PutInputVector.builder()
				.data(vectorData)
				.key(document.getId())
				.metadata(constructMetadata(document.getMetadata()))
				.build());
		}
		requestBuilder.vectors(vectors);
		this.s3VectorsClient.putVectors(requestBuilder.build());
	}

	@Override
	public void doDelete(List<String> idList) {
		deleteVectors(idList);
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		String nextToken = null;
		List<String> keys = new ArrayList<>();
		do {
			ListVectorsRequest.Builder requestBuilder = ListVectorsRequest.builder()
				.vectorBucketName(this.vectorBucketName)
				.indexName(this.indexName)
				.maxResults(LIST_VECTORS_MAX_RESULTS)
				.returnMetadata(true)
				.returnData(false);

			if (StringUtils.hasText(nextToken)) {
				requestBuilder.nextToken(nextToken);
			}

			ListVectorsResponse response = this.s3VectorsClient.listVectors(requestBuilder.build());
			for (ListOutputVector vector : response.vectors()) {
				if (matchesFilter(vector, filterExpression)) {
					keys.add(vector.key());
				}
			}
			nextToken = response.nextToken();
		}
		while (StringUtils.hasText(nextToken));

		deleteVectors(keys);
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");

		QueryVectorsRequest.Builder requestBuilder = QueryVectorsRequest.builder()
			.indexName(this.indexName)
			.vectorBucketName(this.vectorBucketName)
			.topK(searchRequest.getTopK())
			.returnMetadata(true)
			.returnDistance(true);

		if (searchRequest.hasFilterExpression()) {
			Filter.Expression filterExpression = Objects.requireNonNull(searchRequest.getFilterExpression());
			software.amazon.awssdk.core.document.Document filter = this.filterExpressionConverter
				.convertExpression(filterExpression);
			requestBuilder.filter(filter);
		}

		float[] embeddings = this.embeddingModel.embed(searchRequest.getQuery());
		VectorData vectorData = constructVectorData(embeddings);
		requestBuilder.queryVector(vectorData);

		QueryVectorsResponse response = this.s3VectorsClient.queryVectors(requestBuilder.build());
		return response.vectors().stream().map(this::toDocument).toList();
	}

	private Document toDocument(QueryOutputVector vector) {
		Map<String, Object> metadata = DocumentUtils.fromDocument(vector.metadata());
		if (metadata == null) {
			metadata = new HashMap<>();
		}
		if (vector.distance() != null) {
			metadata.put("SPRING_AI_S3_DISTANCE", vector.distance());
		}
		return Document.builder().metadata(metadata).text(vector.key()).build();
	}

	private boolean matchesFilter(ListOutputVector vector, Filter.Expression filterExpression) {
		software.amazon.awssdk.core.document.Document metadataDocument = vector.metadata();
		Map<String, Object> metadata = (metadataDocument != null) ? DocumentUtils.fromDocument(metadataDocument) : null;
		return this.filterExpressionEvaluator.evaluate(filterExpression,
				(metadata != null) ? metadata : Collections.emptyMap());
	}

	private void deleteVectors(List<String> keys) {
		Assert.notNull(keys, "Keys must not be null");
		if (keys.isEmpty()) {
			return;
		}
		for (int i = 0; i < keys.size(); i += DELETE_VECTORS_MAX_KEYS) {
			List<String> batch = keys.subList(i, Math.min(i + DELETE_VECTORS_MAX_KEYS, keys.size()));
			this.s3VectorsClient.deleteVectors(DeleteVectorsRequest.builder()
				.keys(batch)
				.indexName(this.indexName)
				.vectorBucketName(this.vectorBucketName)
				.build());
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.S3_VECTOR.value(), operationName)
			.collectionName(this.indexName)
			.dimensions(this.embeddingModel.dimensions());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// Index requires distance and other stuff to be created. Not sure if this is
		// place to do.
		// I can provide rather Util Class like builder which creates index.
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.s3VectorsClient;
		return Optional.of(client);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final S3VectorsClient s3VectorsClient;

		private @Nullable String vectorBucketName;

		private @Nullable String indexName;

		private S3VectorFilterExpressionConverter filterExpressionConverter = new S3VectorFilterSearchExpressionConverter();

		public Builder(S3VectorsClient s3VectorsClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(s3VectorsClient, "S3VectorsClient must not be null");
			this.s3VectorsClient = s3VectorsClient;
		}

		public Builder vectorBucketName(String vectorBucketName) {
			Assert.notNull(vectorBucketName, "vectorBucketName must not be null");
			this.vectorBucketName = vectorBucketName;
			return this;
		}

		public Builder indexName(String indexName) {
			Assert.notNull(indexName, "indexName must not be null");
			this.indexName = indexName;
			return this;

		}

		public Builder filterExpressionConverter(S3VectorFilterExpressionConverter converter) {
			Assert.notNull(converter, "s3VectorFilterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		@Override
		public S3VectorStore build() {
			return new S3VectorStore(this);
		}

	}

}
