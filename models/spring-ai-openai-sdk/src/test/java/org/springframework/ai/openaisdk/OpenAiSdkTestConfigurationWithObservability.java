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

package org.springframework.ai.openaisdk;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.ai.openaisdk.OpenAiSdkChatOptions.DEFAULT_CHAT_MODEL;
import static org.springframework.ai.openaisdk.OpenAiSdkEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;
import static org.springframework.ai.openaisdk.OpenAiSdkImageOptions.DEFAULT_IMAGE_MODEL;

/**
 * Context configuration for OpenAI Java SDK tests.
 *
 * @author Julien Dubois
 */
@SpringBootConfiguration
public class OpenAiSdkTestConfigurationWithObservability {

	@Bean
	public TestObservationRegistry testObservationRegistry() {
		return TestObservationRegistry.create();
	}

	@Bean
	public OpenAiSdkEmbeddingModel openAiEmbeddingModel(TestObservationRegistry observationRegistry) {
		return new OpenAiSdkEmbeddingModel(MetadataMode.EMBED,
				OpenAiSdkEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build(), observationRegistry);
	}

	@Bean
	public OpenAiSdkImageModel openAiImageModel(TestObservationRegistry observationRegistry) {
		return new OpenAiSdkImageModel(OpenAiSdkImageOptions.builder().model(DEFAULT_IMAGE_MODEL).build(),
				observationRegistry);
	}

	@Bean
	public OpenAiSdkChatModel openAiChatModel(TestObservationRegistry observationRegistry) {
		return new OpenAiSdkChatModel(OpenAiSdkChatOptions.builder().model(DEFAULT_CHAT_MODEL).build(),
				observationRegistry);
	}

}
