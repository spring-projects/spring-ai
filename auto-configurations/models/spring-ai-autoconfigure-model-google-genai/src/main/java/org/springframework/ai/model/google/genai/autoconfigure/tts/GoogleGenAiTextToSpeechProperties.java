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
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.CustomPronunciation;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SsmlVoiceGender;
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
	 * The name of the voice to use for single-speaker synthesis (maps to
	 * {@code voice.name}). Mutually exclusive with {@code speakerVoiceConfigs}.
	 */
	private @Nullable String voiceName;

	/**
	 * The BCP-47 language/locale code for the speech synthesis (maps to
	 * {@code voice.languageCode}).
	 */
	private @Nullable String languageCode;

	/**
	 * Optional natural-language styling instructions describing how the text should be
	 * spoken (maps to {@code input.prompt}).
	 */
	private @Nullable String stylePrompt;

	/**
	 * The speaker-alias-to-voice mappings for multi-speaker synthesis (maps to
	 * {@code voice.multiSpeakerVoiceConfig.speakerVoiceConfigs}). Mutually exclusive with
	 * {@code voiceName}.
	 */
	private @Nullable List<SpeakerVoiceConfig> speakerVoiceConfigs;

	/**
	 * The SSML document to be synthesized (maps to {@code input.ssml}). Mutually
	 * exclusive with {@code markup}.
	 */
	private @Nullable String ssml;

	/**
	 * Markup for Chirp 3: HD voices (maps to {@code input.markup}). Mutually exclusive
	 * with {@code ssml}.
	 */
	private @Nullable String markup;

	/**
	 * Pronunciation customizations applied to the input (maps to
	 * {@code input.customPronunciations}).
	 */
	private @Nullable List<CustomPronunciation> customPronunciations;

	/**
	 * The preferred gender of the voice (maps to {@code voice.ssmlGender}).
	 */
	private @Nullable SsmlVoiceGender ssmlGender;

	/**
	 * The name of the AutoML model that synthesizes the custom voice (maps to
	 * {@code voice.customVoice.model}).
	 */
	private @Nullable String customVoiceModel;

	/**
	 * The voice cloning key created by {@code GenerateVoiceCloningKey} (maps to
	 * {@code voice.voiceClone.voiceCloningKey}).
	 */
	private @Nullable String voiceCloningKey;

	/**
	 * The format of the audio byte stream (maps to {@code audioConfig.audioEncoding}).
	 */
	private @Nullable AudioEncoding audioEncoding;

	/**
	 * Speaking rate/speed, in the range [0.25, 2.0] (maps to
	 * {@code audioConfig.speakingRate}).
	 */
	private @Nullable Double speed;

	/**
	 * Speaking pitch, in the range [-20.0, 20.0] (maps to {@code audioConfig.pitch}).
	 */
	private @Nullable Double pitch;

	/**
	 * Volume gain (in dB), in the range [-96.0, 16.0] (maps to
	 * {@code audioConfig.volumeGainDb}).
	 */
	private @Nullable Double volumeGainDb;

	/**
	 * The synthesis sample rate (in hertz) for the audio (maps to
	 * {@code audioConfig.sampleRateHertz}).
	 */
	private @Nullable Integer sampleRateHertz;

	/**
	 * Identifiers which select 'audio effects' profiles applied on the synthesized audio
	 * (maps to {@code audioConfig.effectsProfileId}).
	 */
	private @Nullable List<String> effectsProfileIds;

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

	public @Nullable String getSsml() {
		return this.ssml;
	}

	public void setSsml(@Nullable String ssml) {
		this.ssml = ssml;
	}

	public @Nullable String getMarkup() {
		return this.markup;
	}

	public void setMarkup(@Nullable String markup) {
		this.markup = markup;
	}

	public @Nullable List<CustomPronunciation> getCustomPronunciations() {
		return this.customPronunciations;
	}

	public void setCustomPronunciations(@Nullable List<CustomPronunciation> customPronunciations) {
		this.customPronunciations = customPronunciations;
	}

	public @Nullable SsmlVoiceGender getSsmlGender() {
		return this.ssmlGender;
	}

	public void setSsmlGender(@Nullable SsmlVoiceGender ssmlGender) {
		this.ssmlGender = ssmlGender;
	}

	public @Nullable String getCustomVoiceModel() {
		return this.customVoiceModel;
	}

	public void setCustomVoiceModel(@Nullable String customVoiceModel) {
		this.customVoiceModel = customVoiceModel;
	}

	public @Nullable String getVoiceCloningKey() {
		return this.voiceCloningKey;
	}

	public void setVoiceCloningKey(@Nullable String voiceCloningKey) {
		this.voiceCloningKey = voiceCloningKey;
	}

	public @Nullable AudioEncoding getAudioEncoding() {
		return this.audioEncoding;
	}

	public void setAudioEncoding(@Nullable AudioEncoding audioEncoding) {
		this.audioEncoding = audioEncoding;
	}

	public @Nullable Double getSpeed() {
		return this.speed;
	}

	public void setSpeed(@Nullable Double speed) {
		this.speed = speed;
	}

	public @Nullable Double getPitch() {
		return this.pitch;
	}

	public void setPitch(@Nullable Double pitch) {
		this.pitch = pitch;
	}

	public @Nullable Double getVolumeGainDb() {
		return this.volumeGainDb;
	}

	public void setVolumeGainDb(@Nullable Double volumeGainDb) {
		this.volumeGainDb = volumeGainDb;
	}

	public @Nullable Integer getSampleRateHertz() {
		return this.sampleRateHertz;
	}

	public void setSampleRateHertz(@Nullable Integer sampleRateHertz) {
		this.sampleRateHertz = sampleRateHertz;
	}

	public @Nullable List<String> getEffectsProfileIds() {
		return this.effectsProfileIds;
	}

	public void setEffectsProfileIds(@Nullable List<String> effectsProfileIds) {
		this.effectsProfileIds = effectsProfileIds;
	}

	public GoogleGenAiAudioSpeechOptions toOptions() {
		return GoogleGenAiAudioSpeechOptions.builder()
			.model(this.model)
			.voiceName(this.voiceName)
			.languageCode(this.languageCode)
			.stylePrompt(this.stylePrompt)
			.speakerVoiceConfigs(this.speakerVoiceConfigs)
			.ssml(this.ssml)
			.markup(this.markup)
			.customPronunciations(this.customPronunciations)
			.ssmlGender(this.ssmlGender)
			.customVoiceModel(this.customVoiceModel)
			.voiceCloningKey(this.voiceCloningKey)
			.audioEncoding(this.audioEncoding)
			.speed(this.speed)
			.pitch(this.pitch)
			.volumeGainDb(this.volumeGainDb)
			.sampleRateHertz(this.sampleRateHertz)
			.effectsProfileIds(this.effectsProfileIds)
			.build();
	}

}
