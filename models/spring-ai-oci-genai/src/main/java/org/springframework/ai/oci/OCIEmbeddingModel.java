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

package org.springframework.ai.oci;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.bmc.generativeaiinference.GenerativeAiInference;
import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;
import com.oracle.bmc.generativeaiinference.model.EmbedTextResult;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.generativeaiinference.requests.EmbedTextRequest;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.metadata.EmptyUsage;
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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.ai.embedding.EmbeddingModel} implementation that uses the
 * OCI GenAI Embedding API.
 *
 * @author Anders Swanson
 * @since 1.0.0
 */
public class OCIEmbeddingModel extends AbstractEmbeddingModel {

	// The OCI GenAI API has a batch size of 96 for embed text requests.
	private static final int EMBEDTEXT_BATCH_SIZE = 96;

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final GenerativeAiInference genAi;

	private final OCIEmbeddingOptions options;

	private final ObservationRegistry observationRegistry;

	private final EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OCIEmbeddingModel(GenerativeAiInference genAi, OCIEmbeddingOptions options) {
		this(genAi, options, ObservationRegistry.NOOP);
	}

	public OCIEmbeddingModel(GenerativeAiInference genAi, OCIEmbeddingOptions options,
			ObservationRegistry observationRegistry) {
		Assert.notNull(genAi, "com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.genAi = genAi;
		this.options = options;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");

		EmbeddingRequest embeddingRequest = buildEmbeddingRequest(request);

		List<EmbedTextRequest> embedTextRequests = createRequests(embeddingRequest.getInstructions(),
				(OCIEmbeddingOptions) embeddingRequest.getOptions());

		EmbeddingModelObservationContext context = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(AiProvider.OCI_GENAI.value())
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> context,
					this.observationRegistry)
			.observe(() -> embedAllWithContext(embedTextRequests, context));
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	private EmbeddingResponse embedAllWithContext(List<EmbedTextRequest> embedTextRequests,
			EmbeddingModelObservationContext context) {
		String modelId = null;
		AtomicInteger index = new AtomicInteger(0);
		List<Embedding> embeddings = new ArrayList<>();
		for (EmbedTextRequest embedTextRequest : embedTextRequests) {
			EmbedTextResult embedTextResult = this.genAi.embedText(embedTextRequest).getEmbedTextResult();
			if (modelId == null) {
				modelId = embedTextResult.getModelId();
			}
			for (List<Float> e : embedTextResult.getEmbeddings()) {
				float[] data = toFloats(e);
				embeddings.add(new Embedding(data, index.getAndIncrement()));
			}
		}
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.setModel(modelId);
		metadata.setUsage(new EmptyUsage());
		EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);
		context.setResponse(embeddingResponse);
		return embeddingResponse;
	}

	private List<EmbedTextRequest> createRequests(List<String> inputs, OCIEmbeddingOptions embeddingOptions) {
		int size = inputs.size();
		List<EmbedTextRequest> requests = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i += EMBEDTEXT_BATCH_SIZE) {
			List<String> batch = inputs.subList(i, Math.min(i + EMBEDTEXT_BATCH_SIZE, size));
			requests.add(createRequest(batch, embeddingOptions));
		}
		return requests;
	}

	private EmbedTextRequest createRequest(List<String> inputs, OCIEmbeddingOptions embeddingOptions) {
		ServingMode servingMode = ServingModeHelper.get(this.options.getServingMode(), this.options.getModel());
		EmbedTextDetails embedTextDetails = EmbedTextDetails.builder()
			.servingMode(servingMode)
			.compartmentId(embeddingOptions.getCompartment())
			.inputs(inputs)
			.truncate(Objects.requireNonNullElse(embeddingOptions.getTruncate(), EmbedTextDetails.Truncate.End))
			.build();
		return EmbedTextRequest.builder().embedTextDetails(embedTextDetails).build();
	}

	private OCIEmbeddingOptions mergeOptions(EmbeddingOptions embeddingOptions, OCIEmbeddingOptions defaultOptions) {
		if (embeddingOptions instanceof OCIEmbeddingOptions) {
			OCIEmbeddingOptions dynamicOptions = ModelOptionsUtils.merge(embeddingOptions, defaultOptions,
					OCIEmbeddingOptions.class);
			if (dynamicOptions != null) {
				return dynamicOptions;
			}
		}
		return defaultOptions;
	}

	EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		// Process runtime options
		OCIEmbeddingOptions runtimeOptions = ModelOptionsUtils.copyToTarget(embeddingRequest.getOptions(),
				EmbeddingOptions.class, OCIEmbeddingOptions.class);

		// Define request options by merging runtime options and default options
		OCIEmbeddingOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.options,
				OCIEmbeddingOptions.class);

		// Validate request options
		if (!StringUtils.hasText(requestOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new EmbeddingRequest(embeddingRequest.getInstructions(), requestOptions);
	}

	private float[] toFloats(List<Float> embedding) {
		float[] floats = new float[embedding.size()];
		for (int i = 0; i < embedding.size(); i++) {
			floats[i] = embedding.get(i);
		}
		return floats;
	}

}
