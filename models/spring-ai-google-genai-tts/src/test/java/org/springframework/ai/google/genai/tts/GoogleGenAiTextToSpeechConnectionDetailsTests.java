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

package org.springframework.ai.google.genai.tts;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GoogleGenAiTextToSpeechConnectionDetails}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechConnectionDetailsTests {

	@Test
	void builderDefaultsLocationToGlobal() {
		Client client = mock(Client.class);

		GoogleGenAiTextToSpeechConnectionDetails details = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.projectId("my-project")
			.genAiClient(client)
			.build();

		assertThat(details.getProjectId()).isEqualTo("my-project");
		assertThat(details.getLocation()).isEqualTo(GoogleGenAiTextToSpeechConnectionDetails.DEFAULT_LOCATION);
		assertThat(details.getGenAiClient()).isSameAs(client);
	}

	@Test
	void builderKeepsExplicitLocation() {
		GoogleGenAiTextToSpeechConnectionDetails details = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.projectId("my-project")
			.location("us")
			.genAiClient(mock(Client.class))
			.build();

		assertThat(details.getLocation()).isEqualTo("us");
	}

	@Test
	void vertexModeBuildsClientWithProjectAndLocation() {
		GoogleGenAiTextToSpeechConnectionDetails details = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.projectId("my-project")
			.location("eu")
			.credentials(mock(com.google.auth.oauth2.GoogleCredentials.class))
			.build();

		assertThat(details.getGenAiClient().vertexAI()).isTrue();
		assertThat(details.getGenAiClient().project()).isEqualTo("my-project");
		assertThat(details.getGenAiClient().location()).isEqualTo("eu");
	}

	@Test
	void builderWithoutProjectIdThrows() {
		assertThatThrownBy(() -> GoogleGenAiTextToSpeechConnectionDetails.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Project ID must be provided");
	}

	@Test
	void apiKeyModeBuildsGeminiDeveloperApiClient() {
		GoogleGenAiTextToSpeechConnectionDetails details = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.apiKey("test-api-key")
			.build();

		assertThat(details.getApiKey()).isEqualTo("test-api-key");
		assertThat(details.getProjectId()).isNull();
		assertThat(details.getLocation()).isEqualTo(GoogleGenAiTextToSpeechConnectionDetails.DEFAULT_LOCATION);
		assertThat(details.getGenAiClient().vertexAI()).isFalse();
		assertThat(details.getGenAiClient().apiKey()).isEqualTo("test-api-key");
	}

	@Test
	void apiKeyTakesPrecedenceOverVertexSettings() {
		GoogleGenAiTextToSpeechConnectionDetails details = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.apiKey("test-api-key")
			.projectId("my-project")
			.location("eu")
			.build();

		assertThat(details.getGenAiClient().vertexAI()).isFalse();
		assertThat(details.getGenAiClient().apiKey()).isEqualTo("test-api-key");
	}

}
