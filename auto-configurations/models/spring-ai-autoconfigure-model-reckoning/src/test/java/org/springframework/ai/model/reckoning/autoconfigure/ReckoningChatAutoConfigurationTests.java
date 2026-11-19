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

package org.springframework.ai.model.reckoning.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.reckoning.api.ReckoningApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReckoningChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ReckoningChatAutoConfiguration.class)
		.withBean(ReckoningApi.class, () -> mock(ReckoningApi.class));

	@Test
	void createsChatModelFromProperties() {
		this.contextRunner.withPropertyValues("spring.ai.reckoning.chat.model=test-model").run(context -> {
			assertThat(context).hasSingleBean(ChatModel.class);
			assertThat(context).hasSingleBean(ReckoningChatProperties.class);
			assertThat(context.getBean(ReckoningChatProperties.class).getModel()).isEqualTo("test-model");
		});
	}

	@Test
	void backsOffWhenChatModelExists() {
		this.contextRunner.withBean(ChatModel.class, () -> mock(ChatModel.class))
			.run(context -> assertThat(context)
				.doesNotHaveBean(org.springframework.ai.reckoning.ReckoningChatModel.class));
	}

}
