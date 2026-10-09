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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.ReplicatedVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;
import com.google.genai.types.VoiceConsentSignature;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.TextToSpeechResponseMetadata;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Implementation of the {@link TextToSpeechModel} interface for Google Gemini-TTS, backed
 * by the Google GenAI SDK ({@code com.google.genai}).
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class GoogleGenAiTextToSpeechModel implements TextToSpeechModel {

	private static final TextToSpeechModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTextToSpeechModelObservationConvention();

	private static final String AUDIO_RESPONSE_MODALITY = "AUDIO";

	private static final String REPLICATED_VOICE_DEFAULT_MIME_TYPE = "audio/wav";

	private final GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

	private final GoogleGenAiAudioSpeechOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private TextToSpeechModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "connectionDetails must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.connectionDetails = connectionDetails;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		final TextToSpeechPrompt textToSpeechPrompt = buildTextToSpeechPrompt(prompt);

		final TextToSpeechModelObservationContext observationContext = TextToSpeechModelObservationContext.builder()
			.textToSpeechPrompt(textToSpeechPrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				final GoogleGenAiAudioSpeechOptions options = (GoogleGenAiAudioSpeechOptions) textToSpeechPrompt
					.getOptions();
				Assert.notNull(options, "Options must not be null");

				final String text = textToSpeechPrompt.getInstructions().getText();
				Assert.hasText(text, "TextToSpeechPrompt must contain non-empty text");

				final String model = StringUtils.hasText(options.getModel()) ? options.getModel()
						: GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL;

				final Content content = Content.fromParts(Part.fromText(buildInputText(text, options)));
				final GenerateContentConfig config = createGenerateContentConfig(options);

				final GenerateContentResponse response = RetryUtils.execute(this.retryTemplate,
						() -> this.connectionDetails.getGenAiClient().models.generateContent(model, content, config));

				final byte[] pcmAudio = extractAudio(response);
				final byte[] audioContent = (options
					.getAudioEncoding() == GoogleGenAiAudioSpeechOptions.AudioEncoding.PCM) ? pcmAudio
							: toWav(pcmAudio);

				final TextToSpeechResponseMetadata metadata = new TextToSpeechResponseMetadata();
				metadata.setUsage(new DefaultUsage(text.length(), audioContent.length));

				final TextToSpeechResponse textToSpeechResponse = new TextToSpeechResponse(
						List.of(new Speech(audioContent)), metadata);

				observationContext.setResponse(textToSpeechResponse);

				return textToSpeechResponse;
			});
	}

	public void setObservationConvention(@Nullable TextToSpeechModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		return Flux.just(call(prompt));
	}

	private static byte[] extractAudio(GenerateContentResponse response) {
		return response.candidates()
			.flatMap(candidates -> candidates.stream().findFirst())
			.flatMap(candidate -> candidate.content())
			.flatMap(content -> content.parts())
			.flatMap(parts -> parts.stream().filter(part -> part.inlineData().isPresent()).findFirst())
			.flatMap(part -> part.inlineData())
			.flatMap(blob -> blob.data())
			.orElseThrow(() -> new IllegalStateException("Gemini-TTS response contains no audio data"));
	}

	private String buildInputText(String text, GoogleGenAiAudioSpeechOptions options) {
		final StringBuilder builder = new StringBuilder();
		if (StringUtils.hasText(options.getStylePrompt())) {
			builder.append(options.getStylePrompt()).append(": ");
		}
		if (!CollectionUtils.isEmpty(options.getMultiSpeakerTurns())) {
			options.getMultiSpeakerTurns()
				.forEach(turn -> builder.append(turn.speaker()).append(": ").append(turn.text()).append('\n'));
		}
		else {
			builder.append(text);
		}
		return builder.toString();
	}

	private GenerateContentConfig createGenerateContentConfig(GoogleGenAiAudioSpeechOptions options) {
		final SpeechConfig.Builder speechConfigBuilder = SpeechConfig.builder();
		if (StringUtils.hasText(options.getLanguageCode())) {
			speechConfigBuilder.languageCode(options.getLanguageCode());
		}
		if (!CollectionUtils.isEmpty(options.getSpeakerVoiceConfigs())) {
			speechConfigBuilder.multiSpeakerVoiceConfig(com.google.genai.types.MultiSpeakerVoiceConfig.builder()
				.speakerVoiceConfigs(options.getSpeakerVoiceConfigs()
					.stream()
					.map(config -> com.google.genai.types.SpeakerVoiceConfig.builder()
						.speaker(config.speaker())
						.voiceConfig(createPrebuiltVoiceConfig(config.voiceName()))
						.build())
					.toList()));
		}
		else if (Objects.nonNull(options.getReplicatedVoice())) {
			speechConfigBuilder.voiceConfig(VoiceConfig.builder()
				.replicatedVoiceConfig(createReplicatedVoiceConfig(options.getReplicatedVoice())));
		}
		else {
			final String voiceName = StringUtils.hasText(options.getVoiceName()) ? options.getVoiceName()
					: GoogleGenAiAudioSpeechOptions.DEFAULT_VOICE;
			speechConfigBuilder.voiceConfig(createPrebuiltVoiceConfig(voiceName));
		}
		return GenerateContentConfig.builder()
			.responseModalities(AUDIO_RESPONSE_MODALITY)
			.speechConfig(speechConfigBuilder)
			.build();
	}

	private static VoiceConfig createPrebuiltVoiceConfig(String voiceName) {
		return VoiceConfig.builder()
			.prebuiltVoiceConfig(PrebuiltVoiceConfig.builder().voiceName(voiceName).build())
			.build();
	}

	private static ReplicatedVoiceConfig createReplicatedVoiceConfig(
			GoogleGenAiAudioSpeechOptions.ReplicatedVoice replicatedVoice) {
		final ReplicatedVoiceConfig.Builder builder = ReplicatedVoiceConfig.builder()
			.mimeType(StringUtils.hasText(replicatedVoice.mimeType()) ? replicatedVoice.mimeType()
					: REPLICATED_VOICE_DEFAULT_MIME_TYPE)
			.voiceSampleAudio(replicatedVoice.voiceSampleAudio());
		if (Objects.nonNull(replicatedVoice.consentAudio())) {
			builder.consentAudio(replicatedVoice.consentAudio());
		}
		if (StringUtils.hasText(replicatedVoice.consentSignature())) {
			builder.voiceConsentSignature(
					VoiceConsentSignature.builder().signature(replicatedVoice.consentSignature()).build());
		}
		return builder.build();
	}

	/**
	 * Wraps 24 kHz, 16-bit, mono PCM data in a WAV (RIFF) header.
	 * @param pcm the raw PCM audio
	 * @return the WAV-encoded audio
	 */
	static byte[] toWav(byte[] pcm) {
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
			out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
			out.write(intToLittleEndian(36 + pcm.length));
			out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
			out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
			out.write(intToLittleEndian(16)); // PCM header size
			out.write(shortToLittleEndian((short) 1)); // PCM format
			out.write(shortToLittleEndian((short) 1)); // mono
			out.write(intToLittleEndian(GoogleGenAiAudioSpeechOptions.OUTPUT_SAMPLE_RATE_HERTZ));
			out.write(intToLittleEndian(GoogleGenAiAudioSpeechOptions.OUTPUT_SAMPLE_RATE_HERTZ * 2)); // byte
																										// rate
			out.write(shortToLittleEndian((short) 2)); // block align
			out.write(shortToLittleEndian((short) 16)); // bits per sample
			out.write("data".getBytes(StandardCharsets.US_ASCII));
			out.write(intToLittleEndian(pcm.length));
			out.write(pcm);
			return out.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Failed to wrap PCM audio in a WAV header", exception);
		}
	}

	private static byte[] intToLittleEndian(int value) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
	}

	private static byte[] shortToLittleEndian(short value) {
		return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
	}

	private TextToSpeechPrompt buildTextToSpeechPrompt(TextToSpeechPrompt textToSpeechPrompt) {
		GoogleGenAiAudioSpeechOptions mergedOptions = this.defaultOptions;

		final TextToSpeechOptions requestOptions = textToSpeechPrompt.getOptions();
		if (Objects.nonNull(requestOptions)) {
			final GoogleGenAiAudioSpeechOptions.Builder builder = GoogleGenAiAudioSpeechOptions.builder()
				.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
				.voiceName(
						ModelOptionsUtils.mergeOption(requestOptions.getVoice(), this.defaultOptions.getVoiceName()));

			if (requestOptions instanceof GoogleGenAiAudioSpeechOptions googleOptions) {
				builder
					.replicatedVoice(ModelOptionsUtils.mergeOption(googleOptions.getReplicatedVoice(),
							this.defaultOptions.getReplicatedVoice()))
					.languageCode(ModelOptionsUtils.mergeOption(googleOptions.getLanguageCode(),
							this.defaultOptions.getLanguageCode()))
					.stylePrompt(ModelOptionsUtils.mergeOption(googleOptions.getStylePrompt(),
							this.defaultOptions.getStylePrompt()))
					.speakerVoiceConfigs(ModelOptionsUtils.mergeOption(googleOptions.getSpeakerVoiceConfigs(),
							this.defaultOptions.getSpeakerVoiceConfigs()))
					.multiSpeakerTurns(ModelOptionsUtils.mergeOption(googleOptions.getMultiSpeakerTurns(),
							this.defaultOptions.getMultiSpeakerTurns()))
					.audioEncoding(ModelOptionsUtils.mergeOption(googleOptions.getAudioEncoding(),
							this.defaultOptions.getAudioEncoding()));
			}

			mergedOptions = builder.build();
		}

		return new TextToSpeechPrompt(textToSpeechPrompt.getInstructions(), mergedOptions);
	}

	@Override
	public GoogleGenAiAudioSpeechOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public GoogleGenAiAudioSpeechOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public static final class Builder {

		private @Nullable GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

		private GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL)
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder connectionDetails(GoogleGenAiTextToSpeechConnectionDetails connectionDetails) {
			this.connectionDetails = connectionDetails;
			return this;
		}

		public Builder options(GoogleGenAiAudioSpeechOptions options) {
			this.options = options;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public GoogleGenAiTextToSpeechModel build() {
			Assert.notNull(this.connectionDetails, "connectionDetails must not be null");
			return new GoogleGenAiTextToSpeechModel(this.connectionDetails, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
