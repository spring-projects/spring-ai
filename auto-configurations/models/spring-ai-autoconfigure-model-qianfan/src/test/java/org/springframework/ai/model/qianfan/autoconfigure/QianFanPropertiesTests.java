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

package org.springframework.ai.model.qianfan.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.qianfan.QianFanChatModel;
import org.springframework.ai.qianfan.QianFanEmbeddingModel;
import org.springframework.ai.qianfan.QianFanImageModel;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link QianFanConnectionProperties}, {@link QianFanChatProperties} and
 * {@link QianFanEmbeddingProperties}.
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 */
public class QianFanPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.qianfan.base-url=TEST_BASE_URL",
				"spring.ai.qianfan.api-key=abc123",
				"spring.ai.qianfan.secret-key=def123",
				"spring.ai.qianfan.chat.options.model=MODEL_XYZ",
				"spring.ai.qianfan.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(QianFanChatProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.qianfan.base-url=TEST_BASE_URL",
				"spring.ai.qianfan.api-key=abc123",
				"spring.ai.qianfan.secret-key=def123",
				"spring.ai.qianfan.chat.base-url=TEST_BASE_URL2",
				"spring.ai.qianfan.chat.api-key=456",
				"spring.ai.qianfan.chat.secret-key=def456",
				"spring.ai.qianfan.chat.options.model=MODEL_XYZ",
				"spring.ai.qianfan.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(QianFanChatProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getSecretKey()).isEqualTo("def456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.qianfan.base-url=TEST_BASE_URL",
				"spring.ai.qianfan.api-key=abc123",
				"spring.ai.qianfan.secret-key=def123",
				"spring.ai.qianfan.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(QianFanEmbeddingProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
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
				"spring.ai.qianfan.base-url=TEST_BASE_URL",
				"spring.ai.qianfan.api-key=abc123",
				"spring.ai.qianfan.secret-key=def123",
				"spring.ai.qianfan.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.qianfan.embedding.api-key=456",
				"spring.ai.qianfan.embedding.secret-key=def456",
				"spring.ai.qianfan.embedding.options.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(QianFanEmbeddingProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getSecretKey()).isEqualTo("def456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.qianfan.api-key=API_KEY",
				"spring.ai.qianfan.secret-key=SECRET_KEY",
				"spring.ai.qianfan.base-url=TEST_BASE_URL",

				"spring.ai.qianfan.chat.options.model=MODEL_XYZ",
				"spring.ai.qianfan.chat.options.frequencyPenalty=-1.5",
				"spring.ai.qianfan.chat.options.logitBias.myTokenId=-5",
				"spring.ai.qianfan.chat.options.maxTokens=123",
				"spring.ai.qianfan.chat.options.presencePenalty=0",
				"spring.ai.qianfan.chat.options.responseFormat.type=json",
				"spring.ai.qianfan.chat.options.stop=boza,koza",
				"spring.ai.qianfan.chat.options.temperature=0.55",
				"spring.ai.qianfan.chat.options.topP=0.56"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(QianFanChatProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("SECRET_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getResponseFormat())
					.isEqualTo(new QianFanApi.ChatCompletionRequest.ResponseFormat("json"));
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.qianfan.api-key=API_KEY",
				"spring.ai.qianfan.secret-key=SECRET_KEY",
				"spring.ai.qianfan.base-url=TEST_BASE_URL",

				"spring.ai.qianfan.embedding.options.model=MODEL_XYZ",
				"spring.ai.qianfan.embedding.options.encodingFormat=MyEncodingFormat"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);
				var embeddingProperties = context.getBean(QianFanEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("SECRET_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void embeddingActivation() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.model.embedding=none")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(QianFanEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.model.chat=qianfan")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(QianFanChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.qianfan.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, QianFanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanChatModel.class)).isNotEmpty();
			});

	}

	@Test
	public void imageProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.qianfan.base-url=TEST_BASE_URL",
						"spring.ai.qianfan.api-key=abc123",
						"spring.ai.qianfan.secret-key=def123",
						"spring.ai.qianfan.image.options.model=MODEL_XYZ",
						"spring.ai.qianfan.image.options.n=3")
				// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(QianFanImageProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
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
						"spring.ai.qianfan.base-url=TEST_BASE_URL",
						"spring.ai.qianfan.api-key=abc123",
						"spring.ai.qianfan.secret-key=def123",
						"spring.ai.qianfan.image.base-url=TEST_BASE_URL2",
						"spring.ai.qianfan.image.api-key=456",
						"spring.ai.qianfan.image.secret-key=def456",
						"spring.ai.qianfan.image.options.model=MODEL_XYZ",
						"spring.ai.qianfan.image.options.n=3")
				// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(QianFanImageProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("def123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(imageProperties.getApiKey()).isEqualTo("456");
				assertThat(imageProperties.getSecretKey()).isEqualTo("def456");
				assertThat(imageProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
			});
	}

	@Test
	public void imageOptionsTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.qianfan.api-key=API_KEY",
						"spring.ai.qianfan.secret-key=SECRET_KEY",
						"spring.ai.qianfan.base-url=TEST_BASE_URL",

						"spring.ai.qianfan.image.options.n=3",
						"spring.ai.qianfan.image.options.model=MODEL_XYZ",
						"spring.ai.qianfan.image.options.size=1024x1024",
						"spring.ai.qianfan.image.options.width=1024",
						"spring.ai.qianfan.image.options.height=1024",
						"spring.ai.qianfan.image.options.style=vivid",
						"spring.ai.qianfan.image.options.user=userXYZ"
				)
				// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(QianFanImageProperties.class);
				var connectionProperties = context.getBean(QianFanConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("SECRET_KEY");

				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getSize()).isEqualTo("1024x1024");
				assertThat(imageProperties.getOptions().getWidth()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getHeight()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getStyle()).isEqualTo("vivid");
				assertThat(imageProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

	@Test
	void imageActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.model.image=none")
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(QianFanImageModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL")
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanImageModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.qianfan.api-key=API_KEY", "spring.ai.qianfan.secret-key=SECRET_KEY",
					"spring.ai.qianfan.base-url=TEST_BASE_URL", "spring.ai.model.chat=qianfan")
			.withConfiguration(
					AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
							WebClientAutoConfiguration.class, QianFanImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(QianFanImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(QianFanImageModel.class)).isNotEmpty();
			});

	}

}
