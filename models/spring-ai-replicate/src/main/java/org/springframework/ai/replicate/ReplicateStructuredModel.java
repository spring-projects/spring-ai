/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.replicate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionRequest;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionResponse;
import org.springframework.util.Assert;

/**
 * Replicate Structured Model implementation for models that return structured JSON
 * objects.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public class ReplicateStructuredModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ReplicateApi replicateApi;

	private final ReplicateOptions defaultOptions;

	public ReplicateStructuredModel(ReplicateApi replicateApi, ReplicateOptions defaultOptions) {
		Assert.notNull(replicateApi, "replicateApi must not be null");
		this.replicateApi = replicateApi;
		this.defaultOptions = defaultOptions;
	}

	/**
	 * Generate structured output using the specified model and options.
	 * @param options The model configuration including model name and input
	 * @return Response containing the structured output as a Map
	 */
	public StructuredResponse generate(ReplicateOptions options) {
		ReplicateOptions mergedOptions = mergeOptions(options);
		Assert.hasText(mergedOptions.getModel(), "model name must not be empty");

		PredictionRequest request = new PredictionRequest(mergedOptions.getVersion(), mergedOptions.getInput(),
				mergedOptions.getWebhook(), mergedOptions.getWebhookEventsFilter(), false);

		PredictionResponse predictionResponse = this.replicateApi.createPredictionAndWait(mergedOptions.getModel(),
				request);

		if (predictionResponse == null) {
			logger.warn("No prediction response returned for model: {}", mergedOptions.getModel());
			return new StructuredResponse(Collections.emptyMap(), predictionResponse);
		}

		Map<String, Object> structuredOutput = parseStructuredOutput(predictionResponse.output());
		return new StructuredResponse(structuredOutput, predictionResponse);
	}

	/**
	 * Merges default options from properties with prompt options. Prompt options take
	 * precedence
	 * @param providedOptions Options from the current Prompt
	 * @return merged Options
	 */
	private ReplicateOptions mergeOptions(ReplicateOptions providedOptions) {
		if (this.defaultOptions == null) {
			return providedOptions != null ? providedOptions : ReplicateOptions.builder().build();
		}
		if (providedOptions == null) {
			return this.defaultOptions;
		}
		ReplicateOptions merged = ReplicateOptions.fromOptions(this.defaultOptions);
		if (providedOptions.getModel() != null) {
			merged.setModel(providedOptions.getModel());
		}
		if (providedOptions.getVersion() != null) {
			merged.setVersion(providedOptions.getVersion());
		}
		if (providedOptions.getWebhook() != null) {
			merged.setWebhook(providedOptions.getWebhook());
		}
		if (providedOptions.getWebhookEventsFilter() != null) {
			merged.setWebhookEventsFilter(providedOptions.getWebhookEventsFilter());
		}
		Map<String, Object> mergedInput = new HashMap<>();
		if (this.defaultOptions.getInput() != null) {
			mergedInput.putAll(this.defaultOptions.getInput());
		}
		if (providedOptions.getInput() != null) {
			mergedInput.putAll(providedOptions.getInput());
		}
		merged.setInput(mergedInput);

		return merged;
	}

	/**
	 * Parse the output field as a Map.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parseStructuredOutput(Object output) {
		if (output == null) {
			return Collections.emptyMap();
		}

		if (output instanceof Map) {
			return (Map<String, Object>) output;
		}

		logger.warn("Unexpected output type for structured model: {}", output.getClass().getName());
		return Collections.emptyMap();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Response containing structured output as a Map and the raw prediction response.
	 */
	public static class StructuredResponse {

		private final Map<String, Object> output;

		private final PredictionResponse predictionResponse;

		public StructuredResponse(Map<String, Object> output, PredictionResponse predictionResponse) {
			this.output = output != null ? Collections.unmodifiableMap(output) : Collections.emptyMap();
			this.predictionResponse = predictionResponse;
		}

		/**
		 * Get the structured output as a Map.
		 */
		public Map<String, Object> getOutput() {
			return this.output;
		}

		/**
		 * Get the raw prediction response from Replicate API.
		 */
		public PredictionResponse getPredictionResponse() {
			return this.predictionResponse;
		}

	}

	/**
	 * Response containing structured output converted to a specific type.
	 */
	public static class TypedStructuredResponse<T> {

		private final T output;

		private final PredictionResponse predictionResponse;

		public TypedStructuredResponse(T output, PredictionResponse predictionResponse) {
			this.output = output;
			this.predictionResponse = predictionResponse;
		}

		/**
		 * Get the structured output as the specified type.
		 */
		public T getOutput() {
			return this.output;
		}

		/**
		 * Get the raw prediction response from Replicate API.
		 */
		public PredictionResponse getPredictionResponse() {
			return this.predictionResponse;
		}

	}

	public static final class Builder {

		private ReplicateApi replicateApi;

		private ReplicateOptions defaultOptions;

		private Builder() {
		}

		public Builder replicateApi(ReplicateApi replicateApi) {
			this.replicateApi = replicateApi;
			return this;
		}

		public Builder defaultOptions(ReplicateOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public ReplicateStructuredModel build() {
			return new ReplicateStructuredModel(this.replicateApi, this.defaultOptions);
		}

	}

}
