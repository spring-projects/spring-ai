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

package org.springframework.ai.vertexai.gemini;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.GeminiRequest;
import org.springframework.ai.vertexai.gemini.tool.MockWeatherService;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
public class CreateGeminiRequestTests {

	@Mock
	VertexAI vertexAI;

	@Test
	public void createRequestWithChatOptions() {

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.defaultOptions(VertexAiGeminiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build())
			.build();

		GeminiRequest request = client.createGeminiRequest(client
			.buildRequestPrompt(new Prompt("Test message content", VertexAiGeminiChatOptions.builder().build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(66.6f);

		request = client.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("PROMPT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(99.9f);
	}

	@Test
	public void createRequestWithFrequencyAndPresencePenalty() {

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.defaultOptions(VertexAiGeminiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.frequencePenalty(.25)
				.presencePenalty(.75)
				.build())
			.build();

		GeminiRequest request = client.createGeminiRequest(client
			.buildRequestPrompt(new Prompt("Test message content", VertexAiGeminiChatOptions.builder().build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getGenerationConfig().getFrequencyPenalty()).isEqualTo(.25F);
		assertThat(request.model().getGenerationConfig().getPresencePenalty()).isEqualTo(.75F);
	}

	@Test
	public void createRequestWithSystemMessage() throws MalformedURLException {

		var systemMessage = new SystemMessage("System Message Text");

		var userMessage = UserMessage.builder()
			.text("User Message Text")
			.media(List
				.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("http://example.com")).build()))
			.build();

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.defaultOptions(VertexAiGeminiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt(List.of(systemMessage, userMessage))));

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

		var toolCallingManager = ToolCallingManager.builder().build();

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.defaultOptions(VertexAiGeminiChatOptions.builder().model("DEFAULT_MODEL").build())
			.toolCallingManager(toolCallingManager)
			.build();

		var requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder()
					.model("PROMPT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()));

		var request = client.createGeminiRequest(requestPrompt);

		List<ToolDefinition> toolDefinitions = toolCallingManager
			.resolveToolDefinitions((ToolCallingChatOptions) requestPrompt.getOptions());

		assertThat(toolDefinitions).hasSize(1);
		assertThat(toolDefinitions.get(0).name()).isSameAs(TOOL_FUNCTION_NAME);

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

		var toolCallingManager = ToolCallingManager.builder().build();

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.toolCallingManager(toolCallingManager)
			.defaultOptions(VertexAiGeminiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build())
			.build();

		var requestPrompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createGeminiRequest(requestPrompt);

		List<ToolDefinition> toolDefinitions = toolCallingManager
			.resolveToolDefinitions((ToolCallingChatOptions) requestPrompt.getOptions());

		assertThat(toolDefinitions).hasSize(1);
		assertThat(toolDefinitions.get(0).name()).isSameAs(TOOL_FUNCTION_NAME);
		assertThat(toolDefinitions.get(0).description()).isEqualTo("Get the weather in location");

		assertThat(request.contents()).hasSize(1);
		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");

		assertThat(request.model().getTools()).hasSize(1);

		// Explicitly enable the function

		requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder().toolName(TOOL_FUNCTION_NAME).build()));

		request = client.createGeminiRequest(requestPrompt);

		assertThat(request.model().getTools()).hasSize(1);
		assertThat(request.model().getTools().get(0).getFunctionDeclarations(0).getName())
			.as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		// Override the default options function with one from the prompt
		requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				VertexAiGeminiChatOptions.builder()
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Overridden function description")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()));
		request = client.createGeminiRequest(requestPrompt);

		assertThat(request.model().getTools()).hasSize(1);
		assertThat(request.model().getTools().get(0).getFunctionDeclarations(0).getName())
			.as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		toolDefinitions = toolCallingManager
			.resolveToolDefinitions((ToolCallingChatOptions) requestPrompt.getOptions());

		assertThat(toolDefinitions).hasSize(1);
		assertThat(toolDefinitions.get(0).name()).isSameAs(TOOL_FUNCTION_NAME);
		assertThat(toolDefinitions.get(0).description()).isEqualTo("Overridden function description");
	}

	@Test
	public void createRequestWithGenerationConfigOptions() {

		var client = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAI)
			.defaultOptions(VertexAiGeminiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.temperature(66.6)
				.maxOutputTokens(100)
				.topK(10)
				.topP(5.0)
				.stopSequences(List.of("stop1", "stop2"))
				.candidateCount(1)
				.responseMimeType("application/json")
				.build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.model().getSystemInstruction()).isNotPresent();
		assertThat(request.model().getModelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.model().getGenerationConfig().getTemperature()).isEqualTo(66.6f);
		assertThat(request.model().getGenerationConfig().getMaxOutputTokens()).isEqualTo(100);
		assertThat(request.model().getGenerationConfig().getTopK()).isEqualTo(10);
		assertThat(request.model().getGenerationConfig().getTopP()).isEqualTo(5.0f);
		assertThat(request.model().getGenerationConfig().getCandidateCount()).isEqualTo(1);
		assertThat(request.model().getGenerationConfig().getStopSequences(0)).isEqualTo("stop1");
		assertThat(request.model().getGenerationConfig().getStopSequences(1)).isEqualTo("stop2");
		assertThat(request.model().getGenerationConfig().getResponseMimeType()).isEqualTo("application/json");
	}

}
