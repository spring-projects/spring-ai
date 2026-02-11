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

package org.springframework.ai.zhipuai;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
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
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuApiConstants;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * ZhiPuAI Embedding Model implementation.
 *
 * @author Geng Rong
 * @author Soby Chacko
 * @author YuJie Wan
 * @since 1.0.0
 */
public class ZhiPuAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(ZhiPuAiEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final ZhiPuAiEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ZhiPuAiApi zhiPuAiApi;

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
	 * Constructor for the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi) {
		this(zhiPuAiApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode) {
		this(zhiPuAiApi, metadataMode,
				ZhiPuAiEmbeddingOptions.builder().model(ZhiPuAiApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param zhiPuAiEmbeddingOptions The options for ZhiPuAI embedding.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode,
			ZhiPuAiEmbeddingOptions zhiPuAiEmbeddingOptions) {
		this(zhiPuAiApi, metadataMode, zhiPuAiEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param zhiPuAiEmbeddingOptions The options for ZhiPuAI embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode,
			ZhiPuAiEmbeddingOptions zhiPuAiEmbeddingOptions, RetryTemplate retryTemplate) {
		this(zhiPuAiApi, metadataMode, zhiPuAiEmbeddingOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the ZhiPuAiEmbeddingModel class.
	 * @param zhiPuAiApi - The ZhiPuAiApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for ZhiPuAI embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 * @param observationRegistry - The ObservationRegistry used for instrumentation.
	 */
	public ZhiPuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi, MetadataMode metadataMode, ZhiPuAiEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(zhiPuAiApi, "ZhiPuAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.zhiPuAiApi = zhiPuAiApi;
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
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");

		var embeddingRequest = buildEmbeddingRequest(request);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(ZhiPuApiConstants.PROVIDER_NAME)
			.build();

		var zhipuEmbeddingRequest = zhipuEmbeddingRequest(embeddingRequest);
		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ResponseEntity<ZhiPuAiApi.EmbeddingList<ZhiPuAiApi.Embedding>> embeddingResponse = RetryUtils
					.execute(this.retryTemplate, () -> this.zhiPuAiApi.embeddings(zhipuEmbeddingRequest));

				if (embeddingResponse == null || embeddingResponse.getBody() == null
						|| CollectionUtils.isEmpty(embeddingResponse.getBody().data())) {
					logger.warn("No embeddings returned for request: {}", request);
					return new EmbeddingResponse(List.of());
				}

				ZhiPuAiApi.Usage usage = embeddingResponse.getBody().usage();
				Usage usageResponse = usage != null ? getDefaultUsage(usage) : new EmptyUsage();

				var metadata = new EmbeddingResponseMetadata(embeddingResponse.getBody().model(), usageResponse);

				List<Embedding> embeddings = embeddingResponse.getBody()
					.data()
					.stream()
					.map(e -> new Embedding(e.embedding(), e.index()))
					.toList();

				EmbeddingResponse response = new EmbeddingResponse(embeddings, metadata);
				observationContext.setResponse(response);
				return response;
			});
	}

	private ZhiPuAiApi.EmbeddingRequest<List<String>> zhipuEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		return new ZhiPuAiApi.EmbeddingRequest<>(embeddingRequest.getInstructions(),
				embeddingRequest.getOptions().getModel(), embeddingRequest.getOptions().getDimensions());
	}

	private DefaultUsage getDefaultUsage(ZhiPuAiApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		ZhiPuAiEmbeddingOptions runtimeOptions = null;
		if (embeddingRequest.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(), EmbeddingOptions.class,
					ZhiPuAiEmbeddingOptions.class);
		}

		// Define request options by merging runtime options and default options
		ZhiPuAiEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				ZhiPuAiEmbeddingOptions.class);

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
