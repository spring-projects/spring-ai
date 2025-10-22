package org.springframework.ai.openaiofficial;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Embedding Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialEmbeddingModel extends AbstractEmbeddingModel {

	private static final String DEFAULT_MODEL_NAME = OpenAiOfficialEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialEmbeddingModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiOfficialEmbeddingOptions defaultOptions;

	private final MetadataMode metadataMode;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient) {
		this(openAiClient, MetadataMode.EMBED);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode) {
		this(openAiClient, metadataMode, OpenAiOfficialEmbeddingOptions.builder().model(DEFAULT_MODEL_NAME).build());
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode,
			OpenAiOfficialEmbeddingOptions options) {
		this(openAiClient, metadataMode, options, ObservationRegistry.NOOP);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode,
			OpenAiOfficialEmbeddingOptions options, ObservationRegistry observationRegistry) {

		Assert.notNull(openAiClient, "com.openai.client.OpenAIClient must not be null");
		Assert.notNull(metadataMode, "Metadata mode must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(options.getModel(), "Model name must not be null");
		Assert.notNull(observationRegistry, "Observation registry must not be null");
		this.openAiClient = openAiClient;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public float[] embed(Document document) {
		EmbeddingResponse response = this
			.call(new EmbeddingRequest(List.of(document.getFormattedContent(this.metadataMode)), null));

		if (CollectionUtils.isEmpty(response.getResults())) {
			return new float[0];
		}
		return response.getResults().get(0).getOutput();
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest embeddingRequest) {
		OpenAiOfficialEmbeddingOptions options = OpenAiOfficialEmbeddingOptions.builder()
			.from(this.defaultOptions)
			.merge(embeddingRequest.getOptions())
			.build();

		EmbeddingRequest embeddingRequestWithMergedOptions = new EmbeddingRequest(embeddingRequest.getInstructions(),
				options);

		EmbeddingCreateParams embeddingCreateParams = options
			.toOpenAiCreateParams(embeddingRequestWithMergedOptions.getInstructions());

		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiOfficialEmbeddingModel call {} with the following options : {} ", options.getModel(),
					embeddingCreateParams);
		}

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequestWithMergedOptions)
			.provider(AiProvider.OPENAI_OFFICIAL.value())
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

	public OpenAiOfficialEmbeddingOptions getDefaultOptions() {
		return this.defaultOptions;
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
