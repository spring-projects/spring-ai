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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.replicate.ReplicateMediaModel.MediaResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicateMediaModel}.
 *
 * @author Rene Maierhofer
 */
@SpringBootTest(classes = ReplicateTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "REPLICATE_API_TOKEN", matches = ".+")
class ReplicateMediaModelIT {

	@Autowired
	private ReplicateMediaModel mediaModel;

	@Test
	void testGenerateMultipleImages() {
		ReplicateOptions options = ReplicateOptions.builder()
			.model("black-forest-labs/flux-schnell")
			.withParameter("prompt", "a cat sitting on a laptop")
			.withParameter("num_outputs", 2)
			.build();

		MediaResponse response = this.mediaModel.generate(options);

		assertThat(response).isNotNull();
		assertThat(response.getUris()).isNotEmpty();
		response.getUris().forEach(uri -> {
			assertThat(uri).isNotEmpty();
			assertThat(uri).startsWith("http");
		});
		assertThat(response.getPredictionResponse()).isNotNull();
		assertThat(response.getPredictionResponse().id()).isNotNull();
		assertThat(response.getPredictionResponse().createdAt()).isNotNull();
		assertThat(response.getPredictionResponse().status()).isNotNull();
		assertThat(response.getPredictionResponse().model()).contains("black-forest-labs/flux-schnell");
	}

	@Test
	void testGenerateWithDefaultOptions() {
		ReplicateOptions options = ReplicateOptions.builder().withParameter("prompt", "a serene lake").build();

		MediaResponse response = this.mediaModel.generate(options);

		assertThat(response).isNotNull();
		assertThat(response.getUris()).isNotEmpty();
	}

}
