package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.ai.openai.models.EmbeddingsUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

public class AzureOpenAiEmbeddingClient extends AbstractEmbeddingClient {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiEmbeddingClient.class);

	private final OpenAIClient azureOpenAiClient;

	private AzureOpenAiEmbeddingOptions defaultOptions = AzureOpenAiEmbeddingOptions.builder()
		.withModel("text-embedding-ada-002")
		.build();

	private final MetadataMode metadataMode;

	public AzureOpenAiEmbeddingClient(OpenAIClient azureOpenAiClient) {
		this(azureOpenAiClient, MetadataMode.EMBED);
	}

	public AzureOpenAiEmbeddingClient(OpenAIClient azureOpenAiClient, MetadataMode metadataMode) {
		Assert.notNull(azureOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		this.azureOpenAiClient = azureOpenAiClient;
		this.metadataMode = metadataMode;
	}

	@Override
	public List<Double> embed(Document document) {
		logger.debug("Retrieving embeddings");

		EmbeddingResponse response = this
			.call(new EmbeddingRequest(List.of(document.getFormattedContent(this.metadataMode)), null));
		logger.debug("Embeddings retrieved");
		return response.getResults().stream().map(embedding -> embedding.getOutput()).flatMap(List::stream).toList();
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest embeddingRequest) {
		logger.debug("Retrieving embeddings");

		EmbeddingsOptions azureOptions = new EmbeddingsOptions(embeddingRequest.getInstructions());
		if (this.defaultOptions != null) {
			azureOptions = ModelOptionsUtils.merge(azureOptions, this.defaultOptions, EmbeddingsOptions.class);
		}
		if (embeddingRequest.getOptions() != null) {
			azureOptions = ModelOptionsUtils.merge(embeddingRequest.getOptions(), azureOptions,
					EmbeddingsOptions.class);
		}
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(azureOptions.getModel(), azureOptions);

		logger.debug("Embeddings retrieved");
		return generateEmbeddingResponse(embeddings);
	}

	/**
	 * Test access
	 */
	EmbeddingsOptions toEmbeddingOptions(EmbeddingRequest embeddingRequest) {
		var azureOptions = new EmbeddingsOptions(embeddingRequest.getInstructions());
		if (this.defaultOptions != null) {
			azureOptions = ModelOptionsUtils.merge(azureOptions, this.defaultOptions, EmbeddingsOptions.class);
		}
		if (embeddingRequest.getOptions() != null) {
			azureOptions = ModelOptionsUtils.merge(embeddingRequest.getOptions(), azureOptions,
					EmbeddingsOptions.class);
		}
		return azureOptions;
	}

	private EmbeddingResponse generateEmbeddingResponse(Embeddings embeddings) {
		List<Embedding> data = generateEmbeddingList(embeddings.getData());
		EmbeddingResponseMetadata metadata = generateMetadata(embeddings.getUsage());
		return new EmbeddingResponse(data, metadata);
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

	private EmbeddingResponseMetadata generateMetadata(EmbeddingsUsage embeddingsUsage) {
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		// metadata.put("model", model);
		metadata.put("prompt-tokens", embeddingsUsage.getPromptTokens());
		metadata.put("total-tokens", embeddingsUsage.getTotalTokens());
		return metadata;
	}

	public AzureOpenAiEmbeddingOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public void setDefaultOptions(AzureOpenAiEmbeddingOptions defaultOptions) {
		Assert.notNull(defaultOptions, "Default options must not be null");
		this.defaultOptions = defaultOptions;
	}

	public AzureOpenAiEmbeddingClient withDefaultOptions(AzureOpenAiEmbeddingOptions options) {
		this.defaultOptions = options;
		return this;
	}

}
