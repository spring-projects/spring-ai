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

package org.springframework.ai.model.watsonxai.autoconfigure;

import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class WatsonxAiAutoConfigurationTests {

	@Test
	public void propertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
			"spring.ai.watsonx.ai.base-url=TEST_BASE_URL",
			"spring.ai.watsonx.ai.stream-endpoint=ml/v1/text/generation_stream?version=2023-05-29",
			"spring.ai.watsonx.ai.text-endpoint=ml/v1/text/generation?version=2023-05-29",
			"spring.ai.watsonx.ai.embedding-endpoint=ml/v1/text/embeddings?version=2023-05-29",
			"spring.ai.watsonx.ai.projectId=1",
			"spring.ai.watsonx.ai.IAMToken=123456")
                // @formatter:on
			.withConfiguration(
					AutoConfigurations.of(RestClientAutoConfiguration.class, WatsonxAiAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(WatsonxAiConnectionProperties.class);
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getStreamEndpoint())
					.isEqualTo("ml/v1/text/generation_stream?version=2023-05-29");
				assertThat(connectionProperties.getTextEndpoint())
					.isEqualTo("ml/v1/text/generation?version=2023-05-29");
				assertThat(connectionProperties.getEmbeddingEndpoint())
					.isEqualTo("ml/v1/text/embeddings?version=2023-05-29");
				assertThat(connectionProperties.getProjectId()).isEqualTo("1");
				assertThat(connectionProperties.getIAMToken()).isEqualTo("123456");
			});
	}

}
