/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.voyageai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

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
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Provides the Voyage AI Embedding Model.
 *
 * @see AbstractEmbeddingModel
 * @author Spring AI
 * @since 2.0.0
 */
public class VoyageAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final Log logger = LogFactory.getLog(VoyageAiEmbeddingModel.class);

	/**
	 * Known embedding dimensions for Voyage AI models. Allows the dimensions() method to
	 * return the correct value without making an API call.
	 */
	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Stream
		.of(VoyageAiEmbeddingModelName.values())
		.collect(Collectors.toMap(VoyageAiEmbeddingModelName::getName, VoyageAiEmbeddingModelName::getDimensions));

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final VoyageAiEmbeddingOptions options;

	private final MetadataMode metadataMode;

	private final VoyageAiApi voyageAiApi;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public VoyageAiEmbeddingModel(VoyageAiApi voyageAiApi, MetadataMode metadataMode, VoyageAiEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(voyageAiApi, "voyageAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.voyageAiApi = voyageAiApi;
		this.metadataMode = metadataMode;
		this.options = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		VoyageAiApi.EmbeddingRequest apiRequest = createRequest(embeddingRequest);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(VoyageAiApi.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				var apiResponse = RetryUtils.execute(this.retryTemplate, () -> this.voyageAiApi.embeddings(apiRequest))
					.getBody();

				if (apiResponse == null || apiResponse.data() == null) {
					if (logger.isWarnEnabled()) {
						logger.warn("No embeddings returned for request: " + request);
					}
					return new EmbeddingResponse(List.of());
				}

				var metadata = new EmbeddingResponseMetadata(apiResponse.model(), getDefaultUsage(apiResponse.usage()));

				var embeddings = apiResponse.data().stream().map(e -> new Embedding(e.embedding(), e.index())).toList();

				var embeddingResponse = new EmbeddingResponse(embeddings, metadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	private EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		EmbeddingOptions requestOptions = embeddingRequest.getOptions();
		VoyageAiEmbeddingOptions mergedOptions = this.options;

		if (requestOptions != null) {
			VoyageAiEmbeddingOptions.Builder builder = VoyageAiEmbeddingOptions.builder()
				.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.options.getModel()))
				.outputDimension(ModelOptionsUtils.mergeOption(requestOptions.getDimensions(),
						this.options.getOutputDimension()));

			if (requestOptions instanceof VoyageAiEmbeddingOptions voyageOptions) {
				builder
					.inputType(ModelOptionsUtils.mergeOption(voyageOptions.getInputType(), this.options.getInputType()))
					.truncation(
							ModelOptionsUtils.mergeOption(voyageOptions.getTruncation(), this.options.getTruncation()));
			}
			else {
				builder.inputType(this.options.getInputType()).truncation(this.options.getTruncation());
			}
			mergedOptions = builder.build();
		}

		return new EmbeddingRequest(embeddingRequest.getInstructions(), mergedOptions);
	}

	private VoyageAiApi.EmbeddingRequest createRequest(EmbeddingRequest request) {
		VoyageAiEmbeddingOptions requestOptions = (VoyageAiEmbeddingOptions) Objects
			.requireNonNull(request.getOptions());
		String model = resolveModel(requestOptions.getModel());
		return new VoyageAiApi.EmbeddingRequest(request.getInstructions(), model, requestOptions.getInputType(),
				requestOptions.getOutputDimension(), requestOptions.getTruncation());
	}

	private String resolveModel(@Nullable String model) {
		return model != null ? model : VoyageAiEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;
	}

	private DefaultUsage getDefaultUsage(VoyageAiApi.@Nullable Usage usage) {
		Integer totalTokens = usage != null ? usage.totalTokens() : 0;
		return new DefaultUsage(0, 0, totalTokens, usage);
	}

	@Override
	public String getEmbeddingContent(Document document) {
		Assert.notNull(document, "Document must not be null");
		return document.getFormattedContent(this.metadataMode);
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public int dimensions() {
		if (this.options.getOutputDimension() != null) {
			return this.options.getOutputDimension();
		}
		String model = resolveModel(this.options.getModel());
		if (KNOWN_EMBEDDING_DIMENSIONS.containsKey(model)) {
			return KNOWN_EMBEDDING_DIMENSIONS.get(model);
		}
		return super.dimensions();
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable VoyageAiApi voyageAiApi;

		private MetadataMode metadataMode = MetadataMode.EMBED;

		private VoyageAiEmbeddingOptions options = VoyageAiEmbeddingOptions.builder().build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder voyageAiApi(VoyageAiApi voyageAiApi) {
			this.voyageAiApi = voyageAiApi;
			return this;
		}

		public Builder metadataMode(MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
			return this;
		}

		public Builder options(VoyageAiEmbeddingOptions options) {
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

		public VoyageAiEmbeddingModel build() {
			Assert.state(this.voyageAiApi != null, "VoyageAiApi must not be null");
			return new VoyageAiEmbeddingModel(this.voyageAiApi, this.metadataMode, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
