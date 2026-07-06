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

package org.springframework.ai.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechOptions;

/**
 * Configuration options for OpenAI text-to-speech using the OpenAI Java SDK.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiAudioSpeechOptions extends AbstractOpenAiOptions implements TextToSpeechOptions {

	public static final String DEFAULT_SPEECH_MODEL = "gpt-4o-mini-tts";

	public static final String DEFAULT_VOICE = Voice.ALLOY.getValue();

	public static final String DEFAULT_RESPONSE_FORMAT = AudioResponseFormat.MP3.getValue();

	public static final Double DEFAULT_SPEED = 1.0;

	public enum Voice {

		ALLOY("alloy"),

		ECHO("echo"),

		FABLE("fable"),

		ONYX("onyx"),

		NOVA("nova"),

		SHIMMER("shimmer"),

		BALLAD("ballad"),

		SAGE("sage"),

		CORAL("coral"),

		VERSE("verse"),

		ASH("ash");

		private final String value;

		Voice(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	public enum AudioResponseFormat {

		MP3("mp3"),

		OPUS("opus"),

		AAC("aac"),

		FLAC("flac"),

		WAV("wav"),

		PCM("pcm");

		private final String value;

		AudioResponseFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	private final @Nullable String input;

	private final String voice;

	private final String responseFormat;

	private final Double speed;

	protected OpenAiAudioSpeechOptions(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable String input, @Nullable String voice, @Nullable String responseFormat, @Nullable Double speed) {
		super(baseUrl, apiKey, credential, model != null ? model : DEFAULT_SPEECH_MODEL, microsoftDeploymentName,
				microsoftFoundryServiceVersion, organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries,
				proxy, customHeaders);
		this.input = input;
		this.voice = voice != null ? voice : DEFAULT_VOICE;
		this.responseFormat = responseFormat != null ? responseFormat : DEFAULT_RESPONSE_FORMAT;
		this.speed = speed != null ? speed : DEFAULT_SPEED;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getInput() {
		return this.input;
	}

	@Override
	public String getVoice() {
		return this.voice;
	}

	public String getResponseFormat() {
		return this.responseFormat;
	}

	@Override
	public Double getSpeed() {
		return this.speed;
	}

	@Override
	public @Nullable String getFormat() {
		return this.responseFormat.toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiAudioSpeechOptions that = (OpenAiAudioSpeechOptions) o;
		return Objects.equals(getModel(), that.getModel()) && Objects.equals(this.input, that.input)
				&& Objects.equals(this.voice, that.voice) && Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.speed, that.speed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getModel(), this.input, this.voice, this.responseFormat, this.speed);
	}

	public static final class Builder extends AbstractBuilder<OpenAiAudioSpeechOptions, Builder> {

		private @Nullable String input;

		private @Nullable String voice;

		private @Nullable String responseFormat;

		private @Nullable Double speed;

		private Builder() {
		}

		public Builder from(OpenAiAudioSpeechOptions fromOptions) {
			// Parent class fields
			this.baseUrl = fromOptions.getBaseUrl();
			this.apiKey = fromOptions.getApiKey();
			this.credential = fromOptions.getCredential();
			this.model = fromOptions.getModel();
			this.microsoftDeploymentName = fromOptions.getDeploymentName();
			this.microsoftFoundryServiceVersion = fromOptions.getMicrosoftFoundryServiceVersion();
			this.organizationId = fromOptions.getOrganizationId();
			this.isMicrosoftFoundry = fromOptions.isMicrosoftFoundry();
			this.isGitHubModels = fromOptions.isGitHubModels();
			this.timeout = fromOptions.getTimeout();
			this.maxRetries = fromOptions.getMaxRetries();
			this.proxy = fromOptions.getProxy();
			this.customHeaders = fromOptions.getCustomHeaders();
			// Child class fields
			this.model = fromOptions.getModel();
			this.input = fromOptions.getInput();
			this.voice = fromOptions.getVoice();
			this.responseFormat = fromOptions.getResponseFormat();
			this.speed = fromOptions.getSpeed();
			return this;
		}

		public Builder merge(@Nullable TextToSpeechOptions from) {
			if (from == null) {
				return this;
			}
			if (from.getModel() != null) {
				this.model = from.getModel();
			}
			if (from.getVoice() != null) {
				this.voice = from.getVoice();
			}
			if (from.getFormat() != null) {
				this.responseFormat = from.getFormat();
			}
			if (from.getSpeed() != null) {
				this.speed = from.getSpeed();
			}
			if (from instanceof AbstractOpenAiOptions castFrom) {
				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getDeploymentName() != null) {
					this.microsoftDeploymentName = castFrom.getDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.isMicrosoftFoundry = castFrom.isMicrosoftFoundry();
				this.isGitHubModels = castFrom.isGitHubModels();
				this.timeout = castFrom.getTimeout();
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				if (castFrom.getCustomHeaders() != null) {
					if (this.customHeaders == null) {
						this.customHeaders = new HashMap<>(castFrom.getCustomHeaders());
					}
					else {
						Map<String, String> merged = new HashMap<>(this.customHeaders);
						merged.putAll(castFrom.getCustomHeaders());
						this.customHeaders = merged;
					}
				}
			}
			if (from instanceof OpenAiAudioSpeechOptions castFrom) {
				if (castFrom.getInput() != null) {
					this.input = castFrom.getInput();
				}
				this.responseFormat = castFrom.getResponseFormat();
			}
			return this;
		}

		public Builder input(@Nullable String input) {
			this.input = input;
			return this;
		}

		public Builder voice(@Nullable String voice) {
			this.voice = voice;
			return this;
		}

		public Builder voice(@Nullable Voice voice) {
			this.voice = (voice != null) ? voice.getValue() : null;
			return this;
		}

		public Builder responseFormat(@Nullable String responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder responseFormat(@Nullable AudioResponseFormat responseFormat) {
			this.responseFormat = (responseFormat != null) ? responseFormat.getValue() : null;
			return this;
		}

		public Builder speed(@Nullable Double speed) {
			this.speed = speed;
			return this;
		}

		@Override
		public OpenAiAudioSpeechOptions build() {
			return new OpenAiAudioSpeechOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.input, this.voice, this.responseFormat, this.speed);
		}

	}

}
