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

package org.springframework.ai.model.ollama.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Nicolas Krier
 * @since 0.8.0
 */
class OllamaChatAutoConfigurationTests {

	@Test
	void propertiesTest() {
		var outputSchemaResource = new ClassPathResource("schemas/country-json-schema.json");

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.ollama.base-url=TEST_BASE_URL",
				"spring.ai.ollama.chat.options.model=MODEL_XYZ",
				"spring.ai.ollama.chat.options.output-schema-resource=classpath:schemas/country-json-schema.json",
				"spring.ai.ollama.chat.options.temperature=0.55",
				"spring.ai.ollama.chat.options.topP=0.56",
				"spring.ai.ollama.chat.options.topK=123")
			// @formatter:on

			.withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaChatAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(OllamaConnectionProperties.class);
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				var chatProperties = context.getBean(OllamaChatProperties.class);
				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				var chatOptions = chatProperties.getOptions();
				assertThat(chatOptions.getTemperature()).isEqualTo(0.55);
				assertThat(chatOptions.getTopP()).isEqualTo(0.56);
				assertThat(chatOptions.getTopK()).isEqualTo(123);
				assertThat(new JsonContent<>(getClass(), null, chatOptions.getOutputSchema()))
					.isEqualToJson(outputSchemaResource);
			});
	}

}
