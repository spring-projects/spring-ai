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

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiEmbeddingUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Azure Open AI Embedding Model implementation.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
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
	public float[] embed(Document document) {
		logger.debug("Retrieving embeddings");

		EmbeddingResponse response = this
			.call(new EmbeddingRequest(List.of(document.getFormattedContent(this.metadataMode)), null));
		logger.debug("Embeddings retrieved");

		if (CollectionUtils.isEmpty(response.getResults())) {
			return new float[0];
		}
		return response.getResults().get(0).getOutput();
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
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.setUsage(AzureOpenAiEmbeddingUsage.from(embeddings.getUsage()));
		return new EmbeddingResponse(data, metadata);
	}

	private List<Embedding> generateEmbeddingList(List<EmbeddingItem> nativeData) {
		List<Embedding> data = new ArrayList<>();
		for (EmbeddingItem nativeDatum : nativeData) {
			List<Float> nativeDatumEmbedding = nativeDatum.getEmbedding();
			int nativeIndex = nativeDatum.getPromptIndex();
			Embedding embedding = new Embedding(EmbeddingUtils.toPrimitive(nativeDatumEmbedding), nativeIndex);
			data.add(embedding);
		}
		return data;
	}

	public AzureOpenAiEmbeddingOptions getDefaultOptions() {
		return this.defaultOptions;
	}

}
