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

package org.springframework.ai.vectorstore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Simple, in-memory implementation of the {@link VectorStore} interface.
 * <p/>
 * It also provides methods to save the current state of the vectors to a file, and to
 * load vectors from a file.
 * <p/>
 * For a deeper understanding of the mathematical concepts and computations involved in
 * calculating similarity scores among vectors, refer to this
 * [resource](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_understanding_vectors).
 *
 * @author Raphael Yu
 * @author Dingmeng Xue
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @author John Blum
 * @see VectorStore
 */
public class SimpleVectorStore extends AbstractObservationVectorStore {

	private static final Logger logger = LoggerFactory.getLogger(SimpleVectorStore.class);

	private final ObjectMapper objectMapper;

	protected Map<String, Document> store = new ConcurrentHashMap<>();

	protected EmbeddingModel embeddingModel;

	public SimpleVectorStore(EmbeddingModel embeddingModel) {
		this(embeddingModel, ObservationRegistry.NOOP, null);
	}

	public SimpleVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention) {

		super(observationRegistry, customObservationConvention);

		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");

		this.embeddingModel = embeddingModel;
		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
	}

	@Override
	public void doAdd(List<Document> documents) {
		for (Document document : documents) {
			logger.info("Calling EmbeddingModel for Document id = {}", document.getId());
			document = embed(document);
			this.store.put(document.getId(), document);
		}
	}

	protected Document embed(Document document) {
		float[] documentEmbedding = this.embeddingModel.embed(document);
		document.setEmbedding(documentEmbedding);
		return document;
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		idList.forEach(this.store::remove);
		return Optional.of(true);
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		if (request.getFilterExpression() != null) {
			throw new UnsupportedOperationException(
					"[%s] doesn't support metadata filtering".formatted(getClass().getName()));
		}

		// @formatter:off
		return this.store.values().stream()
			.map(document -> computeSimilarity(request, document))
			.filter(similarity -> similarity.score >= request.getSimilarityThreshold())
			.sorted(Comparator.<Similarity>comparingDouble(similarity -> similarity.score).reversed())
			.limit(request.getTopK())
			.map(similarity -> this.store.get(similarity.key))
			.toList();
		// @formatter:on
	}

	protected Similarity computeSimilarity(SearchRequest request, Document document) {

		float[] userQueryEmbedding = getUserQueryEmbedding(request);
		float[] documentEmbedding = document.getEmbedding();

		double score = computeCosineSimilarity(userQueryEmbedding, documentEmbedding);

		return new Similarity(document.getId(), score);
	}

	protected double computeCosineSimilarity(float[] userQueryEmbedding, float[] storedDocumentEmbedding) {
		return EmbeddingMath.cosineSimilarity(userQueryEmbedding, storedDocumentEmbedding);
	}

	/**
	 * Serialize the vector store content into a file in JSON format.
	 * @param file the file to save the vector store content
	 */
	public void save(File file) {

		try {
			if (!file.exists()) {
				logger.info("Creating new vector store file: {}", file);
				try {
					Files.createFile(file.toPath());
				}
				catch (FileAlreadyExistsException e) {
					throw new RuntimeException("File already exists: " + file, e);
				}
				catch (IOException e) {
					throw new RuntimeException("Failed to create new file: " + file + "; Reason: " + e.getMessage(), e);
				}
			}
			else {
				logger.info("Overwriting existing vector store file: {}", file);
			}

			try (OutputStream stream = new FileOutputStream(file);
					Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
				String json = getVectorDbAsJson();
				writer.write(json);
				writer.flush();
			}
		}
		catch (IOException | NullPointerException | SecurityException ex) {
			logger.error("%s occurred while saving vector store file".formatted(ex.getClass().getSimpleName()), ex);
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Deserialize the vector store content from a file in JSON format into memory.
	 * @param file the file to load the vector store content
	 */
	public void load(File file) {
		load(new FileSystemResource(file));
	}

	/**
	 * Deserialize the vector store content from a resource in JSON format into memory.
	 * @param resource the resource to load the vector store content
	 */
	public void load(Resource resource) {

		try {
			this.store = this.objectMapper.readValue(resource.getInputStream(), documentMapTypeRef());
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private TypeReference<Map<String, Document>> documentMapTypeRef() {
		return new TypeReference<>() {
		};
	}

	private String getVectorDbAsJson() {

		try {
			return this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this.store);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Error serializing Map of Documents to JSON", e);
		}
	}

	private float[] getUserQueryEmbedding(SearchRequest request) {
		return getUserQueryEmbedding(request.getQuery());
	}

	private float[] getUserQueryEmbedding(String query) {
		return this.embeddingModel.embed(query);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.SIMPLE.value(), operationName)
			.withDimensions(this.embeddingModel.dimensions())
			.withCollectionName("in-memory-map")
			.withSimilarityMetric(VectorStoreSimilarityMetric.COSINE.value());
	}

	public static class Similarity {

		private final String key;

		private final double score;

		public Similarity(String key, double score) {
			this.key = key;
			this.score = score;
		}

	}

	public static final class EmbeddingMath {

		private EmbeddingMath() {
			throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
		}

		public static double cosineSimilarity(float[] vectorX, float[] vectorY) {

			if (vectorX == null || vectorY == null) {
				throw new IllegalArgumentException("Vectors must not be null");
			}

			if (vectorX.length != vectorY.length) {
				throw new IllegalArgumentException("Vectors lengths must be equal");
			}

			float dotProduct = dotProduct(vectorX, vectorY);
			float normX = norm(vectorX);
			float normY = norm(vectorY);

			if (normX == 0 || normY == 0) {
				throw new IllegalArgumentException("Vectors cannot have zero norm");
			}

			return dotProduct / (Math.sqrt(normX) * Math.sqrt(normY));
		}

		private static float dotProduct(float[] vectorX, float[] vectorY) {

			if (vectorX.length != vectorY.length) {
				throw new IllegalArgumentException("Vectors lengths must be equal");
			}

			float result = 0;

			for (int index = 0; index < vectorX.length; ++index) {
				result += vectorX[index] * vectorY[index];
			}

			return result;
		}

		private static float norm(float[] vector) {
			return dotProduct(vector, vector);
		}

	}

}
