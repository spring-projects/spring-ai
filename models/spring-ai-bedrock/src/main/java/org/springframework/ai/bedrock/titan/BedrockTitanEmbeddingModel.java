/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.titan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingRequest;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.Assert;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation;

/**
 * {@link org.springframework.ai.embedding.EmbeddingModel} implementation that uses the
 * Bedrock Titan Embedding API. Titan Embedding supports text and image (encoded in
 * base64) inputs.
 *
 * Note: Titan Embedding does not support batch embedding.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockTitanEmbeddingModel extends AbstractEmbeddingModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final TitanEmbeddingBedrockApi embeddingApi;

	private final ObservationRegistry observationRegistry;

	/**
	 * Titan Embedding API input types. Could be either text or image (encoded in base64).
	 */
	private InputType inputType = InputType.TEXT;

	public BedrockTitanEmbeddingModel(TitanEmbeddingBedrockApi titanEmbeddingBedrockApi,
			ObservationRegistry observationRegistry) {
		this.embeddingApi = titanEmbeddingBedrockApi;
		this.observationRegistry = observationRegistry;
	}

	/**
	 * Titan Embedding API input types. Could be either text or image (encoded in base64).
	 * @param inputType the input type to use.
	 */
	public BedrockTitanEmbeddingModel withInputType(InputType inputType) {
		this.inputType = inputType;
		return this;
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");
		if (request.getInstructions().size() != 1) {
			logger.warn("Titan Embedding does not support batch embedding. Multiple API calls will be made.");
		}

		List<Embedding> embeddings = new ArrayList<>();
		var indexCounter = new AtomicInteger(0);

		for (String inputContent : request.getInstructions()) {
			var apiRequest = createTitanEmbeddingRequest(inputContent, request.getOptions());

			try {
				TitanEmbeddingResponse response = Observation
					.createNotStarted("bedrock.embedding", this.observationRegistry)
					.lowCardinalityKeyValue("model", "titan")
					.lowCardinalityKeyValue("input_type", this.inputType.name().toLowerCase())
					.highCardinalityKeyValue("input_length", String.valueOf(inputContent.length()))
					.observe(() -> {
						TitanEmbeddingResponse r = this.embeddingApi.embedding(apiRequest);
						Assert.notNull(r, "Embedding API returned null response");
						return r;
					});

				if (response.embedding() == null || response.embedding().length == 0) {
					logger.warn("Empty embedding vector returned for input at index {}. Skipping.", indexCounter.get());
					continue;
				}

				embeddings.add(new Embedding(response.embedding(), indexCounter.getAndIncrement()));
			}
			catch (Exception ex) {
				logger.error("Titan API embedding failed for input at index {}: {}", indexCounter.get(),
						summarizeInput(inputContent), ex);
				throw ex; // Optional: Continue instead of throwing if you want partial
							// success
			}
		}

		return new EmbeddingResponse(embeddings);
	}

	private TitanEmbeddingRequest createTitanEmbeddingRequest(String inputContent, EmbeddingOptions requestOptions) {
		InputType inputType = this.inputType;

		if (requestOptions != null
				&& requestOptions instanceof BedrockTitanEmbeddingOptions bedrockTitanEmbeddingOptions) {
			inputType = bedrockTitanEmbeddingOptions.getInputType();
		}

		return (inputType == InputType.IMAGE) ? new TitanEmbeddingRequest.Builder().inputImage(inputContent).build()
				: new TitanEmbeddingRequest.Builder().inputText(inputContent).build();
	}

	@Override
	public int dimensions() {
		if (this.inputType == InputType.IMAGE) {
			if (this.embeddingDimensions.get() < 0) {
				this.embeddingDimensions.set(dimensions(this, this.embeddingApi.getModelId(),
						// small base64 encoded image
						"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
			}
		}
		return super.dimensions();

	}

	private String summarizeInput(String input) {
		if (this.inputType == InputType.IMAGE) {
			return "[image content omitted, length=" + input.length() + "]";
		}
		return input.length() > 100 ? input.substring(0, 100) + "..." : input;
	}

	public enum InputType {

		TEXT, IMAGE

	}

}
