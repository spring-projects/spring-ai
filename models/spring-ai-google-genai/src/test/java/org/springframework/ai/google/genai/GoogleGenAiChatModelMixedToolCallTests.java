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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleGenAiChatModel handling of mixed content responses containing both text
 * (or thinking) parts and function call parts.
 *
 * @author Vedant Madane
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5466">Issue
 * #5466</a>
 */
public class GoogleGenAiChatModelMixedToolCallTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
			.model("gemini-3-flash-preview")
			.temperature(0.7)
			.build();

		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, defaultOptions, retryTemplate);
	}

	@Test
	void toolCallsDetectedWhenMixedWithTextParts() {
		Part thoughtPart = Part.builder().text("Planning to check the weather in Tokyo").build();

		Part functionCallPart = Part.builder()
			.functionCall(FunctionCall.builder()
				.name("getCurrentWeather")
				.args(Map.of("location", "Tokyo", "unit", "C"))
				.build())
			.build();

		Content responseContent = Content.builder().parts(List.of(thoughtPart, functionCallPart)).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(GenerateContentResponseUsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.build())
			.modelVersion("gemini-3-flash-preview")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("What's the weather?"))));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		Generation generation = response.getResults().get(0);
		AssistantMessage assistantMessage = generation.getOutput();

		assertThat(assistantMessage.getToolCalls()).hasSize(1);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("getCurrentWeather");
		assertThat(assistantMessage.getToolCalls().get(0).arguments()).contains("Tokyo");
		assertThat(assistantMessage.getText()).contains("Planning to check the weather in Tokyo");
	}

	@Test
	void toolCallsDetectedWhenMixedWithMultipleFunctionCalls() {
		Part thoughtPart = Part.builder().text("I need to check weather in both cities").build();

		Part functionCallPart1 = Part.builder()
			.functionCall(FunctionCall.builder()
				.name("getCurrentWeather")
				.args(Map.of("location", "Tokyo", "unit", "C"))
				.build())
			.build();

		Part functionCallPart2 = Part.builder()
			.functionCall(FunctionCall.builder()
				.name("getCurrentWeather")
				.args(Map.of("location", "London", "unit", "C"))
				.build())
			.build();

		Content responseContent = Content.builder()
			.parts(List.of(thoughtPart, functionCallPart1, functionCallPart2))
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(GenerateContentResponseUsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(75)
				.totalTokenCount(175)
				.build())
			.modelVersion("gemini-3-flash-preview")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("Weather in Tokyo and London?"))));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		AssistantMessage assistantMessage = response.getResults().get(0).getOutput();

		assertThat(assistantMessage.getToolCalls()).hasSize(2);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("getCurrentWeather");
		assertThat(assistantMessage.getToolCalls().get(1).name()).isEqualTo("getCurrentWeather");
		assertThat(assistantMessage.getText()).contains("I need to check weather in both cities");
	}

	@Test
	void pureTextResponseStillWorks() {
		Content responseContent = Content.builder()
			.parts(Part.builder().text("The weather in Tokyo is sunny and 25 degrees.").build())
			.build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(GenerateContentResponseUsageMetadata.builder()
				.promptTokenCount(50)
				.candidatesTokenCount(30)
				.totalTokenCount(80)
				.build())
			.modelVersion("gemini-3-flash-preview")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("What's the weather?"))));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		AssistantMessage assistantMessage = response.getResults().get(0).getOutput();
		assertThat(assistantMessage.getToolCalls()).isEmpty();
		assertThat(assistantMessage.getText()).isEqualTo("The weather in Tokyo is sunny and 25 degrees.");
	}

	@Test
	void pureFunctionCallResponseStillWorks() {
		Part functionCallPart = Part.builder()
			.functionCall(FunctionCall.builder()
				.name("getCurrentWeather")
				.args(Map.of("location", "Tokyo", "unit", "C"))
				.build())
			.build();

		Content responseContent = Content.builder().parts(List.of(functionCallPart)).build();

		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(GenerateContentResponseUsageMetadata.builder()
				.promptTokenCount(50)
				.candidatesTokenCount(20)
				.totalTokenCount(70)
				.build())
			.modelVersion("gemini-3-flash-preview")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("What's the weather?"))));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		AssistantMessage assistantMessage = response.getResults().get(0).getOutput();
		assertThat(assistantMessage.getToolCalls()).hasSize(1);
		assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("getCurrentWeather");
	}

}
