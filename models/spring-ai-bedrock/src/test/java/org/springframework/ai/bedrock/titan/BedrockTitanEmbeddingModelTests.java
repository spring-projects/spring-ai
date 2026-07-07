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

package org.springframework.ai.bedrock.titan;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dimitar Proynov
 */
class BedrockTitanEmbeddingModelTests {

	@Test
	void inputTypeObservationTagUsesRootLocaleForImage() {
		TestObservationRegistry observationRegistry = TestObservationRegistry.create();
		TitanEmbeddingBedrockApi titanEmbeddingApi = mock(TitanEmbeddingBedrockApi.class);
		when(titanEmbeddingApi.embedding(any()))
			.thenReturn(new TitanEmbeddingResponse(new float[] { 0.1f, 0.2f, 0.3f }, 5, 1, 0, Map.of(), null, null));

		Locale defaultLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			// InputType.IMAGE contains an uppercase I; under tr_TR, toLowerCase()
			// without Locale.ROOT turns it into "ımage" (dotless ı) instead of "image".
			BedrockTitanEmbeddingModel embeddingModel = new BedrockTitanEmbeddingModel(titanEmbeddingApi,
					observationRegistry)
				.withInputType(BedrockTitanEmbeddingModel.InputType.IMAGE);

			EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of("some-base64-image"), null));

			assertThat(response.getResults()).isNotEmpty();
			TestObservationRegistryAssert.assertThat(observationRegistry)
				.hasObservationWithNameEqualTo("bedrock.embedding")
				.that()
				.hasLowCardinalityKeyValue("input_type", "image");
		}
		finally {
			Locale.setDefault(defaultLocale);
		}
	}

}
