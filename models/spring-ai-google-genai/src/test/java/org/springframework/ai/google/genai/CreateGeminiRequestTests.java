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

package org.springframework.ai.google.genai;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCallingConfigMode;
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
import org.springframework.ai.google.genai.common.GoogleGenAiServiceTier;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.google.genai.tool.MockWeatherService;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Dan Dobrin
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
public class CreateGeminiRequestTests {

	@Mock
	Client genAiClient;

	@Test
	public void createRequestWithFrequencyAndPresencePenalty() {

		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.frequencyPenalty(.25)
			.presencePenalty(.75)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

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

		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt(List.of(systemMessage, userMessage),
				GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build()));

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

		// Media parts are now created as inline data with Part.fromBytes()
		// The test needs to be updated based on how media is handled in the new SDK
		Part mediaPart = parts.get(1);
		assertThat(mediaPart.fileData().isPresent()).isTrue();
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var toolCallingManager = ToolCallingManager.builder().build();

		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		var requestPrompt = new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.model("PROMPT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build());

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
	public void createRequestWithGenerationConfigOptions() {

		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.temperature(66.6)
			.maxOutputTokens(100)
			.topK(10)
			.topP(5.0)
			.stopSequences(List.of("stop1", "stop2"))
			.candidateCount(1)
			.responseMimeType("application/json")
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

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

		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(12853).build()));

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
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(10000).build())
			.build();

		// Override default thinkingBudget with prompt-specific value
		GeminiRequest request = client.createGeminiRequest(
				new Prompt("Test message content", GoogleGenAiChatOptions.builder().thinkingBudget(25000).build()));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName())
			.isEqualTo(org.springframework.ai.google.genai.GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue());

		// Verify prompt-specific thinkingBudget overrides default
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget().get()).isEqualTo(25000);
	}

	@Test
	public void createRequestWithNullThinkingBudget() {
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(null).build()));

		assertThat(request.contents()).hasSize(1);
		assertThat(request.modelName()).isEqualTo("DEFAULT_MODEL");

		// Verify thinkingConfig is not present when thinkingBudget is null
		assertThat(request.config().thinkingConfig()).isEmpty();
	}

	@Test
	public void createRequestWithZeroThinkingBudget() {
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingBudget(0).build()));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingBudget().get()).isEqualTo(0);
	}

	@Test
	public void createRequestWithNoMessages() {
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(
				new Prompt(List.of(), GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build()));

		assertThat(request.contents()).isEmpty();
	}

	@Test
	public void createRequestWithOnlySystemMessage() {
		var systemMessage = new SystemMessage("System Message Only");

		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(
				new Prompt(List.of(systemMessage), GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build()));

		assertThat(request.config().systemInstruction()).isPresent();
		assertThat(request.contents()).isEmpty();
	}

	@Test
	public void createRequestWithLabels() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.labels(Map.of("org", "my-org", "env", "test"))
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().labels()).isPresent();
		assertThat(request.config().labels().get()).containsEntry("org", "my-org");
		assertThat(request.config().labels().get()).containsEntry("env", "test");
	}

	@Test
	public void createRequestWithThinkingLevel() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).options(options).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

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
			.options(GoogleGenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		// Override default thinkingLevel with prompt-specific value
		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().thinkingLevel(GoogleGenAiThinkingLevel.HIGH).build()));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("HIGH");
	}

	@Test
	public void createRequestWithThinkingLevelAndBudgetCombined() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.thinkingBudget(8192)
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.includeThoughts(true)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

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
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").thinkingLevel(null).build()));

		// Verify thinkingConfig is not present when only thinkingLevel is null
		assertThat(request.config().thinkingConfig()).isEmpty();
	}

	@Test
	public void createRequestWithOnlyThinkingLevel() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		// Verify thinkingConfig is present when only thinkingLevel is set
		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("LOW");
		// Budget should not be present
		assertThat(request.config().thinkingConfig().get().thinkingBudget()).isEmpty();
	}

	@Test
	public void createRequestWithThinkingLevelMinimal() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-3-flash-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("MINIMAL");
	}

	@Test
	public void createRequestWithThinkingLevelMedium() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-3-flash-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("MEDIUM");
	}

	@Test
	public void createRequestWithThinkingLevelMinimalOnProModelThrows() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-3-pro-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		assertThatThrownBy(() -> client.createGeminiRequest(new Prompt("Test message content", options)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MINIMAL")
			.hasMessageContaining("not supported")
			.hasMessageContaining("Gemini 3 Pro");
	}

	@Test
	public void createRequestWithThinkingLevelMediumOnProModelThrows() {
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		assertThatThrownBy(() -> client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.model("gemini-3-pro-preview")
					.thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM)
					.build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MEDIUM")
			.hasMessageContaining("not supported")
			.hasMessageContaining("Gemini 3 Pro");
	}

	@Test
	public void createRequestWithThinkingLevelLowOnProModel() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-3-pro-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("LOW");
	}

	@Test
	public void createRequestWithThinkingLevelHighOnProModel() {
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.model("gemini-3-pro-preview")
					.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
					.build()));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("HIGH");
	}

	@Test
	public void createRequestWithAllThinkingLevelsOnFlashModel() {
		for (GoogleGenAiThinkingLevel level : List.of(GoogleGenAiThinkingLevel.MINIMAL, GoogleGenAiThinkingLevel.LOW,
				GoogleGenAiThinkingLevel.MEDIUM, GoogleGenAiThinkingLevel.HIGH)) {
			var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

			GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
					GoogleGenAiChatOptions.builder().model("gemini-3-flash-preview").thinkingLevel(level).build()));

			assertThat(request.config().thinkingConfig()).isPresent();
			assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
			assertThat(request.config().thinkingConfig().get().thinkingLevel().get().toString())
				.isEqualTo(level.name());
		}
	}

	@Test
	public void createRequestWithRuntimeThinkingLevelOverrideOnProModelThrows() {
		// Default options are valid for Pro
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder()
				.model("gemini-3-pro-preview")
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		// Runtime override with unsupported level should throw
		assertThatThrownBy(() -> client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.model("gemini-3-pro-preview")
					.thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
					.build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MINIMAL")
			.hasMessageContaining("not supported");
	}

	@Test
	public void createRequestWithTrLocaleWithPreviewModel() {
		Locale defaultLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			// Default options are valid for Pro
			var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

			// With turkish locale the check if this is a gemini3-pro model
			// (isGemini3ProModel) will fail as
			// lowercasing "gemini3-pro" will result in "gemını3-pro"
			assertThatThrownBy(() -> client.createGeminiRequest(new Prompt("Test message content",
					GoogleGenAiChatOptions.builder()
						.model("GEMINI-3-PRO-PREVIEW")
						.thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
						.build())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("MINIMAL")
				.hasMessageContaining("not supported");
		}
		finally {
			Locale.setDefault(defaultLocale);
		}
	}

	@Test
	public void createRequestWithThinkingLevelUnspecifiedOnProModel() {
		// THINKING_LEVEL_UNSPECIFIED should be allowed on Pro models
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("gemini-3-pro-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.THINKING_LEVEL_UNSPECIFIED)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().thinkingConfig()).isPresent();
		assertThat(request.config().thinkingConfig().get().thinkingLevel()).isPresent();
	}

	@Test
	public void createRequestWithProModelInCustomPath() {
		// Test custom paths like "projects/.../gemini-3-pro-preview"
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("projects/my-project/locations/us-central1/publishers/google/models/gemini-3-pro-preview")
			.thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		assertThatThrownBy(() -> client.createGeminiRequest(new Prompt("Test message content", options)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MINIMAL")
			.hasMessageContaining("not supported");
	}

	@Test
	public void createRequestWithIncludeServerSideToolInvocationsEnabled() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.googleSearchRetrieval(true)
					.includeServerSideToolInvocations(true)
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		assertThat(request.config().toolConfig().get().includeServerSideToolInvocations()).isPresent();
		assertThat(request.config().toolConfig().get().includeServerSideToolInvocations().get()).isTrue();
		assertThat(request.config().tools()).isPresent();
	}

	@Test
	public void createRequestWithIncludeServerSideToolInvocationsDisabled() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.googleSearchRetrieval(true)
					.includeServerSideToolInvocations(false)
					.build()));

		assertThat(request.config().toolConfig()).isNotPresent();
	}

	@Test
	public void createRequestWithIncludeServerSideToolInvocationsDefault() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.googleSearchRetrieval(true)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		// Default is false, so no ToolConfig should be set
		assertThat(request.config().toolConfig()).isNotPresent();
	}

	@Test
	public void createRequestWithToolChoiceAuto() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.AUTO)
						.build())
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		assertThat(request.config().toolConfig().get().functionCallingConfig()).isPresent();
		var fcc = request.config().toolConfig().get().functionCallingConfig().get();
		assertThat(fcc.mode()).isPresent();
		assertThat(fcc.mode().get().knownEnum()).isEqualTo(FunctionCallingConfigMode.Known.AUTO);
		assertThat(fcc.allowedFunctionNames()).isNotPresent();
	}

	@Test
	public void createRequestWithToolChoiceNone() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.NONE)
						.build())
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		assertThat(request.config().toolConfig().get().functionCallingConfig()).isPresent();
		var fcc = request.config().toolConfig().get().functionCallingConfig().get();
		assertThat(fcc.mode()).isPresent();
		assertThat(fcc.mode().get().knownEnum()).isEqualTo(FunctionCallingConfigMode.Known.NONE);
		assertThat(fcc.allowedFunctionNames()).isNotPresent();
	}

	@Test
	public void createRequestWithToolChoiceAnyAndAllowedFunctionNames() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.ANY)
						.allowedFunctionNames(List.of("funcA", "funcB"))
						.build())
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		var fcc = request.config().toolConfig().get().functionCallingConfig().get();
		assertThat(fcc.mode().get().knownEnum()).isEqualTo(FunctionCallingConfigMode.Known.ANY);
		assertThat(fcc.allowedFunctionNames()).isPresent();
		assertThat(fcc.allowedFunctionNames().get()).containsExactly("funcA", "funcB");
	}

	@Test
	public void createRequestWithToolChoiceValidatedAndAllowedFunctionNames() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.VALIDATED)
						.allowedFunctionNames(List.of("funcA", "funcB"))
						.build())
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		var fcc = request.config().toolConfig().get().functionCallingConfig().get();
		assertThat(fcc.mode().get().knownEnum()).isEqualTo(FunctionCallingConfigMode.Known.VALIDATED);
		assertThat(fcc.allowedFunctionNames()).isPresent();
		assertThat(fcc.allowedFunctionNames().get()).containsExactly("funcA", "funcB");
	}

	@Test
	public void createRequestWithToolChoiceAnyIgnoresAllowedFunctionNamesForNonAnyMode() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.AUTO)
						.allowedFunctionNames(List.of("funcA"))
						.build())
					.build()));

		var fcc = request.config().toolConfig().get().functionCallingConfig().get();
		assertThat(fcc.mode().get().knownEnum()).isEqualTo(FunctionCallingConfigMode.Known.AUTO);
		assertThat(fcc.allowedFunctionNames()).isNotPresent();
	}

	@Test
	public void createRequestWithToolChoiceCombinedWithServerSideToolInvocations() {
		var client = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content",
				GoogleGenAiChatOptions.builder()
					.includeServerSideToolInvocations(true)
					.toolChoice(GoogleGenAiChatOptions.ToolChoice.builder()
						.mode(GoogleGenAiChatOptions.ToolChoice.Mode.ANY)
						.build())
					.build()));

		assertThat(request.config().toolConfig()).isPresent();
		var toolConfig = request.config().toolConfig().get();
		assertThat(toolConfig.includeServerSideToolInvocations()).isPresent();
		assertThat(toolConfig.includeServerSideToolInvocations().get()).isTrue();
		assertThat(toolConfig.functionCallingConfig()).isPresent();
		assertThat(toolConfig.functionCallingConfig().get().mode().get().knownEnum())
			.isEqualTo(FunctionCallingConfigMode.Known.ANY);
	}

	@Test
	public void createRequestWithServiceTier() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.serviceTier(GoogleGenAiServiceTier.PRIORITY)
			.build();
		var client = GoogleGenAiChatModel.builder().genAiClient(this.genAiClient).build();

		GeminiRequest request = client.createGeminiRequest(new Prompt("Test message content", options));

		assertThat(request.config().serviceTier()).isPresent();
		assertThat(request.config().serviceTier().get().toString()).isEqualTo("priority");

	}

}
