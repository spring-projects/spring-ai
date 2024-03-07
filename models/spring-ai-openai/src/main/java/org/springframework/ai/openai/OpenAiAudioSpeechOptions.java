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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.AudioResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.Voice;

/**
 * Options for OpenAI text to audio - speech synthesis.
 *
 * @author Ahmed Yousri
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
	 * The speed of the voice synthesis. The acceptable range is from 0.0 (slowest) to 1.0
	 * (fastest).
	 */
	@JsonProperty("speed")
	private Float speed;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final OpenAiAudioSpeechOptions options = new OpenAiAudioSpeechOptions();

		public Builder withModel(String model) {
			options.model = model;
			return this;
		}

		public Builder withInput(String input) {
			options.input = input;
			return this;
		}

		public Builder withVoice(Voice voice) {
			options.voice = voice;
			return this;
		}

		public Builder withResponseFormat(AudioResponseFormat responseFormat) {
			options.responseFormat = responseFormat;
			return this;
		}

		public Builder withSpeed(Float speed) {
			options.speed = speed;
			return this;
		}

		public OpenAiAudioSpeechOptions build() {
			return options;
		}

	}

	public String getModel() {
		return model;
	}

	public String getInput() {
		return input;
	}

	public Voice getVoice() {
		return voice;
	}

	public AudioResponseFormat getResponseFormat() {
		return responseFormat;
	}

	public Float getSpeed() {
		return speed;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((input == null) ? 0 : input.hashCode());
		result = prime * result + ((voice == null) ? 0 : voice.hashCode());
		result = prime * result + ((responseFormat == null) ? 0 : responseFormat.hashCode());
		result = prime * result + ((speed == null) ? 0 : speed.hashCode());
		return result;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public void setVoice(Voice voice) {
		this.voice = voice;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public void setSpeed(Float speed) {
		this.speed = speed;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OpenAiAudioSpeechOptions other = (OpenAiAudioSpeechOptions) obj;
		if (model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (input == null) {
			if (other.input != null)
				return false;
		}
		else if (!input.equals(other.input))
			return false;
		if (voice == null) {
			if (other.voice != null)
				return false;
		}
		else if (!voice.equals(other.voice))
			return false;
		if (responseFormat == null) {
			if (other.responseFormat != null)
				return false;
		}
		else if (!responseFormat.equals(other.responseFormat))
			return false;
		if (speed == null) {
			return other.speed == null;
		}
		else
			return speed.equals(other.speed);
	}

	@Override
	public String toString() {
		return "OpenAiAudioSpeechOptions{" + "model='" + model + '\'' + ", input='" + input + '\'' + ", voice='" + voice
				+ '\'' + ", responseFormat='" + responseFormat + '\'' + ", speed=" + speed + '}';
	}

}
