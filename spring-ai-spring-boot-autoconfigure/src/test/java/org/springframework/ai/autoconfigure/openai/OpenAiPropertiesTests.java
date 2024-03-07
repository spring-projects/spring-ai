/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.openai;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool.Type;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties}, {@link OpenAiChatProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
public class OpenAiPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void transcriptionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
			"spring.ai.openai.base-url=TEST_BASE_URL",
			"spring.ai.openai.api-key=abc123",
			"spring.ai.openai.audio.transcription.options.model=MODEL_XYZ",
			"spring.ai.openai.audio.transcription.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.base-url=TEST_BASE_URL2",
				"spring.ai.openai.chat.api-key=456",
				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
			});
	}

	@Test
	public void transcriptionOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.transcription.base-url=TEST_BASE_URL2",
						"spring.ai.openai.audio.transcription.api-key=456",
						"spring.ai.openai.audio.transcription.options.model=MODEL_XYZ",
						"spring.ai.openai.audio.transcription.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.speech.options.model=TTS_1",
						"spring.ai.openai.audio.speech.options.voice=alloy",
						"spring.ai.openai.audio.speech.options.response-format=mp3",
						"spring.ai.openai.audio.speech.options.speed=0.75")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getApiKey()).isNull();
				assertThat(speechProperties.getBaseUrl()).isNull();

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_1");
				assertThat(speechProperties.getOptions().getVoice())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ALLOY);
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.75f);
			});
	}

	@Test
	public void speechPropertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.audio.speech.options.model=TTS_1",
						"spring.ai.openai.audio.speech.options.voice=alloy",
						"spring.ai.openai.audio.speech.options.response-format=mp3",
						"spring.ai.openai.audio.speech.options.speed=0.75")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_1");
				assertThat(speechProperties.getOptions().getVoice())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ALLOY);
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.75f);
			});
	}

	@Test
	public void speechOverrideConnectionPropertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
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
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(OpenAiAudioSpeechProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(speechProperties.getApiKey()).isEqualTo("456");
				assertThat(speechProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(speechProperties.getOptions().getModel()).isEqualTo("TTS_2");
				assertThat(speechProperties.getOptions().getVoice()).isEqualTo(OpenAiAudioApi.SpeechRequest.Voice.ECHO);
				assertThat(speechProperties.getOptions().getResponseFormat())
					.isEqualTo(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.OPUS);
				assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(0.5f);
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.openai.embedding.api-key=456",
				"spring.ai.openai.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.image.options.model=MODEL_XYZ",
						"spring.ai.openai.image.options.n=3")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.openai.base-url=TEST_BASE_URL",
						"spring.ai.openai.api-key=abc123",
						"spring.ai.openai.image.base-url=TEST_BASE_URL2",
						"spring.ai.openai.image.api-key=456",
						"spring.ai.openai.image.options.model=MODEL_XYZ",
						"spring.ai.openai.image.options.n=3")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=TEST_BASE_URL",

				"spring.ai.openai.chat.options.model=MODEL_XYZ",
				"spring.ai.openai.chat.options.frequencyPenalty=-1.5",
				"spring.ai.openai.chat.options.logitBias.myTokenId=-5",
				"spring.ai.openai.chat.options.maxTokens=123",
				"spring.ai.openai.chat.options.n=10",
				"spring.ai.openai.chat.options.presencePenalty=0",
				"spring.ai.openai.chat.options.responseFormat.type=json",
				"spring.ai.openai.chat.options.seed=66",
				"spring.ai.openai.chat.options.stop=boza,koza",
				"spring.ai.openai.chat.options.temperature=0.55",
				"spring.ai.openai.chat.options.topP=0.56",

				// "spring.ai.openai.chat.options.toolChoice.functionName=toolChoiceFunctionName",
				"spring.ai.openai.chat.options.toolChoice=" + ToolChoiceBuilder.FUNCTION("toolChoiceFunctionName"),

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
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("text-embedding-ada-002");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5f);
				assertThat(chatProperties.getOptions().getLogitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getResponseFormat()).isEqualTo(new ResponseFormat("json"));
				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(66);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56f);

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						chatProperties.getOptions().getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");

				assertThat(chatProperties.getOptions().getTools()).hasSize(1);
				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.type()).isEqualTo(Type.FUNCTION);
				var function = tool.function();
				assertThat(function.name()).isEqualTo("myFunction1");
				assertThat(function.description()).isEqualTo("function description");
				assertThat(function.parameters()).isNotEmpty();
			});
	}

	@Test
	public void transcriptionOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
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
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				var transcriptionProperties = context.getBean(OpenAiAudioTranscriptionProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("text-embedding-ada-002");

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

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=TEST_BASE_URL",

				"spring.ai.openai.embedding.options.model=MODEL_XYZ",
				"spring.ai.openai.embedding.options.encodingFormat=MyEncodingFormat",
				"spring.ai.openai.embedding.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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
		new ApplicationContextRunner().withPropertyValues(
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
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
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

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingClient.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingClient.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingClient.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatClient.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatClient.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatClient.class)).isNotEmpty();
			});

	}

	@Test
	void imageActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.image.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageClient.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageClient.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.image.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageClient.class)).isNotEmpty();
			});

	}

}
