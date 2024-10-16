/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.ollama;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse;
import org.springframework.ai.ollama.api.OllamaModelPuller;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.metadata.OllamaEmbeddingUsage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link EmbeddingModel} implementation for {@literal Ollama}.
 *
 * Ollama allows developers to run large language models and generate embeddings locally.
 * It supports open-source models available on [Ollama AI
 * Library](https://ollama.ai/library).
 *
 * Examples of models supported: - Llama 2 (7B parameters, 3.8GB size) - Mistral (7B
 * parameters, 4.1GB size)
 *
 * Please refer to the <a href="https://ollama.ai/">official Ollama website</a> for the
 * most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
public class OllamaEmbeddingModel extends AbstractEmbeddingModel {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final OllamaApi ollamaApi;

	/**
	 * Default options to be used for all chat requests.
	 */
	private final OllamaOptions defaultOptions;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private final OllamaModelPuller modelPuller;

	public OllamaEmbeddingModel(OllamaApi ollamaApi) {
		this(ollamaApi, OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL));
	}

	public OllamaEmbeddingModel(OllamaApi ollamaApi, OllamaOptions defaultOptions) {
		this(ollamaApi, defaultOptions, ObservationRegistry.NOOP);
	}

	public OllamaEmbeddingModel(OllamaApi ollamaApi, OllamaOptions defaultOptions,
			ObservationRegistry observationRegistry) {
		Assert.notNull(ollamaApi, "openAiApi must not be null");
		Assert.notNull(defaultOptions, "options must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.ollamaApi = ollamaApi;
		this.defaultOptions = defaultOptions;
		this.observationRegistry = observationRegistry;
		this.modelPuller = new OllamaModelPuller(ollamaApi);
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getContent());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");

		OllamaApi.EmbeddingsRequest ollamaEmbeddingRequest = ollamaEmbeddingRequest(request.getInstructions(),
				request.getOptions());

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(OllamaApi.PROVIDER_NAME)
			.requestOptions(buildRequestOptions(ollamaEmbeddingRequest))
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				EmbeddingsResponse response = this.ollamaApi.embed(ollamaEmbeddingRequest);

				AtomicInteger indexCounter = new AtomicInteger(0);

				List<Embedding> embeddings = response.embeddings()
					.stream()
					.map(e -> new Embedding(e, indexCounter.getAndIncrement()))
					.toList();

				EmbeddingResponseMetadata embeddingResponseMetadata = new EmbeddingResponseMetadata(response.model(),
						OllamaEmbeddingUsage.from(response));

				EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, embeddingResponseMetadata);

				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	/**
	 * Package access for testing.
	 */
	OllamaApi.EmbeddingsRequest ollamaEmbeddingRequest(List<String> inputContent, EmbeddingOptions options) {

		// runtime options
		OllamaOptions runtimeOptions = null;
		if (options != null && options instanceof OllamaOptions ollamaOptions) {
			runtimeOptions = ollamaOptions;
		}

		OllamaOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OllamaOptions.class);

		mergedOptions.setPullMissingModel(this.defaultOptions.isPullMissingModel());
		if (runtimeOptions != null && runtimeOptions.isPullMissingModel() != null) {
			mergedOptions.setPullMissingModel(runtimeOptions.isPullMissingModel());
		}

		// Override the model.
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}
		String model = mergedOptions.getModel();

		if (mergedOptions.isPullMissingModel()) {
			this.modelPuller.pullModel(model, true);
		}

		return new OllamaApi.EmbeddingsRequest(model, inputContent, DurationParser.parse(mergedOptions.getKeepAlive()),
				OllamaOptions.filterNonSupportedFields(mergedOptions.toMap()), mergedOptions.getTruncate());
	}

	private EmbeddingOptions buildRequestOptions(OllamaApi.EmbeddingsRequest request) {
		return EmbeddingOptionsBuilder.builder().withModel(request.model()).build();
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static class DurationParser {

		private static final Pattern PATTERN = Pattern.compile("(\\d+)(ms|s|m|h)");

		public static Duration parse(String input) {

			if (!StringUtils.hasText(input)) {
				return null;
			}

			Matcher matcher = PATTERN.matcher(input);

			if (matcher.matches()) {
				long value = Long.parseLong(matcher.group(1));
				String unit = matcher.group(2);

				return switch (unit) {
					case "ms" -> Duration.ofMillis(value);
					case "s" -> Duration.ofSeconds(value);
					case "m" -> Duration.ofMinutes(value);
					case "h" -> Duration.ofHours(value);
					default -> throw new IllegalArgumentException("Unsupported time unit: " + unit);
				};
			}
			else {
				throw new IllegalArgumentException("Invalid duration format: " + input);
			}
		}

	}

}