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
package org.springframework.ai.autoconfigure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit Tests for {@link OpenAiChatProperties} #options#responseFormat support.
 *
 * @author Christian Tzolov
 */
public class OpenAiResponseFormatPropertiesTests {

	@Test
	public void responseFormatJsonSchema() {

		String responseFormatJsonSchema = """
				{
					  "$schema" : "https://json-schema.org/draft/2020-12/schema",
					  "type" : "object",
					  "properties" : {
					    "someString" : {
					      "type" : "string"
					    }
					  },
					  "additionalProperties" : false
					}
				""";

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",

				"spring.ai.openai.chat.options.response-format.type=JSON_SCHEMA",
				"spring.ai.openai.chat.options.response-format.name=MyName",
				"spring.ai.openai.chat.options.response-format.schema=" + responseFormatJsonSchema,
				"spring.ai.openai.chat.options.response-format.strict=true"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getResponseFormat()).isEqualTo(
						new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, "MyName", responseFormatJsonSchema, true));
			});
	}

	@Test
	public void responseFormatJsonObject() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY",
					"spring.ai.openai.chat.options.response-format.type=JSON_OBJECT")

			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);

				assertThat(chatProperties.getOptions().getResponseFormat())
					.isEqualTo(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT));
			});
	}

	@Test
	public void emptyResponseFormat() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.openai.api-key=API_KEY")

			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);

				assertThat(chatProperties.getOptions().getResponseFormat()).isNull();
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
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
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
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
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
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
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
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

	}

	@Test
	void imageActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.image.enabled=false")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.image.enabled=true")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			});

	}

	@Test
	void audioSpeechActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.audio.speech.enabled=false")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.audio.speech.enabled=true")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioSpeechModel.class)).isNotEmpty();
			});

	}

	@Test
	void audioTranscriptionActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.audio.transcription.enabled=false")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.openai.audio.transcription.enabled=true")
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiAudioTranscriptionModel.class)).isNotEmpty();
			});

	}

}
