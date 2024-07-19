/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.GeminiRequest;
import org.springframework.util.MimeTypeUtils;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import org.springframework.ai.vertexai.gemini.function.MockWeatherService;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class CreateGeminiRequestTests {

	@Mock
	VertexAI vertexAI;

	@Test
	public void createRequestWithChatOptions() {

		var client = new VertexAiGeminiChatModel(vertexAI,
				VertexAiGeminiChatOptions.builder().withModel("DEFAULT_MODEL").withTemperature(66.6f).build());

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content"));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(66.6f);

		request = client.createGeminiRequest(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder().withModel("PROMPT_MODEL").withTemperature(99.9f).build()));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("PROMPT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(99.9f);
	}

	@Test
	public void createRequestWithSystemMessage() throws MalformedURLException {

		var systemMessage = new SystemMessage("System Message Text");

		var userMessage = new UserMessage("User Message Text",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG, new URL("http://example.com"))));

		var client = new VertexAiGeminiChatModel(vertexAI,
				VertexAiGeminiChatOptions.builder().withModel("DEFAULT_MODEL").withTemperature(66.6f).build());

		GeminiRequest request = client.createGeminiRequest(new Prompt(List.of(systemMessage, userMessage)));

		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(66.6f);

		assertThat(request.model().getSystemInstruction()).isPresent();
		assertThat(request.model().getSystemInstruction().get().getParts(0).getText()).isEqualTo("System Message Text");

		assertThat(request.contents()).hasSize(1);
		Content content = request.contents().get(0);

		Part textPart = content.getParts(0);
		assertThat(textPart.getText()).isEqualTo("User Message Text");

		Part mediaPart = content.getParts(1);
		assertThat(mediaPart.getFileData()).isNotNull();
		assertThat(mediaPart.getFileData().getFileUri()).isEqualTo("http://example.com");
		assertThat(mediaPart.getFileData().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG.toString());
		System.out.println(mediaPart);
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new VertexAiGeminiChatModel(vertexAI,
				VertexAiGeminiChatOptions.builder().withModel("DEFAULT_MODEL").build());

		var request = client.createGeminiRequest(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder()
					.withModel("PROMPT_MODEL")
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName(TOOL_FUNCTION_NAME)
						.withDescription("Get the weather in location")
						.withResponseConverter((response) -> "" + response.temp() + response.unit())
						.build()))
					.build()));

		assertThat(client.getFunctionCallbackRegister()).hasSize(1);
		assertThat(client.getFunctionCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);

		assertThat(request.contents()).hasSize(1);
		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("PROMPT_MODEL");

		assertThat(request.model().getTools()).hasSize(1);
		assertThat(request.model().getTools().get(0).getFunctionDeclarations(0).getName())
			.isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void defaultOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new VertexAiGeminiChatModel(vertexAI,
				VertexAiGeminiChatOptions.builder()
					.withModel("DEFAULT_MODEL")
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName(TOOL_FUNCTION_NAME)
						.withDescription("Get the weather in location")
						.withResponseConverter((response) -> "" + response.temp() + response.unit())
						.build()))
					.build());

		var request = client.createGeminiRequest(new Prompt("Test message content"));

		assertThat(client.getFunctionCallbackRegister()).hasSize(1);
		assertThat(client.getFunctionCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);
		assertThat(client.getFunctionCallbackRegister().get(TOOL_FUNCTION_NAME).getDescription())
			.isEqualTo("Get the weather in location");

		assertThat(request.contents()).hasSize(1);
		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");

		assertThat(request.model().getTools()).as("Default Options callback functions are not automatically enabled!")
			.isNullOrEmpty();

		// Explicitly enable the function
		request = client.createGeminiRequest(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder().withFunction(TOOL_FUNCTION_NAME).build()));

		assertThat(request.model().getTools()).hasSize(1);
		assertThat(request.model().getTools().get(0).getFunctionDeclarations(0).getName())
			.as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		// Override the default options function with one from the prompt
		request = client.createGeminiRequest(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder()
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName(TOOL_FUNCTION_NAME)
						.withDescription("Overridden function description")
						.build()))
					.build()));

		assertThat(request.model().getTools()).hasSize(1);
		assertThat(request.model().getTools().get(0).getFunctionDeclarations(0).getName())
			.as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		assertThat(client.getFunctionCallbackRegister()).hasSize(1);
		assertThat(client.getFunctionCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);
		assertThat(client.getFunctionCallbackRegister().get(TOOL_FUNCTION_NAME).getDescription())
			.isEqualTo("Overridden function description");
	}

}
