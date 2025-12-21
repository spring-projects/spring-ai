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

package org.springframework.ai.google.genai.tts;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;

/**
 * Options for Google Gemini Text-to-Speech.
 *
 * Supports both single-speaker and multi-speaker configurations. For single-speaker, use
 * {@link #getVoice()}. For multi-speaker, use {@link #getSpeakerVoiceConfigs()}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiTtsOptions implements TextToSpeechOptions {

	@JsonProperty("model")
	private String model;

	@JsonProperty("voice")
	private String voice;

	@JsonProperty("speaker_voice_configs")
	private List<GeminiTtsApi.SpeakerVoiceConfig> speakerVoiceConfigs;

	@JsonProperty("speed")
	private Double speed;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return this.model;
	}

	@JsonIgnore
	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public String getVoice() {
		return this.voice;
	}

	@JsonIgnore
	public void setVoice(String voice) {
		this.voice = voice;
	}

	/**
	 * Get the multi-speaker voice configurations.
	 * @return List of speaker voice configurations, or null for single-speaker
	 */
	public List<GeminiTtsApi.SpeakerVoiceConfig> getSpeakerVoiceConfigs() {
		return this.speakerVoiceConfigs;
	}

	public void setSpeakerVoiceConfigs(List<GeminiTtsApi.SpeakerVoiceConfig> speakerVoiceConfigs) {
		this.speakerVoiceConfigs = speakerVoiceConfigs;
	}

	@Override
	@JsonIgnore
	public String getFormat() {
		// Gemini TTS always returns PCM audio
		return "pcm";
	}

	@Override
	@JsonIgnore
	public Double getSpeed() {
		return this.speed;
	}

	@JsonIgnore
	public void setSpeed(Double speed) {
		this.speed = speed;
	}

	@Override
	@SuppressWarnings("unchecked")
	public GeminiTtsOptions copy() {
		return GeminiTtsOptions.builder()
			.model(this.model)
			.voice(this.voice)
			.speakerVoiceConfigs(this.speakerVoiceConfigs != null ? List.copyOf(this.speakerVoiceConfigs) : null)
			.speed(this.speed)
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GeminiTtsOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.voice, that.voice)
				&& Objects.equals(this.speakerVoiceConfigs, that.speakerVoiceConfigs)
				&& Objects.equals(this.speed, that.speed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.voice, this.speakerVoiceConfigs, this.speed);
	}

	@Override
	public String toString() {
		return "GeminiTtsOptions{" + "model='" + this.model + '\'' + ", voice='" + this.voice + '\''
				+ ", speakerVoiceConfigs=" + this.speakerVoiceConfigs + ", speed=" + this.speed + '}';
	}

	public static final class Builder {

		private final GeminiTtsOptions options = new GeminiTtsOptions();

		/**
		 * Sets the model name for text-to-speech generation.
		 * @param model The model name (e.g., "gemini-2.5-flash-preview-tts",
		 * "gemini-2.5-pro-preview-tts")
		 * @return this builder
		 */
		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		/**
		 * Sets the voice name for single-speaker text-to-speech. Mutually exclusive with
		 * {@link #speakerVoiceConfigs(List)}.
		 * @param voice The voice name (e.g., "Kore", "Puck", "Zephyr", "Charon")
		 * @return this builder
		 */
		public Builder voice(String voice) {
			this.options.setVoice(voice);
			return this;
		}

		/**
		 * Sets the speaker voice configurations for multi-speaker text-to-speech.
		 * Mutually exclusive with {@link #voice(String)}. Supports up to 2 speakers.
		 * @param speakerVoiceConfigs List of speaker voice configurations
		 * @return this builder
		 */
		public Builder speakerVoiceConfigs(List<GeminiTtsApi.SpeakerVoiceConfig> speakerVoiceConfigs) {
			this.options.setSpeakerVoiceConfigs(speakerVoiceConfigs);
			return this;
		}

		/**
		 * Sets the speech speed/rate. Note: Gemini TTS controls pace via text prompts
		 * (e.g., "Speak at a faster pace"), not via this parameter. This field exists for
		 * {@link TextToSpeechOptions} interface compatibility but is not sent to the API.
		 * @param speed The speed value (currently not used by Gemini API)
		 * @return this builder
		 */
		public Builder speed(Double speed) {
			this.options.setSpeed(speed);
			return this;
		}

		/**
		 * Builds the GeminiTtsOptions instance.
		 * @return the configured GeminiTtsOptions
		 */
		public GeminiTtsOptions build() {
			return this.options;
		}

	}

}
