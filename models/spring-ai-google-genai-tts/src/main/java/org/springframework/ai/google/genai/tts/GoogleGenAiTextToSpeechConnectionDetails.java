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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Holds the connection details for the Cloud Text-to-Speech V2 API used by Gemini-TTS and
 * builds the underlying {@link TextToSpeechClient}.
 * <p>
 * Cloud Text-to-Speech is authenticated exclusively through Application Default
 * Credentials (ADC) or an explicit service account, and its resource names always embed a
 * Google Cloud project, so a {@code projectId} is required. The V2 API uses
 * location-specific regional endpoints: the {@code global} location uses
 * {@code texttospeech.googleapis.com:443}, and any other location uses
 * {@code <location>-texttospeech.googleapis.com:443}.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public final class GoogleGenAiTextToSpeechConnectionDetails {

	public static final String DEFAULT_LOCATION = "global";

	private final String projectId;

	private final String location;

	private final TextToSpeechClient textToSpeechClient;

	private GoogleGenAiTextToSpeechConnectionDetails(String projectId, String location,
			TextToSpeechClient textToSpeechClient) {
		this.projectId = projectId;
		this.location = location;
		this.textToSpeechClient = textToSpeechClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static String getEndpoint(String location) {
		return DEFAULT_LOCATION.equals(location) ? "texttospeech.googleapis.com:443"
				: location + "-texttospeech.googleapis.com:443";
	}

	public String getProjectId() {
		return this.projectId;
	}

	public String getLocation() {
		return this.location;
	}

	public TextToSpeechClient getTextToSpeechClient() {
		return this.textToSpeechClient;
	}

	public static final class Builder {

		private @Nullable String projectId;

		private @Nullable String location;

		private @Nullable GoogleCredentials credentials;

		private @Nullable TextToSpeechClient textToSpeechClient;

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
		 * Sets an explicit {@link TextToSpeechClient} to use. If provided, the endpoint
		 * and credentials settings are ignored.
		 * @param textToSpeechClient the client to use
		 * @return this builder
		 */
		public Builder textToSpeechClient(@Nullable TextToSpeechClient textToSpeechClient) {
			this.textToSpeechClient = textToSpeechClient;
			return this;
		}

		public GoogleGenAiTextToSpeechConnectionDetails build() {
			Assert.hasText(this.projectId, "Project ID must be provided");

			this.location = StringUtils.hasText(this.location) ? this.location : DEFAULT_LOCATION;

			if (Objects.nonNull(this.textToSpeechClient)) {
				return new GoogleGenAiTextToSpeechConnectionDetails(this.projectId, this.location,
						this.textToSpeechClient);
			}

			return new GoogleGenAiTextToSpeechConnectionDetails(this.projectId, this.location,
					createTextToSpeechClient(this.location));
		}

		private TextToSpeechClient createTextToSpeechClient(String location) {
			final TextToSpeechSettings.Builder settingsBuilder = TextToSpeechSettings.newBuilder()
				.setEndpoint(getEndpoint(location));

			if (Objects.nonNull(this.credentials)) {
				settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(this.credentials));
			}

			try {
				final TextToSpeechSettings speechSettings = settingsBuilder.build();
				return TextToSpeechClient.create(speechSettings);
			}
			catch (IOException exception) {
				throw new UncheckedIOException("Failed to create the Speech client", exception);
			}
		}

	}

}
