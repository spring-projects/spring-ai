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

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.replicate.ReplicateStructuredModel.StructuredResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicateStructuredModel}.
 *
 * @author Rene Maierhofer
 */
@SpringBootTest(classes = ReplicateTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "REPLICATE_API_TOKEN", matches = ".+")
class ReplicateStructuredModelIT {

	@Autowired
	private ReplicateStructuredModel structuredModel;

	@Test
	void testGenerateEmbeddingsWithMetadata() {
		ReplicateOptions options = ReplicateOptions.builder()
			.model("openai/clip")
			.withParameter("text", "spring ai")
			.build();

		StructuredResponse response = this.structuredModel.generate(options);

		assertThat(response).isNotNull();
		assertThat(response.getOutput()).isNotNull().isInstanceOf(Map.class);
		Map<String, Object> output = response.getOutput();
		assertThat(output).isNotEmpty();
		assertThat(response.getPredictionResponse()).isNotNull();
		assertThat(response.getPredictionResponse().id()).isNotNull();
		assertThat(response.getPredictionResponse().status()).isNotNull();
		assertThat(response.getPredictionResponse().model()).contains("openai/clip");
		assertThat(response.getPredictionResponse().createdAt()).isNotNull();
		assertThat(response.getPredictionResponse().output()).isNotNull();
	}

	@Test
	void testWithDefaultOptions() {
		ReplicateOptions options = ReplicateOptions.builder().withParameter("text", "machine learning").build();

		StructuredResponse response = this.structuredModel.generate(options);

		assertThat(response).isNotNull();
		assertThat(response.getOutput()).isNotNull();
	}

}
