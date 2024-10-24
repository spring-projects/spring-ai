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

package org.springframework.ai.autoconfigure.retry;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link SpringAiRetryProperties}.
 *
 * @author Christian Tzolov
 */
public class SpringAiRetryPropertiesTests {

	@Test
	public void retryDefaultProperties() {

		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
			.run(context -> {
				var retryProperties = context.getBean(SpringAiRetryProperties.class);

				assertThat(retryProperties.getMaxAttempts()).isEqualTo(10);
				// do not retry on 4xx errors
				assertThat(retryProperties.isOnClientErrors()).isFalse();
				assertThat(retryProperties.getExcludeOnHttpCodes()).isEmpty();
				assertThat(retryProperties.getOnHttpCodes()).isEmpty();
				assertThat(retryProperties.getBackoff().getInitialInterval().toMillis()).isEqualTo(2000);
				assertThat(retryProperties.getBackoff().getMultiplier()).isEqualTo(5);
				assertThat(retryProperties.getBackoff().getMaxInterval().toMillis()).isEqualTo(3 * 60000);
			});
	}

	@Test
	public void retryCustomProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.retry.max-attempts=100",
				"spring.ai.retry.on-client-errors=false",
				"spring.ai.retry.exclude-on-http-codes=404,500",
				"spring.ai.retry.on-http-codes=429",
				"spring.ai.retry.backoff.initial-interval=1000",
				"spring.ai.retry.backoff.multiplier=2",
				"spring.ai.retry.backoff.max-interval=60000" )
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
			.run(context -> {
				var retryProperties = context.getBean(SpringAiRetryProperties.class);

				assertThat(retryProperties.getMaxAttempts()).isEqualTo(100);
				assertThat(retryProperties.isOnClientErrors()).isFalse();
				assertThat(retryProperties.getExcludeOnHttpCodes()).containsExactly(404, 500);
				assertThat(retryProperties.getOnHttpCodes()).containsExactly(429);
				assertThat(retryProperties.getBackoff().getInitialInterval().toMillis()).isEqualTo(1000);
				assertThat(retryProperties.getBackoff().getMultiplier()).isEqualTo(2);
				assertThat(retryProperties.getBackoff().getMaxInterval().toMillis()).isEqualTo(60000);
			});
	}

}
