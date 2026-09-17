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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.Api;
import org.springframework.ai.openai.responses.ServersideTool;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint side of {@link OpenAiChatAutoConfiguration}: one {@code ChatModel} bean
 * either way, with {@code spring.ai.openai.chat.api} and the model deciding which OpenAI
 * endpoint it talks to.
 *
 * @author Dimitar Proynov
 */
class OpenAiChatAutoConfigurationResponsesApiTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=abc123")
		.withConfiguration(
				AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void oneChatModelBeanRegardlessOfTheEndpoint() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
		});
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses").run(context -> {
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
		});
	}

	@Test
	void theEndpointIsChosenByTheApiProperty() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses")
			.run(context -> assertThat(options(context).resolveApi()).isEqualTo(Api.RESPONSES));
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=chat-completions")
			.run(context -> assertThat(options(context).resolveApi()).isEqualTo(Api.CHAT_COMPLETIONS));
	}

	/**
	 * The default, and the reason the property exists at all: the endpoint follows the
	 * model unless the application says otherwise.
	 */
	@Test
	void withoutTheApiPropertyTheModelDecides() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.model=gpt-5.6-luna")
			.run(context -> assertThat(options(context).resolveApi()).isEqualTo(Api.RESPONSES));
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.model=gpt-4o")
			.run(context -> assertThat(options(context).resolveApi()).isEqualTo(Api.CHAT_COMPLETIONS));
	}

	/**
	 * Configuring something only the Responses API provides is as good as asking for it,
	 * because the alternative is a request that silently does less than it was told to.
	 */
	@Test
	void aResponsesOnlyPropertyIsEnoughToSelectTheEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.model=gpt-4o",
					"spring.ai.openai.chat.serverside-tools.web-search.enabled=true")
			.run(context -> assertThat(options(context).resolveApi()).isEqualTo(Api.RESPONSES));
	}

	@Test
	void noChatModelBeanWhenOpenAiIsNotTheChatProvider() {
		this.contextRunner.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> assertThat(context).doesNotHaveBean(OpenAiChatModel.class));
	}

	@Test
	void responsesPropertiesBindOntoTheOptions() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.chat.api=responses",
				"spring.ai.openai.chat.model=gpt-5-mini",
				"spring.ai.openai.chat.max-completion-tokens=512",
				"spring.ai.openai.chat.temperature=0.3",
				"spring.ai.openai.chat.reasoning-effort=high",
				"spring.ai.openai.chat.reasoning-summary=auto",
				"spring.ai.openai.chat.verbosity=low",
				"spring.ai.openai.chat.truncation=auto",
				"spring.ai.openai.chat.include=web_search_call.results",
				"spring.ai.openai.chat.strict=true",
				"spring.ai.openai.chat.max-tool-calls=3",
				"spring.ai.openai.chat.safety-identifier=user-42")
				// @formatter:on
			.run(context -> {
				var options = options(context);

				assertThat(options.getModel()).isEqualTo("gpt-5-mini");
				assertThat(options.getMaxOutputTokens()).isEqualTo(512);
				assertThat(options.getTemperature()).isEqualTo(0.3);
				assertThat(options.getReasoningEffort()).isEqualTo("high");
				assertThat(options.getReasoningSummary()).isEqualTo("auto");
				assertThat(options.getVerbosity()).isEqualTo("low");
				assertThat(options.getTruncation()).isEqualTo("auto");
				assertThat(options.getInclude()).containsExactly("web_search_call.results");
				assertThat(options.getStrict()).isTrue();
				assertThat(options.getMaxToolCalls()).isEqualTo(3);
				assertThat(options.getSafetyIdentifier()).isEqualTo("user-42");
			});
	}

	@Test
	void serversideToolsAreConfigurableDeclaratively() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.chat.api=responses",
				"spring.ai.openai.chat.serverside-tools.web-search.enabled=true",
				"spring.ai.openai.chat.serverside-tools.web-search.search-context-size=high",
				"spring.ai.openai.chat.serverside-tools.file-search.vector-store-ids=vs_1,vs_2",
				"spring.ai.openai.chat.serverside-tools.code-interpreter.enabled=true")
				// @formatter:on
			.run(context -> {
				List<ServersideTool> serversideTools = options(context).getServersideTools();

				assertThat(serversideTools).containsExactly(new ServersideTool.WebSearch("high", null),
						new ServersideTool.FileSearch(List.of("vs_1", "vs_2"), null),
						new ServersideTool.CodeInterpreter(null));
			});
	}

	@Test
	void noServersideToolsAreConfiguredByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses")
			.run(context -> assertThat(options(context).getServersideTools()).isNull());
	}

	/**
	 * GitHub Models only implements Chat Completions, so asking for the Responses API is
	 * a configuration error rather than something to fall back from.
	 */
	@Test
	void noSupportForGithubModels() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.api=responses",
					"spring.ai.openai.base-url=https://models.github.ai/inference")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void connectionSettingsFallBackToTheCommonProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.chat.api=responses",
				"spring.ai.openai.base-url=http://TEST.BASE.URL")
				// @formatter:on
			.run(context -> {
				var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(
						context.getBean(OpenAiCommonProperties.class), context.getBean(OpenAiChatProperties.class));

				assertThat(resolved.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(resolved.getApiKey()).isEqualTo("abc123");
			});
	}

	private static OpenAiChatOptions options(ApplicationContext context) {
		return context.getBean(OpenAiChatModel.class).getOptions();
	}

}
