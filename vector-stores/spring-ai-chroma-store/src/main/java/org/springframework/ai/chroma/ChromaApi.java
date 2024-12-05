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

package org.springframework.ai.chroma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chroma.ChromaApi.QueryRequest.Include;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Single-class Chroma API implementation based on the (unofficial) Chroma REST API.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 */
public class ChromaApi {

	// Regular expression pattern that looks for a message inside the ValueError(...).
	private static Pattern VALUE_ERROR_PATTERN = Pattern.compile("ValueError\\('([^']*)'\\)");

	// Regular expression pattern that looks for a message.
	private static Pattern MESSAGE_ERROR_PATTERN = Pattern.compile("\"message\":\"(.*?)\"");

	private final ObjectMapper objectMapper;

	private RestClient restClient;

	private String keyToken;

	public ChromaApi(String baseUrl) {
		this(baseUrl, RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()), new ObjectMapper());
	}

	public ChromaApi(String baseUrl, RestClient.Builder restClientBuilder) {
		this(baseUrl, restClientBuilder, new ObjectMapper());
	}

	public ChromaApi(String baseUrl, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(h -> h.setContentType(MediaType.APPLICATION_JSON))
			.build();
		this.objectMapper = objectMapper;
	}

	/**
	 * Configure access to ChromaDB secured with static API Token Authentication:
	 * https://docs.trychroma.com/usage-guide#static-api-token-authentication
	 * @param keyToken Chroma static API Token Authentication. (Optional)
	 */
	public ChromaApi withKeyToken(String keyToken) {
		this.keyToken = keyToken;
		return this;
	}

	/**
	 * Configure access to ChromaDB secured with Basic Authentication:
	 * https://docs.trychroma.com/usage-guide#basic-authentication
	 * @param username Credentials username.
	 * @param password Credentials password.
	 */
	public ChromaApi withBasicAuthCredentials(String username, String password) {
		this.restClient = this.restClient.mutate()
			.requestInterceptor(new BasicAuthenticationInterceptor(username, password))
			.build();
		return this;
	}

	public List<Embedding> toEmbeddingResponseList(QueryResponse queryResponse) {
		List<Embedding> result = new ArrayList<>();

		if (queryResponse != null && !CollectionUtils.isEmpty(queryResponse.ids())) {
			for (int i = 0; i < queryResponse.ids().get(0).size(); i++) {
				result.add(new Embedding(queryResponse.ids().get(0).get(i), queryResponse.embeddings().get(0).get(i),
						queryResponse.documents().get(0).get(i), queryResponse.metadata().get(0).get(i),
						queryResponse.distances().get(0).get(i)));
			}
		}

		return result;
	}

	public Collection createCollection(CreateCollectionRequest createCollectionRequest) {

		return this.restClient.post()
			.uri("/api/v1/collections")
			.headers(this::httpHeaders)
			.body(createCollectionRequest)
			.retrieve()
			.toEntity(Collection.class)
			.getBody();
	}

	/**
	 * Delete a collection with the given name.
	 * @param collectionName the name of the collection to delete.
	 *
	 */
	public void deleteCollection(String collectionName) {

		this.restClient.delete()
			.uri("/api/v1/collections/{collection_name}", collectionName)
			.headers(this::httpHeaders)
			.retrieve()
			.toBodilessEntity();
	}

	public Collection getCollection(String collectionName) {

		try {
			return this.restClient.get()
				.uri("/api/v1/collections/{collection_name}", collectionName)
				.headers(this::httpHeaders)
				.retrieve()
				.toEntity(Collection.class)
				.getBody();
		}
		catch (HttpServerErrorException | HttpClientErrorException e) {
			String msg = this.getErrorMessage(e);
			if (String.format("Collection %s does not exist.", collectionName).equals(msg)) {
				return null;
			}
			throw new RuntimeException(msg, e);
		}
	}

	public List<Collection> listCollections() {

		return this.restClient.get()
			.uri("/api/v1/collections")
			.headers(this::httpHeaders)
			.retrieve()
			.toEntity(CollectionList.class)
			.getBody();
	}

	public void upsertEmbeddings(String collectionId, AddEmbeddingsRequest embedding) {

		this.restClient.post()
			.uri("/api/v1/collections/{collection_id}/upsert", collectionId)
			.headers(this::httpHeaders)
			.body(embedding)
			.retrieve()
			.toBodilessEntity();
	}

	public int deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteRequest) {
		return this.restClient.post()
			.uri("/api/v1/collections/{collection_id}/delete", collectionId)
			.headers(this::httpHeaders)
			.body(deleteRequest)
			.retrieve()
			.toEntity(String.class)
			.getStatusCode()
			.value();
	}

	public Long countEmbeddings(String collectionId) {

		return this.restClient.get()
			.uri("/api/v1/collections/{collection_id}/count", collectionId)
			.headers(this::httpHeaders)
			.retrieve()
			.toEntity(Long.class)
			.getBody();
	}

	public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {

		return this.restClient.post()
			.uri("/api/v1/collections/{collection_id}/query", collectionId)
			.headers(this::httpHeaders)
			.body(queryRequest)
			.retrieve()
			.toEntity(QueryResponse.class)
			.getBody();
	}

	//
	// Chroma Client API (https://docs.trychroma.com/js_reference/Client)
	//

	public GetEmbeddingResponse getEmbeddings(String collectionId, GetEmbeddingsRequest getEmbeddingsRequest) {

		return this.restClient.post()
			.uri("/api/v1/collections/{collection_id}/get", collectionId)
			.headers(this::httpHeaders)
			.body(getEmbeddingsRequest)
			.retrieve()
			.toEntity(GetEmbeddingResponse.class)
			.getBody();
	}

	// Utils
	public Map<String, Object> where(String text) {
		try {
			return this.objectMapper.readValue(text, Map.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private void httpHeaders(HttpHeaders headers) {
		if (StringUtils.hasText(this.keyToken)) {
			headers.setBearerAuth(this.keyToken);
		}
	}

	private String getErrorMessage(HttpStatusCodeException e) {
		var errorMessage = e.getMessage();

		// If the error message is empty or null, return an empty string
		if (!StringUtils.hasText(errorMessage)) {
			return "";
		}

		// If the exception is an HttpServerErrorException, use the VALUE_ERROR_PATTERN
		Matcher valueErrorMatcher = VALUE_ERROR_PATTERN.matcher(errorMessage);
		if (e instanceof HttpServerErrorException && valueErrorMatcher.find()) {
			return valueErrorMatcher.group(1);
		}

		// Otherwise, use the MESSAGE_ERROR_PATTERN for other cases
		Matcher messageErrorMatcher = MESSAGE_ERROR_PATTERN.matcher(errorMessage);
		if (messageErrorMatcher.find()) {
			return messageErrorMatcher.group(1);
		}

		// If no pattern matches, return an empty string
		return "";
	}

	/**
	 * Chroma embedding collection.
	 *
	 * @param id Collection Id.
	 * @param name The name of the collection.
	 * @param metadata Metadata associated with the collection.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Collection(// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("name") String name,
		@JsonProperty("metadata") Map<String, Object> metadata) { // @formatter:on

	}

	/**
	 * Request to create a new collection with the given name and metadata.
	 *
	 * @param name The name of the collection to create.
	 * @param metadata Optional metadata to associate with the collection.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CreateCollectionRequest(// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("metadata") Map<String, Object> metadata) { // @formatter:on

		public CreateCollectionRequest(String name) {
			this(name, new HashMap<>(Map.of("hnsw:space", "cosine")));
		}

	}

	//
	// Chroma Collection API (https://docs.trychroma.com/reference/js-client/Collection)
	//

	/**
	 * Add embeddings to the chroma data store.
	 *
	 * @param ids The ids of the embeddings to add.
	 * @param embeddings The embeddings to add.
	 * @param metadata The metadata to associate with the embeddings. When querying, you
	 * can filter on this metadata.
	 * @param documents The documents contents to associate with the embeddings.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record AddEmbeddingsRequest(// @formatter:off
			@JsonProperty("ids") List<String> ids,
			@JsonProperty("embeddings") List<float[]> embeddings,
			@JsonProperty("metadatas") List<Map<String, Object>> metadata,
			@JsonProperty("documents") List<String> documents) { // @formatter:on

		// Convenance for adding a single embedding.
		public AddEmbeddingsRequest(String id, float[] embedding, Map<String, Object> metadata, String document) {
			this(List.of(id), List.of(embedding), List.of(metadata), List.of(document));
		}
	}

	/**
	 * Request to delete embedding from a collection.
	 *
	 * @param ids The ids of the embeddings to delete. (Optional)
	 * @param where Condition to filter items to delete based on metadata values.
	 * (Optional)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DeleteEmbeddingsRequest(// @formatter:off
		@JsonProperty("ids") List<String> ids,
		@JsonProperty("where") Map<String, Object> where) { // @formatter:on

		public DeleteEmbeddingsRequest(List<String> ids) {
			this(ids, null);
		}
	}

	/**
	 * Get embeddings from a collection.
	 *
	 * @param ids IDs of the embeddings to get.
	 * @param where Condition to filter results based on metadata values.
	 * @param limit Limit on the number of collection embeddings to get.
	 * @param offset Offset on the embeddings to get.
	 * @param include A list of what to include in the results. Can contain "embeddings",
	 * "metadatas", "documents", "distances". Ids are always included. Defaults to
	 * [metadatas, documents, distances].
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GetEmbeddingsRequest(// @formatter:off
		@JsonProperty("ids") List<String> ids,
		@JsonProperty("where") Map<String, Object> where,
		@JsonProperty("limit") Integer limit,
		@JsonProperty("offset") Integer offset,
		@JsonProperty("include") List<Include> include) { // @formatter:on

		public GetEmbeddingsRequest(List<String> ids) {
			this(ids, null, 10, 0, Include.all);
		}

		public GetEmbeddingsRequest(List<String> ids, Map<String, Object> where) {
			this(ids, CollectionUtils.isEmpty(where) ? null : where, 10, 0, Include.all);
		}

		public GetEmbeddingsRequest(List<String> ids, Map<String, Object> where, Integer limit, Integer offset) {
			this(ids, CollectionUtils.isEmpty(where) ? null : where, limit, offset, Include.all);
		}

	}

	/**
	 * Object containing the get embedding results.
	 *
	 * @param ids List of document ids. One for each returned document.
	 * @param embeddings List of document embeddings. One for each returned document.
	 * @param documents List of document contents. One for each returned document.
	 * @param metadata List of document metadata. One for each returned document.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GetEmbeddingResponse(// @formatter:off
		@JsonProperty("ids") List<String> ids,
		@JsonProperty("embeddings") List<float[]> embeddings,
		@JsonProperty("documents") List<String> documents,
		@JsonProperty("metadatas") List<Map<String, String>> metadata) { // @formatter:on
	}

	/**
	 * Request to get the nResults nearest neighbor embeddings for provided
	 * queryEmbeddings.
	 *
	 * @param queryEmbeddings The embeddings to get the closes neighbors of.
	 * @param nResults The number of neighbors to return for each query_embedding or
	 * query_texts.
	 * @param where Condition to filter results based on metadata values.
	 * @param include A list of what to include in the results. Can contain "embeddings",
	 * "metadatas", "documents", "distances". Ids are always included. Defaults to
	 * [metadatas, documents, distances].
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record QueryRequest(// @formatter:off
		@JsonProperty("query_embeddings") List<float[]> queryEmbeddings,
		@JsonProperty("n_results") Integer nResults,
		@JsonProperty("where") Map<String, Object> where,
		@JsonProperty("include") List<Include> include) { // @formatter:on

		/**
		 * Convenience to query for a single embedding instead of a batch of embeddings.
		 */
		public QueryRequest(float[] queryEmbedding, Integer nResults) {
			this(List.of(queryEmbedding), nResults, null, Include.all);
		}

		public QueryRequest(float[] queryEmbedding, Integer nResults, Map<String, Object> where) {
			this(List.of(queryEmbedding), nResults, CollectionUtils.isEmpty(where) ? null : where, Include.all);
		}

		public enum Include {

			@JsonProperty("metadatas")
			METADATAS,

			@JsonProperty("documents")
			DOCUMENTS,

			@JsonProperty("distances")
			DISTANCES,

			@JsonProperty("embeddings")
			EMBEDDINGS;

			public static final List<Include> all = List.of(METADATAS, DOCUMENTS, DISTANCES, EMBEDDINGS);

		}

	}

	/**
	 * A QueryResponse object containing the query results.
	 *
	 * @param ids List of list of document ids. One for each returned document.
	 * @param embeddings List of list of document embeddings. One for each returned
	 * document.
	 * @param documents List of list of document contents. One for each returned document.
	 * @param metadata List of list of document metadata. One for each returned document.
	 * @param distances List of list of search distances. One for each returned document.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record QueryResponse(// @formatter:off
		@JsonProperty("ids") List<List<String>> ids,
		@JsonProperty("embeddings") List<List<float[]>> embeddings,
		@JsonProperty("documents") List<List<String>> documents,
		@JsonProperty("metadatas") List<List<Map<String, Object>>> metadata,
		@JsonProperty("distances") List<List<Double>> distances) { // @formatter:on
	}

	/**
	 * Single query embedding response.
	 *
	 * @param id The id of the document.
	 * @param embedding The embedding of the document.
	 * @param document The content of the document.
	 * @param metadata The metadata of the document.
	 * @param distances The distance of the document to the query embedding.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Embedding(// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("embedding") float[] embedding,
		@JsonProperty("document") String document,
		@JsonProperty("metadata") Map<String, Object> metadata,
		@JsonProperty("distances") Double distances) { // @formatter:on

	}

	private static class CollectionList extends ArrayList<Collection> {

	}

}
