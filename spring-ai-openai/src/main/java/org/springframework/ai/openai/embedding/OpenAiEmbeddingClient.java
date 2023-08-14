package org.springframework.ai.openai.embedding;

import com.theokanning.openai.Usage;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.core.document.Document;
import org.springframework.ai.core.embedding.Embedding;
import org.springframework.ai.core.embedding.EmbeddingClient;
import org.springframework.ai.core.embedding.EmbeddingResponse;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenAiEmbeddingClient implements EmbeddingClient {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

	private final OpenAiService openAiService;

	private String model = "text-embedding-ada-002";

	public OpenAiEmbeddingClient(OpenAiService openAiService) {
		Assert.notNull(openAiService, "OpenAiService must not be null");
		this.openAiService = openAiService;
	}

	@Override
	public List<Double> createEmbedding(String text) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().input(List.of(text)).model(this.model).build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResult(nativeEmbeddingResult).getData().get(0).getEmbedding();
	}

	public List<Double> createEmbedding(Document document) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
			.input(List.of(document.getContent()))
			.model(this.model)
			.build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResult(nativeEmbeddingResult).getData().get(0).getEmbedding();
	}

	public List<List<Double>> createEmbedding(List<String> texts) {
		EmbeddingResponse embeddingResponse = createEmbeddingResult(texts);
		return embeddingResponse.getData().stream().map(emb -> emb.getEmbedding()).collect(Collectors.toList());
	}

	@Override
	public EmbeddingResponse createEmbeddingResult(List<String> texts) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().input(texts).model(this.model).build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResult(nativeEmbeddingResult);
	}

	private EmbeddingResponse generateEmbeddingResult(
			com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult) {
		List<Embedding> data = generateEmbeddingList(nativeEmbeddingResult.getData());
		Map<String, Object> metadata = generateMetadata(nativeEmbeddingResult.getModel(),
				nativeEmbeddingResult.getUsage());
		return new EmbeddingResponse(data, metadata);
	}

	private Map<String, Object> generateMetadata(String model, Usage usage) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("model", model);
		metadata.put("prompt-tokens", usage.getPromptTokens());
		metadata.put("completion-tokens", usage.getCompletionTokens());
		metadata.put("total-tokens", usage.getTotalTokens());
		return metadata;
	}

	private List<Embedding> generateEmbeddingList(List<com.theokanning.openai.embedding.Embedding> nativeData) {
		List<Embedding> data = new ArrayList<>();
		for (com.theokanning.openai.embedding.Embedding nativeDatum : nativeData) {
			List<Double> nativeDatumEmbedding = nativeDatum.getEmbedding();
			int nativeIndex = nativeDatum.getIndex();
			Embedding embedding = new Embedding(nativeDatumEmbedding, nativeIndex);
			data.add(embedding);
		}
		return data;
	}

}
