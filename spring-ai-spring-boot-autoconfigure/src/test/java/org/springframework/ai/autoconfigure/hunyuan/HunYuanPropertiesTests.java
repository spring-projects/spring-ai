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

package org.springframework.ai.autoconfigure.hunyuan;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.hunyuan.HunYuanChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Guo Junyu
 */
public class HunYuanPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.hunyuan.base-url=TEST_BASE_URL",
				"spring.ai.hunyuan.secret-id=abc123",
				"spring.ai.hunyuan.secret-key=zaq123",
				"spring.ai.hunyuan.chat.options.model=MODEL_XYZ",
				"spring.ai.hunyuan.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(HunYuanChatProperties.class);
				var connectionProperties = context.getBean(HunYuanCommonProperties.class);

				assertThat(connectionProperties.getSecretId()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("zaq123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getSecretId()).isNull();
				assertThat(chatProperties.getSecretKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
		 "spring.ai.hunyuan.base-url=TEST_BASE_URL",
				"spring.ai.hunyuan.secret-id=abc123",
				"spring.ai.hunyuan.secret-key=zaq123",
				"spring.ai.hunyuan.chat.base-url=TEST_BASE_URL2",
				"spring.ai.hunyuan.chat.secret-id=456",
				"spring.ai.hunyuan.chat.secret-key=789",
				"spring.ai.hunyuan.chat.options.model=MODEL_XYZ",
				"spring.ai.hunyuan.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(HunYuanChatProperties.class);
				var connectionProperties = context.getBean(HunYuanCommonProperties.class);

				assertThat(connectionProperties.getSecretId()).isEqualTo("abc123");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("zaq123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getSecretId()).isEqualTo("456");
				assertThat(chatProperties.getSecretKey()).isEqualTo("789");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.hunyuan.secret-id=API_ID",
						"spring.ai.hunyuan.secret-key=API_KEY",
				"spring.ai.hunyuan.base-url=TEST_BASE_URL",

				"spring.ai.hunyuan.chat.options.model=MODEL_XYZ",
				"spring.ai.hunyuan.chat.options.n=10",
				"spring.ai.hunyuan.chat.options.responseFormat.type=json",
				"spring.ai.hunyuan.chat.options.seed=66",
				"spring.ai.hunyuan.chat.options.stop=boza,koza",
				"spring.ai.hunyuan.chat.options.temperature=0.55",
				"spring.ai.hunyuan.chat.options.topP=0.56"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(HunYuanChatProperties.class);
				var connectionProperties = context.getBean(HunYuanCommonProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getSecretId()).isEqualTo("API_ID");
				assertThat(connectionProperties.getSecretKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.hunyuan.secret-id=API_ID", "spring.ai.hunyuan.secret-key=API_KEY",
					"spring.ai.hunyuan.base-url=TEST_BASE_URL", "spring.ai.hunyuan.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(HunYuanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(HunYuanChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.hunyuan.secret-id=API_ID", "spring.ai.hunyuan.secret-key=API_KEY",
					"spring.ai.hunyuan.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(HunYuanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(HunYuanChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.hunyuan.secret-id=API_ID", "spring.ai.hunyuan.secret-key=API_KEY",
					"spring.ai.hunyuan.base-url=TEST_BASE_URL", "spring.ai.hunyuan.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, HunYuanAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(HunYuanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(HunYuanChatModel.class)).isNotEmpty();
			});
	}

}
