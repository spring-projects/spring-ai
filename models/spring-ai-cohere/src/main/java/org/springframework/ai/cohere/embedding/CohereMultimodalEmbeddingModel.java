/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.cohere.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingModel;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Implementation of the Cohere Multimodal Embedding Model.
 *
 * @author Ricken Bazolo
 */
public class CohereMultimodalEmbeddingModel implements DocumentEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(CohereMultimodalEmbeddingModel.class);

	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Map.of(
			CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_V3.getValue(), 1024,
			CohereApi.EmbeddingModel.EMBED_ENGLISH_V3.getValue(), 1024,
			CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_LIGHT_V3.getValue(), 384,
			CohereApi.EmbeddingModel.EMBED_ENGLISH_LIGHT_V3.getValue(), 384,
			CohereApi.EmbeddingModel.EMBED_V4.getValue(), 1536);

	private final CohereMultimodalEmbeddingOptions defaultOptions;

	private final CohereApi cohereApi;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	public CohereMultimodalEmbeddingModel(CohereApi cohereApi, CohereMultimodalEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(cohereApi, "cohereApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.cohereApi = cohereApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(DocumentEmbeddingRequest request) {
		CohereMultimodalEmbeddingOptions mergedOptions = mergeOptions(request.getOptions(), this.defaultOptions);

		List<Embedding> allEmbeddings = new ArrayList<>();
		EmbeddingResponseMetadata lastMetadata = null;

		for (Document document : request.getInstructions()) {
			CohereApi.EmbeddingRequest<?> apiRequest;

			if (document.getMedia() != null) {
				apiRequest = createImageRequest(document, mergedOptions);
			}
			else if (StringUtils.hasText(document.getText())) {
				apiRequest = createTextRequest(document, mergedOptions);
			}
			else {
				logger.warn("Document {} has no text or media content", document.getId());
				continue;
			}

			var apiResponse = RetryUtils.execute(this.retryTemplate,
					() -> this.cohereApi.embeddings(apiRequest).getBody());

			if (apiResponse != null) {
				List<float[]> floatEmbeddings = apiResponse.getFloatEmbeddings();
				for (int i = 0; i < floatEmbeddings.size(); i++) {
					allEmbeddings.add(new Embedding(floatEmbeddings.get(i), allEmbeddings.size()));
				}
				lastMetadata = generateResponseMetadata(apiResponse.responseType());
			}
		}

		return new EmbeddingResponse(allEmbeddings, lastMetadata);
	}

	@Override
	public int dimensions() {
		String model = this.defaultOptions.getModel();
		if (model == null) {
			return KNOWN_EMBEDDING_DIMENSIONS.get(CohereApi.EmbeddingModel.EMBED_V4.getValue());
		}
		return KNOWN_EMBEDDING_DIMENSIONS.getOrDefault(model, 1024);
	}

	private CohereApi.EmbeddingRequest<String> createTextRequest(Document document,
			CohereMultimodalEmbeddingOptions options) {
		return CohereApi.EmbeddingRequest.<String>builder()
			.model(options.getModel())
			.inputType(CohereApi.EmbeddingRequest.InputType.CLASSIFICATION)
			.embeddingTypes(options.getEmbeddingTypes())
			.texts(List.of(document.getText()))
			.truncate(options.getTruncate())
			.build();
	}

	private CohereApi.EmbeddingRequest<String> createImageRequest(Document document,
			CohereMultimodalEmbeddingOptions options) {

		String dataUri = CohereEmbeddingUtils.mediaToDataUri(document.getMedia());

		return CohereApi.EmbeddingRequest.<String>builder()
			.model(options.getModel())
			.inputType(CohereApi.EmbeddingRequest.InputType.IMAGE)
			.embeddingTypes(options.getEmbeddingTypes())
			.images(List.of(dataUri))
			.truncate(options.getTruncate())
			.build();
	}

	private CohereMultimodalEmbeddingOptions mergeOptions(
			org.springframework.ai.embedding.EmbeddingOptions requestOptions,
			CohereMultimodalEmbeddingOptions defaultOptions) {
		CohereMultimodalEmbeddingOptions options = (requestOptions != null)
				? ModelOptionsUtils.merge(requestOptions, defaultOptions, CohereMultimodalEmbeddingOptions.class)
				: defaultOptions;

		if (options == null) {
			throw new IllegalArgumentException("Embedding options must not be null");
		}

		return options;
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String embeddingType) {
		return new EmbeddingResponseMetadata(embeddingType, null);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private CohereApi cohereApi;

		private CohereMultimodalEmbeddingOptions options = CohereMultimodalEmbeddingOptions.builder()
			.model(CohereApi.EmbeddingModel.EMBED_V4.getValue())
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder cohereApi(CohereApi cohereApi) {
			this.cohereApi = cohereApi;
			return this;
		}

		public Builder options(CohereMultimodalEmbeddingOptions options) {
			this.options = options;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public CohereMultimodalEmbeddingModel build() {
			return new CohereMultimodalEmbeddingModel(this.cohereApi, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
