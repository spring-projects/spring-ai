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

package org.springframework.ai.mistralai;

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
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Provides the Mistral AI Embedding Model.
 *
 * @see AbstractEmbeddingModel
 * @author Ricken Bazolo
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class MistralAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(MistralAiEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final MistralAiEmbeddingOptions defaultOptions;

	private final MistralAiApi mistralAiApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi) {
		this(mistralAiApi, MetadataMode.EMBED);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MetadataMode metadataMode) {
		this(mistralAiApi, metadataMode,
				MistralAiEmbeddingOptions.builder().withModel(MistralAiApi.EmbeddingModel.EMBED.getValue()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MistralAiEmbeddingOptions options) {
		this(mistralAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MetadataMode metadataMode,
			MistralAiEmbeddingOptions options, RetryTemplate retryTemplate) {
		this(mistralAiApi, metadataMode, options, retryTemplate, ObservationRegistry.NOOP);
	}

	public MistralAiEmbeddingModel(MistralAiApi mistralAiApi, MetadataMode metadataMode,
			MistralAiEmbeddingOptions options, RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {

		super(metadataMode);

		Assert.notNull(mistralAiApi, "mistralAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.mistralAiApi = mistralAiApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		var apiRequest = createRequest(embeddingRequest);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(MistralAiApi.PROVIDER_NAME)
			.requestOptions(embeddingRequest.getOptions())
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				var apiEmbeddingResponse = this.retryTemplate
					.execute(ctx -> this.mistralAiApi.embeddings(apiRequest).getBody());

				if (apiEmbeddingResponse == null) {
					logger.warn("No embeddings returned for request: {}", request);
					return new EmbeddingResponse(List.of());
				}

				var metadata = new EmbeddingResponseMetadata(apiEmbeddingResponse.model(),
						getDefaultUsage(apiEmbeddingResponse.usage()));

				var embeddings = apiEmbeddingResponse.data()
					.stream()
					.map(e -> new Embedding(e.embedding(), e.index()))
					.toList();

				var embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	private EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		MistralAiEmbeddingOptions runtimeOptions = null;
		if (embeddingRequest.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(), EmbeddingOptions.class,
					MistralAiEmbeddingOptions.class);
		}

		// Define request options by merging runtime options and default options
		MistralAiEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				MistralAiEmbeddingOptions.class);

		return new EmbeddingRequest(embeddingRequest.getInstructions(), requestOptions);
	}

	private DefaultUsage getDefaultUsage(MistralAiApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	private MistralAiApi.EmbeddingRequest<List<String>> createRequest(EmbeddingRequest request) {
		MistralAiEmbeddingOptions requestOptions = (MistralAiEmbeddingOptions) request.getOptions();
		return new MistralAiApi.EmbeddingRequest<>(request.getInstructions(), requestOptions.getModel(),
				requestOptions.getEncodingFormat());
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
