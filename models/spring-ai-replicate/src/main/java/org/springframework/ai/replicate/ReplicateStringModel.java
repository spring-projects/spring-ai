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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionRequest;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionResponse;
import org.springframework.util.Assert;

/**
 * Replicate String Model implementation for models that return simple string outputs.
 * Typically used by classifiers, filters, or small utility models.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public class ReplicateStringModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ReplicateApi replicateApi;

	private final ReplicateOptions defaultOptions;

	public ReplicateStringModel(ReplicateApi replicateApi, ReplicateOptions defaultOptions) {
		Assert.notNull(replicateApi, "replicateApi must not be null");
		this.replicateApi = replicateApi;
		this.defaultOptions = defaultOptions;
	}

	/**
	 * Generate a string output using the specified model and options.
	 * @param options The model configuration including model name, and input
	 * @return Response containing the string output
	 */
	public StringResponse generate(ReplicateOptions options) {
		ReplicateOptions mergedOptions = mergeOptions(options);
		Assert.hasText(mergedOptions.getModel(), "model name must not be empty");

		PredictionRequest request = new PredictionRequest(mergedOptions.getVersion(), mergedOptions.getInput(),
				mergedOptions.getWebhook(), mergedOptions.getWebhookEventsFilter(), false);

		PredictionResponse predictionResponse = this.replicateApi.createPredictionAndWait(mergedOptions.getModel(),
				request);

		if (predictionResponse == null) {
			logger.warn("No prediction response returned for model: {}", mergedOptions.getModel());
			return new StringResponse(null, predictionResponse);
		}

		String output = parseStringOutput(predictionResponse.output());
		return new StringResponse(output, predictionResponse);
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

	private String parseStringOutput(Object output) {
		if (output == null) {
			return null;
		}
		if (output instanceof String outputString) {
			return outputString;
		}
		logger.warn("Unexpected output type for string model: {}, converting to string", output.getClass().getName());
		return output.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class StringResponse {

		private final String output;

		private final PredictionResponse predictionResponse;

		public StringResponse(String output, PredictionResponse predictionResponse) {
			this.output = output;
			this.predictionResponse = predictionResponse;
		}

		public String getOutput() {
			return this.output;
		}

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

		public ReplicateStringModel build() {
			return new ReplicateStringModel(this.replicateApi, this.defaultOptions);
		}

	}

}
