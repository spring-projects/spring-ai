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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import org.springframework.core.io.Resource;

/**
 * SimpleVectorStore is a simple implementation of the VectorStore interface.
 *
 * It also provides methods to save the current state of the vectors to a file, and to
 * load vectors from a file.
 *
 * For a deeper understanding of the mathematical concepts and computations involved in
 * calculating similarity scores among vectors, refer to this
 * [resource](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_understanding_vectors).
 *
 * @author Raphael Yu
 * @author Dingmeng Xue
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 */
public class SimpleVectorStore extends AbstractObservationVectorStore {

	private static final Logger logger = LoggerFactory.getLogger(SimpleVectorStore.class);

	private final ObjectMapper objectMapper;

	protected Map<String, SimpleVectorStoreContent> store = new ConcurrentHashMap<>();

	/**
	 * use {@link #SimpleVectorStore(SimpleVectorStoreBuilder)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public SimpleVectorStore(EmbeddingModel embeddingModel) {
		this(embeddingModel, ObservationRegistry.NOOP, null);
	}

	/**
	 * use {@link #SimpleVectorStore(SimpleVectorStoreBuilder)} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public SimpleVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention) {
		this(builder().embeddingModel(embeddingModel)
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention));
	}

	protected SimpleVectorStore(SimpleVectorStoreBuilder builder) {
		super(builder);
		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
	}

	/**
	 * Creates an instance of SimpleVectorStore builder.
	 * @return the SimpleVectorStore builder.
	 */
	public static SimpleVectorStoreBuilder builder() {
		return new SimpleVectorStoreBuilder();
	}

	@Override
	public void doAdd(List<Document> documents) {
		Objects.requireNonNull(documents, "Documents list cannot be null");
		if (documents.isEmpty()) {
			throw new IllegalArgumentException("Documents list cannot be empty");
		}

		for (Document document : documents) {
			logger.info("Calling EmbeddingModel for document id = {}", document.getId());
			float[] embedding = this.embeddingModel.embed(document);
			SimpleVectorStoreContent storeContent = new SimpleVectorStoreContent(document.getId(),
					document.getContent(), document.getMetadata(), embedding);
			this.store.put(document.getId(), storeContent);
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		for (String id : idList) {
			this.store.remove(id);
		}
		return Optional.of(true);
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		if (request.getFilterExpression() != null) {
			throw new UnsupportedOperationException(
					"The [" + this.getClass() + "] doesn't support metadata filtering!");
		}

		float[] userQueryEmbedding = getUserQueryEmbedding(request.getQuery());
		return this.store.values()
			.stream()
			.map(content -> content
				.toDocument(EmbeddingMath.cosineSimilarity(userQueryEmbedding, content.getEmbedding())))
			.filter(document -> document.getScore() >= request.getSimilarityThreshold())
			.sorted(Comparator.comparing(Document::getScore).reversed())
			.limit(request.getTopK())
			.toList();
	}

	/**
	 * Serialize the vector store content into a file in JSON format.
	 * @param file the file to save the vector store content
	 */
	public void save(File file) {
		String json = getVectorDbAsJson();
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
					throw new RuntimeException("Failed to create new file: " + file + ". Reason: " + e.getMessage(), e);
				}
			}
			else {
				logger.info("Overwriting existing vector store file: {}", file);
			}
			try (OutputStream stream = new FileOutputStream(file);
					Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
				writer.write(json);
				writer.flush();
			}
		}
		catch (IOException ex) {
			logger.error("IOException occurred while saving vector store file.", ex);
			throw new RuntimeException(ex);
		}
		catch (SecurityException ex) {
			logger.error("SecurityException occurred while saving vector store file.", ex);
			throw new RuntimeException(ex);
		}
		catch (NullPointerException ex) {
			logger.error("NullPointerException occurred while saving vector store file.", ex);
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Deserialize the vector store content from a file in JSON format into memory.
	 * @param file the file to load the vector store content
	 */
	public void load(File file) {
		TypeReference<HashMap<String, SimpleVectorStoreContent>> typeRef = new TypeReference<>() {

		};
		try {
			this.store = this.objectMapper.readValue(file, typeRef);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Deserialize the vector store content from a resource in JSON format into memory.
	 * @param resource the resource to load the vector store content
	 */
	public void load(Resource resource) {
		TypeReference<HashMap<String, SimpleVectorStoreContent>> typeRef = new TypeReference<>() {

		};
		try {
			this.store = this.objectMapper.readValue(resource.getInputStream(), typeRef);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private String getVectorDbAsJson() {
		ObjectWriter objectWriter = this.objectMapper.writerWithDefaultPrettyPrinter();
		String json;
		try {
			json = objectWriter.writeValueAsString(this.store);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Error serializing documentMap to JSON.", e);
		}
		return json;
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

	public static final class EmbeddingMath {

		private EmbeddingMath() {
			throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
		}

		public static double cosineSimilarity(float[] vectorX, float[] vectorY) {
			if (vectorX == null || vectorY == null) {
				throw new RuntimeException("Vectors must not be null");
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

		public static float dotProduct(float[] vectorX, float[] vectorY) {
			if (vectorX.length != vectorY.length) {
				throw new IllegalArgumentException("Vectors lengths must be equal");
			}

			float result = 0;
			for (int i = 0; i < vectorX.length; ++i) {
				result += vectorX[i] * vectorY[i];
			}

			return result;
		}

		public static float norm(float[] vector) {
			return dotProduct(vector, vector);
		}

	}

	public static final class SimpleVectorStoreBuilder extends AbstractVectorStoreBuilder<SimpleVectorStoreBuilder> {

		@Override
		public SimpleVectorStore build() {
			validate();
			return new SimpleVectorStore(this);
		}

	}

}
