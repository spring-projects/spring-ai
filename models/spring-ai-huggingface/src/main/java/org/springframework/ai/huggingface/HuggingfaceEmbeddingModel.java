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

package org.springframework.ai.huggingface;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.HuggingfaceApi.EmbeddingsResponse;
import org.springframework.ai.huggingface.api.common.HuggingfaceApiConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link EmbeddingModel} implementation for HuggingFace Inference API. HuggingFace
 * provides access to thousands of pre-trained models for various NLP tasks including text
 * embeddings.
 *
 * @author Myeongdeok Kang
 */
public class HuggingfaceEmbeddingModel extends AbstractEmbeddingModel {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final HuggingfaceApi huggingfaceApi;

	private final HuggingfaceEmbeddingOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private final RetryTemplate retryTemplate;

	/**
	 * Constructor for HuggingfaceEmbeddingModel.
	 * @param huggingfaceApi The HuggingFace API client.
	 * @param defaultOptions Default embedding options.
	 * @param observationRegistry Observation registry for metrics.
	 * @param retryTemplate Retry template for handling transient errors.
	 */
	public HuggingfaceEmbeddingModel(HuggingfaceApi huggingfaceApi, HuggingfaceEmbeddingOptions defaultOptions,
			ObservationRegistry observationRegistry, RetryTemplate retryTemplate) {
		Assert.notNull(huggingfaceApi, "huggingfaceApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.huggingfaceApi = huggingfaceApi;
		this.defaultOptions = defaultOptions;
		this.observationRegistry = observationRegistry;
		this.retryTemplate = retryTemplate;
	}

	/**
	 * Create a new builder for HuggingfaceEmbeddingModel.
	 * @return A new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return embed(document.getText());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");

		// Build the final request, merging runtime and default options
		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		HuggingfaceApi.EmbeddingsRequest huggingfaceEmbeddingRequest = createApiRequest(embeddingRequest);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(HuggingfaceApiConstants.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				EmbeddingsResponse response = RetryUtils.execute(this.retryTemplate,
						() -> this.huggingfaceApi.embeddings(huggingfaceEmbeddingRequest));

				AtomicInteger indexCounter = new AtomicInteger(0);

				List<Embedding> embeddings = convertToEmbeddings(response.embeddings(), indexCounter);

				// HuggingFace Inference API doesn't provide usage information
				EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata(response.model(), new EmptyUsage());

				EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	/**
	 * Convert list of float arrays to list of Embedding objects.
	 * @param embeddingsList The list of embedding arrays from the API.
	 * @param indexCounter Counter for tracking embedding indices.
	 * @return List of Embedding objects.
	 */
	private List<Embedding> convertToEmbeddings(List<float[]> embeddingsList, AtomicInteger indexCounter) {
		return embeddingsList.stream()
			.map(embeddingVector -> new Embedding(embeddingVector, indexCounter.getAndIncrement()))
			.toList();
	}

	/**
	 * Build the embedding request by merging runtime and default options.
	 * @param embeddingRequest The original embedding request.
	 * @return A new embedding request with merged options.
	 */
	EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		HuggingfaceEmbeddingOptions runtimeOptions = null;
		if (embeddingRequest.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(), EmbeddingOptions.class,
					HuggingfaceEmbeddingOptions.class);
		}

		// Merge runtime and default options
		HuggingfaceEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				HuggingfaceEmbeddingOptions.class);

		// Validate
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new EmbeddingRequest(embeddingRequest.getInstructions(), requestOptions);
	}

	/**
	 * Create the API request from the embedding request.
	 * @param embeddingRequest The embedding request.
	 * @return The API request.
	 */
	private HuggingfaceApi.EmbeddingsRequest createApiRequest(EmbeddingRequest embeddingRequest) {
		HuggingfaceEmbeddingOptions options = (HuggingfaceEmbeddingOptions) embeddingRequest.getOptions();
		return new HuggingfaceApi.EmbeddingsRequest(options.getModel(), embeddingRequest.getInstructions(),
				options.toMap());
	}

	/**
	 * Set the observation convention for reporting metrics.
	 * @param observationConvention The observation convention.
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Builder for creating HuggingfaceEmbeddingModel instances.
	 */
	public static final class Builder {

		private HuggingfaceApi huggingfaceApi;

		private HuggingfaceEmbeddingOptions defaultOptions = HuggingfaceEmbeddingOptions.builder()
			.model(HuggingfaceApi.DEFAULT_EMBEDDING_MODEL)
			.build();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private Builder() {
		}

		/**
		 * Set the HuggingFace API client.
		 * @param huggingfaceApi The API client.
		 * @return This builder.
		 */
		public Builder huggingfaceApi(HuggingfaceApi huggingfaceApi) {
			this.huggingfaceApi = huggingfaceApi;
			return this;
		}

		/**
		 * Set the default embedding options.
		 * @param defaultOptions The default options.
		 * @return This builder.
		 */
		public Builder defaultOptions(HuggingfaceEmbeddingOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Set the observation registry.
		 * @param observationRegistry The observation registry.
		 * @return This builder.
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Set the retry template.
		 * @param retryTemplate The retry template.
		 * @return This builder.
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Build the HuggingfaceEmbeddingModel instance.
		 * @return A new HuggingfaceEmbeddingModel.
		 */
		public HuggingfaceEmbeddingModel build() {
			Assert.notNull(this.huggingfaceApi, "huggingfaceApi must not be null");
			return new HuggingfaceEmbeddingModel(this.huggingfaceApi, this.defaultOptions, this.observationRegistry,
					this.retryTemplate);
		}

	}

}
