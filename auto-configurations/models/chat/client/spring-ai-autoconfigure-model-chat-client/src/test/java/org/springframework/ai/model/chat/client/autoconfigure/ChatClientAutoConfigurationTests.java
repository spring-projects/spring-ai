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

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.DefaultChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestAttributesThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ChatClientAutoConfiguration} {@link ToolCallingManager} and
 * {@link ToolCallingAdvisor.Builder} wiring.
 *
 * @author Christian Tzolov
 */
class ChatClientAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
		.withBean(ChatModel.class, () -> mock(ChatModel.class));

	// The request attributes accessor is servlet specific, so its auto-configuration only
	// applies to servlet web applications.
	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
		.withBean(ChatModel.class, () -> mock(ChatModel.class));

	@Test
	void autoConfigurationOrdersAfterToolCallingAutoConfiguration() {
		AutoConfiguration autoConfiguration = ChatClientAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

		assertThat(autoConfiguration.after()).containsExactly(ToolCallingAutoConfiguration.class);
	}

	@Test
	void requestAttributesAccessorIsRegisteredWithTheContextRegistry() {
		ContextRegistry registry = ContextRegistry.getInstance();
		ThreadLocalAccessor<?> previousAccessor = removeRequestAttributesAccessor(registry);

		try {
			this.webContextRunner.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(RequestAttributesContextPropagationRegistrar.class)).isNotNull();
				assertThat(registry.getThreadLocalAccessors())
					.as("the accessor has to be registered to make request attributes reach the tools")
					.anyMatch(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()));
			});
		}
		finally {
			restoreRequestAttributesAccessor(registry, previousAccessor);
		}
	}

	@Test
	void requestAttributesAccessorIsRemovedWhenTheContextCloses() {
		ContextRegistry registry = ContextRegistry.getInstance();
		ThreadLocalAccessor<?> previousAccessor = removeRequestAttributesAccessor(registry);

		try {
			this.webContextRunner.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(registry.getThreadLocalAccessors())
					.anyMatch(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()));
			});

			assertThat(registry.getThreadLocalAccessors())
				.as("closing the context has to remove the accessor it registered")
				.noneMatch(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()));
		}
		finally {
			restoreRequestAttributesAccessor(registry, previousAccessor);
		}
	}

	@Test
	void preexistingAccessorIsNotOverriddenOrRemovedByTheAutoConfiguration() {
		ContextRegistry registry = ContextRegistry.getInstance();
		ThreadLocalAccessor<?> previousAccessor = removeRequestAttributesAccessor(registry);
		RequestAttributesThreadLocalAccessor preexisting = new RequestAttributesThreadLocalAccessor();
		registry.registerThreadLocalAccessor(preexisting);

		try {
			this.webContextRunner.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(registry.getThreadLocalAccessors())
					.filteredOn(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()))
					.containsExactly(preexisting);
			});

			assertThat(registry.getThreadLocalAccessors())
				.filteredOn(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()))
				.containsExactly(preexisting);
		}
		finally {
			restoreRequestAttributesAccessor(registry, previousAccessor);
		}
	}

	@Test
	void requestAttributesAccessorIsNotRegisteredForNonServletApplication() {
		ContextRegistry registry = ContextRegistry.getInstance();
		ThreadLocalAccessor<?> previousAccessor = removeRequestAttributesAccessor(registry);

		try {
			this.contextRunner.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(RequestAttributesContextPropagationRegistrar.class);
				assertThat(registry.getThreadLocalAccessors())
					.as("the servlet specific accessor must not be registered outside a servlet application")
					.noneMatch(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()));
			});
		}
		finally {
			restoreRequestAttributesAccessor(registry, previousAccessor);
		}
	}

	private static ThreadLocalAccessor<?> removeRequestAttributesAccessor(ContextRegistry registry) {
		ThreadLocalAccessor<?> previousAccessor = registry.getThreadLocalAccessors()
			.stream()
			.filter(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()))
			.findFirst()
			.orElse(null);
		registry.removeThreadLocalAccessor(RequestAttributesThreadLocalAccessor.KEY);
		return previousAccessor;
	}

	private static void restoreRequestAttributesAccessor(ContextRegistry registry,
			ThreadLocalAccessor<?> previousAccessor) {
		registry.removeThreadLocalAccessor(RequestAttributesThreadLocalAccessor.KEY);
		if (previousAccessor != null) {
			registry.registerThreadLocalAccessor(previousAccessor);
		}
	}

	@Test
	void toolCallingAdvisorBuilderBeanIsAutoConfigured() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(ToolCallingAdvisor.Builder.class)).isNotNull();
		});
	}

	@Test
	void toolCallingAdvisorBuilderUsesInjectedToolCallingManager() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);
			assertThat(ReflectionTestUtils.getField(advisorBuilder, "toolCallingManager")).isSameAs(manager);
		});
	}

	@Test
	void toolCallingAdvisorBuilderUsesAutoConfiguredToolCallingManager() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class)).run(context -> {
			var toolCallingManager = context.getBean(ToolCallingManager.class);
			var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);
			assertThat(ReflectionTestUtils.getField(advisorBuilder, "toolCallingManager")).isSameAs(toolCallingManager);
		});
	}

	@Test
	void toolCallingAdvisorBuilderIsWiredIntoChatClientBuilder() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);

			ChatClient.Builder chatClientBuilder = context.getBean(ChatClient.Builder.class);
			var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils
				.getField(chatClientBuilder, "defaultRequest");
			assertThat(ReflectionTestUtils.getField(defaultRequest, "toolCallingAdvisorBuilder"))
				.isSameAs(advisorBuilder);
		});
	}

	@Test
	void advisorOrderPropertyIsAppliedToToolCallingAdvisorBuilder() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager)
			.withPropertyValues("spring.ai.chat.client.tool-calling.advisor-order=500")
			.run(context -> {
				assertThat(context).hasNotFailed();
				var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(advisorBuilder.getAdvisorOrder()).isEqualTo(500);
			});
	}

	@Test
	void toolExecutionEligibilityCheckerBeanIsWiredIntoAdvisorBuilder() {
		var manager = mock(ToolCallingManager.class);
		ToolExecutionEligibilityChecker customChecker = chatResponse -> false;

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager)
			.withBean(ToolExecutionEligibilityChecker.class, () -> customChecker)
			.run(context -> {
				var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(advisorBuilder, "toolExecutionEligibilityChecker"))
					.isSameAs(customChecker);
			});
	}

	@Test
	void defaultToolExecutionEligibilityCheckerIsUsedWhenNoBeanPresent() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager).run(context -> {
			var advisorBuilder = context.getBean(ToolCallingAdvisor.Builder.class);
			assertThat(ReflectionTestUtils.getField(advisorBuilder, "toolExecutionEligibilityChecker")).isNotNull();
		});
	}

	@Test
	void toolCallingDisabledPropertySetsAutoRegisterFalse() {
		var manager = mock(ToolCallingManager.class);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager)
			.withPropertyValues("spring.ai.chat.client.tool-calling.enabled=false")
			.run(context -> {
				ChatClient.Builder chatClientBuilder = context.getBean(ChatClient.Builder.class);
				var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils
					.getField(chatClientBuilder, "defaultRequest");
				@SuppressWarnings("unchecked")
				var advisorParams = (java.util.Map<String, Object>) ReflectionTestUtils.getField(defaultRequest,
						"advisorParams");
				assertThat(advisorParams)
					.containsEntry(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
			});
	}

	@Test
	void customToolCallingAdvisorBuilderBeanSuppressesAutoConfiguration() {
		var manager = mock(ToolCallingManager.class);
		var customAdvisorBuilder = ToolCallingAdvisor.builder().toolCallingManager(manager);

		this.contextRunner.withBean(ToolCallingManager.class, () -> manager)
			.withBean(ToolCallingAdvisor.Builder.class, () -> customAdvisorBuilder)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(ToolCallingAdvisor.Builder.class)).isSameAs(customAdvisorBuilder);
			});
	}

	@Test
	void ambiguousToolCallingManagerBeansFailAtContextStartup() {
		var defaultManager = mock(ToolCallingManager.class);
		var customManager = mock(ToolCallingManager.class);

		// toolCallingAdvisorBuilder is a singleton; two ambiguous ToolCallingManager
		// beans
		// cause the context itself to fail at startup.
		this.contextRunner.withBean("customToolCallingManager", ToolCallingManager.class, () -> customManager)
			.withBean("defaultToolCallingManager", ToolCallingManager.class, () -> defaultManager)
			.run(context -> assertThat(context).hasFailed());
	}

}
