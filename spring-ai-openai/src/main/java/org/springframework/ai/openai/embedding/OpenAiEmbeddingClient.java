package org.springframework.ai.openai.embedding;

import com.theokanning.openai.Usage;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenAiEmbeddingClient implements EmbeddingClient {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

	private final OpenAiService openAiService;

	private final String model;

	public OpenAiEmbeddingClient(OpenAiService openAiService) {
		this(openAiService, "text-embedding-ada-002");
	}

	public OpenAiEmbeddingClient(OpenAiService openAiService, String model) {
		Assert.notNull(openAiService, "OpenAiService must not be null");
		Assert.notNull(model, "Model must not be null");
		this.openAiService = openAiService;
		this.model = model;
	}

	@Override
	public List<Double> embed(String text) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().input(List.of(text)).model(this.model).build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResponse(nativeEmbeddingResult).getData().get(0).getEmbedding();
	}

	public List<Double> embed(Document document) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
			.input(List.of(document.getContent()))
			.model(this.model)
			.build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResponse(nativeEmbeddingResult).getData().get(0).getEmbedding();
	}

	public List<List<Double>> embed(List<String> texts) {
		EmbeddingResponse embeddingResponse = embedForResponse(texts);
		return embeddingResponse.getData().stream().map(emb -> emb.getEmbedding()).collect(Collectors.toList());
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().input(texts).model(this.model).build();
		com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult = this.openAiService
			.createEmbeddings(embeddingRequest);
		return generateEmbeddingResponse(nativeEmbeddingResult);
	}

	private EmbeddingResponse generateEmbeddingResponse(
			com.theokanning.openai.embedding.EmbeddingResult nativeEmbeddingResult) {
		List<Embedding> data = generateEmbeddingList(nativeEmbeddingResult.getData());
		Map<String, Object> metadata = generateMetadata(nativeEmbeddingResult.getModel(),
				nativeEmbeddingResult.getUsage());
		return new EmbeddingResponse(data, metadata);
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

	private Map<String, Object> generateMetadata(String model, Usage usage) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("model", model);
		metadata.put("prompt-tokens", usage.getPromptTokens());
		metadata.put("completion-tokens", usage.getCompletionTokens());
		metadata.put("total-tokens", usage.getTotalTokens());
		return metadata;
	}

}
