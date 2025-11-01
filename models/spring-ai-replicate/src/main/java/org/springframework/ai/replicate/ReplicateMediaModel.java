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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionRequest;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionResponse;
import org.springframework.util.Assert;

/**
 * Replicate Media Model implementation for image, video, and audio generation. Handles
 * both single URI outputs and multiple URI outputs (arrays).
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public class ReplicateMediaModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ReplicateApi replicateApi;

	private final ReplicateOptions defaultOptions;

	public ReplicateMediaModel(ReplicateApi replicateApi, ReplicateOptions defaultOptions) {
		Assert.notNull(replicateApi, "replicateApi must not be null");
		this.replicateApi = replicateApi;
		this.defaultOptions = defaultOptions;
	}

	/**
	 * Generate media (image/video/audio) using the specified model and options.
	 * @param options The model configuration including model name and input.
	 * @return Response containing URIs to generated media files
	 */
	public MediaResponse generate(ReplicateOptions options) {
		ReplicateOptions mergedOptions = mergeOptions(options);
		Assert.hasText(mergedOptions.getModel(), "model name must not be empty");

		PredictionRequest request = new PredictionRequest(mergedOptions.getVersion(), mergedOptions.getInput(),
				mergedOptions.getWebhook(), mergedOptions.getWebhookEventsFilter(), false);

		PredictionResponse predictionResponse = this.replicateApi.createPredictionAndWait(mergedOptions.getModel(),
				request);

		if (predictionResponse == null) {
			logger.warn("No prediction response returned for model: {}", mergedOptions.getModel());
			return new MediaResponse(Collections.emptyList(), predictionResponse);
		}

		List<String> uris = parseMediaOutput(predictionResponse.output());
		return new MediaResponse(uris, predictionResponse);
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
	 * Parse the output field which can be either a single URI string or an array of URI
	 * strings.
	 */
	private List<String> parseMediaOutput(Object output) {
		if (output == null) {
			return Collections.emptyList();
		}
		if (output instanceof String outputString) {
			return List.of(outputString);
		}
		if (output instanceof List) {
			List<?> list = (List<?>) output;
			List<String> uris = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof String itemString) {
					uris.add(itemString);
				}
			}
			return uris;
		}
		logger.warn("Unexpected output type: {}", output.getClass().getName());
		return Collections.emptyList();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Response containing generated media URIs and the raw prediction response.
	 */
	public static class MediaResponse {

		private final List<String> uris;

		private final PredictionResponse predictionResponse;

		public MediaResponse(List<String> uris, PredictionResponse predictionResponse) {
			this.uris = uris != null ? Collections.unmodifiableList(uris) : Collections.emptyList();
			this.predictionResponse = predictionResponse;
		}

		/**
		 * Get the list of URIs pointing to generated media files.
		 */
		public List<String> getUris() {
			return this.uris;
		}

		/**
		 * Get the first URI if available, useful for single-file outputs.
		 */
		public String getFirstUri() {
			return this.uris.isEmpty() ? null : this.uris.get(0);
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

		public ReplicateMediaModel build() {
			return new ReplicateMediaModel(this.replicateApi, this.defaultOptions);
		}

	}

}
