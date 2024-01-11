/*
 * Copyright 2023-2023 the original author or authors.
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
package org.springframework.ai.openai;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingList;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.api.OpenAiApi.Usage;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Open AI Embedding Client implementation.
 *
 * @author Christian Tzolov
 */
public class OpenAiEmbeddingClient extends AbstractEmbeddingClient {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

	public static final String DEFAULT_OPENAI_EMBEDDING_MODEL = "text-embedding-ada-002";

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	private final OpenAiApi openAiApi;

	private final String embeddingModelName;

	private final MetadataMode metadataMode;

	public OpenAiEmbeddingClient(OpenAiApi openAiApi) {
		this(openAiApi, DEFAULT_OPENAI_EMBEDDING_MODEL);
	}

	public OpenAiEmbeddingClient(OpenAiApi openAiApi, String embeddingModel) {
		this(openAiApi, embeddingModel, MetadataMode.EMBED);
	}

	public OpenAiEmbeddingClient(OpenAiApi openAiApi, String model, MetadataMode metadataMode) {
		Assert.notNull(openAiApi, "OpenAiService must not be null");
		Assert.notNull(model, "Model must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		this.openAiApi = openAiApi;
		this.embeddingModelName = model;
		this.metadataMode = metadataMode;
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public List<Double> embed(String text) {
		Assert.notNull(text, "Text must not be null");
		return this.embed(List.of(text)).iterator().next();
	}

	@Override
	public List<List<Double>> embed(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		EmbeddingRequest<List<String>> request = new EmbeddingRequest<>(texts, this.embeddingModelName);
		return this.retryTemplate.execute(ctx -> {
			EmbeddingList<OpenAiApi.Embedding> body = this.openAiApi.embeddings(request).getBody();
			if (body == null) {
				logger.warn("No embeddings returned for request: {}", request);
				return List.of();
			}
			return body.data().stream().map(embedding -> embedding.embedding()).toList();
		});
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {

		Assert.notNull(texts, "Texts must not be null");

		return this.retryTemplate.execute(ctx -> {
			EmbeddingRequest<List<String>> request = new EmbeddingRequest<>(texts, this.embeddingModelName);

			EmbeddingList<OpenAiApi.Embedding> embeddingResponse = this.openAiApi.embeddings(request).getBody();

			if (embeddingResponse == null) {
				logger.warn("No embeddings returned for request: {}", request);
				return new EmbeddingResponse(List.of(), Map.of());
			}

			Map<String, Object> metadata = generateMetadata(embeddingResponse.model(), embeddingResponse.usage());

			List<Embedding> embeddings = embeddingResponse.data()
				.stream()
				.map(e -> new Embedding(e.embedding(), e.index()))
				.toList();

			return new EmbeddingResponse(embeddings, metadata);
		});
	}

	private Map<String, Object> generateMetadata(String model, Usage usage) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("model", model);
		metadata.put("prompt-tokens", usage.promptTokens());
		metadata.put("completion-tokens", usage.completionTokens());
		metadata.put("total-tokens", usage.totalTokens());
		return metadata;
	}

}
