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

package org.springframework.ai.minimax;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.metadata.DefaultUsage;
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
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.api.MiniMaxApiConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * MiniMax Embedding Model implementation.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Soby Chacko
 * @since 1.0.0
 */
public class MiniMaxEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(MiniMaxEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final MiniMaxEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final MiniMaxApi miniMaxApi;

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
	 * Constructor for the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi) {
		this(miniMaxApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode) {
		this(miniMaxApi, metadataMode,
				MiniMaxEmbeddingOptions.builder().model(MiniMaxApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param miniMaxEmbeddingOptions The options for MiniMax embedding.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode,
			MiniMaxEmbeddingOptions miniMaxEmbeddingOptions) {
		this(miniMaxApi, metadataMode, miniMaxEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE,
				ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param miniMaxEmbeddingOptions The options for MiniMax embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode,
			MiniMaxEmbeddingOptions miniMaxEmbeddingOptions, RetryTemplate retryTemplate) {
		this(miniMaxApi, metadataMode, miniMaxEmbeddingOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi - The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for MiniMax embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 * @param observationRegistry - The ObservationRegistry used for instrumentation.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode, MiniMaxEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(miniMaxApi, "MiniMaxApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.miniMaxApi = miniMaxApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		MiniMaxApi.EmbeddingRequest apiRequest = new MiniMaxApi.EmbeddingRequest(request.getInstructions(),
				embeddingRequest.getOptions().getModel());

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(MiniMaxApiConstants.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				MiniMaxApi.EmbeddingList apiEmbeddingResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.miniMaxApi.embeddings(apiRequest).getBody());

				if (apiEmbeddingResponse == null) {
					logger.warn("No embeddings returned for request: {}", request);
					return new EmbeddingResponse(List.of());
				}

				var metadata = new EmbeddingResponseMetadata(apiRequest.model(), getDefaultUsage(apiEmbeddingResponse));

				List<Embedding> embeddings = new ArrayList<>();
				for (int i = 0; i < apiEmbeddingResponse.vectors().size(); i++) {
					float[] vector = apiEmbeddingResponse.vectors().get(i);
					embeddings.add(new Embedding(vector, i));
				}
				EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);
				observationContext.setResponse(embeddingResponse);
				return embeddingResponse;
			});
	}

	private DefaultUsage getDefaultUsage(MiniMaxApi.EmbeddingList apiEmbeddingList) {
		return new DefaultUsage(0, 0, apiEmbeddingList.totalTokens());
	}

	EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		MiniMaxEmbeddingOptions runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(),
				EmbeddingOptions.class, MiniMaxEmbeddingOptions.class);

		// Define request options by merging runtime options and default options
		MiniMaxEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				MiniMaxEmbeddingOptions.class);

		// Validate request options
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new EmbeddingRequest(embeddingRequest.getInstructions(), requestOptions);
	}

	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
