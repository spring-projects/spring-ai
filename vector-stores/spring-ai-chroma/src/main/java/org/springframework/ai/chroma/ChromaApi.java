/*
 * Copyright 2023-2023 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chroma.ChromaApi.QueryRequest.Include;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Single-class Chroma API implementation based on the (unofficial) Chroma REST API.
 *
 * @author Christian Tzolov
 */
public class ChromaApi {

	// Regular expression pattern that looks for a message inside the ValueError(...).
	private static Pattern VALUE_ERROR_PATTERN = Pattern.compile("ValueError\\('([^']*)'\\)");

	private final String baseUrl;

	private final RestTemplate restTemplate;

	private final ObjectMapper objectMapper;

	private String keyToken;

	public ChromaApi(String baseUrl, RestTemplate restTemplate) {
		this(baseUrl, restTemplate, new ObjectMapper());
	}

	public ChromaApi(String baseUrl, RestTemplate restTemplate, ObjectMapper objectMapper) {
		this.baseUrl = baseUrl;
		this.restTemplate = restTemplate;
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
		this.restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, username));
		return this;
	}

	/**
	 * Chroma embedding collection.
	 *
	 * @param id Collection Id.
	 * @param name The name of the collection.
	 * @param metadata Metadata associated with the collection.
	 */
	public record Collection(String id, String name, Map<String, String> metadata) {
	}

	/**
	 * Request to create a new collection with the given name and metadata.
	 *
	 * @param name The name of the collection to create.
	 * @param metadata Optional metadata to associate with the collection.
	 */
	public record CreateCollectionRequest(String name, Map<String, String> metadata) {
		public CreateCollectionRequest(String name) {
			this(name, new HashMap<>(Map.of("hnsw:space", "cosine")));
		}
	}

	/**
	 * Add embeddings to the chroma data store.
	 *
	 * @param ids The ids of the embeddings to add.
	 * @param embeddings The embeddings to add.
	 * @param metadata The metadata to associate with the embeddings. When querying, you
	 * can filter on this metadata.
	 * @param documents The documents contents to associate with the embeddings.
	 */
	public record AddEmbeddingsRequest(List<String> ids, List<float[]> embeddings,
			@JsonProperty("metadatas") List<Map<String, Object>> metadata, List<String> documents) {

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
	public record DeleteEmbeddingsRequest(List<String> ids, Map<String, Object> where) {
		public DeleteEmbeddingsRequest(List<String> ids) {
			this(ids, Map.of());
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
	public record GetEmbeddingsRequest(List<String> ids, Map<String, Object> where, int limit, int offset,
			List<Include> include) {

		public GetEmbeddingsRequest(List<String> ids) {
			this(ids, Map.of(), 10, 0, Include.all);
		}

		public GetEmbeddingsRequest(List<String> ids, Map<String, Object> where) {
			this(ids, where, 10, 0, Include.all);
		}

		public GetEmbeddingsRequest(List<String> ids, Map<String, Object> where, int limit, int offset) {
			this(ids, where, limit, offset, Include.all);
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
	public record GetEmbeddingResponse(List<String> ids, List<List<Float>> embeddings, List<String> documents,
			@JsonProperty("metadatas") List<Map<String, String>> metadata) {
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
	public record QueryRequest(@JsonProperty("query_embeddings") List<List<Float>> queryEmbeddings,
			@JsonProperty("n_results") int nResults, Map<String, Object> where, List<Include> include) {

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

		/**
		 * Convenience to query for a single embedding instead of a batch of embeddings.
		 */
		public QueryRequest(List<Float> queryEmbedding, int nResults) {
			this(List.of(queryEmbedding), nResults, Map.of(), Include.all);
		}

		public QueryRequest(List<Float> queryEmbedding, int nResults, Map<String, Object> where) {
			this(List.of(queryEmbedding), nResults, where, Include.all);
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
	public record QueryResponse(List<List<String>> ids, List<List<List<Float>>> embeddings,
			List<List<String>> documents, @JsonProperty("metadatas") List<List<Map<String, Object>>> metadata,
			List<List<Double>> distances) {
	}

	/**
	 * Single query embedding response.
	 */
	public record Embedding(String id, List<Float> embedding, String document, Map<String, Object> metadata,
			Double distances) {
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

	//
	// Chroma Client API (https://docs.trychroma.com/js_reference/Client)
	//

	public Collection createCollection(CreateCollectionRequest createCollectionRequest) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections", HttpMethod.POST,
					this.getHttpEntityFor(createCollectionRequest), Collection.class)
			.getBody();
	}

	/**
	 * Delete a collection with the given name.
	 * @param collectionName the name of the collection to delete.
	 *
	 */
	public void deleteCollection(String collectionName) {

		this.restTemplate.exchange(this.baseUrl + "/api/v1/collections/{collection_name}", HttpMethod.DELETE,
				new HttpEntity<>(httpHeaders()), Void.class, collectionName);
	}

	public Collection getCollection(String collectionName) {

		try {
			return this.restTemplate
				.exchange(this.baseUrl + "/api/v1/collections/{collection_name}", HttpMethod.GET,
						new HttpEntity<>(httpHeaders()), Collection.class, collectionName)
				.getBody();
		}
		catch (HttpServerErrorException e) {
			String msg = this.getValueErrorMessage(e.getMessage());
			if (String.format("Collection %s does not exist.", collectionName).equals(msg)) {
				return null;
			}
			throw new RuntimeException(msg, e);
		}
	}

	private static class CollectionList extends ArrayList<Collection> {

	}

	public List<Collection> listCollections() {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/", HttpMethod.GET, new HttpEntity<>(httpHeaders()),
					CollectionList.class)
			.getBody();
	}

	//
	// Chroma Collection API (https://docs.trychroma.com/js_reference/Collection)
	//

	public Boolean upsertEmbeddings(String collectionId, AddEmbeddingsRequest embedding) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/{collection_id}/upsert", HttpMethod.POST,
					this.getHttpEntityFor(embedding), Boolean.class, collectionId)
			.getBody();
	}

	public List<String> deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteRequest) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/{collection_id}/delete", HttpMethod.POST,
					this.getHttpEntityFor(deleteRequest), List.class, collectionId)
			.getBody();
	}

	public Long countEmbeddings(String collectionId) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/{collection_id}/count", HttpMethod.GET,
					new HttpEntity<>(httpHeaders()), Long.class, collectionId)
			.getBody();
	}

	public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/{collection_id}/query", HttpMethod.POST,
					this.getHttpEntityFor(queryRequest), QueryResponse.class, collectionId)
			.getBody();
	}

	public GetEmbeddingResponse getEmbeddings(String collectionId, GetEmbeddingsRequest getEmbeddingsRequest) {

		return this.restTemplate
			.exchange(this.baseUrl + "/api/v1/collections/{collection_id}/get", HttpMethod.POST,
					this.getHttpEntityFor(getEmbeddingsRequest), GetEmbeddingResponse.class, collectionId)
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

	private <T> HttpEntity<T> getHttpEntityFor(T body) {
		return new HttpEntity<>(body, httpHeaders());
	}

	private HttpHeaders httpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (StringUtils.hasText(this.keyToken)) {
			headers.setBearerAuth(this.keyToken);
		}
		return headers;
	}

	private String getValueErrorMessage(String logString) {
		if (!StringUtils.hasText(logString)) {
			return "";
		}
		Matcher m = VALUE_ERROR_PATTERN.matcher(logString);
		return (m.find()) ? m.group(1) : "";
	}

}
