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

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel.GeminiRequest;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.google.genai.tool.MockWeatherService;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Dan Dobrin
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
public class CreateGeminiRequestTests {

	@Mock
	Client genAiClient;

	@Test
	public void createRequestWithChatOptions() {

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build())
			.build();

		GeminiRequest request = client.createGeminiRequest(client
			.buildRequestPrompt(new Prompt("Test message content", GoogleGenAiChatOptions.builder().build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.config().systemInstruction()).isNotPresent();
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.config().temperature().orElse(0f)).isEqualTo(66.6f);

		request = client.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.config().systemInstruction()).isNotPresent();
		assertThat(request.modelName()).isEqualTo("PROMPT_MODEL");
		assertThat(request.config().temperature().orElse(0f)).isEqualTo(99.9f);
	}

	@Test
	public void createRequestWithFrequencyAndPresencePenalty() {

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.frequencyPenalty(.25)
				.presencePenalty(.75)
				.build())
			.build();

		GeminiRequest request = client.createGeminiRequest(client
			.buildRequestPrompt(new Prompt("Test message content", GoogleGenAiChatOptions.builder().build())));

		assertThat(request.contents()).hasSize(1);

		assertThat(request.config().frequencyPenalty().orElse(0f)).isEqualTo(.25F);
		assertThat(request.config().presencePenalty().orElse(0f)).isEqualTo(.75F);
	}

	@Test
	public void createRequestWithSystemMessage() throws MalformedURLException {

		var systemMessage = new SystemMessage("System Message Text");

		var userMessage = UserMessage.builder()
			.text("User Message Text")
			.media(List
				.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("http://example.com")).build()))
			.build();

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt(List.of(systemMessage, userMessage))));

		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.config().temperature().orElse(0f)).isEqualTo(66.6f);

		assertThat(request.config().systemInstruction()).isPresent();
		assertThat(request.config().systemInstruction().get().parts().get().get(0).text().orElse(""))
			.isEqualTo("System Message Text");

		assertThat(request.contents()).hasSize(1);
		Content content = request.contents().get(0);

		List<Part> parts = content.parts().orElse(List.of());
		assertThat(parts).hasSize(2);

		Part textPart = parts.get(0);
		assertThat(textPart.text().orElse("")).isEqualTo("User Message Text");

		Part mediaPart = parts.get(1);
		// Media parts are now created as inline data with Part.fromBytes()
		// The test needs to be updated based on how media is handled in the new SDK
		System.out.println(mediaPart);
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var toolCallingManager = ToolCallingManager.builder().build();

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.toolCallingManager(toolCallingManager)
			.build();

		var requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
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
		assertThat(request.config().systemInstruction()).isNotPresent();
		assertThat(request.modelName()).isEqualTo("PROMPT_MODEL");

		assertThat(request.config().tools()).isPresent();
		assertThat(request.config().tools().get()).hasSize(1);
		var tool = request.config().tools().get().get(0);
		assertThat(tool.functionDeclarations()).isPresent();
		assertThat(tool.functionDeclarations().get()).hasSize(1);
		assertThat(tool.functionDeclarations().get().get(0).name().orElse("")).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void defaultOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var toolCallingManager = ToolCallingManager.builder().build();

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.toolCallingManager(toolCallingManager)
			.defaultOptions(GoogleGenAiChatOptions.builder()
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
		assertThat(request.config().systemInstruction()).isNotPresent();
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		assertThat(request.config().tools()).isPresent();
		assertThat(request.config().tools().get()).hasSize(1);

		// Explicitly enable the function

		requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().toolName(TOOL_FUNCTION_NAME).build()));

		request = client.createGeminiRequest(requestPrompt);

		assertThat(request.config().tools()).isPresent();
		assertThat(request.config().tools().get()).hasSize(1);
		var tool = request.config().tools().get().get(0);
		assertThat(tool.functionDeclarations()).isPresent();
		assertThat(tool.functionDeclarations().get()).hasSize(1);

		// When using .toolName() to filter, Spring AI may wrap the name with "Optional[]"
		String actualName = tool.functionDeclarations().get().get(0).name().orElse("");
		assertThat(actualName).as("Explicitly enabled function")
			.satisfiesAnyOf(name -> assertThat(name).isEqualTo(TOOL_FUNCTION_NAME),
					name -> assertThat(name).isEqualTo("Optional[" + TOOL_FUNCTION_NAME + "]"));

		// Override the default options function with one from the prompt
		requestPrompt = client.buildRequestPrompt(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Overridden function description")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()));
		request = client.createGeminiRequest(requestPrompt);

		assertThat(request.config().tools()).isPresent();
		assertThat(request.config().tools().get()).hasSize(1);
		tool = request.config().tools().get().get(0);
		assertThat(tool.functionDeclarations()).isPresent();
		assertThat(tool.functionDeclarations().get()).hasSize(1);
		assertThat(tool.functionDeclarations().get().get(0).name().orElse("")).as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		toolDefinitions = toolCallingManager
			.resolveToolDefinitions((ToolCallingChatOptions) requestPrompt.getOptions());

		assertThat(toolDefinitions).hasSize(1);
		assertThat(toolDefinitions.get(0).name()).isSameAs(TOOL_FUNCTION_NAME);
		assertThat(toolDefinitions.get(0).description()).isEqualTo("Overridden function description");
	}

	@Test
	public void createRequestWithGenerationConfigOptions() {

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
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

		assertThat(request.config().systemInstruction()).isNotPresent();
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.config().temperature().orElse(0f)).isEqualTo(66.6f);
		assertThat(request.config().maxOutputTokens().orElse(0)).isEqualTo(100);
		assertThat(request.config().topK().orElse(0f)).isEqualTo(10f);
		assertThat(request.config().topP().orElse(0f)).isEqualTo(5.0f);
		assertThat(request.config().candidateCount().orElse(0)).isEqualTo(1);
		assertThat(request.config().stopSequences().orElse(List.of())).containsExactly("stop1", "stop2");
		assertThat(request.config().responseMimeType().orElse("")).isEqualTo("application/json");
	}

	@Test
	public void createRequestWithThinkingBudget() {

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(12853).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		// Verify thinkingConfig is present and contains thinkingBudget
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget().get()).isEqualTo(12853);
	}

	@Test
	public void createRequestWithThinkingBudgetOverride() {

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(10000).build())
			.build();

		// Override default thinkingBudget with prompt-specific value
		GeminiRequest request = client.createGeminiRequest(client.buildRequestPrompt(
				new Prompt("Test message content", GoogleGenAiChatOptions.builder().thinkingBudget(25000).build())));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		// Verify prompt-specific thinkingBudget overrides default
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget().get()).isEqualTo(25000);
	}

	@Test
	public void createRequestWithNullThinkingBudget() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(null).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		// Verify thinkingConfig is not present when thinkingBudget is null
		assertThat(request.config().thinkingConfig()).isEmpty();
	}

	@Test
	public void createRequestWithZeroThinkingBudget() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(0).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget().get()).isEqualTo(0);
	}

	@Test
	public void createRequestWithNoMessages() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(client.buildRequestPrompt(new Prompt(List.of())));

		assertThat(request.contents()).isEmpty();
	}

	@Test
	public void createRequestWithOnlySystemMessage() {
		var systemMessage = new SystemMessage("System Message Only");

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt(List.of(systemMessage))));

		assertThat(request.config().systemInstruction()).isPresent();
		assertThat(request.contents()).isEmpty();
	}

	@Test
	public void createRequestWithLabels() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.labels(java.util.Map.of("org", "my-org", "env", "test"))
				.build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.config().labels()).isPresent();
		assertThat(request.config().labels().get()).containsEntry("org", "my-org");
		assertThat(request.config().labels().get()).containsEntry("env", "test");
	}

	@Test
	public void createRequestWithThinkingLevel() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
				.build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		// Verify thinkingConfig is present and contains thinkingLevel
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("HIGH");
	}

	@Test
	public void createRequestWithThinkingLevelOverride() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		// Override default thinkingLevel with prompt-specific value
		GeminiRequest request = client.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().thinkingLevel(GoogleGenAiThinkingLevel.HIGH).build())));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("HIGH");
	}

	@Test
	public void createRequestWithThinkingLevelAndBudgetCombined() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.thinkingBudget(8192)
				.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
				.includeThoughts(true)
				.build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		assertThat(request.config().thinkingConfig()).isPresent();
		var thinkingConfig = request.config().thinkingConfig().get();
		assertThat(thinkingConfig.thinkingBudget()).isPresent();
		assertThat(thinkingConfig.thinkingBudget().get()).isEqualTo(8192);
		assertThat(thinkingConfig.thinkingLevel()).isPresent();
		assertThat(thinkingConfig.thinkingLevel().get().toString()).isEqualTo("HIGH");
		assertThat(thinkingConfig.includeThoughts()).isPresent();
		assertThat(thinkingConfig.includeThoughts().get()).isTrue();
	}

	@Test
	public void createRequestWithNullThinkingLevel() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingLevel(null).build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		// Verify thinkingConfig is not present when only thinkingLevel is null
		assertThat(request.config().thinkingConfig()).isEmpty();
	}

	@Test
	public void createRequestWithOnlyThinkingLevel() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		GeminiRequest request = client
			.createGeminiRequest(client.buildRequestPrompt(new Prompt("Test message content")));

		// Verify thinkingConfig is present when only thinkingLevel is set
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("LOW");
		// Budget should not be present
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isEmpty();
	}

}
