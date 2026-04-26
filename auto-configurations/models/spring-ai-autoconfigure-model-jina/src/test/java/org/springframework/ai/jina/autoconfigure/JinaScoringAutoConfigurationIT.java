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

package org.springframework.ai.jina.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.jina.JinaScoringModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JinaScoringAutoConfiguration}.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
public class JinaScoringAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JinaScoringAutoConfiguration.class));

	@Test
	void elementActivation() {
		// Test when API key is missing
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(JinaScoringProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(JinaScoringModel.class)).isEmpty();
		});

		// Test when API key is present
		this.contextRunner.withPropertyValues("spring.ai.jina.scoring.api-key=TEST_API_KEY").run(context -> {
			assertThat(context.getBeansOfType(JinaScoringProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(JinaScoringModel.class)).isNotEmpty();
		});

		// Test when component is explicitly disabled
		this.contextRunner
			.withPropertyValues("spring.ai.jina.scoring.enabled=false", "spring.ai.jina.scoring.api-key=TEST_API_KEY")
			.run(context -> {
				assertThat(context.getBeansOfType(JinaScoringProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(JinaScoringModel.class)).isEmpty();
			});
	}

	@Test
	void optionsBinding() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.jina.scoring.api-key=TEST_API_KEY",
				"spring.ai.jina.scoring.base-url=http://TEST.BASE.URL",
				"spring.ai.jina.scoring.options.model=CUSTOM_MODEL",
				"spring.ai.jina.scoring.options.top-k=10"
				// @formatter:on
		).run(context -> {
			var properties = context.getBean(JinaScoringProperties.class);

			assertThat(properties.getApiKey()).isEqualTo("TEST_API_KEY");
			assertThat(properties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

			assertThat(properties.getOptions().getModel()).isEqualTo("CUSTOM_MODEL");
			assertThat(properties.getOptions().getTopK()).isEqualTo(10);
		});
	}

	@Test
	void customScoringModelBean() {
		this.contextRunner.withPropertyValues("spring.ai.jina.scoring.api-key=TEST_API_KEY")
			.withUserConfiguration(CustomScoringModelConfig.class)
			.run(context -> {
				assertThat(context.getBeansOfType(JinaScoringModel.class)).hasSize(1);
				assertThat(context.getBean(JinaScoringModel.class)).isSameAs(CustomScoringModelConfig.customModel);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomScoringModelConfig {

		static final JinaScoringModel customModel = JinaScoringModel.builder()
			.jinaScoringApi(org.springframework.ai.jina.api.JinaScoringApi.builder().apiKey("DUMMY_KEY").build())
			.build();

		@Bean
		public JinaScoringModel customJinaScoringModel() {
			return customModel;
		}

	}

}
