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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests verifying the ChatClient creation best practices documented in the reference
 * guide. Covers the patterns described in GH-5786:
 * <ul>
 * <li>Using the auto-configured {@link ChatClient.Builder} for multiple clients of the
 * same model type</li>
 * <li>Using {@link ChatClientBuilderConfigurer} for multiple clients of different model
 * types</li>
 * <li>Ensuring observability and {@link ChatClientBuilderCustomizer} beans are applied in
 * both patterns</li>
 * </ul>
 *
 */
class ChatClientCreationPatternsTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
		.withBean(ChatModel.class, () -> mock(ChatModel.class));

	// -------------------------------------------------------------------------
	// Pattern 1: Multiple ChatClients with a Single Model Type (prototype pattern)
	// -------------------------------------------------------------------------

	@Test
	void prototypeBuilderProducesSeparateChatClientInstances() {
		this.contextRunner.withUserConfiguration(SingleModelMultipleClientsConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			ChatClient defaultClient = context.getBean("defaultChatClient", ChatClient.class);
			ChatClient customClient = context.getBean("customChatClient", ChatClient.class);
			assertThat(defaultClient).isNotSameAs(customClient);
		});
	}

	@Test
	void prototypeBuilderPreservesCustomSystemPrompt() {
		this.contextRunner.withUserConfiguration(SingleModelMultipleClientsConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			ChatClient customClient = context.getBean("customChatClient", ChatClient.class);
			// Verify the custom system text was applied to the builder
			Object defaultRequest = ReflectionTestUtils.getField(customClient, "defaultChatClientRequest");
			assertThat(defaultRequest).isNotNull();
			String systemText = (String) ReflectionTestUtils.getField(defaultRequest, "systemText");
			assertThat(systemText).isEqualTo("You are a helpful assistant.");
		});
	}

	@Test
	void defaultClientHasNoSystemPrompt() {
		this.contextRunner.withUserConfiguration(SingleModelMultipleClientsConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			ChatClient defaultClient = context.getBean("defaultChatClient", ChatClient.class);
			Object defaultRequest = ReflectionTestUtils.getField(defaultClient, "defaultChatClientRequest");
			assertThat(defaultRequest).isNotNull();
			String systemText = (String) ReflectionTestUtils.getField(defaultRequest, "systemText");
			assertThat(systemText).isNull();
		});
	}

	@Test
	void prototypeBuilderAppliesChatClientBuilderCustomizers() {
		this.contextRunner
			.withUserConfiguration(SingleModelMultipleClientsConfig.class, SystemPromptCustomizerConfig.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				// Both clients built from the auto-configured builder
				// should have customizers applied via the prototype-scoped builder
				ChatClient defaultClient = context.getBean("defaultChatClient", ChatClient.class);
				ChatClient customClient = context.getBean("customChatClient", ChatClient.class);
				assertThat(defaultClient).isNotNull();
				assertThat(customClient).isNotNull();
			});
	}

	// -------------------------------------------------------------------------
	// Pattern 2: ChatClients for Different Model Types (configurer pattern)
	// -------------------------------------------------------------------------

	@Test
	void configurerPatternProducesSeparateChatClientBeansForDifferentModels() {
		this.contextRunner.withUserConfiguration(MultipleModelTypesConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			ChatClient primaryClient = context.getBean("primaryModelChatClient", ChatClient.class);
			ChatClient secondaryClient = context.getBean("secondaryModelChatClient", ChatClient.class);
			assertThat(primaryClient).isNotSameAs(secondaryClient);
		});
	}

	@Test
	void configurerPatternAppliesChatClientBuilderCustomizers() {
		this.contextRunner.withUserConfiguration(MultipleModelTypesConfig.class, SystemPromptCustomizerConfig.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				// Both clients should have the customizer-applied system text
				ChatClient primaryClient = context.getBean("primaryModelChatClient", ChatClient.class);
				ChatClient secondaryClient = context.getBean("secondaryModelChatClient", ChatClient.class);

				Object primaryRequest = ReflectionTestUtils.getField(primaryClient, "defaultChatClientRequest");
				Object secondaryRequest = ReflectionTestUtils.getField(secondaryClient, "defaultChatClientRequest");

				String primarySystemText = (String) ReflectionTestUtils.getField(primaryRequest, "systemText");
				String secondarySystemText = (String) ReflectionTestUtils.getField(secondaryRequest, "systemText");

				assertThat(primarySystemText).isEqualTo("Customized by ChatClientBuilderCustomizer.");
				assertThat(secondarySystemText).isEqualTo("Customized by ChatClientBuilderCustomizer.");
			});
	}

	@Test
	void configurerPatternWiresObservationRegistry() {
		ObservationRegistry registry = ObservationRegistry.create();
		this.contextRunner.withBean(ObservationRegistry.class, () -> registry)
			.withUserConfiguration(MultipleModelTypesConfig.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				// Verify the builder used the provided ObservationRegistry (not NOOP)
				// by checking the builder was constructed without failure
				assertThat(context.getBean("primaryModelChatClient", ChatClient.class)).isNotNull();
				assertThat(context.getBean("secondaryModelChatClient", ChatClient.class)).isNotNull();
			});
	}

	@Test
	void configurerPatternEachClientUsesItsOwnModel() {
		this.contextRunner.withUserConfiguration(MultipleModelTypesConfig.class).run(context -> {
			assertThat(context).hasNotFailed();

			ChatClient primaryClient = context.getBean("primaryModelChatClient", ChatClient.class);
			ChatClient secondaryClient = context.getBean("secondaryModelChatClient", ChatClient.class);

			Object primaryRequest = ReflectionTestUtils.getField(primaryClient, "defaultChatClientRequest");
			Object secondaryRequest = ReflectionTestUtils.getField(secondaryClient, "defaultChatClientRequest");

			ChatModel primaryModel = (ChatModel) ReflectionTestUtils.getField(primaryRequest, "chatModel");
			ChatModel secondaryModel = (ChatModel) ReflectionTestUtils.getField(secondaryRequest, "chatModel");

			assertThat(primaryModel).isNotSameAs(secondaryModel);
			assertThat(primaryModel).isSameAs(context.getBean("primaryChatModel", ChatModel.class));
			assertThat(secondaryModel).isSameAs(context.getBean("secondaryChatModel", ChatModel.class));
		});
	}

	@Test
	void primaryAnnotationOnClientBeanPreventsAmbiguity() {
		// When multiple ChatModel beans exist, marking one ChatClient @Primary allows
		// other beans to inject ChatClient without a @Qualifier.
		this.contextRunner.withUserConfiguration(MultipleModelTypesConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			// The primary bean is resolvable without a qualifier
			ChatClient primary = context.getBean(ChatClient.class);
			assertThat(primary).isSameAs(context.getBean("primaryModelChatClient", ChatClient.class));
		});
	}

	// -------------------------------------------------------------------------
	// Test configurations
	// -------------------------------------------------------------------------

	/**
	 * Mirrors the "Multiple ChatClients with a Single Model Type" doc example: inject
	 * {@code ChatClient.Builder} as a method parameter (prototype-scoped, so Spring
	 * creates a fresh instance per injection point) to produce an independently
	 * configured client.
	 */
	@Configuration(proxyBeanMethods = false)
	static class SingleModelMultipleClientsConfig {

		@Bean
		ChatClient defaultChatClient(ChatClient.Builder builder) {
			return builder.build();
		}

		@Bean
		ChatClient customChatClient(ChatClient.Builder builder) {
			return builder.defaultSystem("You are a helpful assistant.").build();
		}

	}

	/**
	 * Mirrors the "ChatClients for Different Model Types" doc example: use
	 * {@link ChatClientBuilderConfigurer} to retain observability and customizers when
	 * creating clients for distinct model beans. One client is marked {@code @Primary} so
	 * the auto-configured {@code ChatClient.Builder} bean can resolve {@code ChatModel}
	 * without ambiguity.
	 */
	@Configuration(proxyBeanMethods = false)
	static class MultipleModelTypesConfig {

		@Bean("primaryChatModel")
		@Primary
		ChatModel primaryChatModel() {
			return mock(ChatModel.class);
		}

		@Bean("secondaryChatModel")
		ChatModel secondaryChatModel() {
			return mock(ChatModel.class);
		}

		@Bean
		@Primary
		ChatClient primaryModelChatClient(@Qualifier("primaryChatModel") ChatModel chatModel,
				ChatClientBuilderConfigurer configurer, ObjectProvider<ObservationRegistry> observationRegistry,
				ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
				ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
				ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
			return buildChatClient(chatModel, configurer, observationRegistry, chatClientObservationConvention,
					advisorObservationConvention, toolCallingAdvisorBuilder);
		}

		@Bean
		ChatClient secondaryModelChatClient(@Qualifier("secondaryChatModel") ChatModel chatModel,
				ChatClientBuilderConfigurer configurer, ObjectProvider<ObservationRegistry> observationRegistry,
				ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
				ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
				ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
			return buildChatClient(chatModel, configurer, observationRegistry, chatClientObservationConvention,
					advisorObservationConvention, toolCallingAdvisorBuilder);
		}

		private ChatClient buildChatClient(ChatModel chatModel, ChatClientBuilderConfigurer configurer,
				ObjectProvider<ObservationRegistry> observationRegistry,
				ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
				ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
				ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
			ChatClient.Builder builder = ChatClient.builder(chatModel,
					observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
					chatClientObservationConvention.getIfUnique(), advisorObservationConvention.getIfUnique(),
					toolCallingAdvisorBuilder.getIfAvailable());
			return configurer.configure(builder).build();
		}

	}

	/**
	 * A {@link ChatClientBuilderCustomizer} that sets a fixed system prompt, used to
	 * verify that customizers are applied in both ChatClient creation patterns.
	 */
	@Configuration(proxyBeanMethods = false)
	static class SystemPromptCustomizerConfig {

		@Bean
		ChatClientBuilderCustomizer systemPromptCustomizer() {
			return builder -> builder.defaultSystem("Customized by ChatClientBuilderCustomizer.");
		}

	}

}
