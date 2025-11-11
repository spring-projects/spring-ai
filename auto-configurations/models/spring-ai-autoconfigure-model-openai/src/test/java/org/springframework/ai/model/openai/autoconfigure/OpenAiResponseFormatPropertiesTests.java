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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiChatProperties} #options#responseFormat support.
 *
 * @author Christian Tzolov
 * @author Issam El-atif
 */
public class OpenAiResponseFormatPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

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

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",

				"spring.ai.openai.chat.options.response-format.type=JSON_SCHEMA",
				"spring.ai.openai.chat.options.response-format.name=MyName",
				"spring.ai.openai.chat.options.response-format.schema=" + responseFormatJsonSchema,
				"spring.ai.openai.chat.options.response-format.strict=true"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getResponseFormat())
					.isEqualTo(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, responseFormatJsonSchema));
			});
	}

	@Test
	public void responseFormatJsonObject() {

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY",
					"spring.ai.openai.chat.options.response-format.type=JSON_OBJECT")

			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);

				assertThat(chatProperties.getOptions().getResponseFormat())
					.isEqualTo(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
			});
	}

	@Test
	public void emptyResponseFormat() {

		this.contextRunner.withPropertyValues("spring.ai.openai.api-key=API_KEY")

			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);

				assertThat(chatProperties.getOptions().getResponseFormat()).isNull();
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

}
