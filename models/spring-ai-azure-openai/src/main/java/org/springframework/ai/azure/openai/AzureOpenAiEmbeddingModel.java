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
package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.ai.openai.models.EmbeddingsUsage;

public class AzureOpenAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiEmbeddingModel.class);

	private final OpenAIClient azureOpenAiClient;

	private final AzureOpenAiEmbeddingOptions defaultOptions;

	private final MetadataMode metadataMode;

	public AzureOpenAiEmbeddingModel(OpenAIClient azureOpenAiClient) {
		this(azureOpenAiClient, MetadataMode.EMBED);
	}

	public AzureOpenAiEmbeddingModel(OpenAIClient azureOpenAiClient, MetadataMode metadataMode) {
		this(azureOpenAiClient, metadataMode,
				AzureOpenAiEmbeddingOptions.builder().withDeploymentName("text-embedding-ada-002").build());
	}

	public AzureOpenAiEmbeddingModel(OpenAIClient azureOpenAiClient, MetadataMode metadataMode,
			AzureOpenAiEmbeddingOptions options) {
		Assert.notNull(azureOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		Assert.notNull(options, "Options must not be null");
		this.azureOpenAiClient = azureOpenAiClient;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
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

		EmbeddingsOptions azureOptions = toEmbeddingOptions(embeddingRequest);
		Embeddings embeddings = this.azureOpenAiClient.getEmbeddings(azureOptions.getModel(), azureOptions);

		logger.debug("Embeddings retrieved");
		return generateEmbeddingResponse(embeddings);
	}

	/**
	 * Test access
	 */
	EmbeddingsOptions toEmbeddingOptions(EmbeddingRequest embeddingRequest) {

		return AzureOpenAiEmbeddingOptions.builder()
			.from(this.defaultOptions)
			.merge(embeddingRequest.getOptions())
			.build()
			.toAzureOptions(embeddingRequest.getInstructions());
	}

	private EmbeddingResponse generateEmbeddingResponse(Embeddings embeddings) {
		List<Embedding> data = generateEmbeddingList(embeddings.getData());
		EmbeddingResponseMetadata metadata = generateMetadata(embeddings.getUsage());
		return new EmbeddingResponse(data, metadata);
	}

	private List<Embedding> generateEmbeddingList(List<EmbeddingItem> nativeData) {
		List<Embedding> data = new ArrayList<>();
		for (EmbeddingItem nativeDatum : nativeData) {
			List<Float> nativeDatumEmbedding = nativeDatum.getEmbedding();
			int nativeIndex = nativeDatum.getPromptIndex();
			Embedding embedding = new Embedding(nativeDatumEmbedding.stream().map(f -> f.doubleValue()).toList(),
					nativeIndex);
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

}
