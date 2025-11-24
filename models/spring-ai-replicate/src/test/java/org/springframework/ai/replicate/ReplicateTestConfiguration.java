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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Test configuration for Replicate integration tests.
 *
 * @author Rene Maierhofer
 */
@SpringBootConfiguration
public class ReplicateTestConfiguration {

	@Bean
	public ReplicateApi replicateApi() {
		return ReplicateApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public ReplicateChatModel replicateChatModel(ReplicateApi api, ObservationRegistry observationRegistry) {
		return ReplicateChatModel.builder()
			.replicateApi(api)
			.observationRegistry(observationRegistry)
			.defaultOptions(ReplicateChatOptions.builder().model("meta/meta-llama-3-8b-instruct").build())
			.build();
	}

	@Bean
	public ReplicateMediaModel replicateMediaModel(ReplicateApi api) {
		return ReplicateMediaModel.builder()
			.replicateApi(api)
			.defaultOptions(ReplicateOptions.builder().model("black-forest-labs/flux-schnell").build())
			.build();
	}

	@Bean
	public ReplicateStringModel replicateStringModel(ReplicateApi api) {
		return ReplicateStringModel.builder()
			.replicateApi(api)
			.defaultOptions(ReplicateOptions.builder().model("falcons-ai/nsfw_image_detection").build())
			.build();
	}

	@Bean
	public ReplicateStructuredModel replicateStructuredModel(ReplicateApi api) {
		return ReplicateStructuredModel.builder()
			.replicateApi(api)
			.defaultOptions(ReplicateOptions.builder().model("openai/clip").build())
			.build();
	}

	@Bean
	public ObservationRegistry observationRegistry() {
		return ObservationRegistry.create();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	private String getApiKey() {
		String apiKey = System.getenv("REPLICATE_API_TOKEN");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide a Replicate API token. Please set the REPLICATE_API_TOKEN environment variable.");
		}
		return apiKey;
	}

}
