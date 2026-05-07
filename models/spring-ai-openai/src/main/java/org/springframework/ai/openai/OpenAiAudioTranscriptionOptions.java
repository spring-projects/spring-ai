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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiAudioTranscriptionOptions extends AbstractOpenAiOptions implements AudioTranscriptionOptions {

	/**
	 * Default transcription model (Whisper 1).
	 */
	public static final String DEFAULT_TRANSCRIPTION_MODEL = AudioModel.WHISPER_1.asString();

	/**
	 * Default response format.
	 */
	public static final AudioResponseFormat DEFAULT_RESPONSE_FORMAT = AudioResponseFormat.TEXT;

	private final @Nullable String model;

	private final AudioResponseFormat responseFormat;

	private final @Nullable String prompt;

	private final @Nullable String language;

	private final @Nullable Float temperature;

	private final @Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities;

	protected OpenAiAudioTranscriptionOptions(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable AudioResponseFormat responseFormat, @Nullable String prompt, @Nullable String language,
			@Nullable Float temperature,
			@Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities) {
		super(baseUrl, apiKey, credential, model, microsoftDeploymentName, microsoftFoundryServiceVersion,
				organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries, proxy, customHeaders);
		this.model = model;
		this.responseFormat = responseFormat != null ? responseFormat : DEFAULT_RESPONSE_FORMAT;
		this.prompt = prompt;
		this.language = language;
		this.temperature = temperature;
		this.timestampGranularities = timestampGranularities;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model != null ? this.model : DEFAULT_TRANSCRIPTION_MODEL;
	}

	public AudioResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public @Nullable String getPrompt() {
		return this.prompt;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public @Nullable List<TranscriptionCreateParams.TimestampGranularity> getTimestampGranularities() {
		return this.timestampGranularities;
	}

	public OpenAiAudioTranscriptionOptions copy() {
		return OpenAiAudioTranscriptionOptions.builder()
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
		OpenAiAudioTranscriptionOptions that = (OpenAiAudioTranscriptionOptions) o;
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
		return "OpenAiAudioTranscriptionOptions{" + "model='" + this.model + '\'' + ", responseFormat="
				+ this.responseFormat + ", prompt='" + this.prompt + '\'' + ", language='" + this.language + '\''
				+ ", temperature=" + this.temperature + ", timestampGranularities=" + this.timestampGranularities + '}';
	}

	public static final class Builder extends AbstractBuilder<OpenAiAudioTranscriptionOptions, Builder> {

		private @Nullable AudioResponseFormat responseFormat;

		private @Nullable String prompt;

		private @Nullable String language;

		private @Nullable Float temperature;

		private @Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities;

		private Builder() {
		}

		public Builder from(OpenAiAudioTranscriptionOptions fromOptions) {
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
			this.responseFormat = fromOptions.getResponseFormat();
			this.prompt = fromOptions.getPrompt();
			this.language = fromOptions.getLanguage();
			this.temperature = fromOptions.getTemperature();
			this.timestampGranularities = fromOptions.getTimestampGranularities();
			return this;
		}

		public Builder merge(@Nullable AudioTranscriptionOptions from) {
			if (from == null) {
				return this;
			}
			if (from.getModel() != null) {
				this.model = from.getModel();
			}
			if (from instanceof OpenAiAudioTranscriptionOptions castFrom) {
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
				if (castFrom.getTimeout() != null) {
					this.timeout = castFrom.getTimeout();
				}
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				if (castFrom.getCustomHeaders() != null) {
					this.customHeaders = castFrom.getCustomHeaders();
				}
				if (castFrom.getResponseFormat() != null) {
					this.responseFormat = castFrom.getResponseFormat();
				}
				if (castFrom.getPrompt() != null) {
					this.prompt = castFrom.getPrompt();
				}
				if (castFrom.getLanguage() != null) {
					this.language = castFrom.getLanguage();
				}
				if (castFrom.getTemperature() != null) {
					this.temperature = castFrom.getTemperature();
				}
				if (castFrom.getTimestampGranularities() != null) {
					this.timestampGranularities = castFrom.getTimestampGranularities();
				}
			}
			return this;
		}

		public Builder responseFormat(AudioResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder prompt(@Nullable String prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder language(@Nullable String language) {
			this.language = language;
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder timestampGranularities(
				@Nullable List<TranscriptionCreateParams.TimestampGranularity> timestampGranularities) {
			this.timestampGranularities = timestampGranularities;
			return this;
		}

		@Override
		public OpenAiAudioTranscriptionOptions build() {
			return new OpenAiAudioTranscriptionOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.responseFormat, this.prompt, this.language, this.temperature,
					this.timestampGranularities);
		}

	}

}
