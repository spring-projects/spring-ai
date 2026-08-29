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
import com.google.api.gax.core.NoCredentialsProvider;
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

	/**
	 * The default Speech-to-Text V2 location. The {@code global} location uses the
	 * multi-regional endpoint.
	 */
	public static final String DEFAULT_LOCATION = "europe-west2";

	/**
	 * The well-known recognizer identifier used for inline (recognizer-less) recognition.
	 */
	private static final String INLINE_RECOGNIZER = "_";

	/**
	 * Your Google Cloud project ID.
	 */
	private final String projectId;

	/**
	 * The Speech-to-Text V2 location (e.g. {@code global}, {@code us}, {@code eu}).
	 */
	private final String location;

	/**
	 * The API key used for authentication. If null, Google credentials are used.
	 */
	private final @Nullable String apiKey;

	/**
	 * The Speech client instance configured for this connection.
	 */
	private final SpeechClient speechClient;

	private GoogleGenAiTranscriptionConnectionDetails(String projectId, String location, @Nullable String apiKey,
			SpeechClient speechClient) {
		this.projectId = projectId;
		this.location = location;
		this.apiKey = apiKey;
		this.speechClient = speechClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getProjectId() {
		return this.projectId;
	}

	public String getLocation() {
		return this.location;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public SpeechClient getSpeechClient() {
		return this.speechClient;
	}

	/**
	 * Returns the fully-qualified recognizer resource name used for inline recognition.
	 * @return the recognizer resource name in the format
	 * {@code projects/{project}/locations/{location}/recognizers/_}
	 */
	public String getRecognizerName() {
		return "projects/%s/locations/%s/recognizers/%s".formatted(this.projectId, this.location, INLINE_RECOGNIZER);
	}

	public static final class Builder {

		private @Nullable String projectId;

		private @Nullable String location;

		private @Nullable String apiKey;

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

		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
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

			if (!StringUtils.hasText(this.location)) {
				this.location = DEFAULT_LOCATION;
			}

			if (Objects.nonNull(this.speechClient)) {
				return new GoogleGenAiTranscriptionConnectionDetails(this.projectId, this.location, this.apiKey,
						this.speechClient);
			}

			return new GoogleGenAiTranscriptionConnectionDetails(this.projectId, this.location, this.apiKey,
					createSpeechClient(this.location));
		}

		private SpeechClient createSpeechClient(String location) {
			final SpeechSettings.Builder settingsBuilder = SpeechSettings.newBuilder()
				.setEndpoint("%s-speech.googleapis.com:443".formatted(location));

			if (StringUtils.hasText(this.apiKey)) {
				settingsBuilder.setCredentialsProvider(NoCredentialsProvider.create());
				settingsBuilder.setApiKey(this.apiKey);
			}
			else if (this.credentials != null) {
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
