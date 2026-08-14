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

package org.springframework.ai.openai.transcription;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.openai.models.audio.AudioModel;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.metadata.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiAudioTranscriptionModel}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author guan xu
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioTranscriptionModelIT {

	@Autowired
	private OpenAiAudioTranscriptionModel transcriptionModel;

	@Test
	void callTest() {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void transcribeTest() {
		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"));

		assertThat(text).isNotBlank();
	}

	@Test
	void transcribeWithOptionsTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.TEXT)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void transcribeWithVerboseFormatTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.responseFormat(AudioResponseFormat.VERBOSE_JSON)
			.build();

		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isNotBlank();
	}

	@Test
	void transcribeWithVerboseFormatExposesUsageAndSegments() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.responseFormat(AudioResponseFormat.VERBOSE_JSON)
			.timestampGranularities(List.of(TranscriptionCreateParams.TimestampGranularity.WORD,
					TranscriptionCreateParams.TimestampGranularity.SEGMENT))
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isNotBlank();
		OpenAiAudioTranscriptionResponseMetadata metadata = (OpenAiAudioTranscriptionResponseMetadata) response
			.getMetadata();
		assertThat(metadata.getDuration()).isPositive();
		assertThat(metadata.getLanguage()).isNotBlank();
		assertThat(metadata.getUsage()).isNotNull();
		assertThat(metadata.getSegments()).isNotEmpty();
		assertThat(metadata.getWords()).isNotEmpty();
	}

	@Test
	void transcribeWithDiarizedFormatExposesSpeakerSegments() {
		// The OpenAI Java SDK (4.42.0) misclassifies diarized_json
		// responses as a plain Transcription instead of TranscriptionDiarized, because
		// the real API response omits the "duration" field the SDK treats as required
		// (see https://github.com/openai/openai-java/issues/802). Spring AI works
		// around this (DiarizedJsonMisclassificationRecovery, enabled by default) by
		// recovering the clean text, speaker segments and usage from the raw JSON
		// payload the SDK would otherwise surface as-is. Duration itself stays
		// unavailable since the API never sends it for this format.
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_TRANSCRIBE_DIARIZE.asString())
			.responseFormat(AudioResponseFormat.DIARIZED_JSON)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isNotBlank();
		assertThat(response.getResult().getOutput()).doesNotStartWith("{");
		OpenAiAudioTranscriptionResponseMetadata metadata = (OpenAiAudioTranscriptionResponseMetadata) response
			.getMetadata();
		assertThat(metadata.getDuration()).isNull();
		assertThat(metadata.getSegments()).isNotEmpty();
		assertThat(metadata.getUsage()).isNotNull();
	}

	@Test
	void transcribeWithDiarizedFormatAndWorkaroundDisabledReturnsRawJson() {
		// With the workaround explicitly turned off, the SDK's broken raw-JSON
		// fallback text passes through unchanged.
		// TODO: remove after the https://github.com/openai/openai-java/issues/802 is
		// fixed.
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_TRANSCRIBE_DIARIZE.asString())
			.responseFormat(AudioResponseFormat.DIARIZED_JSON)
			.diarizedJsonWorkaroundEnabled(false)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).startsWith("{");
	}

	@Test
	void transcribeWithChunkingStrategyAutoSucceeds() {
		// chunking_strategy is only accepted by the streaming-capable gpt-4o-transcribe
		// family, not by whisper-1.
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_MINI_TRANSCRIBE.asString())
			.chunkingStrategy(TranscriptionCreateParams.ChunkingStrategy.ofAuto())
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void transcribeWithKnownSpeakerNamesAndReferences() {
		// known_speaker_names/known_speaker_references let callers help the
		// gpt-4o-transcribe-diarize model attach a real name to a speaker instead of a
		// generic "A"/"B" label.
		// The API rejects multipart fields over 1024KB once base64-encoded, so
		// a trimmed ~3s clip is used instead.
		String speakerReference = "data:audio/flac;base64," + base64Encode("/speech-speaker-reference.flac");

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_TRANSCRIBE_DIARIZE.asString())
			.responseFormat(AudioResponseFormat.DIARIZED_JSON)
			.knownSpeakerNames(List.of("JFK"))
			.knownSpeakerReferences(List.of(speakerReference))
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isNotBlank();
		OpenAiAudioTranscriptionResponseMetadata metadata = (OpenAiAudioTranscriptionResponseMetadata) response
			.getMetadata();
		assertThat(metadata.getSegments()).isNotEmpty();
	}

	private static String base64Encode(String classpathResource) {
		try {
			return Base64.getEncoder()
				.encodeToString(new ClassPathResource(classpathResource).getInputStream().readAllBytes());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Test
	void transcribeTestWithOptions() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.prompt("Ask not this, but ask that")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.TEXT)
			.build();

		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isNotBlank();
	}

	@Test
	void callTestWithVttFormat() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.prompt("Ask not this, but ask that")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.VTT)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void streamTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_MINI_TRANSCRIBE.asString())
			.build();
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		Flux<AudioTranscriptionResponse> flux = this.transcriptionModel.stream(prompt);

		List<AudioTranscriptionResponse> chunks = flux.collectList().block();

		assertThat(chunks).isNotNull();
		String text = chunks.stream()
			.map(AudioTranscriptionResponse::getResult)
			.filter(Objects::nonNull)
			.map(AudioTranscription::getOutput)
			.collect(Collectors.joining());
		assertThat(text).isNotBlank();
	}

	@Test
	void streamTranscribeWithResourceTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_MINI_TRANSCRIBE.asString())
			.build();
		Flux<String> flux = this.transcriptionModel.streamTranscribe(new ClassPathResource("/speech.flac"), options);

		List<String> chunks = flux.collectList().block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isNotBlank();
	}

	@Test
	void streamTranscribeWithResourceAndOptionsTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model(AudioModel.GPT_4O_MINI_TRANSCRIBE.asString())
			.language("en")
			.temperature(0f)
			.build();

		Flux<String> flux = this.transcriptionModel.streamTranscribe(new ClassPathResource("/speech.flac"), options);

		List<String> chunks = flux.collectList().block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isNotBlank();
	}

}
