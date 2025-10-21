/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

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
 * Unit Tests for {@link ChatModel}.
 *
 * @author John Blum
 * @since 0.2.0
 */
class ChatModelTests {

	@Test
	void generateWithStringCallsGenerateWithPromptAndReturnsResponseCorrectly() {

		String userMessage = "Zero Wing";
		String responseMessage = "All your bases are belong to us";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(responseMessage);

		// Create a mock Generation
		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		// Create a mock ChatResponse with the mock Generation
		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		// Generation generation = spy(new Generation(responseMessage));
		// ChatResponse response = spy(new
		// ChatResponse(Collections.singletonList(generation)));

		doCallRealMethod().when(mockClient).call(anyString());

		given(mockClient.call(any(Prompt.class))).willAnswer(invocationOnMock -> {
			Prompt prompt = invocationOnMock.getArgument(0);

			assertThat(prompt).isNotNull();
			assertThat(prompt.getContents()).isEqualTo(userMessage);

			return response;
		});

		assertThat(mockClient.call(userMessage)).isEqualTo(responseMessage);

		verify(mockClient, times(1)).call(eq(userMessage));
		verify(mockClient, times(1)).call(isA(Prompt.class));
		verify(response, times(1)).getResult();
		verify(generation, times(1)).getOutput();
		verify(mockAssistantMessage, times(1)).getText();
		verifyNoMoreInteractions(mockClient, generation, response);
	}

	@Test
	void generateWithEmptyStringReturnsEmptyResponse() {
		String userMessage = "";
		String responseMessage = "";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(responseMessage);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(response);

		String result = mockClient.call(userMessage);

		assertThat(result).isEqualTo(responseMessage);
		verify(mockClient, times(1)).call(eq(userMessage));
		verify(mockClient, times(1)).call(isA(Prompt.class));
	}

	@Test
	void generateWithWhitespaceOnlyStringHandlesCorrectly() {
		String userMessage = "   \t\n   ";
		String responseMessage = "I received whitespace input";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(responseMessage);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(response);

		String result = mockClient.call(userMessage);

		assertThat(result).isEqualTo(responseMessage);
		verify(mockClient, times(1)).call(eq(userMessage));
	}

	@Test
	void generateWhenPromptCallThrowsExceptionPropagatesCorrectly() {
		String userMessage = "Test message";
		RuntimeException expectedException = new RuntimeException("API call failed");

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willThrow(expectedException);

		assertThatThrownBy(() -> mockClient.call(userMessage)).isEqualTo(expectedException);

		verify(mockClient, times(1)).call(eq(userMessage));
		verify(mockClient, times(1)).call(isA(Prompt.class));
	}

	@Test
	void generateWhenResponseIsNullHandlesGracefully() {
		String userMessage = "Test message";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(null);

		assertThatThrownBy(() -> mockClient.call(userMessage)).isInstanceOf(NullPointerException.class);

		verify(mockClient, times(1)).call(eq(userMessage));
		verify(mockClient, times(1)).call(isA(Prompt.class));
	}

	@Test
	void generateWhenAssistantMessageIsNullHandlesGracefully() {
		String userMessage = "Test message";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(null);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(response);

		assertThatThrownBy(() -> mockClient.call(userMessage)).isInstanceOf(NullPointerException.class);

		verify(mockClient, times(1)).call(eq(userMessage));
		verify(generation, times(1)).getOutput();
	}

	@Test
	void generateWhenAssistantMessageTextIsNullReturnsNull() {
		String userMessage = "Test message";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(null);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(response);

		String result = mockClient.call(userMessage);

		assertThat(result).isNull();
		verify(mockClient, times(1)).call(eq(userMessage));
		verify(mockAssistantMessage, times(1)).getText();
	}

	@Test
	void generateWithMultilineStringHandlesCorrectly() {
		String userMessage = "Line 1\nLine 2\r\nLine 3\rLine 4";
		String responseMessage = "Multiline input processed";

		ChatModel mockClient = Mockito.mock(ChatModel.class);

		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(responseMessage);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		doCallRealMethod().when(mockClient).call(anyString());
		given(mockClient.call(any(Prompt.class))).willReturn(response);

		String result = mockClient.call(userMessage);

		assertThat(result).isEqualTo(responseMessage);
		verify(mockClient, times(1)).call(eq(userMessage));
	}

	@Test
	void generateMultipleTimesWithSameClientMaintainsState() {
		ChatModel mockClient = Mockito.mock(ChatModel.class);

		doCallRealMethod().when(mockClient).call(anyString());

		// First call
		setupMockResponse(mockClient, "Response 1");
		String result1 = mockClient.call("Message 1");
		assertThat(result1).isEqualTo("Response 1");

		// Second call
		setupMockResponse(mockClient, "Response 2");
		String result2 = mockClient.call("Message 2");
		assertThat(result2).isEqualTo("Response 2");

		verify(mockClient, times(2)).call(anyString());
		verify(mockClient, times(2)).call(any(Prompt.class));
	}

	private void setupMockResponse(ChatModel mockClient, String responseText) {
		AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
		given(mockAssistantMessage.getText()).willReturn(responseText);

		Generation generation = Mockito.mock(Generation.class);
		given(generation.getOutput()).willReturn(mockAssistantMessage);

		ChatResponse response = Mockito.mock(ChatResponse.class);
		given(response.getResult()).willReturn(generation);

		given(mockClient.call(any(Prompt.class))).willReturn(response);
	}

}
