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

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link GoogleGenAiTranscriptionConnectionDetails}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionConnectionDetailsTests {

	@Test
	void builderDefaultsLocationToDefaultLocation() throws IOException {
		GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
			.projectId("my-project")
			.speechClient(mock(SpeechClient.class))
			.build();

		assertThat(details.getLocation()).isEqualTo(GoogleGenAiTranscriptionConnectionDetails.DEFAULT_LOCATION);
	}

	@Test
	void builderWithExplicitLocation() throws IOException {
		GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
			.projectId("my-project")
			.location("us")
			.speechClient(mock(SpeechClient.class))
			.build();

		assertThat(details.getLocation()).isEqualTo("us");
	}

	@Test
	void builderWithoutProjectIdThrows() {
		assertThatThrownBy(() -> GoogleGenAiTranscriptionConnectionDetails.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Project ID must be provided");
	}

	@Test
	void builderWithCustomClientOverridesOtherProperties() throws IOException {
		SpeechClient customClient = mock(SpeechClient.class);
		GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
			.projectId("my-project")
			.location("ignored")
			.speechClient(customClient)
			.build();

		assertThat(details.getSpeechClient()).isSameAs(customClient);
	}

	@Test
	void recognizerNameFormat() throws IOException {
		GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
			.projectId("my-project")
			.location("eu")
			.speechClient(mock(SpeechClient.class))
			.build();

		assertThat(details.getRecognizerName()).isEqualTo("projects/my-project/locations/eu/recognizers/_");
	}

	@Test
	void builderWithCredentialsAndWithoutCustomClientCreatesClientWithFixedCredentialsProvider() throws IOException {
		SpeechClient createdClient = mock(SpeechClient.class);
		GoogleCredentials credentials = mock(GoogleCredentials.class);
		ArgumentCaptor<SpeechSettings> settingsCaptor = ArgumentCaptor.forClass(SpeechSettings.class);

		try (MockedStatic<SpeechClient> mockedSpeechClient = mockStatic(SpeechClient.class)) {
			mockedSpeechClient.when(() -> SpeechClient.create(any(SpeechSettings.class))).thenReturn(createdClient);

			GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
				.projectId("my-project")
				.credentials(credentials)
				.build();

			assertThat(details.getSpeechClient()).isSameAs(createdClient);
			mockedSpeechClient.verify(() -> SpeechClient.create(settingsCaptor.capture()), times(1));
			SpeechSettings capturedSettings = settingsCaptor.getValue();

			CredentialsProvider credentialsProvider = capturedSettings.getCredentialsProvider();
			assertThat(credentialsProvider).isInstanceOf(FixedCredentialsProvider.class);
			assertThat(credentialsProvider.getCredentials()).isSameAs(credentials);
		}
	}

	@Test
	void builderWithoutCredentialsAndWithoutCustomClientUsesDefaultCredentialsProvider() throws IOException {
		SpeechClient createdClient = mock(SpeechClient.class);
		ArgumentCaptor<SpeechSettings> settingsCaptor = ArgumentCaptor.forClass(SpeechSettings.class);

		try (MockedStatic<SpeechClient> mockedSpeechClient = mockStatic(SpeechClient.class)) {
			mockedSpeechClient.when(() -> SpeechClient.create(any(SpeechSettings.class))).thenReturn(createdClient);

			GoogleGenAiTranscriptionConnectionDetails details = GoogleGenAiTranscriptionConnectionDetails.builder()
				.projectId("my-project")
				.build();

			assertThat(details.getSpeechClient()).isSameAs(createdClient);
			mockedSpeechClient.verify(() -> SpeechClient.create(settingsCaptor.capture()), times(1));
			SpeechSettings capturedSettings = settingsCaptor.getValue();
			assertThat(capturedSettings.getCredentialsProvider()).isNotInstanceOf(NoCredentialsProvider.class)
				.isNotInstanceOf(FixedCredentialsProvider.class);
		}
	}

	@Test
	void builderWrapsSpeechClientCreationFailureInUncheckedIOException() {
		IOException cause = new IOException("boom");

		try (MockedStatic<SpeechClient> mockedSpeechClient = mockStatic(SpeechClient.class)) {
			mockedSpeechClient.when(() -> SpeechClient.create(any(SpeechSettings.class))).thenThrow(cause);

			assertThatThrownBy(
					() -> GoogleGenAiTranscriptionConnectionDetails.builder().projectId("my-project").build())
				.isInstanceOf(UncheckedIOException.class)
				.hasMessageContaining("Failed to create the Speech client")
				.hasCause(cause);

			mockedSpeechClient.verify(() -> SpeechClient.create(any(SpeechSettings.class)), times(1));
		}
	}

}
