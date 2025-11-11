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

package org.springframework.ai.utils;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Issam El-atif
 */
class SpringAiTestAutoConfigurationsTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void shouldLoadProvidedConfiguration() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(SimpleConfiguration.class))
			.run(context -> assertThat(context).hasSingleBean(SimpleConfiguration.class));
	}

	@Test
	void shouldIncludeConfigurationsDeclaredInAfterAttribute() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AfterConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(SimpleConfiguration.class);
				assertThat(context).hasSingleBean(AfterConfiguration.class);
			});
	}

	@AutoConfiguration
	static class SimpleConfiguration {

	}

	@AutoConfiguration(after = { SimpleConfiguration.class })
	static class AfterConfiguration {

	}

}
