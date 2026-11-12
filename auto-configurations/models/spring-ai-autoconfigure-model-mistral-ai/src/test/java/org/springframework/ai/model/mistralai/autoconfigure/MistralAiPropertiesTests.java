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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.ai.mistralai.transcription.MistralAiTranscriptionModel;
import org.springframework.ai.mistralai.transcription.MistralAiTranscriptionOptions;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit Tests for {@link MistralAiCommonProperties}, {@link MistralAiEmbeddingProperties}.
 */
public class MistralAiPropertiesTests {

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL", "spring.ai.mistralai.api-key=abc123",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.chat.tools[0].function.name=myFunction1",
				"spring.ai.mistralai.chat.tools[0].function.description=function description",
				"spring.ai.mistralai.chat.tools[0].function.jsonSchema=" + """
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

				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(MistralAiChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {

				var chatProperties = context.getBean(MistralAiChatProperties.class);

				var tool = chatProperties.toOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(MistralAiApi.FunctionTool.Type.FUNCTION);
				var function = tool.getFunction();
				assertThat(function.getName()).isEqualTo("myFunction1");
				assertThat(function.getDescription()).isEqualTo("function description");
				assertThat(function.getParameters()).isNotEmpty();
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.mistralai.base-url=TEST_BASE_URL",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ",
					"spring.ai.mistralai.embedding.options.encoding-format=MyEncodingFormat")
			.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getEncodingFormat()).isEqualTo("MyEncodingFormat");
				assertThat(embeddingProperties.toOptions().getEncodingFormat()).isEqualTo("MyEncodingFormat");
			});
	}

	@Test
	public void moderationOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.moderation.base-url=TEST_BASE_URL",
					"spring.ai.mistralai.moderation.api-key=abc123",
					"spring.ai.mistralai.moderation.options.model=MODERATION_MODEL")
			.withConfiguration(
					AutoConfigurations.of(MistralAiModerationAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var moderationProperties = context.getBean(MistralAiModerationProperties.class);
				assertThat(moderationProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(moderationProperties.getApiKey()).isEqualTo("abc123");
				assertThat(moderationProperties.getModel()).isEqualTo("MODERATION_MODEL");
				assertThat(moderationProperties.toOptions().getModel()).isEqualTo("MODERATION_MODEL");
			});
	}

	@Test
	public void transcriptionOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL", "spring.ai.mistralai.api-key=abc123",
					"spring.ai.mistralai.audio.transcription.model=TRANSCRIPTION_MODEL",
					"spring.ai.mistralai.audio.transcription.language=fr",
					"spring.ai.mistralai.audio.transcription.temperature=0.3",
					"spring.ai.mistralai.audio.transcription.diarize=true",
					"spring.ai.mistralai.audio.transcription.context-bias=Northwind,Quillara",
					"spring.ai.mistralai.audio.transcription.timestamp-granularities=segment")
			.withConfiguration(AutoConfigurations.of(MistralAiTranscriptionAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					WebClientAutoConfiguration.class))
			.run(context -> {
				var transcriptionProperties = context.getBean(MistralAiTranscriptionProperties.class);
				var commonProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(commonProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(commonProperties.getApiKey()).isEqualTo("abc123");
				// Left unset, so that the common connection properties apply.
				assertThat(transcriptionProperties.getBaseUrl()).isNull();
				assertThat(transcriptionProperties.getApiKey()).isNull();

				var options = transcriptionProperties.toOptions();
				assertThat(options.getModel()).isEqualTo("TRANSCRIPTION_MODEL");
				assertThat(options.getLanguage()).isEqualTo("fr");
				assertThat(options.getTemperature()).isEqualTo(0.3);
				assertThat(options.getDiarize()).isTrue();
				assertThat(options.getContextBias()).containsExactly("Northwind", "Quillara");
				assertThat(options.getTimestampGranularities()).containsExactly(TimestampGranularity.SEGMENT);

				var transcriptionModel = context.getBean(MistralAiTranscriptionModel.class);
				assertThat(transcriptionModel.getOptions()).isEqualTo(options);
			});
	}

	@Test
	public void transcriptionUsesCommonConnectionProperties() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(requestTo("https://proxy.example.com/v1/audio/transcriptions"))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc123"))
			.andRespond(withSuccess("""
					{"model": "voxtral-mini-latest", "text": "Hello.", "language": null, "segments": [],
					"usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
					""", MediaType.APPLICATION_JSON));

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=https://proxy.example.com",
					"spring.ai.mistralai.api-key=abc123")
			.withBean(RestClient.Builder.class, () -> restClientBuilder)
			.withConfiguration(AutoConfigurations.of(MistralAiTranscriptionAutoConfiguration.class,
					SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var transcriptionModel = context.getBean(MistralAiTranscriptionModel.class);

				assertThat(transcriptionModel.transcribe(new ByteArrayResource("audio".getBytes())))
					.isEqualTo("Hello.");
				server.verify();
			});
	}

	@Test
	public void transcriptionDefaultModel() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.api-key=abc123")
			.withConfiguration(AutoConfigurations.of(MistralAiTranscriptionAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					WebClientAutoConfiguration.class))
			.run(context -> assertThat(context.getBean(MistralAiTranscriptionModel.class).getOptions().getModel())
				.isEqualTo(MistralAiTranscriptionOptions.DEFAULT_MODEL));
	}

}
