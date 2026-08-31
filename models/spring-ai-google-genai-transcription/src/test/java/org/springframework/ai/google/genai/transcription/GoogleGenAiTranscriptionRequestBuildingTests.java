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

package org.springframework.ai.google.genai.transcription;

import java.io.IOException;
import java.util.List;

import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests verifying that {@link GoogleGenAiAudioTranscriptionOptions} are correctly
 * translated into the underlying {@link RecognizeRequest}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionRequestBuildingTests {

	private SpeechClient mockSpeechClient;

	private final Resource audioResource = new ByteArrayResource("audio-bytes".getBytes());

	@BeforeEach
	void setUp() throws IOException {
		this.mockSpeechClient = mock(SpeechClient.class);
		given(this.mockSpeechClient.recognize(any(RecognizeRequest.class)))
			.willReturn(RecognizeResponse.getDefaultInstance());
	}

	@Test
	void denoiseAudioAndSnrThresholdAreSetOnDenoiserConfig() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.denoiseAudio(true)
			.snrThreshold(5.0f)
			.build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		assertThat(requestCaptor.getValue().getConfig().getDenoiserConfig().getDenoiseAudio()).isTrue();
		assertThat(requestCaptor.getValue().getConfig().getDenoiserConfig().getSnrThreshold()).isEqualTo(5.0f);
	}

	@Test
	void customPromptIsSetOnRecognitionFeatures() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_3")
			.customPrompt("Transcribe verbatim, including filler words.")
			.build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		assertThat(requestCaptor.getValue().getConfig().getFeatures().getCustomPromptConfig().getCustomPrompt())
			.isEqualTo("Transcribe verbatim, including filler words.");
	}

	@Test
	void noDenoiserConfigWhenNeitherOptionIsSet() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder().build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		assertThat(requestCaptor.getValue().getConfig().hasDenoiserConfig()).isFalse();
	}

	@Test
	void phraseHintsAreSetOnInlinePhraseSetAdaptation() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.phraseHints(List.of(GoogleGenAiAudioTranscriptionOptions.PhraseHint.of("Spring AI"),
					GoogleGenAiAudioTranscriptionOptions.PhraseHint.of("Chirp", 15.0f)))
			.phraseSetBoost(5.0f)
			.build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		com.google.cloud.speech.v2.PhraseSet phraseSet = requestCaptor.getValue()
			.getConfig()
			.getAdaptation()
			.getPhraseSets(0)
			.getInlinePhraseSet();
		assertThat(phraseSet.getBoost()).isEqualTo(5.0f);
		assertThat(phraseSet.getPhrasesList()).extracting(com.google.cloud.speech.v2.PhraseSet.Phrase::getValue)
			.containsExactly("Spring AI", "Chirp");
		assertThat(phraseSet.getPhrases(1).getBoost()).isEqualTo(15.0f);
	}

	@Test
	void noAdaptationWhenNoPhraseHintsAreSet() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder().build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		assertThat(requestCaptor.getValue().getConfig().hasAdaptation()).isFalse();
	}

	@Test
	void transcriptNormalizationEntriesAreSetOnRecognitionConfig() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.transcriptNormalizationEntries(List
				.of(GoogleGenAiAudioTranscriptionOptions.TranscriptNormalizationEntry.of("Mister", "Mr.", true)))
			.build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		com.google.cloud.speech.v2.TranscriptNormalization.Entry entry = requestCaptor.getValue()
			.getConfig()
			.getTranscriptNormalization()
			.getEntries(0);
		assertThat(entry.getSearch()).isEqualTo("Mister");
		assertThat(entry.getReplace()).isEqualTo("Mr.");
		assertThat(entry.getCaseSensitive()).isTrue();
	}

	@Test
	void noTranscriptNormalizationWhenNoEntriesAreSet() {
		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder().build();

		GoogleGenAiTranscriptionModel transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				options);
		transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource));

		ArgumentCaptor<RecognizeRequest> requestCaptor = ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(this.mockSpeechClient).recognize(requestCaptor.capture());

		assertThat(requestCaptor.getValue().getConfig().hasTranscriptNormalization()).isFalse();
	}

}
