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

package org.springframework.ai.model.chat.client.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ChatClientAutoConfiguration} {@link ToolCallingManager}
 * injection.
 *
 * @author Christian Tzolov
 */
class ChatClientAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
		.withBean(ChatModel.class, () -> mock(ChatModel.class));

	@Test
	void chatClientBuilderUsesInjectedToolCallingManager() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);
			var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
					"defaultRequest");
			assertThat(ReflectionTestUtils.getField(defaultRequest, "toolCallingManager")).isSameAs(manager);
		});
	}

	@Test
	void ambiguousToolCallingManagerBeansFailAtBuilderInstantiation() {
		var defaultManager = mock(ToolCallingManager.class);
		var customManager = mock(ToolCallingManager.class);

		// chatClientBuilder is prototype-scoped, so the context starts fine even with two
		// ambiguous ToolCallingManager beans. The failure surfaces only when the builder
		// is actually requested.
		this.contextRunner.withBean("customToolCallingManager", ToolCallingManager.class, () -> customManager)
			.withBean("defaultToolCallingManager", ToolCallingManager.class, () -> defaultManager)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThatThrownBy(() -> context.getBean(ChatClient.Builder.class)).isInstanceOf(Exception.class);
			});
	}

	@Test
	void singleToolCallingManagerBeanIsWiredIntoChatClientBuilder() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(ToolCallingManager.class)).isSameAs(manager);

			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);
			var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
					"defaultRequest");
			assertThat(ReflectionTestUtils.getField(defaultRequest, "toolCallingManager")).isSameAs(manager);
		});
	}

}
