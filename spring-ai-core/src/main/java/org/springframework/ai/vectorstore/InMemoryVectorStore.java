package org.springframework.ai.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/***
 * @author Raphael Yu
 * @author Dingmeng Xue
 * @author Mark Pollack
 */
public class InMemoryVectorStore implements VectorStore {

	private static final Logger logger = LoggerFactory.getLogger(InMemoryVectorStore.class);

	protected Map<String, Document> store = new ConcurrentHashMap<>();

	protected EmbeddingClient embeddingClient;

	public InMemoryVectorStore(EmbeddingClient embeddingClient) {
		Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
		this.embeddingClient = embeddingClient;
	}

	@Override
	public void add(List<Document> documents) {
		for (Document document : documents) {
			logger.info("Calling EmbeddingClient for document id = " + document.getId());
			List<Double> embedding = this.embeddingClient.embed(document);
			document.setEmbedding(embedding);
			this.store.put(document.getId(), document);
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		for (String id : idList) {
			this.store.remove(id);
		}
		return Optional.of(true);
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(query, 4);
	}

	@Override
	public List<Document> similaritySearch(String query, int k) {
		List<Double> userQueryEmbedding = getUserQueryEmbedding(query);
		var similarities = this.store.values()
			.stream()
			.map(entry -> new Similarity(entry.getId(),
					EmbeddingMath.cosineSimilarity(userQueryEmbedding, entry.getEmbedding())))
			.sorted(Comparator.<Similarity>comparingDouble(s -> s.similarity).reversed())
			.limit(k)
			.map(s -> store.get(s.key))
			.toList();
		return similarities;
	}

	@Override
	public List<Document> similaritySearch(String query, int k, double threshold) {
		List<Double> userQueryEmbedding = getUserQueryEmbedding(query);
		var similarities = this.store.values()
			.stream()
			.map(entry -> new Similarity(entry.getId(),
					EmbeddingMath.cosineSimilarity(userQueryEmbedding, entry.getEmbedding())))
			.filter(s -> s.similarity >= threshold)
			.sorted(Comparator.<Similarity>comparingDouble(s -> s.similarity).reversed())
			.limit(k)
			.map(s -> store.get(s.key))
			.toList();
		return similarities;
	}

	private List<Double> getUserQueryEmbedding(String query) {
		List<Double> userQueryEmbedding = this.embeddingClient.embed(query);
		return userQueryEmbedding;
	}

	public static class Similarity {

		private String key;

		private double similarity;

		public Similarity(String key, double similarity) {
			this.key = key;
			this.similarity = similarity;
		}

	}

	public class EmbeddingMath {

		public static double cosineSimilarity(List<Double> vectorX, List<Double> vectorY) {
			if (vectorX.size() != vectorY.size()) {
				throw new IllegalArgumentException("Vectors lengths must be equal");
			}

			double dotProduct = dotProduct(vectorX, vectorY);
			double normX = norm(vectorX);
			double normY = norm(vectorY);

			if (normX == 0 || normY == 0) {
				throw new IllegalArgumentException("Vectors cannot have zero norm");
			}

			return dotProduct / (Math.sqrt(normX) * Math.sqrt(normY));
		}

		public static double dotProduct(List<Double> vectorX, List<Double> vectorY) {
			if (vectorX.size() != vectorY.size()) {
				throw new IllegalArgumentException("Vectors lengths must be equal");
			}

			double result = 0;
			for (int i = 0; i < vectorX.size(); ++i) {
				result += vectorX.get(i) * vectorY.get(i);
			}

			return result;
		}

		public static double norm(List<Double> vector) {
			return dotProduct(vector, vector);
		}

	}

}
