/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.audio.tts;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit Tests for {@link TextToSpeechModel}.
 *
 * @author Mark Pollack
 * @since 1.1.0
 */
class TextToSpeechModelTests {

	@Test
	void callWithStringCallsCallWithPromptAndReturnsAudioCorrectly() {
		String inputText = "Hello, world!";
		byte[] expectedAudio = new byte[] { 1, 2, 3, 4, 5 };

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		Speech mockSpeech = Mockito.mock(Speech.class);
		given(mockSpeech.getOutput()).willReturn(expectedAudio);

		TextToSpeechResponse response = Mockito.mock(TextToSpeechResponse.class);
		given(response.getResult()).willReturn(mockSpeech);

		doCallRealMethod().when(mockModel).call(anyString());

		given(mockModel.call(any(TextToSpeechPrompt.class))).willAnswer(invocationOnMock -> {
			TextToSpeechPrompt prompt = invocationOnMock.getArgument(0);

			assertThat(prompt).isNotNull();
			assertThat(prompt.getInstructions().getText()).isEqualTo(inputText);

			return response;
		});

		byte[] actualAudio = mockModel.call(inputText);

		assertThat(actualAudio).isEqualTo(expectedAudio);

		verify(mockModel, times(1)).call(eq(inputText));
		verify(mockModel, times(1)).call(isA(TextToSpeechPrompt.class));
		verify(response, times(1)).getResult();
		verify(mockSpeech, times(1)).getOutput();
		verifyNoMoreInteractions(mockModel, mockSpeech, response);
	}

	@Test
	void callWithEmptyStringReturnsEmptyAudio() {
		String inputText = "";
		byte[] expectedAudio = new byte[0];

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		Speech mockSpeech = Mockito.mock(Speech.class);
		given(mockSpeech.getOutput()).willReturn(expectedAudio);

		TextToSpeechResponse response = Mockito.mock(TextToSpeechResponse.class);
		given(response.getResult()).willReturn(mockSpeech);

		doCallRealMethod().when(mockModel).call(anyString());
		given(mockModel.call(any(TextToSpeechPrompt.class))).willReturn(response);

		byte[] result = mockModel.call(inputText);

		assertThat(result).isEqualTo(expectedAudio);
		verify(mockModel, times(1)).call(eq(inputText));
		verify(mockModel, times(1)).call(isA(TextToSpeechPrompt.class));
	}

	@Test
	void callWhenPromptCallThrowsExceptionPropagatesCorrectly() {
		String inputText = "Test message";
		RuntimeException expectedException = new RuntimeException("API call failed");

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		doCallRealMethod().when(mockModel).call(anyString());
		given(mockModel.call(any(TextToSpeechPrompt.class))).willThrow(expectedException);

		assertThatThrownBy(() -> mockModel.call(inputText)).isEqualTo(expectedException);

		verify(mockModel, times(1)).call(eq(inputText));
		verify(mockModel, times(1)).call(isA(TextToSpeechPrompt.class));
	}

	@Test
	void callWhenResponseIsNullHandlesGracefully() {
		String inputText = "Test message";

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		doCallRealMethod().when(mockModel).call(anyString());
		given(mockModel.call(any(TextToSpeechPrompt.class))).willReturn(null);

		assertThatThrownBy(() -> mockModel.call(inputText)).isInstanceOf(NullPointerException.class);

		verify(mockModel, times(1)).call(eq(inputText));
		verify(mockModel, times(1)).call(isA(TextToSpeechPrompt.class));
	}

	@Test
	void callWhenSpeechIsNullReturnsEmptyArray() {
		String inputText = "Test message";

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		TextToSpeechResponse response = Mockito.mock(TextToSpeechResponse.class);
		given(response.getResult()).willReturn(null);

		doCallRealMethod().when(mockModel).call(anyString());
		given(mockModel.call(any(TextToSpeechPrompt.class))).willReturn(response);

		byte[] result = mockModel.call(inputText);

		assertThat(result).isEmpty();
		verify(mockModel, times(1)).call(eq(inputText));
		verify(response, times(1)).getResult();
	}

	@Test
	void callWhenAudioOutputIsNullReturnsEmptyArray() {
		String inputText = "Test message";

		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		Speech mockSpeech = Mockito.mock(Speech.class);
		given(mockSpeech.getOutput()).willReturn(null);

		TextToSpeechResponse response = Mockito.mock(TextToSpeechResponse.class);
		given(response.getResult()).willReturn(mockSpeech);

		doCallRealMethod().when(mockModel).call(anyString());
		given(mockModel.call(any(TextToSpeechPrompt.class))).willReturn(response);

		byte[] result = mockModel.call(inputText);

		assertThat(result).isEmpty();
		verify(mockModel, times(1)).call(eq(inputText));
		verify(mockSpeech, times(1)).getOutput();
	}

	@Test
	void callMultipleTimesWithSameModelMaintainsState() {
		TextToSpeechModel mockModel = Mockito.mock(TextToSpeechModel.class);

		doCallRealMethod().when(mockModel).call(anyString());

		// First call
		setupMockResponse(mockModel, new byte[] { 1, 2, 3 });
		byte[] result1 = mockModel.call("Message 1");
		assertThat(result1).isEqualTo(new byte[] { 1, 2, 3 });

		// Second call
		setupMockResponse(mockModel, new byte[] { 4, 5, 6 });
		byte[] result2 = mockModel.call("Message 2");
		assertThat(result2).isEqualTo(new byte[] { 4, 5, 6 });

		verify(mockModel, times(2)).call(anyString());
		verify(mockModel, times(2)).call(any(TextToSpeechPrompt.class));
	}

	private void setupMockResponse(TextToSpeechModel mockModel, byte[] audioOutput) {
		Speech mockSpeech = Mockito.mock(Speech.class);
		given(mockSpeech.getOutput()).willReturn(audioOutput);

		TextToSpeechResponse response = Mockito.mock(TextToSpeechResponse.class);
		given(response.getResult()).willReturn(mockSpeech);

		given(mockModel.call(any(TextToSpeechPrompt.class))).willReturn(response);
	}

}
