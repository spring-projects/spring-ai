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

package org.springframework.ai.google.genai.tts;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechOptions;

/**
 * Options for Google Gemini-TTS text-to-speech, backed by the Cloud Text-to-Speech V2
 * API.
 * <p>
 * These options map to the fields of a Cloud Text-to-Speech
 * {@code SynthesizeSpeechRequest}: {@link #getModel() model} maps to
 * {@code voice.modelName}, {@link #getVoiceName() voiceName} to {@code voice.name},
 * {@link #getLanguageCode() languageCode} to {@code voice.languageCode},
 * {@link #getStylePrompt() stylePrompt} to {@code input.prompt},
 * {@link #getSpeakerVoiceConfigs() speakerVoiceConfigs} to
 * {@code voice.multiSpeakerVoiceConfig.speakerVoiceConfigs}, {@link #getSsml()
 * ssml}/{@link #getMarkup() markup}/{@link #getMultiSpeakerTurns() multiSpeakerTurns} to
 * {@code input.ssml}/{@code input.markup}/ {@code input.multiSpeakerMarkup.turns},
 * {@link #getCustomPronunciations() customPronunciations} to
 * {@code input.customPronunciations}, {@link #getSsmlGender() ssmlGender} to
 * {@code voice.ssmlGender}, {@link #getCustomVoiceModel() customVoiceModel} to
 * {@code voice.customVoice.model}, {@link #getVoiceCloningKey() voiceCloningKey} to
 * {@code voice.voiceClone.voiceCloningKey}, {@link #getAudioEncoding() audioEncoding} to
 * {@code audioConfig.audioEncoding}, {@link #getSpeed() speed} to
 * {@code audioConfig.speakingRate}, {@link #getPitch() pitch} to
 * {@code audioConfig.pitch}, {@link #getVolumeGainDb() volumeGainDb} to
 * {@code audioConfig.volumeGainDb}, {@link #getSampleRateHertz() sampleRateHertz} to
 * {@code audioConfig.sampleRateHertz} and {@link #getEffectsProfileIds()
 * effectsProfileIds} to {@code audioConfig.effectsProfileId}.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class GoogleGenAiAudioSpeechOptions implements TextToSpeechOptions {

	/**
	 * The default Gemini-TTS model.
	 */
	public static final String DEFAULT_MODEL = "gemini-2.5-flash-tts";

	/**
	 * The default audio encoding used for the synthesized audio.
	 */
	public static final AudioEncoding DEFAULT_AUDIO_ENCODING = AudioEncoding.LINEAR16;

	/**
	 * The Gemini-TTS model to use (maps to {@code voice.modelName}).
	 */
	private @Nullable String model;

	/**
	 * The name of the voice to use for single-speaker synthesis (maps to
	 * {@code voice.name}). Mutually exclusive with {@link #speakerVoiceConfigs}.
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
	 * {@link #voiceName}.
	 */
	private @Nullable List<SpeakerVoiceConfig> speakerVoiceConfigs;

	/**
	 * The SSML document to be synthesized (maps to {@code input.ssml}). Mutually
	 * exclusive with {@link #markup}; when set, this takes precedence over the plain text
	 * carried by the {@code TextToSpeechPrompt}.
	 */
	private @Nullable String ssml;

	/**
	 * Markup for Chirp 3: HD voices (maps to {@code input.markup}). Mutually exclusive
	 * with {@link #ssml}; when set, this takes precedence over the plain text carried by
	 * the {@code TextToSpeechPrompt}.
	 */
	private @Nullable String markup;

	/**
	 * The multi-speaker turns to be synthesized (maps to
	 * {@code input.multiSpeakerMarkup.turns}). Mutually exclusive with {@link #ssml} and
	 * {@link #markup}; when set, this takes precedence over the plain text carried by the
	 * {@code TextToSpeechPrompt}.
	 */
	private @Nullable List<MultiSpeakerTurn> multiSpeakerTurns;

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

	public GoogleGenAiAudioSpeechOptions() {
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
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

	@Override
	public @Nullable String getVoice() {
		return this.voiceName;
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

	public @Nullable List<MultiSpeakerTurn> getMultiSpeakerTurns() {
		return this.multiSpeakerTurns;
	}

	public void setMultiSpeakerTurns(@Nullable List<MultiSpeakerTurn> multiSpeakerTurns) {
		this.multiSpeakerTurns = multiSpeakerTurns;
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

	/**
	 * Speaking rate/speed, in the range [0.25, 2.0] (maps to
	 * {@code audioConfig.speakingRate}). 1.0 is the normal native speed supported by the
	 * specific voice.
	 * @return the speaking rate, or {@code null} to use the native voice speed
	 */
	@Override
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

	/**
	 * The output format maps to {@link #getAudioEncoding() audioEncoding}.
	 * @return the name of the configured {@link AudioEncoding}, or {@code null} if unset
	 */
	@Override
	public @Nullable String getFormat() {
		return this.audioEncoding != null ? this.audioEncoding.name() : null;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GoogleGenAiAudioSpeechOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.voiceName, that.voiceName)
				&& Objects.equals(this.languageCode, that.languageCode)
				&& Objects.equals(this.stylePrompt, that.stylePrompt)
				&& Objects.equals(this.speakerVoiceConfigs, that.speakerVoiceConfigs)
				&& Objects.equals(this.ssml, that.ssml) && Objects.equals(this.markup, that.markup)
				&& Objects.equals(this.multiSpeakerTurns, that.multiSpeakerTurns)
				&& Objects.equals(this.customPronunciations, that.customPronunciations)
				&& this.ssmlGender == that.ssmlGender && Objects.equals(this.customVoiceModel, that.customVoiceModel)
				&& Objects.equals(this.voiceCloningKey, that.voiceCloningKey)
				&& this.audioEncoding == that.audioEncoding && Objects.equals(this.speed, that.speed)
				&& Objects.equals(this.pitch, that.pitch) && Objects.equals(this.volumeGainDb, that.volumeGainDb)
				&& Objects.equals(this.sampleRateHertz, that.sampleRateHertz)
				&& Objects.equals(this.effectsProfileIds, that.effectsProfileIds);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.voiceName, this.languageCode, this.stylePrompt, this.speakerVoiceConfigs,
				this.ssml, this.markup, this.multiSpeakerTurns, this.customPronunciations, this.ssmlGender,
				this.customVoiceModel, this.voiceCloningKey, this.audioEncoding, this.speed, this.pitch,
				this.volumeGainDb, this.sampleRateHertz, this.effectsProfileIds);
	}

	@Override
	public String toString() {
		return "GoogleGenAiAudioSpeechOptions{" + "model='" + this.model + '\'' + ", voiceName='" + this.voiceName
				+ '\'' + ", languageCode='" + this.languageCode + '\'' + ", stylePrompt='" + this.stylePrompt + '\''
				+ ", speakerVoiceConfigs=" + this.speakerVoiceConfigs + ", ssml='" + this.ssml + '\'' + ", markup='"
				+ this.markup + '\'' + ", multiSpeakerTurns=" + this.multiSpeakerTurns + ", customPronunciations="
				+ this.customPronunciations + ", ssmlGender=" + this.ssmlGender + ", customVoiceModel='"
				+ this.customVoiceModel + '\'' + ", voiceCloningKey='" + this.voiceCloningKey + '\''
				+ ", audioEncoding=" + this.audioEncoding + ", speed=" + this.speed + ", pitch=" + this.pitch
				+ ", volumeGainDb=" + this.volumeGainDb + ", sampleRateHertz=" + this.sampleRateHertz
				+ ", effectsProfileIds=" + this.effectsProfileIds + '}';
	}

	/**
	 * A single speaker-alias-to-voice mapping for multi-speaker synthesis.
	 *
	 * @param speakerAlias the speaker label used in the text (for example {@code "Sam"})
	 * @param speakerId the voice name to use for that speaker (for example
	 * {@code "Kore"})
	 */
	public record SpeakerVoiceConfig(String speakerAlias, String speakerId) {
	}

	/**
	 * A single speaker turn used for multi-speaker synthesis input (maps to
	 * {@code input.multiSpeakerMarkup.turns}).
	 *
	 * @param speaker the speaker of the turn, for example {@code "O"} or {@code "Q"}
	 * @param text the text to speak for that turn
	 */
	public record MultiSpeakerTurn(String speaker, String text) {
	}

	/**
	 * A single pronunciation customization applied to the input.
	 *
	 * @param phrase the phrase to which the customization is applied
	 * @param phoneticEncoding the phonetic encoding of {@code pronunciation}
	 * @param pronunciation the pronunciation of the phrase, in the given
	 * {@code phoneticEncoding}
	 */
	public record CustomPronunciation(String phrase, PhoneticEncoding phoneticEncoding, String pronunciation) {
	}

	/**
	 * The phonetic encoding of a {@link CustomPronunciation}.
	 */
	public enum PhoneticEncoding {

		/**
		 * Not specified.
		 */
		UNSPECIFIED,

		/**
		 * IPA, such as apple -&gt; ˈæpəl.
		 */
		IPA,

		/**
		 * X-SAMPA, such as apple -&gt; "{p@l".
		 */
		X_SAMPA,

		/**
		 * Reading-to-pronunciation for Japanese, expressed in Kanji, Hiragana and
		 * Katakana.
		 */
		JAPANESE_YOMIGANA,

		/**
		 * Pinyin, used to specify pronunciations for Mandarin words.
		 */
		PINYIN

	}

	/**
	 * The preferred gender of the voice.
	 */
	public enum SsmlVoiceGender {

		/**
		 * The client doesn't care which gender the selected voice will have.
		 */
		UNSPECIFIED,

		/**
		 * A male voice.
		 */
		MALE,

		/**
		 * A female voice.
		 */
		FEMALE,

		/**
		 * A gender-neutral voice.
		 */
		NEUTRAL

	}

	/**
	 * The format of the audio byte stream returned by the synthesis.
	 */
	public enum AudioEncoding {

		/**
		 * Uncompressed 16-bit signed little-endian samples (Linear PCM), wrapped in a WAV
		 * header.
		 */
		LINEAR16,

		/**
		 * MP3 audio at 32kbps.
		 */
		MP3,

		/**
		 * Opus encoded audio wrapped in an ogg container.
		 */
		OGG_OPUS,

		/**
		 * 8-bit samples that compand 14-bit audio samples using G.711 PCMU/mu-law,
		 * wrapped in a WAV header.
		 */
		MULAW,

		/**
		 * 8-bit samples that compand 14-bit audio samples using G.711 PCMU/A-law, wrapped
		 * in a WAV header.
		 */
		ALAW,

		/**
		 * Uncompressed 16-bit signed little-endian samples (Linear PCM), without a WAV
		 * (or any other) header.
		 */
		PCM,

		/**
		 * M4A audio.
		 */
		M4A

	}

	public static final class Builder {

		private final GoogleGenAiAudioSpeechOptions options = new GoogleGenAiAudioSpeechOptions();

		private Builder() {
		}

		public Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder voiceName(@Nullable String voiceName) {
			this.options.setVoiceName(voiceName);
			return this;
		}

		public Builder languageCode(@Nullable String languageCode) {
			this.options.setLanguageCode(languageCode);
			return this;
		}

		public Builder stylePrompt(@Nullable String stylePrompt) {
			this.options.setStylePrompt(stylePrompt);
			return this;
		}

		public Builder speakerVoiceConfigs(@Nullable List<SpeakerVoiceConfig> speakerVoiceConfigs) {
			this.options.setSpeakerVoiceConfigs(speakerVoiceConfigs);
			return this;
		}

		public Builder ssml(@Nullable String ssml) {
			this.options.setSsml(ssml);
			return this;
		}

		public Builder markup(@Nullable String markup) {
			this.options.setMarkup(markup);
			return this;
		}

		public Builder multiSpeakerTurns(@Nullable List<MultiSpeakerTurn> multiSpeakerTurns) {
			this.options.setMultiSpeakerTurns(multiSpeakerTurns);
			return this;
		}

		public Builder customPronunciations(@Nullable List<CustomPronunciation> customPronunciations) {
			this.options.setCustomPronunciations(customPronunciations);
			return this;
		}

		public Builder ssmlGender(@Nullable SsmlVoiceGender ssmlGender) {
			this.options.setSsmlGender(ssmlGender);
			return this;
		}

		public Builder customVoiceModel(@Nullable String customVoiceModel) {
			this.options.setCustomVoiceModel(customVoiceModel);
			return this;
		}

		public Builder voiceCloningKey(@Nullable String voiceCloningKey) {
			this.options.setVoiceCloningKey(voiceCloningKey);
			return this;
		}

		public Builder audioEncoding(@Nullable AudioEncoding audioEncoding) {
			this.options.setAudioEncoding(audioEncoding);
			return this;
		}

		public Builder speed(@Nullable Double speed) {
			this.options.setSpeed(speed);
			return this;
		}

		public Builder pitch(@Nullable Double pitch) {
			this.options.setPitch(pitch);
			return this;
		}

		public Builder volumeGainDb(@Nullable Double volumeGainDb) {
			this.options.setVolumeGainDb(volumeGainDb);
			return this;
		}

		public Builder sampleRateHertz(@Nullable Integer sampleRateHertz) {
			this.options.setSampleRateHertz(sampleRateHertz);
			return this;
		}

		public Builder effectsProfileIds(@Nullable List<String> effectsProfileIds) {
			this.options.setEffectsProfileIds(effectsProfileIds);
			return this;
		}

		public GoogleGenAiAudioSpeechOptions build() {
			return this.options;
		}

	}

}
