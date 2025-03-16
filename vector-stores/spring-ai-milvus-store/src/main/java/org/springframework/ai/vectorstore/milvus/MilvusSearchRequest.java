package org.springframework.ai.vectorstore.milvus;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.Nullable;

/**
 * A specialized {@link SearchRequest} for Milvus vector search, extending the base
 * request with Milvus-specific parameters.
 * <p>
 * This class introduces two additional fields:
 * <ul>
 * <li>{@code nativeExpression} - A native Milvus filter expression (e.g.,
 * {@code "city LIKE
 * 'New%'"}).</li>
 * <li>{@code searchParamsJson} - A JSON string containing search parameters (e.g.,
 * {@code "{\"nprobe\":128}"}).</li>
 * </ul>
 * <p>
 * Use the {@link MilvusBuilder} to construct instances of this class.
 *
 * @author waileong
 */
public final class MilvusSearchRequest extends SearchRequest {

	@Nullable
	private final String nativeExpression;

	@Nullable
	private final String searchParamsJson;

	/**
	 * Private constructor to initialize a MilvusSearchRequest using the base request and
	 * builder.
	 * @param baseRequest The base {@link SearchRequest} containing standard search
	 * fields.
	 * @param builder The {@link MilvusBuilder} containing Milvus-specific parameters.
	 */
	private MilvusSearchRequest(SearchRequest baseRequest, MilvusBuilder builder) {
		super(baseRequest); // Copy all standard fields
		this.nativeExpression = builder.nativeExpression;
		this.searchParamsJson = builder.searchParamsJson;
	}

	/**
	 * Retrieves the native Milvus filter expression.
	 * @return A string representing the native Milvus expression, or {@code null} if not
	 * set.
	 */
	@Nullable
	public String getNativeExpression() {
		return this.nativeExpression;
	}

	/**
	 * Retrieves the JSON-encoded search parameters.
	 * @return A JSON string containing search parameters, or {@code null} if not set.
	 */
	@Nullable
	public String getSearchParamsJson() {
		return this.searchParamsJson;
	}

	/**
	 * Creates a new {@link MilvusBuilder} for constructing a {@link MilvusSearchRequest}.
	 * @return A new {@link MilvusBuilder} instance.
	 */
	public static MilvusBuilder milvusBuilder() {
		return new MilvusBuilder();
	}

	/**
	 * Builder class for constructing instances of {@link MilvusSearchRequest}.
	 */
	public static class MilvusBuilder {

		private final SearchRequest.Builder baseBuilder = SearchRequest.builder();

		@Nullable
		private String nativeExpression;

		@Nullable
		private String searchParamsJson;

		/**
		 * {@link Builder#query(java.lang.String)}
		 */
		public MilvusBuilder query(String query) {
			this.baseBuilder.query(query);
			return this;
		}

		/**
		 * {@link Builder#topK(int)}
		 */
		public MilvusBuilder topK(int topK) {
			this.baseBuilder.topK(topK);
			return this;
		}

		/**
		 * {@link Builder#similarityThreshold(double)}
		 */
		public MilvusBuilder similarityThreshold(double threshold) {
			this.baseBuilder.similarityThreshold(threshold);
			return this;
		}

		/**
		 * {@link Builder#similarityThresholdAll()}
		 */
		public MilvusBuilder similarityThresholdAll() {
			this.baseBuilder.similarityThresholdAll();
			return this;
		}

		/**
		 * {@link Builder#filterExpression(String)}
		 */
		public MilvusBuilder filterExpression(String textExpression) {
			this.baseBuilder.filterExpression(textExpression);
			return this;
		}

		/**
		 * {@link Builder#filterExpression(Filter.Expression)}
		 */
		public MilvusBuilder filterExpression(Filter.Expression expression) {
			this.baseBuilder.filterExpression(expression);
			return this;
		}

		/**
		 * Sets the native Milvus filter expression.
		 * @param nativeExpression The native Milvus expression string.
		 * @return This builder instance.
		 */
		public MilvusBuilder nativeExpression(String nativeExpression) {
			this.nativeExpression = nativeExpression;
			return this;
		}

		/**
		 * Sets the JSON-encoded search parameters.
		 * @param searchParamsJson A JSON string containing search parameters.
		 * @return This builder instance.
		 */
		public MilvusBuilder searchParamsJson(String searchParamsJson) {
			this.searchParamsJson = searchParamsJson;
			return this;
		}

		/**
		 * Builds and returns a {@link MilvusSearchRequest} instance.
		 * @return A new {@link MilvusSearchRequest} object with the specified parameters.
		 */
		public MilvusSearchRequest build() {
			SearchRequest parentRequest = this.baseBuilder.build();
			return new MilvusSearchRequest(parentRequest, this);
		}

	}

}
