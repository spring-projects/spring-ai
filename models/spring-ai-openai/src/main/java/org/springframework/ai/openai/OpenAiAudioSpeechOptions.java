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

	private final @Nullable String voice;

	private final @Nullable String responseFormat;

	private final @Nullable Double speed;


	protected OpenAiAudioSpeechOptions(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable String input, @Nullable String voice, @Nullable String responseFormat, @Nullable Double speed) {
		super(baseUrl, apiKey, credential, model, microsoftDeploymentName, microsoftFoundryServiceVersion,
				organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries, proxy, customHeaders);
		this.input = input;
		this.voice = voice;
		this.responseFormat = responseFormat;
		this.speed = speed;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getInput() {
		return this.input;
	}

	@Override
	public @Nullable String getVoice() {
		return this.voice;
	}

	public @Nullable String getResponseFormat() {
		return this.responseFormat;
	}

	@Override
	public @Nullable Double getSpeed() {
		return this.speed;
	}

	@Override
	public @Nullable String getFormat() {
		return (this.responseFormat != null) ? this.responseFormat.toLowerCase() : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OpenAiAudioSpeechOptions copy() {
		return OpenAiAudioSpeechOptions.builder()
			.model(this.getModel())
			.input(this.input)
			.voice(this.voice)
			.responseFormat(this.responseFormat)
			.speed(this.speed)
			.baseUrl(this.getBaseUrl())
			.apiKey(this.getApiKey())
			.credential(this.getCredential())
			.deploymentName(this.getDeploymentName())
			.microsoftFoundryServiceVersion(this.getMicrosoftFoundryServiceVersion())
			.organizationId(this.getOrganizationId())
			.microsoftFoundry(this.isMicrosoftFoundry())
			.gitHubModels(this.isGitHubModels())
			.timeout(this.getTimeout())
			.maxRetries(this.getMaxRetries())
			.proxy(this.getProxy())
			.customHeaders(this.getCustomHeaders())
			.build();
	}

	@Override
	public boolean equals(Object o) {
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

	@Override
	public String toString() {
		return "OpenAiAudioSpeechOptions{" + "model='" + getModel() + '\'' + ", input='" + this.input + '\''
				+ ", voice='" + this.voice + '\'' + ", responseFormat='" + this.responseFormat + '\'' + ", speed="
				+ this.speed + '}';
	}

	public static final class Builder {

		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable Credential credential;

		private @Nullable String model;

		private @Nullable String deploymentName;

		private @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

		private @Nullable String organizationId;

		private @Nullable Boolean microsoftFoundry;

		private @Nullable Boolean gitHubModels;

		private @Nullable Duration timeout;

		private @Nullable Integer maxRetries;

		private @Nullable Proxy proxy;

		private @Nullable Map<String, String> customHeaders;

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
			this.deploymentName = fromOptions.getDeploymentName();
			this.microsoftFoundryServiceVersion = fromOptions.getMicrosoftFoundryServiceVersion();
			this.organizationId = fromOptions.getOrganizationId();
			this.microsoftFoundry = fromOptions.isMicrosoftFoundry();
			this.gitHubModels = fromOptions.isGitHubModels();
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
			if (from instanceof OpenAiAudioSpeechOptions castFrom) {
				// Parent class fields
				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getModel() != null) {
					this.model = castFrom.getModel();
				}
				if (castFrom.getDeploymentName() != null) {
					this.deploymentName = castFrom.getDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.microsoftFoundry = castFrom.isMicrosoftFoundry();
				this.gitHubModels = castFrom.isGitHubModels();
				this.timeout = castFrom.getTimeout();
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				this.customHeaders = castFrom.getCustomHeaders();
				// Child class fields
				if (castFrom.getInput() != null) {
					this.input = castFrom.getInput();
				}
				if (castFrom.getVoice() != null) {
					this.voice = castFrom.getVoice();
				}
				if (castFrom.getResponseFormat() != null) {
					this.responseFormat = castFrom.getResponseFormat();
				}
				if (castFrom.getSpeed() != null) {
					this.speed = castFrom.getSpeed();
				}
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
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

		public Builder deploymentName(@Nullable String deploymentName) {
			this.deploymentName = deploymentName;
			return this;
		}

		public Builder baseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder credential(com.openai.credential.@Nullable Credential credential) {
			this.credential = credential;
			return this;
		}

		public Builder microsoftFoundryServiceVersion(
				com.openai.azure.@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
			this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
			return this;
		}

		public Builder organizationId(@Nullable String organizationId) {
			this.organizationId = organizationId;
			return this;
		}

		public Builder microsoftFoundry(boolean microsoftFoundry) {
			this.microsoftFoundry = microsoftFoundry;
			return this;
		}

		public Builder gitHubModels(boolean gitHubModels) {
			this.gitHubModels = gitHubModels;
			return this;
		}

		public Builder timeout(java.time.Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder maxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public Builder proxy(java.net.@Nullable Proxy proxy) {
			this.proxy = proxy;
			return this;
		}

		public Builder customHeaders(Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
			return this;
		}

		public OpenAiAudioSpeechOptions build() {
			return new OpenAiAudioSpeechOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.deploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.microsoftFoundry, this.gitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.input, this.voice, this.responseFormat, this.speed);
		}

	}

}
