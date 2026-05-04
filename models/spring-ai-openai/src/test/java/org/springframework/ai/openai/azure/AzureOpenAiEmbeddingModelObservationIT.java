/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai.azure;

import java.util.List;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Azure integration tests for observation instrumentation in
 * {@link OpenAiEmbeddingModel}.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
@SpringBootTest(classes = AzureOpenAiEmbeddingModelObservationIT.Config.class)
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+") })
public class AzureOpenAiEmbeddingModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	OpenAiEmbeddingModel embeddingModel;

	@Test
	void observationForEmbeddingOperation() {
		var options = OpenAiEmbeddingOptions.builder()
			.deploymentName("text-embedding-ada-002")
			// should not send dimension value?
			// https://github.com/SciPhi-AI/R2R/issues/354
			// .withDimensions(1536)
			.build();

		EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of("Here comes the sun"), options);

		EmbeddingResponse embeddingResponse = this.embeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).isNotEmpty();

		EmbeddingResponseMetadata responseMetadata = embeddingResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultEmbeddingModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("embedding")
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.EMBEDDING.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.OPENAI.value())
			// .hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
			// "text-embedding-ada-002")
			// .hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS.asString(),
			// "1536")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public OpenAiEmbeddingModel azureEmbeddingModel(TestObservationRegistry observationRegistry) {
			return new OpenAiEmbeddingModel(MetadataMode.EMBED,
					OpenAiEmbeddingOptions.builder()
						.baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
						.apiKey(System.getenv("AZURE_OPENAI_API_KEY"))
						.deploymentName("text-embedding-ada-002")
						.build(),
					observationRegistry);
		}

	}

}
