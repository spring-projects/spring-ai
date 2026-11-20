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

package org.springframework.ai.voyageai.api;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Client for the Voyage AI REST API: text embeddings, contextualized chunk embeddings,
 * multimodal embeddings and reranking.
 *
 * <p>
 * Voyage AI has no official Java SDK, so this is a thin client over the REST endpoints.
 * Every request type checks, on construction, the constraints that Voyage AI documents
 * for it, so that an invalid request fails before it is sent. The model is always
 * explicit: there is no default model.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 * @see <a href="https://docs.voyageai.com/reference/embeddings-api">Voyage AI API
 * reference</a>
 */
public class VoyageAiApi {

	/**
	 * Provider name reported in observations.
	 */
	public static final String PROVIDER_NAME = AiProvider.VOYAGE_AI.value();

	/**
	 * Base URL of the Voyage AI API.
	 */
	public static final String DEFAULT_BASE_URL = "https://api.voyageai.com";

	/**
	 * The most inputs, documents or queries Voyage AI accepts in one request.
	 */
	public static final int MAX_INPUTS_PER_REQUEST = 1000;

	/**
	 * The largest {@code chunk_size}, in tokens, Voyage AI accepts for auto-chunking.
	 */
	public static final int MAX_CHUNK_SIZE = 32_000;

	private final RestClient restClient;

	/**
	 * Create a client.
	 * @param baseUrl the base URL of the API
	 * @param apiKey the API key
	 * @param restClientBuilder the builder to derive the {@link RestClient} from
	 * @param responseErrorHandler the handler for error responses
	 */
	public VoyageAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};
		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	/**
	 * Embed texts with {@code POST /v1/embeddings}.
	 * @param request the request
	 * @return the embeddings
	 */
	public EmbeddingResponse embeddings(EmbeddingRequest request) {
		return post("/v1/embeddings", request, EmbeddingResponse.class);
	}

	/**
	 * Embed documents or queries in context with
	 * {@code POST /v1/contextualizedembeddings}.
	 * @param request the request
	 * @return the embeddings, grouped by document
	 */
	public ContextualizedEmbeddingResponse contextualizedEmbeddings(ContextualizedEmbeddingRequest request) {
		return post("/v1/contextualizedembeddings", request, ContextualizedEmbeddingResponse.class);
	}

	/**
	 * Embed text, image and video content with {@code POST /v1/multimodalembeddings}.
	 * @param request the request
	 * @return the embeddings
	 */
	public EmbeddingResponse multimodalEmbeddings(MultimodalEmbeddingRequest request) {
		return post("/v1/multimodalembeddings", request, EmbeddingResponse.class);
	}

	/**
	 * Rerank documents for a query with {@code POST /v1/rerank}.
	 * @param request the request
	 * @return the relevance scores, most relevant first
	 */
	public RerankResponse rerank(RerankRequest request) {
		return post("/v1/rerank", request, RerankResponse.class);
	}

	private <T> T post(String uri, Object request, Class<T> responseType) {
		T body = this.restClient.post().uri(uri).body(request).retrieve().body(responseType);
		if (body == null) {
			throw new IllegalStateException("Voyage AI returned an empty response for " + uri);
		}
		return body;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static List<Integer> matryoshkaDimensions() {
		return List.of(256, 512, 1024, 2048);
	}

	private static void requireUniformMediaSource(List<MultimodalInput> inputs) {
		List<ContentPart> parts = inputs.stream().flatMap(input -> input.content().stream()).toList();
		Assert.isTrue(!(parts.stream().anyMatch(ContentPart::isUrl) && parts.stream().anyMatch(ContentPart::isBase64)),
				"A request must give its images and videos either all as URLs or all as base64, not both");
	}

	private static String names(Class<? extends Enum<?>> type) {
		return Arrays.stream(type.getEnumConstants()).map(Enum::toString).collect(Collectors.joining(", "));
	}

	private static void requireInputCount(int count) {
		Assert.isTrue(count > 0, "inputs must not be empty");
		Assert.isTrue(count <= MAX_INPUTS_PER_REQUEST,
				() -> "Voyage AI accepts at most " + MAX_INPUTS_PER_REQUEST + " inputs per request, got " + count);
	}

	private static void requireDimension(String model, @Nullable Integer dimension, List<Integer> supported) {
		Assert.isTrue(dimension == null || supported.contains(dimension),
				() -> "Model " + model + " supports the output dimensions " + supported + ", not " + dimension);
	}

	/**
	 * Whether the text is a query or a document. Voyage AI prepends a retrieval prompt
	 * for the given type, which makes the vectors fit search better.
	 */
	public enum InputType {

		/**
		 * A document to be searched.
		 */
		DOCUMENT("document"),

		/**
		 * A query to search with.
		 */
		QUERY("query");

		private final String value;

		InputType(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

		@Override
		public String toString() {
			return this.value;
		}

	}

	/**
	 * Text embedding models.
	 */
	public enum EmbeddingModel {

		VOYAGE_4_LARGE("voyage-4-large", matryoshkaDimensions()),

		VOYAGE_4("voyage-4", matryoshkaDimensions()),

		VOYAGE_4_LITE("voyage-4-lite", matryoshkaDimensions()),

		VOYAGE_CODE_4("voyage-code-4", matryoshkaDimensions()),

		VOYAGE_3_LARGE("voyage-3-large", matryoshkaDimensions()),

		VOYAGE_3_5("voyage-3.5", matryoshkaDimensions()),

		VOYAGE_3_5_LITE("voyage-3.5-lite", matryoshkaDimensions()),

		VOYAGE_CODE_3("voyage-code-3", matryoshkaDimensions()),

		VOYAGE_FINANCE_2("voyage-finance-2", List.of(1024)),

		VOYAGE_LAW_2("voyage-law-2", List.of(1024));

		/**
		 * The dimension the model returns when no dimension is requested.
		 */
		public static final int DEFAULT_DIMENSIONS = 1024;

		private final String value;

		private final List<Integer> supportedDimensions;

		EmbeddingModel(String value, List<Integer> supportedDimensions) {
			this.value = value;
			this.supportedDimensions = supportedDimensions;
		}

		public String getValue() {
			return this.value;
		}

		public List<Integer> getSupportedDimensions() {
			return this.supportedDimensions;
		}

		/**
		 * Resolve a model name.
		 * @param value the model name
		 * @return the model
		 * @throws IllegalArgumentException when the name is not a Voyage AI text
		 * embedding model
		 */
		public static EmbeddingModel fromValue(@Nullable String value) {
			Assert.hasText(value, "The Voyage AI embedding model must be set, there is no default model");
			return Arrays.stream(values())
				.filter(model -> model.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("'" + value
						+ "' is not a Voyage AI text embedding model. Use one of: " + names(EmbeddingModel.class)));
		}

		@Override
		public String toString() {
			return this.value;
		}

	}

	/**
	 * Contextualized chunk embedding models.
	 */
	public enum ContextualizedEmbeddingModel {

		VOYAGE_CONTEXT_4("voyage-context-4"),

		VOYAGE_CONTEXT_3("voyage-context-3");

		private final String value;

		ContextualizedEmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public List<Integer> getSupportedDimensions() {
			return matryoshkaDimensions();
		}

		/**
		 * Resolve a model name.
		 * @param value the model name
		 * @return the model
		 * @throws IllegalArgumentException when the name is not a Voyage AI
		 * contextualized embedding model
		 */
		public static ContextualizedEmbeddingModel fromValue(@Nullable String value) {
			Assert.hasText(value,
					"The Voyage AI contextualized embedding model must be set, there is no default model");
			return Arrays.stream(values())
				.filter(model -> model.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"'" + value + "' is not a Voyage AI contextualized embedding model. Use one of: "
								+ names(ContextualizedEmbeddingModel.class)));
		}

		@Override
		public String toString() {
			return this.value;
		}

	}

	/**
	 * Multimodal embedding models.
	 */
	public enum MultimodalEmbeddingModel {

		/**
		 * Text, images and video; output dimensions 256, 512, 1024 and 2048.
		 */
		VOYAGE_MULTIMODAL_3_5("voyage-multimodal-3.5", matryoshkaDimensions(), true),

		/**
		 * Text and images; output dimension 1024 only.
		 */
		VOYAGE_MULTIMODAL_3("voyage-multimodal-3", List.of(1024), false);

		private final String value;

		private final List<Integer> supportedDimensions;

		private final boolean videoSupported;

		MultimodalEmbeddingModel(String value, List<Integer> supportedDimensions, boolean videoSupported) {
			this.value = value;
			this.supportedDimensions = supportedDimensions;
			this.videoSupported = videoSupported;
		}

		public String getValue() {
			return this.value;
		}

		public List<Integer> getSupportedDimensions() {
			return this.supportedDimensions;
		}

		public boolean isVideoSupported() {
			return this.videoSupported;
		}

		/**
		 * Resolve a model name.
		 * @param value the model name
		 * @return the model
		 * @throws IllegalArgumentException when the name is not a Voyage AI multimodal
		 * embedding model
		 */
		public static MultimodalEmbeddingModel fromValue(@Nullable String value) {
			Assert.hasText(value, "The Voyage AI multimodal embedding model must be set, there is no default model");
			return Arrays.stream(values())
				.filter(model -> model.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"'" + value + "' is not a Voyage AI multimodal embedding model. Use one of: "
								+ names(MultimodalEmbeddingModel.class)));
		}

		@Override
		public String toString() {
			return this.value;
		}

	}

	/**
	 * Reranking models.
	 */
	public enum RerankModel {

		RERANK_3("rerank-3"),

		RERANK_3_LITE("rerank-3-lite"),

		RERANK_2_5("rerank-2.5"),

		RERANK_2_5_LITE("rerank-2.5-lite");

		private final String value;

		RerankModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		/**
		 * Resolve a model name.
		 * @param value the model name
		 * @return the model
		 * @throws IllegalArgumentException when the name is not a Voyage AI reranking
		 * model
		 */
		public static RerankModel fromValue(@Nullable String value) {
			Assert.hasText(value, "The Voyage AI rerank model must be set, there is no default model");
			return Arrays.stream(values())
				.filter(model -> model.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"'" + value + "' is not a Voyage AI rerank model. Use one of: " + names(RerankModel.class)));
		}

		@Override
		public String toString() {
			return this.value;
		}

	}

	/**
	 * Token usage reported by Voyage AI. {@code totalTokens} is the number that is
	 * billed; the other fields are only reported for multimodal requests.
	 *
	 * @param totalTokens the tokens billed for the request
	 * @param textTokens the text tokens of a multimodal request
	 * @param imagePixels the image pixels of a multimodal request
	 * @param videoPixels the video pixels of a multimodal request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(@JsonProperty("total_tokens") @Nullable Integer totalTokens,
			@JsonProperty("text_tokens") @Nullable Integer textTokens,
			@JsonProperty("image_pixels") @Nullable Long imagePixels,
			@JsonProperty("video_pixels") @Nullable Long videoPixels) {

	}

	/**
	 * Request of {@code POST /v1/embeddings}.
	 *
	 * @param input the texts to embed, at most {@value #MAX_INPUTS_PER_REQUEST}
	 * @param model the model name, one of {@link EmbeddingModel}
	 * @param inputType whether the texts are queries or documents; omitted when
	 * {@code null}
	 * @param truncation whether over-long texts are truncated; omitted when {@code null}
	 * @param outputDimension the dimension of the vectors; omitted when {@code null},
	 * otherwise it must be supported by the model
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingRequest(@JsonProperty("input") List<String> input, @JsonProperty("model") String model,
			@JsonProperty("input_type") @Nullable InputType inputType,
			@JsonProperty("truncation") @Nullable Boolean truncation,
			@JsonProperty("output_dimension") @Nullable Integer outputDimension) {

		public EmbeddingRequest {
			Assert.notNull(input, "input must not be null");
			requireInputCount(input.size());
			requireDimension(model, outputDimension, EmbeddingModel.fromValue(model).getSupportedDimensions());
		}

	}

	/**
	 * One embedding of an {@link EmbeddingResponse}.
	 *
	 * @param embedding the vector
	 * @param index the position of the input the vector belongs to
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingData(@JsonProperty("embedding") float[] embedding, @JsonProperty("index") Integer index) {

	}

	/**
	 * Response of the text and multimodal embedding endpoints.
	 *
	 * @param data the embeddings
	 * @param model the model that produced them
	 * @param usage the token usage; absent usage is reported as {@code null}
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingResponse(@JsonProperty("data") List<EmbeddingData> data, @JsonProperty("model") String model,
			@JsonProperty("usage") @Nullable Usage usage) {

	}

	/**
	 * Request of {@code POST /v1/contextualizedembeddings}.
	 *
	 * <p>
	 * Voyage AI accepts two shapes. With auto-chunking, {@code inputs} holds one string
	 * per document and Voyage AI splits each document into chunks. Without it,
	 * {@code inputs} holds one inner list of texts per document or per query.
	 * Auto-chunking works only for {@link InputType#DOCUMENT} inputs.
	 *
	 * @param inputs one string per document with auto-chunking, otherwise one list of
	 * texts per document or query
	 * @param model the model name, one of {@link ContextualizedEmbeddingModel}
	 * @param inputType whether the inputs are queries or documents
	 * @param outputDimension the dimension of the vectors; omitted when {@code null},
	 * otherwise it must be supported by the model
	 * @param enableAutoChunking whether Voyage AI chunks the documents; omitted when
	 * {@code null}
	 * @param chunkSize the target chunk size in tokens, only valid with auto-chunking
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContextualizedEmbeddingRequest(@JsonProperty("inputs") List<?> inputs,
			@JsonProperty("model") String model, @JsonProperty("input_type") InputType inputType,
			@JsonProperty("output_dimension") @Nullable Integer outputDimension,
			@JsonProperty("enable_auto_chunking") @Nullable Boolean enableAutoChunking,
			@JsonProperty("chunk_size") @Nullable Integer chunkSize) {

		public ContextualizedEmbeddingRequest {
			Assert.notNull(inputs, "inputs must not be null");
			Assert.notNull(inputType, "inputType must not be null: Voyage AI needs to know whether the inputs are "
					+ "documents or queries");
			requireInputCount(inputs.size());
			requireDimension(model, outputDimension,
					ContextualizedEmbeddingModel.fromValue(model).getSupportedDimensions());
			boolean autoChunking = Boolean.TRUE.equals(enableAutoChunking);
			if (autoChunking) {
				Assert.isTrue(inputType == InputType.DOCUMENT, "auto-chunking works only for document inputs");
				Assert.isTrue(inputs.stream().allMatch(String.class::isInstance),
						"auto-chunking expects one string per document, not a list of chunks");
				Assert.isTrue(chunkSize == null || (chunkSize > 0 && chunkSize <= MAX_CHUNK_SIZE),
						() -> "chunk_size must be between 1 and " + MAX_CHUNK_SIZE + ", got " + chunkSize);
			}
			else {
				Assert.isTrue(chunkSize == null, "chunk_size is only valid with auto-chunking");
				Assert.isTrue(inputs.stream().allMatch(List.class::isInstance) || inputType == InputType.QUERY,
						"document inputs without auto-chunking must be lists of chunks, one list per document");
			}
		}

	}

	/**
	 * One embedded chunk of a document or one embedded query.
	 *
	 * @param embedding the vector
	 * @param index the position of the chunk within its document
	 * @param text the text of the chunk; only returned when Voyage AI chunked the
	 * document
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContextualizedChunk(@JsonProperty("embedding") float[] embedding,
			@JsonProperty("index") Integer index, @JsonProperty("text") @Nullable String text) {

	}

	/**
	 * The chunks of one document, or the embedding of one query.
	 *
	 * @param data the chunks, in document order
	 * @param index the position of the document or query in the request
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContextualizedDocument(@JsonProperty("data") List<ContextualizedChunk> data,
			@JsonProperty("index") Integer index) {

	}

	/**
	 * Response of {@code POST /v1/contextualizedembeddings}.
	 *
	 * @param data one entry per document or query
	 * @param model the model that produced the embeddings
	 * @param usage the token usage; absent usage is reported as {@code null}
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContextualizedEmbeddingResponse(@JsonProperty("data") List<ContextualizedDocument> data,
			@JsonProperty("model") String model, @JsonProperty("usage") @Nullable Usage usage) {

	}

	/**
	 * One piece of content of a multimodal input. Use the factory methods.
	 *
	 * @param type the kind of content
	 * @param text the text, for {@code text}
	 * @param imageUrl the image URL, for {@code image_url}
	 * @param imageBase64 the image as a data URL, for {@code image_base64}
	 * @param videoUrl the video URL, for {@code video_url}
	 * @param videoBase64 the video as a data URL, for {@code video_base64}
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentPart(@JsonProperty("type") String type, @JsonProperty("text") @Nullable String text,
			@JsonProperty("image_url") @Nullable String imageUrl,
			@JsonProperty("image_base64") @Nullable String imageBase64,
			@JsonProperty("video_url") @Nullable String videoUrl,
			@JsonProperty("video_base64") @Nullable String videoBase64) {

		public static ContentPart text(String text) {
			return new ContentPart("text", text, null, null, null, null);
		}

		public static ContentPart imageUrl(String url) {
			return new ContentPart("image_url", null, url, null, null, null);
		}

		public static ContentPart imageBase64(String dataUrl) {
			return new ContentPart("image_base64", null, null, dataUrl, null, null);
		}

		public static ContentPart videoUrl(String url) {
			return new ContentPart("video_url", null, null, null, url, null);
		}

		public static ContentPart videoBase64(String dataUrl) {
			return new ContentPart("video_base64", null, null, null, null, dataUrl);
		}

		boolean isUrl() {
			return this.imageUrl != null || this.videoUrl != null;
		}

		boolean isBase64() {
			return this.imageBase64 != null || this.videoBase64 != null;
		}

		boolean isVideo() {
			return this.videoUrl != null || this.videoBase64 != null;
		}

	}

	/**
	 * One multimodal input: an interleaved sequence of text, images and videos that is
	 * embedded as a whole.
	 *
	 * @param content the pieces of content, not empty
	 */
	public record MultimodalInput(@JsonProperty("content") List<ContentPart> content) {

		public MultimodalInput {
			Assert.notEmpty(content, "content must not be empty");
		}

	}

	/**
	 * Request of {@code POST /v1/multimodalembeddings}.
	 *
	 * @param inputs the inputs, at most {@value #MAX_INPUTS_PER_REQUEST}. Across all
	 * inputs the media must be given either all as URLs or all as base64 data URLs.
	 * @param model the model name, one of {@link MultimodalEmbeddingModel}
	 * @param inputType whether the inputs are queries or documents; omitted when
	 * {@code null}
	 * @param truncation whether over-long inputs are truncated; omitted when {@code null}
	 * @param outputDimension the dimension of the vectors; omitted when {@code null},
	 * otherwise it must be supported by the model
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MultimodalEmbeddingRequest(@JsonProperty("inputs") List<MultimodalInput> inputs,
			@JsonProperty("model") String model, @JsonProperty("input_type") @Nullable InputType inputType,
			@JsonProperty("truncation") @Nullable Boolean truncation,
			@JsonProperty("output_dimension") @Nullable Integer outputDimension) {

		public MultimodalEmbeddingRequest {
			Assert.notNull(inputs, "inputs must not be null");
			requireInputCount(inputs.size());
			MultimodalEmbeddingModel embeddingModel = MultimodalEmbeddingModel.fromValue(model);
			requireDimension(model, outputDimension, embeddingModel.getSupportedDimensions());
			requireUniformMediaSource(inputs);
			Assert.isTrue(embeddingModel.isVideoSupported()
					|| inputs.stream().flatMap(input -> input.content().stream()).noneMatch(ContentPart::isVideo),
					() -> "Model " + model + " does not support video");
		}

	}

	/**
	 * Request of {@code POST /v1/rerank}.
	 *
	 * @param query the query
	 * @param documents the documents to rank, at most {@value #MAX_INPUTS_PER_REQUEST}
	 * @param model the model name, one of {@link RerankModel}
	 * @param topK how many of the most relevant documents to return; all when
	 * {@code null}
	 * @param returnDocuments whether the response repeats the document texts
	 * @param truncation whether over-long input is truncated; omitted when {@code null}
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RerankRequest(@JsonProperty("query") String query, @JsonProperty("documents") List<String> documents,
			@JsonProperty("model") String model, @JsonProperty("top_k") @Nullable Integer topK,
			@JsonProperty("return_documents") @Nullable Boolean returnDocuments,
			@JsonProperty("truncation") @Nullable Boolean truncation) {

		public RerankRequest {
			Assert.hasText(query, "query must not be empty");
			Assert.notNull(documents, "documents must not be null");
			requireInputCount(documents.size());
			RerankModel.fromValue(model);
			Assert.isTrue(topK == null || topK > 0, "topK must be positive");
		}

	}

	/**
	 * One scored document of a {@link RerankResponse}.
	 *
	 * @param index the position of the document in the request
	 * @param relevanceScore the relevance of the document to the query
	 * @param document the document text; only returned when {@code return_documents} was
	 * set
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RerankResult(@JsonProperty("index") Integer index,
			@JsonProperty("relevance_score") Double relevanceScore,
			@JsonProperty("document") @Nullable String document) {

	}

	/**
	 * Response of {@code POST /v1/rerank}.
	 *
	 * @param data the scored documents, most relevant first
	 * @param model the model that produced the scores
	 * @param usage the token usage; absent usage is reported as {@code null}
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RerankResponse(@JsonProperty("data") List<RerankResult> data, @JsonProperty("model") String model,
			@JsonProperty("usage") @Nullable Usage usage) {

	}

	/**
	 * Builder for {@link VoyageAiApi}.
	 */
	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private @Nullable String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = new VoyageAiResponseErrorHandler();

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey cannot be null or empty");
			this.apiKey = apiKey;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public VoyageAiApi build() {
			Assert.state(this.apiKey != null, "The API key must not be null");
			return new VoyageAiApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.responseErrorHandler);
		}

	}

}
