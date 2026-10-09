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

package org.springframework.ai.google.genai.transcription;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GoogleGenAiTranscriptionConnectionDetails}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionConnectionDetailsTests {

	@Test
	void customClientWins() {
		Client customClient = mock(Client.class);

		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.client(customClient)
			.apiKey("ignored-key")
			.projectId("ignored-project")
			.build();

		assertThat(connectionDetails.getGenAiClient()).isSameAs(customClient);
	}

	@Test
	void missingApiKeyAndProjectIdThrows() {
		assertThatThrownBy(() -> GoogleGenAiTranscriptionConnectionDetails.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Either an API key (Gemini Developer API) or a project ID (Vertex AI) must be provided");
	}

	@Test
	void apiKeyBuildsGeminiDeveloperApiClient() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.apiKey("test-key")
			.build();

		assertThat(connectionDetails.getGenAiClient()).isNotNull();
	}

	@Test
	void projectIdBuildsVertexAiClient() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("test-project")
			.build();

		assertThat(connectionDetails.getGenAiClient()).isNotNull();
	}

	@Test
	void projectIdWithExplicitLocationBuildsVertexAiClient() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("test-project")
			.location("us-central1")
			.build();

		assertThat(connectionDetails.getGenAiClient()).isNotNull();
	}

	@Test
	void defaultLocationIsGlobal() {
		assertThat(GoogleGenAiTranscriptionConnectionDetails.DEFAULT_LOCATION).isEqualTo("global");
	}

}
