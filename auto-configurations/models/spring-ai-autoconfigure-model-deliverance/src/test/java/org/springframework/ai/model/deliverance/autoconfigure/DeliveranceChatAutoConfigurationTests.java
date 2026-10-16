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

package org.springframework.ai.model.deliverance.autoconfigure;

import io.teknek.deliverance.client.spring.model.CreateChatCompletionRequest;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponse;
import io.teknek.deliverance.client.spring.model.ListModelsResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deliverance.DeliveranceChatModel;
import org.springframework.ai.deliverance.DeliveranceChatOptions;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(DeliveranceChatAutoConfiguration.class))
		.withBean(ToolCallingManager.class, () -> ToolCallingManager.builder().build());

	@Test
	void chatModelCreatedWhenDeliveranceSelected() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=deliverance", "spring.ai.deliverance.chat.model=test-model")
			.run(context -> {
				assertThat(context).hasSingleBean(DeliveranceApi.class);
				assertThat(context).hasSingleBean(DeliveranceChatModel.class);
			});
	}

	@Test
	void customDeliveranceApiIsUsed() {
		this.contextRunner.withBean(DeliveranceApi.class, TestDeliveranceApi::new)
			.withPropertyValues("spring.ai.model.chat=deliverance", "spring.ai.deliverance.chat.model=test-model")
			.run(context -> {
				assertThat(context).hasSingleBean(DeliveranceApi.class);
				assertThat(context.getBean(DeliveranceApi.class)).isInstanceOf(TestDeliveranceApi.class);
			});
	}

	@Test
	void chatPropertiesMapUniformTopP() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=deliverance", "spring.ai.deliverance.chat.model=test-model",
					"spring.ai.deliverance.chat.top-p=0.95", "spring.ai.deliverance.chat.uniform-top-p=1.0")
			.run(context -> {
				DeliveranceChatOptions options = context.getBean(DeliveranceChatModel.class).getOptions();
				assertThat(options.getTopP()).isEqualTo(0.95);
				assertThat(options.getUniformTopP()).isEqualTo(1.0);
			});
	}

	private static final class TestDeliveranceApi implements DeliveranceApi {

		@Override
		public CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request) {
			return null;
		}

		@Override
		public Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request) {
			return Flux.empty();
		}

		@Override
		public ListModelsResponse listModels() {
			return new ListModelsResponse();
		}

	}

}
