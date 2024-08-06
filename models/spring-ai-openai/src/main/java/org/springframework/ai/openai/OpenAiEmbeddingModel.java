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
package org.springframework.ai.openai;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelRequestOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingList;
import org.springframework.ai.openai.metadata.OpenAiUsage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Open AI Embedding Model implementation.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
public class OpenAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final OpenAiEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final OpenAiApi openAiApi;

	private final MetadataMode metadataMode;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Constructor for the OpenAiEmbeddingModel class.
	 * @param openAiApi The OpenAiApi instance to use for making API requests.
	 */
	public OpenAiEmbeddingModel(OpenAiApi openAiApi) {
		this(openAiApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the OpenAiEmbeddingModel class.
	 * @param openAiApi The OpenAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public OpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode) {
		this(openAiApi, metadataMode,
				OpenAiEmbeddingOptions.builder().withModel(OpenAiApi.DEFAULT_EMBEDDING_MODEL).build());
	}

	/**
	 * Initializes a new instance of the OpenAiEmbeddingModel class.
	 * @param openAiApi The OpenAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param openAiEmbeddingOptions The options for OpenAi embedding.
	 */
	public OpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode,
			OpenAiEmbeddingOptions openAiEmbeddingOptions) {
		this(openAiApi, metadataMode, openAiEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiEmbeddingModel class.
	 * @param openAiApi - The OpenAiApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for OpenAI embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public OpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode, OpenAiEmbeddingOptions options,
			RetryTemplate retryTemplate) {
		this(openAiApi, metadataMode, options, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the OpenAiEmbeddingModel class.
	 * @param openAiApi - The OpenAiApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for OpenAI embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 * @param observationRegistry - The ObservationRegistry used for instrumentation.
	 */
	public OpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode, OpenAiEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(openAiApi, "OpenAiService must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.openAiApi = openAiApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest<List<String>> apiRequest = createRequest(request);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.operationMetadata(buildOperationMetadata())
			.requestOptions(buildRequestOptions(apiRequest))
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				EmbeddingList<OpenAiApi.Embedding> apiEmbeddingResponse = this.retryTemplate
					.execute(ctx -> this.openAiApi.embeddings(apiRequest).getBody());

				if (apiEmbeddingResponse == null) {
					logger.warn("No embeddings returned for request: {}", request);
					return new EmbeddingResponse(List.of());
				}

				var metadata = new EmbeddingResponseMetadata(apiEmbeddingResponse.model(),
						OpenAiUsage.from(apiEmbeddingResponse.usage()));

				List<Embedding> embeddings = apiEmbeddingResponse.data()
					.stream()
					.map(e -> new Embedding(e.embedding(), e.index()))
					.toList();

				EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	@SuppressWarnings("unchecked")
	private OpenAiApi.EmbeddingRequest<List<String>> createRequest(EmbeddingRequest request) {
		org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest<List<String>> apiRequest = (this.defaultOptions != null)
				? new org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest<>(request.getInstructions(),
						this.defaultOptions.getModel(), this.defaultOptions.getEncodingFormat(),
						this.defaultOptions.getDimensions(), this.defaultOptions.getUser())
				: new org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest<>(request.getInstructions(),
						OpenAiApi.DEFAULT_EMBEDDING_MODEL);

		if (request.getOptions() != null && !EmbeddingOptions.EMPTY.equals(request.getOptions())) {
			apiRequest = ModelOptionsUtils.merge(request.getOptions(), apiRequest,
					org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest.class);
		}

		return apiRequest;
	}

	private AiOperationMetadata buildOperationMetadata() {
		return AiOperationMetadata.builder()
			.operationType(AiOperationType.EMBEDDING.value())
			.provider(AiProvider.OPENAI.value())
			.build();
	}

	private EmbeddingModelRequestOptions buildRequestOptions(OpenAiApi.EmbeddingRequest<List<String>> request) {
		return EmbeddingModelRequestOptions.builder()
			.model(StringUtils.hasText(request.model()) ? request.model() : "unknown")
			.dimensions(request.dimensions())
			.encodingFormat(request.encodingFormat())
			.build();
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
