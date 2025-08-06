package org.springframework.ai.vectorstore.s3;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Matej Nedic
 */
public class S3VectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private final S3VectorsClient s3VectorsClient;

	private final String vectorBucketName;

	private final String indexName;

	private final S3VectorFilterExpressionConverter filterExpressionConverter;


	/**
	 * Creates a new S3VectorStore instance with the specified builder
	 * settings. Initializes observation-related components and the embedding model.
	 *
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
		this.vectorBucketName = builder.vectorBucketName;
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embedding = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		PutVectorsRequest.Builder requestBuilder = PutVectorsRequest.builder();
		requestBuilder.indexName(indexName).vectorBucketName(vectorBucketName);

		List<PutInputVector> vectors = new ArrayList<>(documents.size());
		for (Document document : documents) {
			float[] embs = embedding.get(documents.indexOf(document));
			VectorData vectorData = constructVectorData(embs);
			vectors.add(PutInputVector.builder().data(vectorData).key(document.getId()).metadata(constructMetadata(document.getMetadata())).build());
		}
		requestBuilder.vectors(vectors);
		s3VectorsClient.putVectors(requestBuilder.build());
	}

	@Override
	public void doDelete(List<String> idList) {
		s3VectorsClient.deleteVectors(DeleteVectorsRequest.builder().keys(idList).indexName(indexName).vectorBucketName(vectorBucketName).build());
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression mus not be null");

		software.amazon.awssdk.core.document.Document filterDoc = this.filterExpressionConverter.convertExpression(filterExpression);
		QueryVectorsRequest request = QueryVectorsRequest.builder().filter(filterDoc).vectorBucketName(vectorBucketName).indexName(indexName).build();
		List<String> keys = s3VectorsClient.queryVectors(request).vectors().stream().map(QueryOutputVector::key).collect(Collectors.toList());

		s3VectorsClient.deleteVectors(DeleteVectorsRequest.builder().vectorBucketName(this.vectorBucketName).keys(keys).indexName(indexName).build());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");

		QueryVectorsRequest.Builder requestBuilder =
				QueryVectorsRequest.builder().indexName(indexName).vectorBucketName(vectorBucketName)
						.topK(searchRequest.getTopK()).returnMetadata(true).returnDistance(true);

		if (searchRequest.hasFilterExpression()) {
			software.amazon.awssdk.core.document.Document filter = filterExpressionConverter.convertExpression(searchRequest.getFilterExpression());
			requestBuilder.filter(filter);
		}

		float[] embeddings = this.embeddingModel.embed(searchRequest.getQuery());
		VectorData vectorData = constructVectorData(embeddings);
		requestBuilder.queryVector(vectorData);

		QueryVectorsResponse response = s3VectorsClient.queryVectors(requestBuilder.build());
		return response.vectors().stream().map(this::toDocument).collect(Collectors.toList());
	}

	private Document toDocument(QueryOutputVector vector) {
		Map<String, Object> metadata = DocumentUtils.fromDocument(vector.metadata());
		if (vector.distance() != null) {
			metadata.put("SPRING_AI_S3_DISTANCE", vector.distance());
		}
		return Document.builder().metadata(DocumentUtils.fromDocument(vector.metadata()))
				.text(vector.key()).build();
	}

	private static software.amazon.awssdk.core.document.Document constructMetadata(Map<String, Object> originalMetadata) {
		Map<String, software.amazon.awssdk.core.document.Document> metadata = new HashMap<>(originalMetadata.size());
		originalMetadata.forEach((k, v) ->
				metadata.put(k, DocumentUtils.toDocument(v))
		);
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
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.S3_VECTOR.value(), operationName)
				.collectionName(this.indexName)
				.dimensions(this.embeddingModel.dimensions());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// Index requires distance and other stuff to be created. Not sure if this is place to do.
		// I can provide rather Util Class like builder which creates index.
	}



	public static class Builder extends AbstractVectorStoreBuilder<Builder> {
		private final S3VectorsClient s3VectorsClient;

		private String vectorBucketName;

		private String indexName;

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
