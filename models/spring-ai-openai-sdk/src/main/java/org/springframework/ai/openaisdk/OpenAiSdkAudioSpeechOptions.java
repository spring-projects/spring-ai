/*
 * Copyright 2026-2026 the original author or authors.
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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.audio.tts.TextToSpeechOptions;

/**
 * Configuration options for OpenAI text-to-speech using the OpenAI Java SDK.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Jonghoon Park
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiSdkAudioSpeechOptions extends AbstractOpenAiSdkOptions implements TextToSpeechOptions {

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

	@JsonProperty("model")
	private String model;

	@JsonProperty("input")
	private String input;

	@JsonProperty("voice")
	private String voice;

	@JsonProperty("response_format")
	private String responseFormat;

	@JsonProperty("speed")
	private Double speed;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getInput() {
		return this.input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	@Override
	public String getVoice() {
		return this.voice;
	}

	public void setVoice(String voice) {
		this.voice = voice;
	}

	public void setVoice(Voice voice) {
		this.voice = voice.getValue();
	}

	public String getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat.getValue();
	}

	@Override
	public Double getSpeed() {
		return this.speed;
	}

	public void setSpeed(Double speed) {
		this.speed = speed;
	}

	@Override
	public String getFormat() {
		return (this.responseFormat != null) ? this.responseFormat.toLowerCase() : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OpenAiSdkAudioSpeechOptions copy() {
		return OpenAiSdkAudioSpeechOptions.builder()
			.model(this.model)
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
		OpenAiSdkAudioSpeechOptions that = (OpenAiSdkAudioSpeechOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.input, that.input)
				&& Objects.equals(this.voice, that.voice) && Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.speed, that.speed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.input, this.voice, this.responseFormat, this.speed);
	}

	@Override
	public String toString() {
		return "OpenAiSdkAudioSpeechOptions{" + "model='" + this.model + '\'' + ", input='" + this.input + '\''
				+ ", voice='" + this.voice + '\'' + ", responseFormat='" + this.responseFormat + '\'' + ", speed="
				+ this.speed + '}';
	}

	public static final class Builder {

		private final OpenAiSdkAudioSpeechOptions options;

		private Builder() {
			this.options = new OpenAiSdkAudioSpeechOptions();
		}

		public Builder from(OpenAiSdkAudioSpeechOptions fromOptions) {
			// Parent class fields
			this.options.setBaseUrl(fromOptions.getBaseUrl());
			this.options.setApiKey(fromOptions.getApiKey());
			this.options.setCredential(fromOptions.getCredential());
			this.options.setModel(fromOptions.getModel());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setMicrosoftFoundryServiceVersion(fromOptions.getMicrosoftFoundryServiceVersion());
			this.options.setOrganizationId(fromOptions.getOrganizationId());
			this.options.setMicrosoftFoundry(fromOptions.isMicrosoftFoundry());
			this.options.setGitHubModels(fromOptions.isGitHubModels());
			this.options.setTimeout(fromOptions.getTimeout());
			this.options.setMaxRetries(fromOptions.getMaxRetries());
			this.options.setProxy(fromOptions.getProxy());
			this.options.setCustomHeaders(fromOptions.getCustomHeaders());
			// Child class fields
			this.options.setModel(fromOptions.getModel());
			this.options.setInput(fromOptions.getInput());
			this.options.setVoice(fromOptions.getVoice());
			this.options.setResponseFormat(fromOptions.getResponseFormat());
			this.options.setSpeed(fromOptions.getSpeed());
			return this;
		}

		public Builder merge(TextToSpeechOptions from) {
			if (from instanceof OpenAiSdkAudioSpeechOptions castFrom) {
				// Parent class fields
				if (castFrom.getBaseUrl() != null) {
					this.options.setBaseUrl(castFrom.getBaseUrl());
				}
				if (castFrom.getApiKey() != null) {
					this.options.setApiKey(castFrom.getApiKey());
				}
				if (castFrom.getCredential() != null) {
					this.options.setCredential(castFrom.getCredential());
				}
				if (castFrom.getModel() != null) {
					this.options.setModel(castFrom.getModel());
				}
				if (castFrom.getDeploymentName() != null) {
					this.options.setDeploymentName(castFrom.getDeploymentName());
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.options.setMicrosoftFoundryServiceVersion(castFrom.getMicrosoftFoundryServiceVersion());
				}
				if (castFrom.getOrganizationId() != null) {
					this.options.setOrganizationId(castFrom.getOrganizationId());
				}
				this.options.setMicrosoftFoundry(castFrom.isMicrosoftFoundry());
				this.options.setGitHubModels(castFrom.isGitHubModels());
				if (castFrom.getTimeout() != null) {
					this.options.setTimeout(castFrom.getTimeout());
				}
				if (castFrom.getMaxRetries() != null) {
					this.options.setMaxRetries(castFrom.getMaxRetries());
				}
				if (castFrom.getProxy() != null) {
					this.options.setProxy(castFrom.getProxy());
				}
				if (castFrom.getCustomHeaders() != null) {
					this.options.setCustomHeaders(castFrom.getCustomHeaders());
				}
				// Child class fields
				if (castFrom.getInput() != null) {
					this.options.setInput(castFrom.getInput());
				}
				if (castFrom.getVoice() != null) {
					this.options.setVoice(castFrom.getVoice());
				}
				if (castFrom.getResponseFormat() != null) {
					this.options.setResponseFormat(castFrom.getResponseFormat());
				}
				if (castFrom.getSpeed() != null) {
					this.options.setSpeed(castFrom.getSpeed());
				}
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder input(String input) {
			this.options.setInput(input);
			return this;
		}

		public Builder voice(String voice) {
			this.options.setVoice(voice);
			return this;
		}

		public Builder voice(Voice voice) {
			this.options.setVoice(voice);
			return this;
		}

		public Builder responseFormat(String responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder responseFormat(AudioResponseFormat responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder speed(Double speed) {
			this.options.setSpeed(speed);
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder baseUrl(String baseUrl) {
			this.options.setBaseUrl(baseUrl);
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.options.setApiKey(apiKey);
			return this;
		}

		public Builder credential(com.openai.credential.Credential credential) {
			this.options.setCredential(credential);
			return this;
		}

		public Builder microsoftFoundryServiceVersion(
				com.openai.azure.AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
			this.options.setMicrosoftFoundryServiceVersion(microsoftFoundryServiceVersion);
			return this;
		}

		public Builder organizationId(String organizationId) {
			this.options.setOrganizationId(organizationId);
			return this;
		}

		public Builder microsoftFoundry(boolean microsoftFoundry) {
			this.options.setMicrosoftFoundry(microsoftFoundry);
			return this;
		}

		public Builder gitHubModels(boolean gitHubModels) {
			this.options.setGitHubModels(gitHubModels);
			return this;
		}

		public Builder timeout(java.time.Duration timeout) {
			this.options.setTimeout(timeout);
			return this;
		}

		public Builder maxRetries(Integer maxRetries) {
			this.options.setMaxRetries(maxRetries);
			return this;
		}

		public Builder proxy(java.net.Proxy proxy) {
			this.options.setProxy(proxy);
			return this;
		}

		public Builder customHeaders(java.util.Map<String, String> customHeaders) {
			this.options.setCustomHeaders(customHeaders);
			return this;
		}

		public OpenAiSdkAudioSpeechOptions build() {
			return this.options;
		}

	}

}
