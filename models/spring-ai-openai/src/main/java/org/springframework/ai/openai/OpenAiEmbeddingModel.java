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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
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
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Embedding Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @author Josh Long
 */
public class OpenAiEmbeddingModel extends AbstractEmbeddingModel {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private static final Log logger = LogFactory.getLog(OpenAiEmbeddingModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiEmbeddingOptions options;

	private final MetadataMode metadataMode;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new OpenAiEmbeddingModel with default options.
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel() {
		this(null, null, null, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given options.
	 * @param options the embedding options
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAiEmbeddingOptions options) {
		this(null, null, options, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given metadata mode and options.
	 * @param metadataMode the metadata mode
	 * @param options the embedding options
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable MetadataMode metadataMode, @Nullable OpenAiEmbeddingOptions options) {
		this(null, metadataMode, options, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given options and observation registry.
	 * @param options the embedding options
	 * @param observationRegistry the observation registry
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAiEmbeddingOptions options,
			@Nullable ObservationRegistry observationRegistry) {
		this(null, null, options, observationRegistry);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given metadata mode, options, and
	 * observation registry.
	 * @param metadataMode the metadata mode
	 * @param options the embedding options
	 * @param observationRegistry the observation registry
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable MetadataMode metadataMode, @Nullable OpenAiEmbeddingOptions options,
			@Nullable ObservationRegistry observationRegistry) {
		this(null, metadataMode, options, observationRegistry);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given OpenAI client.
	 * @param openAiClient the OpenAI client
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAIClient openAiClient) {
		this(openAiClient, null, null, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with the given OpenAI client and metadata mode.
	 * @param openAiClient the OpenAI client
	 * @param metadataMode the metadata mode
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAIClient openAiClient, @Nullable MetadataMode metadataMode) {
		this(openAiClient, metadataMode, null, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with all configuration options.
	 * @param openAiClient the OpenAI client
	 * @param metadataMode the metadata mode
	 * @param options the embedding options
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAIClient openAiClient, @Nullable MetadataMode metadataMode,
			@Nullable OpenAiEmbeddingOptions options) {
		this(openAiClient, metadataMode, options, null);
	}

	/**
	 * Creates a new OpenAiEmbeddingModel with all configuration options.
	 * @param openAiClient the OpenAI client
	 * @param metadataMode the metadata mode
	 * @param options the embedding options
	 * @param observationRegistry the observation registry
	 * @deprecated in favor of {@link OpenAiEmbeddingModel#builder()}
	 */
	@Deprecated
	public OpenAiEmbeddingModel(@Nullable OpenAIClient openAiClient, @Nullable MetadataMode metadataMode,
			@Nullable OpenAiEmbeddingOptions options, @Nullable ObservationRegistry observationRegistry) {

		this(builder().openAiClient(openAiClient)
			.metadataMode(metadataMode)
			.options(options)
			.observationRegistry(observationRegistry));
	}

	private OpenAiEmbeddingModel(Builder builder) {
		this.options = builder.options != null ? builder.options : OpenAiEmbeddingOptions.builder().build();
		this.metadataMode = Objects.requireNonNullElse(builder.metadataMode, MetadataMode.EMBED);
		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, null,
						builder.httpClientCustomizers));
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getEmbeddingContent(Document document) {
		Assert.notNull(document, "Document must not be null");
		return document.getFormattedContent(this.metadataMode);
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document, this.options);
	}

	@Override
	public float[] embed(Document document, EmbeddingOptions options) {
		Assert.notNull(document, "Document must not be null");
		Assert.notNull(options, "OpenAiEmbeddingOptions must not be null");

		EmbeddingResponse response = this
			.call(new EmbeddingRequest(List.of(document.getFormattedContent(this.metadataMode)), options));

		if (CollectionUtils.isEmpty(response.getResults())) {
			return new float[0];
		}
		return response.getResults().get(0).getOutput();
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest embeddingRequest) {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
			.from(this.options)
			.merge(embeddingRequest.getOptions())
			.build();

		EmbeddingRequest embeddingRequestWithMergedOptions = new EmbeddingRequest(embeddingRequest.getInstructions(),
				options);

		EmbeddingCreateParams embeddingCreateParams = options
			.toOpenAiCreateParams(embeddingRequestWithMergedOptions.getInstructions());

		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiEmbeddingModel call " + options.getModel() + " with the following options : "
					+ embeddingCreateParams);
		}

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequestWithMergedOptions)
			.provider(AiProvider.OPENAI.value())
			.build();

		return Objects.requireNonNull(
				EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						CreateEmbeddingResponse response = this.openAiClient.embeddings().create(embeddingCreateParams);

						var embeddingResponse = generateEmbeddingResponse(response);
						observationContext.setResponse(embeddingResponse);
						return embeddingResponse;
					}));
	}

	private EmbeddingResponse generateEmbeddingResponse(CreateEmbeddingResponse response) {

		List<Embedding> data = generateEmbeddingList(response.data());
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.setModel(response.model());
		metadata.setUsage(getDefaultUsage(response.usage()));
		return new EmbeddingResponse(data, metadata);
	}

	private DefaultUsage getDefaultUsage(CreateEmbeddingResponse.Usage nativeUsage) {
		return new DefaultUsage(Math.toIntExact(nativeUsage.promptTokens()), 0,
				Math.toIntExact(nativeUsage.totalTokens()), nativeUsage);
	}

	private List<Embedding> generateEmbeddingList(List<com.openai.models.embeddings.Embedding> nativeData) {
		List<Embedding> data = new ArrayList<>();
		for (com.openai.models.embeddings.Embedding nativeDatum : nativeData) {
			List<Float> nativeDatumEmbedding = nativeDatum.embedding();
			long nativeIndex = nativeDatum.index();
			Embedding embedding = new Embedding(EmbeddingUtils.toPrimitive(nativeDatumEmbedding),
					Math.toIntExact(nativeIndex));
			data.add(embedding);
		}
		return data;
	}

	/**
	 * Gets the embedding options for this model.
	 * @return the embedding options
	 */
	public OpenAiEmbeddingOptions getOptions() {
		return this.options;
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiEmbeddingOptions options;

		private @Nullable MetadataMode metadataMode;

		private @Nullable ObservationRegistry observationRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		public Builder openAiClient(@Nullable OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder options(@Nullable OpenAiEmbeddingOptions options) {
			this.options = options;
			return this;
		}

		public Builder metadataMode(@Nullable MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
			return this;
		}

		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Registers an {@link OpenAiHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the OpenAI clients are constructed. Use
		 * this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection), swap
		 * the dispatcher executor, or tweak any other OkHttp setting. Customizers are
		 * applied in the order they are registered, after Spring AI's own defaults, so
		 * user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link OpenAiHttpClientBuilderCustomizer customizers} to
		 * apply, replacing any customizers registered earlier on this builder. The order
		 * of the list is preserved when invoking the customizers.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		public OpenAiEmbeddingModel build() {
			return new OpenAiEmbeddingModel(this);
		}

	}

}
