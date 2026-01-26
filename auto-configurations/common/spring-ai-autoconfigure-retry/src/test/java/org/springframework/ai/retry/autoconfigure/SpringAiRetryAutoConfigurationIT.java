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

package org.springframework.ai.retry.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class SpringAiRetryAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.forThisModuleAnd());

	@Test
	void testRetryAutoConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RetryTemplate.class);
			assertThat(context).hasSingleBean(ResponseErrorHandler.class);
			assertThat(context).getBean(RetryTemplate.class)
				.extracting("retryPolicy.backOff.maxAttempts")
				.isEqualTo(10L /* our default */);
		});
	}

	@Test
	void testRetryAutoConfigurationBeanAlreadyDefined() {
		this.contextRunner.withUserConfiguration(MyConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(RetryTemplate.class);
			assertThat(context).hasSingleBean(ResponseErrorHandler.class);
			assertThat(context).getBean(RetryTemplate.class)
				.extracting("retryPolicy.backOff.maxAttempts")
				.isEqualTo(3L /* RetryTemplate default */);
		});
	}

	@Configuration
	static class MyConfiguration {

		@Bean
		public RetryTemplate retryTemplate() {
			return new RetryTemplate();
		}

	}

}
