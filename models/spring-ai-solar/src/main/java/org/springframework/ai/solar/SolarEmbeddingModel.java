package org.springframework.ai.solar;

import java.util.List;

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
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.solar.api.SolarApi;
import org.springframework.ai.solar.api.common.SolarConstants;
import org.springframework.ai.solar.metadata.SolarUsage;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import io.micrometer.observation.ObservationRegistry;

public class SolarEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(SolarEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final SolarEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final SolarApi solarApi;

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
	 * Constructor for the SolarEmbeddingModel class.
	 * @param solarApi The SolarApi instance to use for making API requests.
	 */
	public SolarEmbeddingModel(SolarApi solarApi) {
		this(solarApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the SolarEmbeddingModel class.
	 * @param solarApi The SolarApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public SolarEmbeddingModel(SolarApi solarApi, MetadataMode metadataMode) {
		this(solarApi, metadataMode,
				SolarEmbeddingOptions.builder().withModel(SolarApi.DEFAULT_EMBEDDING_MODEL).build());
	}

	/**
	 * Initializes a new instance of the SolarEmbeddingModel class.
	 * @param solarApi The SolarApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param SolarEmbeddingOptions The options for Solar embedding.
	 */
	public SolarEmbeddingModel(SolarApi solarApi, MetadataMode metadataMode,
			SolarEmbeddingOptions SolarEmbeddingOptions) {
		this(solarApi, metadataMode, SolarEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the SolarEmbeddingModel class.
	 * @param solarApi The SolarApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param SolarEmbeddingOptions The options for Solar embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public SolarEmbeddingModel(SolarApi solarApi, MetadataMode metadataMode,
			SolarEmbeddingOptions SolarEmbeddingOptions, RetryTemplate retryTemplate) {
		this(solarApi, metadataMode, SolarEmbeddingOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the SolarEmbeddingModel class.
	 * @param solarApi - The SolarApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for Solar embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 * @param observationRegistry - The ObservationRegistry used for instrumentation.
	 */
	public SolarEmbeddingModel(SolarApi solarApi, MetadataMode metadataMode, SolarEmbeddingOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(solarApi, "SolarApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.solarApi = solarApi;
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
		SolarEmbeddingOptions requestOptions = mergeOptions(request.getOptions(), this.defaultOptions);
		SolarApi.EmbeddingRequest apiRequest = new SolarApi.EmbeddingRequest(request.getInstructions(),
				requestOptions.getModel());

		var observationContext = EmbeddingModelObservationContext.builder()
				.embeddingRequest(request)
				.provider(SolarConstants.PROVIDER_NAME)
				.requestOptions(requestOptions)
				.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry)
				.observe(() -> {
					SolarApi.EmbeddingList apiEmbeddingResponse = this.retryTemplate
							.execute(ctx -> this.solarApi.embeddings(apiRequest).getBody());

					if (apiEmbeddingResponse == null) {
						logger.warn("No embeddings returned for request: {}", request);
						return new EmbeddingResponse(List.of());
					}

					if (apiEmbeddingResponse.errorNsg() != null) {
						logger.error("Error message returned for request: {}", apiEmbeddingResponse.errorNsg());
						throw new RuntimeException("Embedding failed: error code:" + apiEmbeddingResponse.errorCode()
								+ ", message:" + apiEmbeddingResponse.errorNsg());
					}

					var metadata = new EmbeddingResponseMetadata(apiRequest.model(),
							SolarUsage.from(apiEmbeddingResponse.usage()));

					List<Embedding> embeddings = apiEmbeddingResponse.data()
							.stream()
							.map(e -> new Embedding(e.embedding(), e.index()))
							.toList();

					EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

					observationContext.setResponse(embeddingResponse);

					return embeddingResponse;
				});
	}

	/**
	 * Merge runtime and default {@link EmbeddingOptions} to compute the final options to
	 * use in the request.
	 */
	private SolarEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions runtimeOptions,
			SolarEmbeddingOptions defaultOptions) {
		var runtimeOptionsForProvider = ModelOptionsUtils.copyToTarget(runtimeOptions, EmbeddingOptions.class,
				SolarEmbeddingOptions.class);

		if (runtimeOptionsForProvider == null) {
			return defaultOptions;
		}

		return SolarEmbeddingOptions.builder()
				.withModel(ModelOptionsUtils.mergeOption(runtimeOptionsForProvider.getModel(), defaultOptions.getModel()))
				.build();
	}

	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}
}

