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

package org.springframework.ai.model.deepseek.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Hyunsang Han
 * @author Issam El-atif
 */
public class DeepSeekPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.deepseek.base-url=TEST_BASE_URL",
				"spring.ai.deepseek.api-key=abc123",
				"spring.ai.deepseek.chat.options.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
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
				"spring.ai.deepseek.base-url=TEST_BASE_URL",
				"spring.ai.deepseek.api-key=abc123",
				"spring.ai.deepseek.chat.base-url=TEST_BASE_URL2",
				"spring.ai.deepseek.chat.api-key=456",
				"spring.ai.deepseek.chat.options.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.deepseek.api-key=API_KEY",
				"spring.ai.deepseek.base-url=TEST_BASE_URL",

				"spring.ai.deepseek.chat.options.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.options.frequencyPenalty=-1.5",
				"spring.ai.deepseek.chat.options.logitBias.myTokenId=-5",
				"spring.ai.deepseek.chat.options.maxTokens=123",
				"spring.ai.deepseek.chat.options.presencePenalty=0",
				"spring.ai.deepseek.chat.options.responseFormat.type=json_object",
				"spring.ai.deepseek.chat.options.seed=66",
				"spring.ai.deepseek.chat.options.stop=boza,koza",
				"spring.ai.deepseek.chat.options.temperature=0.55",
				"spring.ai.deepseek.chat.options.topP=0.56",
				"spring.ai.deepseek.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=deepseek")
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isNotEmpty();
			});
	}

	@Test
	public void httpClientDefaultProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				var httpClientConfig = connectionProperties.getHttpClient();

				assertThat(httpClientConfig.isEnabled()).isTrue();
				assertThat(httpClientConfig.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
				assertThat(httpClientConfig.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
				assertThat(httpClientConfig.getRedirects()).isNull();
				assertThat(httpClientConfig.getSslBundle()).isNull();
			});
	}

	@Test
	public void httpClientCustomTimeouts() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.connect-timeout=5s",
						"spring.ai.deepseek.http-client.read-timeout=30s")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				var httpClientConfig = connectionProperties.getHttpClient();

				assertThat(httpClientConfig.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(httpClientConfig.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
			});
	}

	@Test
	public void httpClientRedirects() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.redirects=DONT_FOLLOW")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				assertThat(connectionProperties.getHttpClient().getRedirects()).isEqualTo(HttpRedirects.DONT_FOLLOW);
			});
	}

	@Test
	public void httpClientSslBundle() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.ssl-bundle=deepseek-ssl")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				assertThat(connectionProperties.getHttpClient().getSslBundle()).isEqualTo("deepseek-ssl");
			});
	}

	@Test
	public void httpClientAllProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.enabled=true",
						"spring.ai.deepseek.http-client.connect-timeout=15s",
						"spring.ai.deepseek.http-client.read-timeout=45s",
						"spring.ai.deepseek.http-client.redirects=FOLLOW",
						"spring.ai.deepseek.http-client.ssl-bundle=my-bundle")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				var httpClientConfig = connectionProperties.getHttpClient();

				assertThat(httpClientConfig.isEnabled()).isTrue();
				assertThat(httpClientConfig.getConnectTimeout()).isEqualTo(Duration.ofSeconds(15));
				assertThat(httpClientConfig.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
				assertThat(httpClientConfig.getRedirects()).isEqualTo(HttpRedirects.FOLLOW);
				assertThat(httpClientConfig.getSslBundle()).isEqualTo("my-bundle");
			});
	}

	@Test
	public void httpClientDisabled() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.enabled=false")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				assertThat(connectionProperties.getHttpClient().isEnabled()).isFalse();

				// RestClientCustomizer should not be created when disabled
				assertThat(context.containsBean("deepSeekRestClientCustomizer")).isFalse();
			});
	}

	@Test
	public void restClientCustomizerCreated() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(RestClientCustomizer.class);
				assertThat(context).hasBean("deepSeekRestClientCustomizer");
			});
	}

	@Test
	public void httpClientWithChatOptions() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.deepseek.api-key=API_KEY",
						"spring.ai.deepseek.base-url=TEST_BASE_URL",
						"spring.ai.deepseek.http-client.connect-timeout=8s",
						"spring.ai.deepseek.http-client.read-timeout=40s",
						"spring.ai.deepseek.chat.options.model=deepseek-chat",
						"spring.ai.deepseek.chat.options.temperature=0.7")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(DeepSeekChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);
				var chatProperties = context.getBean(DeepSeekChatProperties.class);

				// HTTP client configuration
				assertThat(connectionProperties.getHttpClient().getConnectTimeout()).isEqualTo(Duration.ofSeconds(8));
				assertThat(connectionProperties.getHttpClient().getReadTimeout()).isEqualTo(Duration.ofSeconds(40));

				// Chat options
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("deepseek-chat");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.7);
			});
	}

}
