/*
 * Copyright 2025-2025 the original author or authors.
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

import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link HuggingfaceChatAutoConfiguration}'s conditional enabling of
 * models.
 *
 * @author Ilayaperumal Gopinathan
 */
public class HuggingfaceModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.huggingface.api-key=TEST_API_KEY")
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
		.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
				HuggingfaceChatAutoConfiguration.class));

	@Test
	void chatModelActivation() {
		this.contextRunner.run(context -> assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isNotEmpty());

		this.contextRunner.withPropertyValues("spring.ai.model.chat=none").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceChatProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.chat=huggingface").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(HuggingfaceChatModel.class)).isNotEmpty();
		});
	}

}
