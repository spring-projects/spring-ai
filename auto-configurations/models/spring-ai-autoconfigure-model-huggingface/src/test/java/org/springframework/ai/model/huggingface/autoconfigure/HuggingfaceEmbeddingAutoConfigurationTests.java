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

package org.springframework.ai.model.huggingface.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HuggingfaceEmbeddingAutoConfiguration}.
 *
 * @author Myeongdeok Kang
 */
public class HuggingfaceEmbeddingAutoConfigurationTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
					"spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.embedding.url=https://test.huggingface.co/hf-inference/models",
					"spring.ai.huggingface.embedding.options.model=sentence-transformers/all-MiniLM-L6-v2",
					"spring.ai.huggingface.embedding.options.normalize=true"
					// @formatter:on
		)
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(HuggingfaceEmbeddingProperties.class);
				assertThat(context).hasSingleBean(HuggingfaceConnectionProperties.class);
			});
	}

	@Test
	public void embeddingActivationTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceEmbeddingAutoConfiguration.class))
			.run(context -> assertThat(context).doesNotHaveBean("huggingfaceEmbeddingModel"));

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceEmbeddingAutoConfiguration.class))
			.run(context -> assertThat(context)
				.hasSingleBean(org.springframework.ai.huggingface.HuggingfaceEmbeddingModel.class));
	}

}
