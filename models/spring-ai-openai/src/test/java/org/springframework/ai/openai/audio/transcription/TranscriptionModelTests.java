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

package org.springframework.ai.openai.audio.transcription;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit Tests for {@link TranscriptionModel}.
 *
 * @author Michael Lavelle
 */
class TranscriptionModelTests {

	@Test
	void transcribeRequestReturnsResponseCorrectly() {

		Resource mockAudioFile = Mockito.mock(Resource.class);

		OpenAiAudioTranscriptionModel mockClient = Mockito.mock(OpenAiAudioTranscriptionModel.class);

		String mockTranscription = "All your bases are belong to us";

		// Create a mock Transcript
		AudioTranscription transcript = Mockito.mock(AudioTranscription.class);
		given(transcript.getOutput()).willReturn(mockTranscription);

		// Create a mock TranscriptionResponse with the mock Transcript
		AudioTranscriptionResponse response = Mockito.mock(AudioTranscriptionResponse.class);
		given(response.getResult()).willReturn(transcript);

		// Transcript transcript = spy(new Transcript(responseMessage));
		// TranscriptionResponse response = spy(new
		// TranscriptionResponse(Collections.singletonList(transcript)));

		doCallRealMethod().when(mockClient).call(any(Resource.class));

		given(mockClient.call(any(AudioTranscriptionPrompt.class))).will(invocation -> {
			AudioTranscriptionPrompt transcriptionRequest = invocation.getArgument(0);

			assertThat(transcriptionRequest).isNotNull();
			assertThat(transcriptionRequest.getInstructions()).isEqualTo(mockAudioFile);

			return response;
		});

		assertThat(mockClient.call(mockAudioFile)).isEqualTo(mockTranscription);

		verify(mockClient, times(1)).call(eq(mockAudioFile));
		verify(mockClient, times(1)).call(isA(AudioTranscriptionPrompt.class));
		verify(response, times(1)).getResult();
		verify(transcript, times(1)).getOutput();
		verifyNoMoreInteractions(mockClient, transcript, response);
	}

}
