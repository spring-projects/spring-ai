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

package org.springframework.ai.http.client.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link SpringAiRestClientProperties}.
 *
 * @author Song Jaegeun
 */
public class SpringAiRestClientPropertiesTests {

	@Test
	public void restClientDefaultProperties() {

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SpringAiRestClientAutoConfiguration.class))
			.run(context -> {
				var httpClientProperties = context.getBean(SpringAiRestClientProperties.class);

				assertThat(httpClientProperties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(10));
				assertThat(httpClientProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
			});
	}

	@Test
	public void restClientCustomProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.rest.client.connection-timeout=10s",
				"spring.ai.rest.client.read-timeout=30s")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRestClientAutoConfiguration.class))
			.run(context -> {
				var httpClientProperties = context.getBean(SpringAiRestClientProperties.class);

				assertThat(httpClientProperties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(10));
				assertThat(httpClientProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
			});
	}

}
