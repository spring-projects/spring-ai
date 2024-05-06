/*
 * Copyright 2023 - 2024 the original author or authors.
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

import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.chroma.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.ChromaApi.DeleteEmbeddingsRequest;
import org.springframework.ai.chroma.ChromaApi.Embedding;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.converter.ChromaFilterExpressionConverter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * {@link ChromaVectorStore} is a concrete implementation of the {@link VectorStore}
 * interface. It is responsible for adding, deleting, and searching documents based on
 * their similarity to a query, using the {@link ChromaApi} and {@link EmbeddingClient}
 * for embedding calculations. For more information about how it does this, see the
 * official <a href="https://www.trychroma.com/">Chroma website</a>.
 *
 *
 */
public class ChromaVectorStore implements VectorStore, ApplicationListener<ApplicationReadyEvent> {

	public static final String DISTANCE_FIELD_NAME = "distance";

	public static final String DEFAULT_COLLECTION_NAME = "SpringAiCollection";

	public static final double SIMILARITY_THRESHOLD_ALL = 0.0;

	public static final int DEFAULT_TOP_K = 4;

	private final EmbeddingClient embeddingClient;

	private final ChromaApi chromaApi;

	private final String collectionName;

	private FilterExpressionConverter filterExpressionConverter;

	private String collectionId;

	public ChromaVectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi) {
		this(embeddingClient, chromaApi, DEFAULT_COLLECTION_NAME);
	}

	public ChromaVectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi, String collectionName) {
		this.embeddingClient = embeddingClient;
		this.chromaApi = chromaApi;
		this.collectionName = collectionName;
		this.filterExpressionConverter = new ChromaFilterExpressionConverter();
	}

	public void setFilterExpressionConverter(FilterExpressionConverter filterExpressionConverter) {
		Assert.notNull(filterExpressionConverter, "FilterExpressionConverter should not be null.");
		this.filterExpressionConverter = filterExpressionConverter;
	}

	@Override
	public void add(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");
		if (CollectionUtils.isEmpty(documents)) {
			return;
		}

		List<String> ids = new ArrayList<>();
		List<Map<String, Object>> metadatas = new ArrayList<>();
		List<String> contents = new ArrayList<>();
		List<float[]> embeddings = new ArrayList<>();

		for (Document document : documents) {
			ids.add(document.getId());
			metadatas.add(document.getMetadata());
			contents.add(document.getContent());
			document.setEmbedding(this.embeddingClient.embed(document));
			embeddings.add(JsonUtils.toFloatArray(document.getEmbedding()));
		}

		this.chromaApi.upsertEmbeddings(this.collectionId,
				new AddEmbeddingsRequest(ids, embeddings, metadatas, contents));
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");
		List<String> deletedIds = this.chromaApi.deleteEmbeddings(this.collectionId,
				new DeleteEmbeddingsRequest(idList));
		return Optional.of(deletedIds.size() == idList.size());
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		String nativeFilterExpression = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		String query = request.getQuery();
		Assert.notNull(query, "Query string must not be null");

		List<Double> embedding = this.embeddingClient.embed(query);
		Map<String, Object> where = (StringUtils.hasText(nativeFilterExpression))
				? JsonUtils.jsonToMap(nativeFilterExpression) : Map.of();
		var queryRequest = new ChromaApi.QueryRequest(JsonUtils.toFloatList(embedding), request.getTopK(), where);
		var queryResponse = this.chromaApi.queryCollection(this.collectionId, queryRequest);
		var embeddings = this.chromaApi.toEmbeddingResponseList(queryResponse);

		List<Document> responseDocuments = new ArrayList<>();

		for (Embedding chromaEmbedding : embeddings) {
			float distance = chromaEmbedding.distances().floatValue();
			if ((1 - distance) >= request.getSimilarityThreshold()) {
				String id = chromaEmbedding.id();
				String content = chromaEmbedding.document();
				Map<String, Object> metadata = chromaEmbedding.metadata();
				if (metadata == null) {
					metadata = new HashMap<>();
				}
				metadata.put(DISTANCE_FIELD_NAME, distance);
				Document document = new Document(id, content, metadata);
				document.setEmbedding(JsonUtils.toDouble(chromaEmbedding.embedding()));
				responseDocuments.add(document);
			}
		}

		return responseDocuments;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		var collection = this.chromaApi.getCollection(this.collectionName);
		if (collection == null) {
			collection = this.chromaApi.createCollection(new ChromaApi.CreateCollectionRequest(this.collectionName));
		}
		this.collectionId = collection.id();
	}

}
