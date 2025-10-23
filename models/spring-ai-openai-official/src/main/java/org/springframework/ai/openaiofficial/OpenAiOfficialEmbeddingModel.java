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

import static org.springframework.ai.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;

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

	private final OpenAiOfficialEmbeddingOptions options;

	private final MetadataMode metadataMode;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OpenAiOfficialEmbeddingModel() {
		this(null, null, null, null);
	}

	public OpenAiOfficialEmbeddingModel(OpenAiOfficialEmbeddingOptions options) {
		this(null, null, options, null);
	}

	public OpenAiOfficialEmbeddingModel(MetadataMode metadataMode, OpenAiOfficialEmbeddingOptions options) {
		this(null, metadataMode, options, null);
	}

	public OpenAiOfficialEmbeddingModel(OpenAiOfficialEmbeddingOptions options,
			ObservationRegistry observationRegistry) {
		this(null, null, options, observationRegistry);
	}

	public OpenAiOfficialEmbeddingModel(MetadataMode metadataMode, OpenAiOfficialEmbeddingOptions options,
			ObservationRegistry observationRegistry) {
		this(null, metadataMode, options, observationRegistry);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient) {
		this(openAiClient, null, null, null);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode) {
		this(openAiClient, metadataMode, null, null);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode,
			OpenAiOfficialEmbeddingOptions options) {
		this(openAiClient, metadataMode, options, null);
	}

	public OpenAiOfficialEmbeddingModel(OpenAIClient openAiClient, MetadataMode metadataMode,
			OpenAiOfficialEmbeddingOptions options, ObservationRegistry observationRegistry) {

		if (options == null) {
			this.options = OpenAiOfficialEmbeddingOptions.builder().model(DEFAULT_MODEL_NAME).build();
		}
		else {
			this.options = options;
		}
		this.openAiClient = Objects.requireNonNullElseGet(openAiClient,
				() -> setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(), this.options.getCredential(),
						this.options.getAzureDeploymentName(), this.options.getAzureOpenAIServiceVersion(),
						this.options.getOrganizationId(), this.options.isAzure(), this.options.isGitHubModels(),
						this.options.getModel(), this.options.getTimeout(), this.options.getMaxRetries(),
						this.options.getProxy(), this.options.getCustomHeaders()));
		this.metadataMode = Objects.requireNonNullElse(metadataMode, MetadataMode.EMBED);
		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
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
			.from(this.options)
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

	public OpenAiOfficialEmbeddingOptions getOptions() {
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

}
