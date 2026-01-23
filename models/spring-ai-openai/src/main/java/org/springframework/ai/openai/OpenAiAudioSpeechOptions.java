/*
 * Copyright 2023-2025 the original author or authors.
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

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.AudioResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.Voice;

/**
 * Options for OpenAI text to audio - speech synthesis.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Ilayaperumal Gopinathan
 * @author Jonghoon Park
 * @since 1.0.0-M1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiAudioSpeechOptions implements TextToSpeechOptions {

	/**
	 * ID of the model to use for generating the audio. For OpenAI's TTS API, use one of
	 * the available models: tts-1 or tts-1-hd.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The input text to synthesize. Must be at most 4096 tokens long.
	 */
	@JsonProperty("input")
	private String input;

	/**
	 * The voice to use for synthesis. For OpenAI's TTS API, One of the available voices
	 * for the chosen model: 'alloy', 'echo', 'fable', 'onyx', 'nova', and 'shimmer'.
	 */
	@JsonProperty("voice")
	private String voice;

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
	private Double speed;

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

	public String getVoice() {
		return this.voice;
	}

	public void setVoice(String voice) {
		this.voice = voice;
	}

	public void setVoice(Voice voice) {
		this.voice = voice.getValue();
	}

	public AudioResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Override
	public Double getSpeed() {
		return this.speed;
	}

	public void setSpeed(Double speed) {
		this.speed = speed;
	}

	// TextToSpeechOptions interface methods

	@Override
	public String getFormat() {
		return (this.responseFormat != null) ? this.responseFormat.name().toLowerCase() : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OpenAiAudioSpeechOptions copy() {
		return OpenAiAudioSpeechOptions.builder()
			.model(this.model)
			.input(this.input)
			.voice(this.voice)
			.responseFormat(this.responseFormat)
			.speed(this.speed)
			.build();
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

	public static final class Builder {

		private final OpenAiAudioSpeechOptions options = new OpenAiAudioSpeechOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder input(String input) {
			this.options.input = input;
			return this;
		}

		public Builder voice(String voice) {
			this.options.voice = voice;
			return this;
		}

		public Builder voice(Voice voice) {
			this.options.voice = voice.getValue();
			return this;
		}

		public Builder responseFormat(AudioResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder speed(Double speed) {
			this.options.speed = speed;
			return this;
		}

		public OpenAiAudioSpeechOptions build() {
			return this.options;
		}

	}

}
