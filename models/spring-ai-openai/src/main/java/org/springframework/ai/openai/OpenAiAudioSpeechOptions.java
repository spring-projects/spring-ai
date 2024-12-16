/*
 * Copyright 2023-2024 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.AudioResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.Voice;

/**
 * Options for OpenAI text to audio - speech synthesis.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0-M1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiAudioSpeechOptions implements ModelOptions {

	/**
	 * ID of the model to use for generating the audio. One of the available TTS models:
	 * tts-1 or tts-1-hd.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The input text to synthesize. Must be at most 4096 tokens long.
	 */
	@JsonProperty("input")
	private String input;

	/**
	 * The voice to use for synthesis. One of the available voices for the chosen model:
	 * 'alloy', 'echo', 'fable', 'onyx', 'nova', and 'shimmer'.
	 */
	@JsonProperty("voice")
	private Voice voice;

	/**
	 * The format of the audio output. Supported formats are mp3, opus, aac, and flac.
	 * Defaults to mp3.
	 */
	@JsonProperty("response_format")
	private AudioResponseFormat responseFormat;

	/**
	 * The speed of the voice synthesis. The acceptable range is from 0.25 (slowest) to
	 * 4.0 (fastest). Defaults to 1 (normal)
	 */
	@JsonProperty("speed")
	private Float speed;

	public static Builder builder() {
		return new Builder();
	}

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

	public Voice getVoice() {
		return this.voice;
	}

	public void setVoice(Voice voice) {
		this.voice = voice;
	}

	public AudioResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Float getSpeed() {
		return this.speed;
	}

	public void setSpeed(Float speed) {
		this.speed = speed;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.input == null) ? 0 : this.input.hashCode());
		result = prime * result + ((this.voice == null) ? 0 : this.voice.hashCode());
		result = prime * result + ((this.responseFormat == null) ? 0 : this.responseFormat.hashCode());
		result = prime * result + ((this.speed == null) ? 0 : this.speed.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		OpenAiAudioSpeechOptions other = (OpenAiAudioSpeechOptions) obj;
		if (this.model == null) {
			if (other.model != null) {
				return false;
			}
		}
		else if (!this.model.equals(other.model)) {
			return false;
		}
		if (this.input == null) {
			if (other.input != null) {
				return false;
			}
		}
		else if (!this.input.equals(other.input)) {
			return false;
		}
		if (this.voice == null) {
			if (other.voice != null) {
				return false;
			}
		}
		else if (!this.voice.equals(other.voice)) {
			return false;
		}
		if (this.responseFormat == null) {
			if (other.responseFormat != null) {
				return false;
			}
		}
		else if (!this.responseFormat.equals(other.responseFormat)) {
			return false;
		}
		if (this.speed == null) {
			return other.speed == null;
		}
		else {
			return this.speed.equals(other.speed);
		}
	}

	@Override
	public String toString() {
		return "OpenAiAudioSpeechOptions{" + "model='" + this.model + '\'' + ", input='" + this.input + '\''
				+ ", voice='" + this.voice + '\'' + ", responseFormat='" + this.responseFormat + '\'' + ", speed="
				+ this.speed + '}';
	}

	public static class Builder {

		private final OpenAiAudioSpeechOptions options = new OpenAiAudioSpeechOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder input(String input) {
			this.options.input = input;
			return this;
		}

		public Builder voice(Voice voice) {
			this.options.voice = voice;
			return this;
		}

		public Builder responseFormat(AudioResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder speed(Float speed) {
			this.options.speed = speed;
			return this;
		}

		/**
		 * @deprecated use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		/**
		 * @deprecated use {@link #input(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withInput(String input) {
			this.options.input = input;
			return this;
		}

		/**
		 * @deprecated use {@link #voice(Voice)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withVoice(Voice voice) {
			this.options.voice = voice;
			return this;
		}

		/**
		 * @deprecated use {@link #responseFormat(AudioResponseFormat)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withResponseFormat(AudioResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		/**
		 * @deprecated use {@link #speed(Float)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withSpeed(Float speed) {
			this.options.speed = speed;
			return this;
		}

		public OpenAiAudioSpeechOptions build() {
			return this.options;
		}

	}

}
