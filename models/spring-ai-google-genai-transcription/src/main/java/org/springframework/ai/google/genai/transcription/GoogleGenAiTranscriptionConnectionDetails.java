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
import java.util.Objects;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GoogleGenAiTranscriptionConnectionDetails represents the details of a connection to the
 * Google Cloud Speech-to-Text V2 service used by the Chirp transcription models. It
 * constructs and exposes a {@link SpeechClient} configured for either API-key or Google
 * credentials authentication.
 * <p>
 * Note: unlike the Gemini Developer API used by the chat/embedding/image modules,
 * Speech-to-Text V2 resource names always embed a project ID. A project ID is therefore
 * required in both authentication modes.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public final class GoogleGenAiTranscriptionConnectionDetails {

	public static final String DEFAULT_LOCATION = "global";

	private static final String INLINE_RECOGNIZER = "_";

	private final String projectId;

	private final String location;

	private final SpeechClient speechClient;

	private GoogleGenAiTranscriptionConnectionDetails(String projectId, String location, SpeechClient speechClient) {
		this.projectId = projectId;
		this.location = location;
		this.speechClient = speechClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static String getEndpoint(String location) {
		return DEFAULT_LOCATION.equals(location) ? "speech.googleapis.com:443"
				: location + "-speech.googleapis.com:443";
	}

	public String getProjectId() {
		return this.projectId;
	}

	public String getLocation() {
		return this.location;
	}

	public SpeechClient getSpeechClient() {
		return this.speechClient;
	}

	public String getRecognizerName() {
		return "projects/%s/locations/%s/recognizers/%s".formatted(this.projectId, this.location, INLINE_RECOGNIZER);
	}

	public static final class Builder {

		private @Nullable String projectId;

		private @Nullable String location;

		private @Nullable GoogleCredentials credentials;

		private @Nullable SpeechClient speechClient;

		private Builder() {
		}

		public Builder projectId(@Nullable String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder location(@Nullable String location) {
			this.location = location;
			return this;
		}

		public Builder credentials(@Nullable GoogleCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		/**
		 * Sets a custom {@link SpeechClient}. If provided, all other connection settings
		 * are ignored when creating the client.
		 * @param speechClient the custom Speech client
		 * @return this builder
		 */
		public Builder speechClient(@Nullable SpeechClient speechClient) {
			this.speechClient = speechClient;
			return this;
		}

		public GoogleGenAiTranscriptionConnectionDetails build() {
			Assert.hasText(this.projectId, "Project ID must be provided");

			final String location = StringUtils.hasText(this.location) ? this.location : DEFAULT_LOCATION;

			if (Objects.nonNull(this.speechClient)) {
				return new GoogleGenAiTranscriptionConnectionDetails(this.projectId, location, this.speechClient);
			}

			return new GoogleGenAiTranscriptionConnectionDetails(this.projectId, location,
					createSpeechClient(location));
		}

		private SpeechClient createSpeechClient(String location) {
			final SpeechSettings.Builder settingsBuilder = SpeechSettings.newBuilder()
				.setEndpoint(getEndpoint(location));

			if (Objects.nonNull(this.credentials)) {
				settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(this.credentials));
			}

			try {
				final SpeechSettings speechSettings = settingsBuilder.build();
				return SpeechClient.create(speechSettings);
			}
			catch (IOException exception) {
				throw new UncheckedIOException("Failed to create the Speech client", exception);
			}
		}

	}

}
