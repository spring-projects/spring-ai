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

package org.springframework.ai.qianfan;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;

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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.qianfan.api.QianFanApi.EmbeddingList;
import org.springframework.ai.qianfan.api.QianFanConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * QianFan Embedding Client implementation.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0
 */
public class QianFanEmbeddingModel extends AbstractEmbeddingModel {

	private static final LogAccessor logger = new LogAccessor(QianFanEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final QianFanEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final QianFanApi qianFanApi;

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
	 * Constructor for the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi) {
		this(qianFanApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode) {
		this(qianFanApi, metadataMode,
				QianFanEmbeddingOptions.builder().model(QianFanApi.DEFAULT_EMBEDDING_MODEL).build());
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param qianFanEmbeddingOptions The options for QianFan embedding.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode,
			QianFanEmbeddingOptions qianFanEmbeddingOptions) {
		this(qianFanApi, metadataMode, qianFanEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi The QianFanApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param qianFanEmbeddingOptions The options for QianFan embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode,
			QianFanEmbeddingOptions qianFanEmbeddingOptions, RetryTemplate retryTemplate) {
		this(qianFanApi, metadataMode, qianFanEmbeddingOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the QianFanEmbeddingModel class.
	 * @param qianFanApi - The QianFanApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for QianFan embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 * @param observationRegistry - The ObservationRegistry used for instrumentation.
	 */
	public QianFanEmbeddingModel(QianFanApi qianFanApi, MetadataMode metadataMode, QianFanEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(qianFanApi, "QianFanApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.qianFanApi = qianFanApi;
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
		QianFanEmbeddingOptions requestOptions = mergeOptions(request.getOptions(), this.defaultOptions);
		QianFanApi.EmbeddingRequest apiRequest = new QianFanApi.EmbeddingRequest(request.getInstructions(),
				requestOptions.getModel(), requestOptions.getUser());

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(QianFanConstants.PROVIDER_NAME)
			.requestOptions(requestOptions)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				EmbeddingList apiEmbeddingResponse = this.retryTemplate
					.execute(ctx -> this.qianFanApi.embeddings(apiRequest).getBody());

				if (apiEmbeddingResponse == null) {
					logger.warn("No embeddings returned for request: " + request);
					return new EmbeddingResponse(List.of());
				}

				if (apiEmbeddingResponse.errorNsg() != null) {
					logger.error("Error message returned for request: " + apiEmbeddingResponse.errorNsg());
					throw new RuntimeException("Embedding failed: error code:" + apiEmbeddingResponse.errorCode()
							+ ", message:" + apiEmbeddingResponse.errorNsg());
				}

				var metadata = new EmbeddingResponseMetadata(apiRequest.model(),
						getDefaultUsage(apiEmbeddingResponse.usage()));

				List<Embedding> embeddings = apiEmbeddingResponse.data()
					.stream()
					.map(e -> new Embedding(e.embedding(), e.index()))
					.toList();

				EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});

	}

	private DefaultUsage getDefaultUsage(QianFanApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	/**
	 * Merge runtime and default {@link EmbeddingOptions} to compute the final options to
	 * use in the request.
	 */
	private QianFanEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions runtimeOptions,
			QianFanEmbeddingOptions defaultOptions) {
		var runtimeOptionsForProvider = ModelOptionsUtils.copyToTarget(runtimeOptions, EmbeddingOptions.class,
				QianFanEmbeddingOptions.class);

		if (runtimeOptionsForProvider == null) {
			return defaultOptions;
		}

		return QianFanEmbeddingOptions.builder()
			.model(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getModel(), defaultOptions.getModel()))
			.user(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getUser(), defaultOptions.getUser()))
			.build();
	}

	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
