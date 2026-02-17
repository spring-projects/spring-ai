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

package org.springframework.ai.model.openai.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties}, {@link OpenAiChatProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @author Issam El-atif
 * @author Yanming Zhou
 * @since 0.8.0
 */
public class OpenAiPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void chatProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void transcriptionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
			"spring.ai.openai.base-url=TEST_BASE_URL",
			"spring.ai.openai.api-key=abc123",
			"spring.ai.openai.audio.transcription.options.model=MODEL_XYZ",
			"spring.ai.openai.audio.transcription.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				var transcriptionProperties = context.getBean(OpenAiAudioTranscriptionProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(transcriptionProperties.getApiKey()).isNull();
				assertThat(transcriptionProperties.getBaseUrl()).isNull();

				assertThat(transcriptionProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(transcriptionProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.base-url=TEST_BASE_URL2",
				"spring.ai.openai.chat.api-key=456",
				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void transcriptionOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.transcription.base-url=TEST_BASE_URL2",
						"spring.ai.openai.audio.transcription.api-key=456",
						"spring.ai.openai.audio.transcription.options.model=MODEL_XYZ",
						"spring.ai.openai.audio.transcription.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				var transcriptionProperties = context.getBean(OpenAiAudioTranscriptionProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(transcriptionProperties.getApiKey()).isEqualTo("456");
				assertThat(transcriptionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(transcriptionProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(transcriptionProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void speechProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.speech.options.model=TTS_1",
						"spring.ai.openai.audio.speech.options.voice=alloy",
						"spring.ai.openai.audio.speech.options.response-format=mp3",
						"spring.ai.openai.audio.speech.options.speed=0.75")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getApiKey()).isNull();
				assertThat(speechProperties.getBaseUrl()).isNull();

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_1");
				assertThat(speechProperties.getOptions().getVoice())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue());
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.75f);
			});
	}

	@Test
	public void speechPropertiesTest() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.speech.options.model=TTS_1",
						"spring.ai.openai.audio.speech.options.voice=alloy",
						"spring.ai.openai.audio.speech.options.response-format=mp3",
						"spring.ai.openai.audio.speech.options.speed=0.75")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_1");
				assertThat(speechProperties.getOptions().getVoice())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue());
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.75f);
			});
	}

	@Test
	public void speechOverrideConnectionPropertiesTest() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.speech.base-url=TEST_BASE_URL2",
						"spring.ai.openai.audio.speech.api-key=456",
						"spring.ai.openai.audio.speech.options.model=TTS_2",
						"spring.ai.openai.audio.speech.options.voice=echo",
						"spring.ai.openai.audio.speech.options.response-format=opus",
						"spring.ai.openai.audio.speech.options.speed=0.5")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getApiKey()).isEqualTo("456");
				assertThat(speechProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_2");
				assertThat(speechProperties.getOptions().getVoice())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ECHO.getValue());
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.OPUS);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.5f);
			});
	}

	@Test
	public void embeddingProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isNull();

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.openai.embedding.api-key=456",
				"spring.ai.openai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void imageProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.image.options.model=MODEL_XYZ",
						"spring.ai.openai.image.options.n=3")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiImageProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(imageProperties.getApiKey()).isNull();
				assertThat(imageProperties.getBaseUrl()).isNull();

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
			});
	}

	@Test
	public void imageOverrideConnectionProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.image.base-url=TEST_BASE_URL2",
						"spring.ai.openai.image.api-key=456",
						"spring.ai.openai.image.options.model=MODEL_XYZ",
						"spring.ai.openai.image.options.n=3")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiImageProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(imageProperties.getApiKey()).isEqualTo("456");
				assertThat(imageProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
			});
	}

	@Test
	public void chatOptionsTest() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=TEST_BASE_URL",

				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.frequencyPenalty=-1.5",
				"spring.ai.openai.chat.options.logitBias.myTokenId=-5",
				"spring.ai.openai.chat.options.maxTokens=123",
				"spring.ai.openai.chat.options.n=10",
				"spring.ai.openai.chat.options.presencePenalty=0",
				"spring.ai.openai.chat.options.seed=66",
				"spring.ai.openai.chat.options.stop=boza,koza",
				"spring.ai.openai.chat.options.temperature=0.55",
				"spring.ai.openai.chat.options.topP=0.56",

				// "spring.ai.openai.chat.options.toolChoice.functionName=toolChoiceFunctionName",
				"spring.ai.openai.chat.options.toolChoice=" + ModelOptionsUtils.toJsonString(ToolChoiceBuilder.function("toolChoiceFunctionName")),

				"spring.ai.openai.chat.options.tools[0].function.name=myFunction1",
				"spring.ai.openai.chat.options.tools[0].function.description=function description",
				"spring.ai.openai.chat.options.tools[0].function.jsonSchema=" + """
					{
						"type": "object",
						"properties": {
							"location": {
								"type": "string",
								"description": "The city and state e.g. San Francisco, CA"
							},
							"lat": {
								"type": "number",
								"description": "The city latitude"
							},
							"lon": {
								"type": "number",
								"description": "The city longitude"
							},
							"unit": {
								"type": "string",
								"enum": ["c", "f"]
							}
						},
						"required": ["location", "lat", "lon", "unit"]
					}
					""",
					"spring.ai.openai.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getLogitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(66);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						"" + chatProperties.getOptions().getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");

				assertThat(chatProperties.getOptions().getTools()).hasSize(1);
				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(OpenAiApi.FunctionTool.Type.FUNCTION);
				var function = tool.getFunction();
				assertThat(function.getName()).isEqualTo("myFunction1");
				assertThat(function.getDescription()).isEqualTo("function description");
				assertThat(function.getParameters()).isNotEmpty();
			});
	}

	@Test
	public void transcriptionOptionsTest() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.api-key=API_KEY",
						"spring.ai.openai.base-url=TEST_BASE_URL",

						"spring.ai.openai.audio.transcription.options.model=MODEL_XYZ",
						"spring.ai.openai.audio.transcription.options.language=en",
						"spring.ai.openai.audio.transcription.options.prompt=Er, yes, I think so",
						"spring.ai.openai.audio.transcription.options.responseFormat=JSON",
						"spring.ai.openai.audio.transcription.options.temperature=0.55"
				)
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				var transcriptionProperties = context.getBean(OpenAiAudioTranscriptionProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(transcriptionProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(transcriptionProperties.getOptions().getLanguage()).isEqualTo("en");
				assertThat(transcriptionProperties.getOptions().getPrompt()).isEqualTo("Er, yes, I think so");
				assertThat(transcriptionProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.TranscriptResponseFormat.JSON);
				assertThat(transcriptionProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void embeddingOptionsTest() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=TEST_BASE_URL",

				"spring.ai.openai.embedding.options.model=MODEL_XYZ",
				"spring.ai.openai.embedding.options.encodingFormat=MyEncodingFormat",
				"spring.ai.openai.embedding.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getEncodingFormat()).isEqualTo("MyEncodingFormat");
				assertThat(embeddingProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

	@Test
	public void imageOptionsTest() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.api-key=API_KEY",
						"spring.ai.openai.base-url=TEST_BASE_URL",

						"spring.ai.openai.image.options.n=3",
						"spring.ai.openai.image.options.model=MODEL_XYZ",
						"spring.ai.openai.image.options.quality=hd",
						"spring.ai.openai.image.options.response_format=url",
						"spring.ai.openai.image.options.size=1024x1024",
						"spring.ai.openai.image.options.width=1024",
						"spring.ai.openai.image.options.height=1024",
						"spring.ai.openai.image.options.style=vivid",
						"spring.ai.openai.image.options.user=userXYZ"
				)
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiImageProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getQuality()).isEqualTo("hd");
				assertThat(imageProperties.getOptions().getResponseFormat()).isEqualTo("url");
				assertThat(imageProperties.getOptions().getSize()).isEqualTo("1024x1024");
				assertThat(imageProperties.getOptions().getWidth()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getHeight()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getStyle()).isEqualTo("vivid");
				assertThat(imageProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

	@Test
	void embeddingActivation() {

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

	}

	@Test
	void imageActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.image=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.image=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			});

	}

	@Test
	void audioSpeechActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.audio.speech=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.audio.speech=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
			});

	}

	@Test
	void audioTranscriptionActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.audio.transcription=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.audio.transcription=openai")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});

	}

	@Test
	public void moderationOptionsTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.moderation.base-url=TEST_BASE_URL",
					"spring.ai.openai.moderation.api-key=abc123",
					"spring.ai.openai.moderation.options.model=MODERATION_MODEL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiModerationAutoConfiguration.class))
			.run(context -> {
				var moderationProperties = context.getBean(OpenAiModerationProperties.class);
				assertThat(moderationProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(moderationProperties.getApiKey()).isEqualTo("abc123");
				assertThat(moderationProperties.getOptions().getModel()).isEqualTo("MODERATION_MODEL");
			});
	}

	@Test
	public void httpClientCustomTimeouts() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.connect-timeout=5s",
						"spring.ai.openai.read-timeout=30s")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
			});
	}

}
