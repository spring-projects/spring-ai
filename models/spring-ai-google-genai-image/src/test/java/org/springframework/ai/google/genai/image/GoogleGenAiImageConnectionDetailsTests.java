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

package org.springframework.ai.google.genai.image;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GoogleGenAiImageConnectionDetails}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageConnectionDetailsTests {

	@Test
	void builderWithApiKeyBuildsGeminiDeveloperClient() {
		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.apiKey("test-api-key")
			.build();

		assertThat(details.getApiKey()).isEqualTo("test-api-key");
		assertThat(details.getProjectId()).isNull();
		assertThat(details.getLocation()).isNull();
		assertThat(details.getGenAiClient()).isNotNull();
	}

	@Test
	void builderWithVertexAiProjectAndLocation() {
		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.projectId("my-project")
			.location("europe-west1")
			.credentials(mock(GoogleCredentials.class))
			.build();

		assertThat(details.getProjectId()).isEqualTo("my-project");
		assertThat(details.getLocation()).isEqualTo("europe-west1");
		assertThat(details.getApiKey()).isNull();
		assertThat(details.getGenAiClient()).isNotNull();
	}

	@Test
	void builderDefaultsLocationForVertexAiWhenMissing() {
		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.projectId("my-project")
			.credentials(mock(GoogleCredentials.class))
			.build();

		assertThat(details.getLocation()).isEqualTo(GoogleGenAiImageConnectionDetails.DEFAULT_LOCATION);
	}

	@Test
	void builderWithCredentialsForVertexAiMode() {
		GoogleCredentials credentials = mock(GoogleCredentials.class);

		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.projectId("my-project")
			.location("us-central1")
			.credentials(credentials)
			.build();

		assertThat(details.getGenAiClient()).isNotNull();
	}

	@Test
	void builderWithoutApiKeyOrProjectThrowsException() {
		assertThatThrownBy(() -> GoogleGenAiImageConnectionDetails.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Project ID must be provided for Vertex AI mode");
	}

	@Test
	void builderWithCustomClientIgnoresOtherProperties() {
		Client customClient = mock(Client.class);

		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.genAiClient(customClient)
			.apiKey("ignored-api-key")
			.projectId("ignored-project")
			.build();

		assertThat(details.getGenAiClient()).isSameAs(customClient);
		// Other properties are still recorded for accessor purposes even though the
		// custom client takes precedence.
		assertThat(details.getApiKey()).isEqualTo("ignored-api-key");
		assertThat(details.getProjectId()).isEqualTo("ignored-project");
	}

	@Test
	void getModelEndpointNameReturnsModelNameAsIs() {
		GoogleGenAiImageConnectionDetails details = GoogleGenAiImageConnectionDetails.builder()
			.apiKey("test-api-key")
			.build();

		assertThat(details.getModelEndpointName("gemini-2.5-flash-image")).isEqualTo("gemini-2.5-flash-image");
	}

}
