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

package org.springframework.ai.openaiofficial;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.ai.openaiofficial.OpenAiOfficialEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;
import static org.springframework.ai.openaiofficial.OpenAiOfficialImageOptions.DEFAULT_IMAGE_MODEL;

/**
 * Context configuration for OpenAI official SDK tests.
 *
 * @author Julien Dubois
 */
@SpringBootConfiguration
public class OpenAiOfficialTestConfigurationWithObservability {

	@Bean
	public TestObservationRegistry testObservationRegistry() {
		return TestObservationRegistry.create();
	}

	@Bean
	public OpenAiOfficialEmbeddingModel openAiEmbeddingModel(TestObservationRegistry observationRegistry) {
		return new OpenAiOfficialEmbeddingModel(MetadataMode.EMBED,
				OpenAiOfficialEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build(), observationRegistry);
	}

	@Bean
	public OpenAiOfficialImageModel openAiImageModel(TestObservationRegistry observationRegistry) {
		return new OpenAiOfficialImageModel(OpenAiOfficialImageOptions.builder().model(DEFAULT_IMAGE_MODEL).build(),
				observationRegistry);
	}

}
