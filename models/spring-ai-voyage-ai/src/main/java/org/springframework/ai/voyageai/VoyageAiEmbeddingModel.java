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

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
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
 * Voyage AI text embedding model.
 *
 * <p>
 * The model is never defaulted: a request without a model, or with a model that is not a
 * Voyage AI text embedding model, fails before anything is sent. A request with more than
 * {@value VoyageAiApi#MAX_INPUTS_PER_REQUEST} texts is split into several API calls; the
 * vectors come back in the order of the texts. Voyage AI also limits the tokens of a
 * request, which is not checked here: an over-sized request is rejected by the API.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final VoyageAiApi voyageAiApi;

	private final MetadataMode metadataMode;

	private final VoyageAiEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public VoyageAiEmbeddingModel(VoyageAiApi voyageAiApi, MetadataMode metadataMode,
			VoyageAiEmbeddingOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(voyageAiApi, "voyageAiApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.voyageAiApi = voyageAiApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		VoyageAiEmbeddingOptions options = mergeOptions(request.getOptions());
		String model = VoyageAiApi.EmbeddingModel.fromValue(options.getModel()).getValue();
		EmbeddingRequest embeddingRequest = new EmbeddingRequest(request.getInstructions(), options);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(VoyageAiApi.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				List<Embedding> embeddings = new ArrayList<>();
				long totalTokens = 0;
				for (List<String> texts : VoyageAiResponseSupport.partition(request.getInstructions(),
						VoyageAiApi.MAX_INPUTS_PER_REQUEST)) {
					var apiRequest = new VoyageAiApi.EmbeddingRequest(texts, model, options.getInputType(),
							options.getTruncation(), options.getDimensions());
					var apiResponse = RetryUtils.execute(this.retryTemplate,
							() -> this.voyageAiApi.embeddings(apiRequest));
					totalTokens += VoyageAiResponseSupport.requireTotalTokens(apiResponse.usage(), "embeddings");
					for (float[] vector : VoyageAiResponseSupport.orderedVectors(apiResponse.data(), texts.size())) {
						embeddings.add(new Embedding(vector, embeddings.size()));
					}
				}
				var usage = new DefaultUsage((int) totalTokens, 0, (int) totalTokens);
				var response = new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata(model, usage));
				observationContext.setResponse(response);
				return response;
			});
	}

	private VoyageAiEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions requestOptions) {
		if (requestOptions == null) {
			return this.defaultOptions;
		}
		var builder = VoyageAiEmbeddingOptions.builder()
			.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
			.dimensions(
					ModelOptionsUtils.mergeOption(requestOptions.getDimensions(), this.defaultOptions.getDimensions()));
		if (requestOptions instanceof VoyageAiEmbeddingOptions voyageOptions) {
			builder
				.inputType(
						ModelOptionsUtils.mergeOption(voyageOptions.getInputType(), this.defaultOptions.getInputType()))
				.truncation(ModelOptionsUtils.mergeOption(voyageOptions.getTruncation(),
						this.defaultOptions.getTruncation()));
		}
		else {
			builder.inputType(this.defaultOptions.getInputType()).truncation(this.defaultOptions.getTruncation());
		}
		return builder.build();
	}

	@Override
	public String getEmbeddingContent(Document document) {
		Assert.notNull(document, "Document must not be null");
		return document.getFormattedContent(this.metadataMode);
	}

	@Override
	public float[] embed(Document document) {
		return embed(getEmbeddingContent(document));
	}

	@Override
	public int dimensions() {
		// fails with a clear message when the default options name no valid model
		VoyageAiApi.EmbeddingModel.fromValue(this.defaultOptions.getModel());
		Integer dimensions = this.defaultOptions.getDimensions();
		return (dimensions != null) ? dimensions : VoyageAiApi.EmbeddingModel.DEFAULT_DIMENSIONS;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the convention
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
