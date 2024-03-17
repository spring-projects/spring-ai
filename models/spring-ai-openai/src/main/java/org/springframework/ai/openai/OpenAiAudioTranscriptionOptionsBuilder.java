/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai;

import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest.GranularityType;

/**
 * Builder for {@link OpenAiAudioTranscriptionOptions}
 *
 * @author youngmon
 * @version 0.8.1
 */
public class OpenAiAudioTranscriptionOptionsBuilder {

	private String model;

	private TranscriptResponseFormat responseFormat;

	private String prompt;

	private String language;

	private Float temperature;

	private GranularityType granularityType;

	private OpenAiAudioTranscriptionOptionsBuilder() {
	}

	public static OpenAiAudioTranscriptionOptionsBuilder builder() {
		return new OpenAiAudioTranscriptionOptionsBuilder();
	}

	/**
	 * Copy Constructor for {@link OpenAiAudioTranscriptionOptionsBuilder}
	 * @param options Existing {@link OpenAiAudioTranscriptionOptions}
	 * @return new OpenAiAudioTranscriptionsBuilder
	 */
	public static OpenAiAudioTranscriptionOptionsBuilder builder(OpenAiAudioTranscriptionOptions options) {
		return builder().withModel(options.getModel())
			.withResponseFormat(options.getResponseFormat())
			.withPrompt(options.getPrompt())
			.withLanguage(options.getLanguage())
			.withTemperature(options.getTemperature())
			.withGranularityType(options.getGranularityType());
	}

	public OpenAiAudioTranscriptionOptions build() {
		return new OpenAiAudioTranscriptionOptionsImpl(this);
	}

	public OpenAiAudioTranscriptionOptionsBuilder withModel(final String model) {
		if (model == null)
			return this;
		this.model = model;
		return this;
	}

	public OpenAiAudioTranscriptionOptionsBuilder withResponseFormat(final TranscriptResponseFormat responseFormat) {
		if (responseFormat == null)
			return this;
		this.responseFormat = responseFormat;
		return this;
	}

	public OpenAiAudioTranscriptionOptionsBuilder withPrompt(final String prompt) {
		if (prompt == null)
			return this;
		this.prompt = prompt;
		return this;
	}

	public OpenAiAudioTranscriptionOptionsBuilder withLanguage(final String language) {
		if (language == null)
			return this;
		this.language = language;
		return this;
	}

	public OpenAiAudioTranscriptionOptionsBuilder withTemperature(final Float temperature) {
		if (temperature == null)
			return this;
		this.temperature = temperature;
		return this;
	}

	public OpenAiAudioTranscriptionOptionsBuilder withGranularityType(final GranularityType granularityType) {
		if (granularityType == null)
			return this;
		this.granularityType = granularityType;
		return this;
	}

	private static class OpenAiAudioTranscriptionOptionsImpl implements OpenAiAudioTranscriptionOptions {

		private final String model;

		private final TranscriptResponseFormat responseFormat;

		private final String prompt;

		private final String language;

		private final Float temperature;

		private final GranularityType granularityType;

		private OpenAiAudioTranscriptionOptionsImpl(OpenAiAudioTranscriptionOptionsBuilder builder) {
			this.model = builder.model;
			this.responseFormat = builder.responseFormat;
			this.prompt = builder.prompt;
			this.language = builder.language;
			this.temperature = builder.temperature;
			this.granularityType = builder.granularityType;
		}

		@Override
		public String getModel() {
			return this.model;
		}

		@Override
		public TranscriptResponseFormat getResponseFormat() {
			return this.responseFormat;
		}

		@Override
		public String getPrompt() {
			return this.prompt;
		}

		@Override
		public String getLanguage() {
			return this.language;
		}

		@Override
		public Float getTemperature() {
			return this.temperature;
		}

		@Override
		public GranularityType getGranularityType() {
			return this.granularityType;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((model == null) ? 0 : model.hashCode());
			result = prime * result + ((prompt == null) ? 0 : prompt.hashCode());
			result = prime * result + ((language == null) ? 0 : language.hashCode());
			result = prime * result + ((responseFormat == null) ? 0 : responseFormat.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OpenAiAudioTranscriptionOptionsImpl other = (OpenAiAudioTranscriptionOptionsImpl) obj;
			if ((this.model == null) != (other.model == null))
				return false;
			else if (!model.equals(other.model))
				return false;
			if ((this.prompt == null) != (other.prompt == null))
				return false;
			else if (!this.prompt.equals(other.prompt))
				return false;
			if ((this.language == null) != (other.language == null))
				return false;
			else if (!this.language.equals(other.language))
				return false;
			if ((this.responseFormat == null) != (other.responseFormat == null))
				return false;
			else
				return this.responseFormat.equals(other.responseFormat);
		}

	}

}
