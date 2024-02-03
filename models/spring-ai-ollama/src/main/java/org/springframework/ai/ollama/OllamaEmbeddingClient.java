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

package org.springframework.ai.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link EmbeddingClient} implementation for {@literal Ollama}.
 *
 * Ollama allows developers to run large language models and generate embeddings locally.
 * It supports open-source models available on [Ollama AI
 * Library](https://ollama.ai/library).
 *
 * Examples of models supported: - Llama 2 (7B parameters, 3.8GB size) - Mistral (7B
 * parameters, 4.1GB size)
 *
 * Please refer to the <a href="https://ollama.ai/">official Ollama website</a> for the
 * most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaEmbeddingClient extends AbstractEmbeddingClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OllamaApi ollamaApi;

	/**
	 * Default options to be used for all chat requests.
	 */
	private OllamaOptions defaultOptions = OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL);

	public OllamaEmbeddingClient(OllamaApi ollamaApi) {
		this.ollamaApi = ollamaApi;
	}

	/**
	 * @deprecated Use {@link OllamaOptions#setModel} instead.
	 */
	@Deprecated
	public OllamaEmbeddingClient withModel(String model) {
		this.defaultOptions.setModel(model);
		return this;
	}

	public OllamaEmbeddingClient withDefaultOptions(OllamaOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public List<Double> embed(Document document) {
		return embed(document.getContent());
	}

	@Override
	public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");
		if (request.getInstructions().size() != 1) {
			logger.warn(
					"Ollama Embedding does not support batch embedding. Will make multiple API calls to embed(Document)");
		}

		List<List<Double>> embeddingList = new ArrayList<>();
		for (String inputContent : request.getInstructions()) {

			var ollamaEmbeddingRequest = ollamaEmbeddingRequest(inputContent, request.getOptions());

			OllamaApi.EmbeddingResponse response = this.ollamaApi.embeddings(ollamaEmbeddingRequest);

			embeddingList.add(response.embedding());
		}
		var indexCounter = new AtomicInteger(0);

		List<Embedding> embeddings = embeddingList.stream()
			.map(e -> new Embedding(e, indexCounter.getAndIncrement()))
			.toList();
		return new EmbeddingResponse(embeddings);
	}

	/**
	 * Package access for testing.
	 */
	OllamaApi.EmbeddingRequest ollamaEmbeddingRequest(String inputContent, EmbeddingOptions options) {

		// runtime options
		OllamaOptions runtimeOptions = null;
		if (options != null) {
			if (options instanceof OllamaOptions ollamaOptions) {
				runtimeOptions = ollamaOptions;
			}
			// currently EmbeddingOptions does not have any portable options to be
			// merged.
			runtimeOptions = null;
		}

		OllamaOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OllamaOptions.class);

		// Override the model.
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}
		String model = mergedOptions.getModel();
		return new EmbeddingRequest(model, inputContent, OllamaOptions.filterNonSupportedFields(mergedOptions.toMap()));
	}

}