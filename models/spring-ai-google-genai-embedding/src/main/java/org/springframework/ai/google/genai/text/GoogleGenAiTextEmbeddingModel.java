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

package org.springframework.ai.google.genai.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.ContentEmbeddingStatistics;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
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
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A class representing a Vertex AI Text Embedding Model using the new Google Gen AI SDK.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Rodrigo Malara
 * @author Soby Chacko
 * @author Dan Dobrin
 * @since 1.0.0
 */
public class GoogleGenAiTextEmbeddingModel extends AbstractEmbeddingModel {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Stream
		.of(GoogleGenAiTextEmbeddingModelName.values())
		.collect(Collectors.toMap(GoogleGenAiTextEmbeddingModelName::getName,
				GoogleGenAiTextEmbeddingModelName::getDimensions));

	public final GoogleGenAiTextEmbeddingOptions defaultOptions;

	private final GoogleGenAiEmbeddingConnectionDetails connectionDetails;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * The GenAI client instance.
	 */
	private final Client genAiClient;

	public GoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails,
			GoogleGenAiTextEmbeddingOptions defaultEmbeddingOptions) {
		this(connectionDetails, defaultEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails,
			GoogleGenAiTextEmbeddingOptions defaultEmbeddingOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultEmbeddingOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails,
			GoogleGenAiTextEmbeddingOptions defaultEmbeddingOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "GoogleGenAiEmbeddingConnectionDetails must not be null");
		Assert.notNull(defaultEmbeddingOptions, "GoogleGenAiTextEmbeddingOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.defaultOptions = defaultEmbeddingOptions.initializeDefaults();
		this.connectionDetails = connectionDetails;
		this.genAiClient = connectionDetails.getGenAiClient();
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(AiProvider.VERTEX_AI.value())
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				GoogleGenAiTextEmbeddingOptions options = (GoogleGenAiTextEmbeddingOptions) embeddingRequest
					.getOptions();
				String modelName = this.connectionDetails.getModelEndpointName(options.getModel());

				// Build the EmbedContentConfig
				EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();

				// Set dimensions if specified
				if (options.getDimensions() != null) {
					configBuilder.outputDimensionality(options.getDimensions());
				}

				// Set task type if specified - this might need to be handled differently
				// as the new SDK might not have a direct taskType field
				// We'll need to check the SDK documentation for this

				EmbedContentConfig config = configBuilder.build();

				// Convert instructions to Content list for embedding
				List<String> texts = embeddingRequest.getInstructions();

				// Validate that we have texts to embed
				if (texts == null || texts.isEmpty()) {
					throw new IllegalArgumentException("No embedding input is provided - instructions list is empty");
				}

				// Filter out null or empty strings
				List<String> validTexts = texts.stream().filter(StringUtils::hasText).toList();

				if (validTexts.isEmpty()) {
					throw new IllegalArgumentException("No embedding input is provided - all texts are null or empty");
				}

				// Call the embedding API with retry
				EmbedContentResponse embeddingResponse = null;
				try {
					embeddingResponse = this.retryTemplate
						.execute(() -> this.genAiClient.models.embedContent(modelName, validTexts, config));
				}
				catch (RetryException e) {
					if (e.getCause() instanceof RuntimeException r) {
						throw r;
					}
					else {
						throw new RuntimeException(e.getCause());
					}
				}

				// Process the response
				// Note: We need to handle the case where some texts were filtered out
				// The response will only contain embeddings for valid texts
				int totalTokenCount = 0;
				List<Embedding> embeddingList = new ArrayList<>();

				// Create a map to track original indices
				int originalIndex = 0;
				int validIndex = 0;

				if (embeddingResponse.embeddings().isPresent()) {
					for (String originalText : texts) {
						if (StringUtils.hasText(originalText)
								&& validIndex < embeddingResponse.embeddings().get().size()) {
							ContentEmbedding contentEmbedding = embeddingResponse.embeddings().get().get(validIndex);

							// Extract the embedding values
							if (contentEmbedding.values().isPresent()) {
								List<Float> floatList = contentEmbedding.values().get();
								float[] vectorValues = new float[floatList.size()];
								for (int i = 0; i < floatList.size(); i++) {
									vectorValues[i] = floatList.get(i);
								}
								embeddingList.add(new Embedding(vectorValues, originalIndex));
							}

							// Extract token count if available
							if (contentEmbedding.statistics().isPresent()) {
								ContentEmbeddingStatistics stats = contentEmbedding.statistics().get();
								if (stats.tokenCount().isPresent()) {
									totalTokenCount += stats.tokenCount().get().intValue();
								}
							}

							validIndex++;
						}
						else if (!StringUtils.hasText(originalText)) {
							// For empty texts, add a null embedding to maintain index
							// alignment
							embeddingList.add(new Embedding(new float[0], originalIndex));
						}
						originalIndex++;
					}
				}

				EmbeddingResponse response = new EmbeddingResponse(embeddingList,
						generateResponseMetadata(options.getModel(), totalTokenCount));

				observationContext.setResponse(response);

				return response;
			});
	}

	EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		GoogleGenAiTextEmbeddingOptions runtimeOptions = null;
		if (embeddingRequest.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(), EmbeddingOptions.class,
					GoogleGenAiTextEmbeddingOptions.class);
		}

		// Define request options by merging runtime options and default options
		GoogleGenAiTextEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				GoogleGenAiTextEmbeddingOptions.class);

		// Validate request options
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new EmbeddingRequest(embeddingRequest.getInstructions(), requestOptions);
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String model, Integer totalTokens) {
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.setModel(model);
		Usage usage = getDefaultUsage(totalTokens);
		metadata.setUsage(usage);
		return metadata;
	}

	private DefaultUsage getDefaultUsage(Integer totalTokens) {
		return new DefaultUsage(0, 0, totalTokens);
	}

	@Override
	public int dimensions() {
		return KNOWN_EMBEDDING_DIMENSIONS.computeIfAbsent(this.defaultOptions.getModel(), model -> super.dimensions());
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
