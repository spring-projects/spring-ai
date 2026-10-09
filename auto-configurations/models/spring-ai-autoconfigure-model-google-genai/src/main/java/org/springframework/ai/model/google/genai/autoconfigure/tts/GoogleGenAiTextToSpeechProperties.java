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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.AudioEncoding;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Google GenAI Gemini-TTS text-to-speech model.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
@ConfigurationProperties(GoogleGenAiTextToSpeechProperties.CONFIG_PREFIX)
public class GoogleGenAiTextToSpeechProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.tts";

	/**
	 * The Gemini-TTS model to use (maps to {@code voice.modelName}).
	 */
	private @Nullable String model = GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL;

	/**
	 * The name of the prebuilt voice to use for single-speaker synthesis (maps to
	 * {@code speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName}). Mutually exclusive
	 * with {@code speakerVoiceConfigs}.
	 */
	private @Nullable String voiceName;

	/**
	 * The language code (ISO 639-1) for the speech synthesis (maps to
	 * {@code speechConfig.languageCode}).
	 */
	private @Nullable String languageCode;

	/**
	 * Optional natural-language styling instructions describing how the text should be
	 * spoken (prepended to the synthesized text).
	 */
	private @Nullable String stylePrompt;

	/**
	 * The speaker-to-voice mappings for multi-speaker synthesis (maps to
	 * {@code speechConfig.multiSpeakerVoiceConfig.speakerVoiceConfigs}). Exactly two
	 * speakers must be provided. Mutually exclusive with {@code voiceName}.
	 */
	private @Nullable List<SpeakerVoiceConfig> speakerVoiceConfigs;

	/**
	 * The multi-speaker turns to be synthesized, rendered as {@code "Speaker: text"}
	 * lines. When set, this takes precedence over the plain text carried by the
	 * {@code TextToSpeechPrompt}.
	 */
	private @Nullable List<MultiSpeakerTurn> multiSpeakerTurns;

	/**
	 * The format of the audio byte stream: raw PCM or WAV-wrapped PCM.
	 */
	private @Nullable AudioEncoding audioEncoding;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getVoiceName() {
		return this.voiceName;
	}

	public void setVoiceName(@Nullable String voiceName) {
		this.voiceName = voiceName;
	}

	public @Nullable String getLanguageCode() {
		return this.languageCode;
	}

	public void setLanguageCode(@Nullable String languageCode) {
		this.languageCode = languageCode;
	}

	public @Nullable String getStylePrompt() {
		return this.stylePrompt;
	}

	public void setStylePrompt(@Nullable String stylePrompt) {
		this.stylePrompt = stylePrompt;
	}

	public @Nullable List<SpeakerVoiceConfig> getSpeakerVoiceConfigs() {
		return this.speakerVoiceConfigs;
	}

	public void setSpeakerVoiceConfigs(@Nullable List<SpeakerVoiceConfig> speakerVoiceConfigs) {
		this.speakerVoiceConfigs = speakerVoiceConfigs;
	}

	public @Nullable List<MultiSpeakerTurn> getMultiSpeakerTurns() {
		return this.multiSpeakerTurns;
	}

	public void setMultiSpeakerTurns(@Nullable List<MultiSpeakerTurn> multiSpeakerTurns) {
		this.multiSpeakerTurns = multiSpeakerTurns;
	}

	public @Nullable AudioEncoding getAudioEncoding() {
		return this.audioEncoding;
	}

	public void setAudioEncoding(@Nullable AudioEncoding audioEncoding) {
		this.audioEncoding = audioEncoding;
	}

	public GoogleGenAiAudioSpeechOptions toOptions() {
		return GoogleGenAiAudioSpeechOptions.builder()
			.model(this.model)
			.voiceName(this.voiceName)
			.languageCode(this.languageCode)
			.stylePrompt(this.stylePrompt)
			.speakerVoiceConfigs(this.speakerVoiceConfigs)
			.multiSpeakerTurns(this.multiSpeakerTurns)
			.audioEncoding(this.audioEncoding)
			.build();
	}

}
