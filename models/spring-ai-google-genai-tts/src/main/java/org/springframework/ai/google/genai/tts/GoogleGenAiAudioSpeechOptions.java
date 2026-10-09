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
 * Options for Google Gemini-TTS text-to-speech, backed by the Google GenAI SDK
 * ({@code com.google.genai}).
 * <p>
 * These options map to the {@code speechConfig} field of a {@code GenerateContentConfig}
 * requested with the {@code AUDIO} response modality: {@link #getModel() model} selects
 * the Gemini-TTS model (for example {@code gemini-2.5-flash-tts}), {@link #getVoiceName()
 * voiceName} maps to {@code speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName},
 * {@link #getReplicatedVoice() replicatedVoice} maps to
 * {@code speechConfig.voiceConfig.replicatedVoiceConfig}, {@link #getLanguageCode()
 * languageCode} to {@code speechConfig.languageCode} and {@link #getSpeakerVoiceConfigs()
 * speakerVoiceConfigs} to
 * {@code speechConfig.multiSpeakerVoiceConfig.speakerVoiceConfigs}.
 * <p>
 * The Gemini-TTS models also accept natural-language steering: {@link #getStylePrompt()
 * stylePrompt} is prepended to the synthesized text, and {@link #getMultiSpeakerTurns()
 * multiSpeakerTurns} is rendered as {@code "Speaker: text"} lines matching the speaker
 * names of {@link #getSpeakerVoiceConfigs()}.
 * <p>
 * The Gemini API returns 24 kHz, 16-bit, mono PCM audio. {@link #getAudioEncoding()
 * audioEncoding} only selects whether the raw PCM bytes ({@link AudioEncoding#PCM}) or a
 * WAV-wrapped version ({@link AudioEncoding#LINEAR16}) are returned.
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
	 * The default prebuilt voice.
	 */
	public static final String DEFAULT_VOICE = "Kore";

	/**
	 * The default audio encoding used for the synthesized audio.
	 */
	public static final AudioEncoding DEFAULT_AUDIO_ENCODING = AudioEncoding.LINEAR16;

	/**
	 * The sample rate (in hertz) of the audio returned by the Gemini-TTS models.
	 */
	public static final int OUTPUT_SAMPLE_RATE_HERTZ = 24000;

	/**
	 * The Gemini-TTS model to use (for example {@code gemini-2.5-flash-tts} or
	 * {@code gemini-2.5-pro-tts}).
	 */
	private @Nullable String model;

	/**
	 * The name of the prebuilt voice to use for single-speaker synthesis (maps to
	 * {@code speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName}). Mutually exclusive
	 * with {@link #replicatedVoice} and {@link #speakerVoiceConfigs}.
	 */
	private @Nullable String voiceName;

	/**
	 * A cloned voice to use for single-speaker synthesis (maps to
	 * {@code speechConfig.voiceConfig.replicatedVoiceConfig}). Mutually exclusive with
	 * {@link #voiceName} and {@link #speakerVoiceConfigs}.
	 */
	private @Nullable ReplicatedVoice replicatedVoice;

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
	 * speakers must be provided. Mutually exclusive with {@link #voiceName} and
	 * {@link #replicatedVoice}.
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

	public @Nullable ReplicatedVoice getReplicatedVoice() {
		return this.replicatedVoice;
	}

	public void setReplicatedVoice(@Nullable ReplicatedVoice replicatedVoice) {
		this.replicatedVoice = replicatedVoice;
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

	/**
	 * The Gemini-TTS models do not expose a speaking-rate parameter; pacing is controlled
	 * through the {@link #getStylePrompt() style prompt}.
	 * @return always {@code null}
	 */
	@Override
	public @Nullable Double getSpeed() {
		return null;
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
				&& Objects.equals(this.replicatedVoice, that.replicatedVoice)
				&& Objects.equals(this.languageCode, that.languageCode)
				&& Objects.equals(this.stylePrompt, that.stylePrompt)
				&& Objects.equals(this.speakerVoiceConfigs, that.speakerVoiceConfigs)
				&& Objects.equals(this.multiSpeakerTurns, that.multiSpeakerTurns)
				&& this.audioEncoding == that.audioEncoding;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.voiceName, this.replicatedVoice, this.languageCode, this.stylePrompt,
				this.speakerVoiceConfigs, this.multiSpeakerTurns, this.audioEncoding);
	}

	@Override
	public String toString() {
		return "GoogleGenAiAudioSpeechOptions{" + "model='" + this.model + '\'' + ", voiceName='" + this.voiceName
				+ '\'' + ", replicatedVoice=" + this.replicatedVoice + ", languageCode='" + this.languageCode + '\''
				+ ", stylePrompt='" + this.stylePrompt + '\'' + ", speakerVoiceConfigs=" + this.speakerVoiceConfigs
				+ ", multiSpeakerTurns=" + this.multiSpeakerTurns + ", audioEncoding=" + this.audioEncoding + '}';
	}

	/**
	 * A single speaker-to-voice mapping for multi-speaker synthesis.
	 *
	 * @param speaker the speaker label used in the text (for example {@code "Joe"}). Must
	 * match the speaker names of the {@link MultiSpeakerTurn turns}.
	 * @param voiceName the prebuilt voice to use for that speaker (for example
	 * {@code "Kore"})
	 */
	public record SpeakerVoiceConfig(String speaker, String voiceName) {
	}

	/**
	 * A single speaker turn used for multi-speaker synthesis input.
	 *
	 * @param speaker the speaker of the turn, for example {@code "Joe"}
	 * @param text the text to speak for that turn
	 */
	public record MultiSpeakerTurn(String speaker, String text) {
	}

	/**
	 * A cloned voice sample used to synthesize speech with a replicated voice (maps to
	 * {@code speechConfig.voiceConfig.replicatedVoiceConfig}).
	 *
	 * @param voiceSampleAudio the sample of the custom voice, 16-bit signed little-endian
	 * WAV data with a 24 kHz sampling rate
	 * @param consentAudio recorded consent verifying ownership of the voice, in the same
	 * format as {@code voiceSampleAudio}
	 * @param mimeType the mimetype of the voice sample; only {@code audio/wav} is
	 * currently supported. Defaults to {@code audio/wav} when {@code null}
	 * @param consentSignature signature of a previously verified consent audio, used
	 * instead of {@code consentAudio} to reduce latency
	 */
	public record ReplicatedVoice(byte[] voiceSampleAudio, byte @Nullable [] consentAudio, @Nullable String mimeType,
			@Nullable String consentSignature) {

		public ReplicatedVoice {
			Objects.requireNonNull(voiceSampleAudio, "voiceSampleAudio must not be null");
		}

	}

	/**
	 * The format of the audio byte stream returned by the synthesis. The Gemini-TTS
	 * models always produce 24 kHz, 16-bit, mono PCM audio; the encoding only controls
	 * whether a WAV header is added.
	 */
	public enum AudioEncoding {

		/**
		 * Raw 24 kHz, 16-bit, mono PCM, exactly as returned by the API.
		 */
		PCM,

		/**
		 * The 24 kHz, 16-bit, mono PCM audio wrapped in a WAV header.
		 */
		LINEAR16

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

		public Builder replicatedVoice(@Nullable ReplicatedVoice replicatedVoice) {
			this.options.setReplicatedVoice(replicatedVoice);
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

		public Builder multiSpeakerTurns(@Nullable List<MultiSpeakerTurn> multiSpeakerTurns) {
			this.options.setMultiSpeakerTurns(multiSpeakerTurns);
			return this;
		}

		public Builder audioEncoding(@Nullable AudioEncoding audioEncoding) {
			this.options.setAudioEncoding(audioEncoding);
			return this;
		}

		public GoogleGenAiAudioSpeechOptions build() {
			return this.options;
		}

	}

}
