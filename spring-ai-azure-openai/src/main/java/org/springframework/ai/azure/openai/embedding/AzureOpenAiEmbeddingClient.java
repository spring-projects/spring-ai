package org.springframework.ai.azure.openai.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.ai.openai.models.EmbeddingsUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingUtil;
import org.springframework.util.Assert;

public class AzureOpenAiEmbeddingClient implements EmbeddingClient {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiEmbeddingClient.class);

	private final OpenAIClient azureOpenAiClient;

	private final String model;

	private final AtomicInteger embeddingDimensions = new AtomicInteger(-1);

	private final MetadataMode metadataMode;

	public AzureOpenAiEmbeddingClient(OpenAIClient azureOpenAiClient) {
		this(azureOpenAiClient, "text-embedding-ada-002");
	}

	public AzureOpenAiEmbeddingClient(OpenAIClient azureOpenAiClient, String model) {
		this(azureOpenAiClient, model, MetadataMode.EMBED);
	}

	public AzureOpenAiEmbeddingClient(OpenAIClient azureOpenAiClient, String model, MetadataMode metadataMode) {
		Assert.notNull(azureOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(model, "Model must not be null");
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		this.azureOpenAiClient = azureOpenAiClient;
		this.model = model;
		this.metadataMode = metadataMode;
	}

	@Override
	public List<Double> embed(String text) {
		logger.debug("Retrieving embeddings");
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(this.model, new EmbeddingsOptions(List.of(text)));
		logger.debug("Embeddings retrieved");
		return extractEmbeddingsList(embeddings);
	}

	@Override
	public List<Double> embed(Document document) {
		logger.debug("Retrieving embeddings");
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(this.model,
				new EmbeddingsOptions(List.of(document.getFormattedContent(this.metadataMode))));
		logger.debug("Embeddings retrieved");
		return extractEmbeddingsList(embeddings);
	}

	private List<Double> extractEmbeddingsList(Embeddings embeddings) {
		return embeddings.getData().stream().map(EmbeddingItem::getEmbedding).flatMap(List::stream).toList();
	}

	@Override
	public List<List<Double>> embed(List<String> texts) {
		logger.debug("Retrieving embeddings");
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(this.model, new EmbeddingsOptions(texts));
		logger.debug("Embeddings retrieved");
		return embeddings.getData().stream().map(emb -> emb.getEmbedding()).toList();
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		logger.debug("Retrieving embeddings");
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(this.model, new EmbeddingsOptions(texts));
		logger.debug("Embeddings retrieved");
		return generateEmbeddingResponse(embeddings);
	}

	private EmbeddingResponse generateEmbeddingResponse(Embeddings embeddings) {
		List<Embedding> data = generateEmbeddingList(embeddings.getData());
		Map<String, Object> metadata = generateMetadata(this.model, embeddings.getUsage());
		return new EmbeddingResponse(data, metadata);
	}

	private Map<String, Object> generateMetadata(String model, EmbeddingsUsage embeddingsUsage) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("model", model);
		metadata.put("prompt-tokens", embeddingsUsage.getPromptTokens());
		metadata.put("total-tokens", embeddingsUsage.getTotalTokens());
		return metadata;
	}

	private List<Embedding> generateEmbeddingList(List<EmbeddingItem> nativeData) {
		List<Embedding> data = new ArrayList<>();
		for (EmbeddingItem nativeDatum : nativeData) {
			List<Double> nativeDatumEmbedding = nativeDatum.getEmbedding();
			int nativeIndex = nativeDatum.getPromptIndex();
			Embedding embedding = new Embedding(nativeDatumEmbedding, nativeIndex);
			data.add(embedding);
		}
		return data;
	}

	@Override
	public int dimensions() {
		if (this.embeddingDimensions.get() < 0) {
			this.embeddingDimensions.set(EmbeddingUtil.dimensions(this, this.model));
		}
		return this.embeddingDimensions.get();
	}

}
