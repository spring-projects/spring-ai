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

package org.springframework.ai.openaisdk;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

/**
 * OpenAI SDK Audio Transcription Options.
 *
 * @author Ilayaperumal Gopinathan
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiSdkAudioTranscriptionOptions extends AbstractOpenAiSdkOptions implements AudioTranscriptionOptions {

	/**
	 * Default transcription model (Whisper 1).
	 */
	public static final String DEFAULT_TRANSCRIPTION_MODEL = AudioModel.WHISPER_1.asString();

	/**
	 * Default response format.
	 */
	public static final AudioResponseFormat DEFAULT_RESPONSE_FORMAT = AudioResponseFormat.TEXT;

	@JsonProperty("model")
	private @Nullable String model;

	@JsonProperty("response_format")
	private AudioResponseFormat responseFormat = DEFAULT_RESPONSE_FORMAT;

	@JsonProperty("prompt")
	private @Nullable String prompt;

	@JsonProperty("language")
	private @Nullable String language;

	@JsonProperty("temperature")
	private @Nullable Float temperature;

	@JsonProperty("timestamp_granularities")
	private @Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model != null ? this.model : DEFAULT_TRANSCRIPTION_MODEL;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public AudioResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable String getPrompt() {
		return this.prompt;
	}

	public void setPrompt(@Nullable String prompt) {
		this.prompt = prompt;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Float temperature) {
		this.temperature = temperature;
	}

	public @Nullable List<TranscriptionCreateParams.TimestampGranularity> getTimestampGranularities() {
		return this.timestampGranularities;
	}

	public void setTimestampGranularities(
			@Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities) {
		this.timestampGranularities = timestampGranularities;
	}

	public OpenAiSdkAudioTranscriptionOptions copy() {
		return OpenAiSdkAudioTranscriptionOptions.builder()
			.model(this.model)
			.responseFormat(this.responseFormat)
			.prompt(this.prompt)
			.language(this.language)
			.temperature(this.temperature)
			.timestampGranularities(this.timestampGranularities)
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
		OpenAiSdkAudioTranscriptionOptions that = (OpenAiSdkAudioTranscriptionOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.prompt, that.prompt) && Objects.equals(this.language, that.language)
				&& Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.timestampGranularities, that.timestampGranularities);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.responseFormat, this.prompt, this.language, this.temperature,
				this.timestampGranularities);
	}

	@Override
	public String toString() {
		return "OpenAiSdkAudioTranscriptionOptions{" + "model='" + this.model + '\'' + ", responseFormat="
				+ this.responseFormat + ", prompt='" + this.prompt + '\'' + ", language='" + this.language + '\''
				+ ", temperature=" + this.temperature + ", timestampGranularities=" + this.timestampGranularities + '}';
	}

	public static final class Builder {

		private final OpenAiSdkAudioTranscriptionOptions options;

		private Builder() {
			this.options = new OpenAiSdkAudioTranscriptionOptions();
		}

		public Builder from(OpenAiSdkAudioTranscriptionOptions fromOptions) {
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
			this.options.setResponseFormat(fromOptions.getResponseFormat());
			this.options.setPrompt(fromOptions.getPrompt());
			this.options.setLanguage(fromOptions.getLanguage());
			this.options.setTemperature(fromOptions.getTemperature());
			this.options.setTimestampGranularities(fromOptions.getTimestampGranularities());
			return this;
		}

		public Builder merge(@Nullable AudioTranscriptionOptions from) {
			if (from == null) {
				return this;
			}
			if (from.getModel() != null) {
				this.options.setModel(from.getModel());
			}
			if (from instanceof OpenAiSdkAudioTranscriptionOptions castFrom) {
				if (castFrom.getBaseUrl() != null) {
					this.options.setBaseUrl(castFrom.getBaseUrl());
				}
				if (castFrom.getApiKey() != null) {
					this.options.setApiKey(castFrom.getApiKey());
				}
				if (castFrom.getCredential() != null) {
					this.options.setCredential(castFrom.getCredential());
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
				this.options.setTimeout(castFrom.getTimeout());
				this.options.setMaxRetries(castFrom.getMaxRetries());
				if (castFrom.getProxy() != null) {
					this.options.setProxy(castFrom.getProxy());
				}
				this.options.setCustomHeaders(castFrom.getCustomHeaders());
				if (castFrom.getResponseFormat() != null) {
					this.options.setResponseFormat(castFrom.getResponseFormat());
				}
				if (castFrom.getPrompt() != null) {
					this.options.setPrompt(castFrom.getPrompt());
				}
				if (castFrom.getLanguage() != null) {
					this.options.setLanguage(castFrom.getLanguage());
				}
				if (castFrom.getTemperature() != null) {
					this.options.setTemperature(castFrom.getTemperature());
				}
				if (castFrom.getTimestampGranularities() != null) {
					this.options.setTimestampGranularities(castFrom.getTimestampGranularities());
				}
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder responseFormat(AudioResponseFormat responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder prompt(@Nullable String prompt) {
			this.options.setPrompt(prompt);
			return this;
		}

		public Builder language(@Nullable String language) {
			this.options.setLanguage(language);
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder timestampGranularities(
				@Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities) {
			this.options.setTimestampGranularities(timestampGranularities);
			return this;
		}

		public Builder baseUrl(@Nullable String baseUrl) {
			this.options.setBaseUrl(baseUrl);
			return this;
		}

		public Builder apiKey(@Nullable String apiKey) {
			this.options.setApiKey(apiKey);
			return this;
		}

		public Builder credential(@Nullable Credential credential) {
			this.options.setCredential(credential);
			return this;
		}

		public Builder deploymentName(@Nullable String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder microsoftFoundryServiceVersion(
				@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
			this.options.setMicrosoftFoundryServiceVersion(microsoftFoundryServiceVersion);
			return this;
		}

		public Builder organizationId(@Nullable String organizationId) {
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

		public Builder timeout(Duration timeout) {
			this.options.setTimeout(timeout);
			return this;
		}

		public Builder maxRetries(int maxRetries) {
			this.options.setMaxRetries(maxRetries);
			return this;
		}

		public Builder proxy(@Nullable Proxy proxy) {
			this.options.setProxy(proxy);
			return this;
		}

		public Builder customHeaders(Map<String, String> customHeaders) {
			this.options.setCustomHeaders(customHeaders);
			return this;
		}

		public OpenAiSdkAudioTranscriptionOptions build() {
			return this.options;
		}

	}

}
