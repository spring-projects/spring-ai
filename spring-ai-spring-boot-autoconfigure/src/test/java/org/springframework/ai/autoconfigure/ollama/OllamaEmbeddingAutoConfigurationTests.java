/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.ollama;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaEmbeddingAutoConfigurationTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.ollama.base-url=TEST_BASE_URL", "spring.ai.ollama.embedding.model=MODEL_XYZ",
					"spring.ai.ollama.embedding.options.temperature=0.13", "spring.ai.ollama.embedding.options.topK=13")
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, OllamaAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OllamaEmbeddingProperties.class);
				var connectionProperties = context.getBean(OllamaConnectionProperties.class);

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(embeddingProperties.getOptions().toMap()).containsKeys("temperature");
				assertThat(embeddingProperties.getOptions().toMap().get("temperature")).isEqualTo(0.13);
				assertThat(embeddingProperties.getOptions().getTopK()).isEqualTo(13);
			});
	}

}
