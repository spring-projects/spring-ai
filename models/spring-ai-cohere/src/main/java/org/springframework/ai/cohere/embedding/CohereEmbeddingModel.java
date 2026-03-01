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

import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Provides the Cohere Embedding Model.
 *
 * @author Ricken Bazolo
 * @see AbstractEmbeddingModel
 */
public class CohereEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(CohereEmbeddingModel.class);

	/**
	 * Known embedding dimensions for Cohere models. Maps model names to their respective
	 * embedding vector dimensions. This allows the dimensions() method to return the
	 * correct value without making an API call.
	 */
	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Map.of(
			CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_V3.getValue(), 1024,
			CohereApi.EmbeddingModel.EMBED_ENGLISH_V3.getValue(), 1024,
			CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_LIGHT_V3.getValue(), 384,
			CohereApi.EmbeddingModel.EMBED_ENGLISH_LIGHT_V3.getValue(), 384,
			CohereApi.EmbeddingModel.EMBED_V4.getValue(), 1536);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final CohereEmbeddingOptions defaultOptions;

	private final MetadataMode metadataMode;

	private final CohereApi cohereApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public CohereEmbeddingModel(CohereApi cohereApi, MetadataMode metadataMode, CohereEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(cohereApi, "cohereApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.cohereApi = cohereApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		var apiRequest = createRequest(request);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(CohereApi.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				var apiEmbeddingResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.cohereApi.embeddings(apiRequest).getBody());

				if (apiEmbeddingResponse == null) {
					logger.warn("No embeddings returned for request: {}", request);
					return new EmbeddingResponse(List.of());
				}

				var metadata = generateResponseMetadata(apiEmbeddingResponse.responseType());

				// Extract float embeddings from response
				List<float[]> floatEmbeddings = apiEmbeddingResponse.getFloatEmbeddings();

				// Map to Spring AI Embedding objects with proper indexing
				List<Embedding> embeddings = new java.util.ArrayList<>();
				for (int i = 0; i < floatEmbeddings.size(); i++) {
					embeddings.add(new Embedding(floatEmbeddings.get(i), i));
				}

				var embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String embeddingType) {
		return new EmbeddingResponseMetadata(embeddingType, null);
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	private CohereApi.EmbeddingRequest<String> createRequest(EmbeddingRequest request) {
		CohereEmbeddingOptions options = mergeOptions(request.getOptions(), this.defaultOptions);

		return CohereApi.EmbeddingRequest.<String>builder()
			.model(options.getModel())
			.inputType(options.getInputType())
			.embeddingTypes(options.getEmbeddingTypes())
			.texts(request.getInstructions())
			.truncate(options.getTruncate())
			.build();
	}

	private CohereEmbeddingOptions mergeOptions(EmbeddingOptions requestOptions,
			CohereEmbeddingOptions defaultOptions) {
		CohereEmbeddingOptions options = (requestOptions != null)
				? ModelOptionsUtils.merge(requestOptions, defaultOptions, CohereEmbeddingOptions.class)
				: defaultOptions;

		if (options == null) {
			throw new IllegalArgumentException("Embedding options must not be null");
		}

		return options;
	}

	private CohereEmbeddingOptions buildRequestOptions(EmbeddingRequest request) {
		return mergeOptions(request.getOptions(), this.defaultOptions);
	}

	@Override
	public int dimensions() {
		String model = this.defaultOptions.getModel();
		if (model == null) {
			return KNOWN_EMBEDDING_DIMENSIONS.get(CohereApi.EmbeddingModel.EMBED_V4.getValue());
		}
		return KNOWN_EMBEDDING_DIMENSIONS.getOrDefault(model, 1024);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private CohereApi cohereApi;

		private MetadataMode metadataMode = MetadataMode.EMBED;

		private CohereEmbeddingOptions options = CohereEmbeddingOptions.builder()
			.model(CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_LIGHT_V3.getValue())
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder cohereApi(CohereApi cohereApi) {
			this.cohereApi = cohereApi;
			return this;
		}

		public Builder metadataMode(MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
			return this;
		}

		public Builder options(CohereEmbeddingOptions options) {
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

		public CohereEmbeddingModel build() {
			return new CohereEmbeddingModel(this.cohereApi, this.metadataMode, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
