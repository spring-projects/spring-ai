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
import org.springframework.ai.openai.responses.HostedTool;
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two OpenAI chat endpoints are mutually exclusive, because {@code ChatClient}
 * auto-configuration needs exactly one {@code ChatModel} bean.
 *
 * @author Dimitar Proynov
 */
class OpenAiResponsesChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=abc123")
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,
				OpenAiResponsesChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void chatCompletionsIsTheDefault() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiResponsesChatModel.class);
			assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
		});
	}

	@Test
	void selectingResponsesReplacesTheChatCompletionsBeanRatherThanAddingToIt() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses").run(context -> {
			assertThat(context).hasSingleBean(OpenAiResponsesChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
		});
	}

	@Test
	void neitherBeanIsCreatedWhenOpenAiIsNotTheChatProvider() {
		this.contextRunner.withPropertyValues("spring.ai.model.chat=none").run(context -> {
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OpenAiResponsesChatModel.class);
		});
	}

	@Test
	void responsesPropertiesBindOntoTheOptions() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.chat.api=responses",
				"spring.ai.openai.responses.model=gpt-5-mini",
				"spring.ai.openai.responses.max-output-tokens=512",
				"spring.ai.openai.responses.temperature=0.3",
				"spring.ai.openai.responses.reasoning-effort=high",
				"spring.ai.openai.responses.reasoning-summary=auto",
				"spring.ai.openai.responses.verbosity=low",
				"spring.ai.openai.responses.truncation=auto",
				"spring.ai.openai.responses.include=web_search_call.results",
				"spring.ai.openai.responses.strict=true",
				"spring.ai.openai.responses.max-tool-calls=3",
				"spring.ai.openai.responses.safety-identifier=user-42")
				// @formatter:on
			.run(context -> {
				var options = context.getBean(OpenAiResponsesChatModel.class).getOptions();

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
	void hostedToolsAreConfigurableDeclaratively() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.chat.api=responses",
				"spring.ai.openai.responses.hosted-tools.web-search.enabled=true",
				"spring.ai.openai.responses.hosted-tools.web-search.search-context-size=high",
				"spring.ai.openai.responses.hosted-tools.file-search.vector-store-ids=vs_1,vs_2",
				"spring.ai.openai.responses.hosted-tools.code-interpreter.enabled=true")
				// @formatter:on
			.run(context -> {
				List<HostedTool> hostedTools = context.getBean(OpenAiResponsesChatModel.class)
					.getOptions()
					.getHostedTools();

				assertThat(hostedTools).containsExactly(new HostedTool.WebSearch("high", null),
						new HostedTool.FileSearch(List.of("vs_1", "vs_2"), null), new HostedTool.CodeInterpreter(null));
			});
	}

	@Test
	void noHostedToolsAreConfiguredByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses")
			.run(context -> assertThat(context.getBean(OpenAiResponsesChatModel.class).getOptions().getHostedTools())
				.isNull());
	}

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
						context.getBean(OpenAiCommonProperties.class),
						context.getBean(OpenAiResponsesChatProperties.class));

				assertThat(resolved.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(resolved.getApiKey()).isEqualTo("abc123");
			});
	}

	/**
	 * {@code api} binds to an enum, so a typo fails at startup with the property named,
	 * rather than leaving both conditions unmatched and the context without a
	 * {@code ChatModel} and without an explanation.
	 */
	@Test
	void anUnknownApiValueFailsToBindWithThePropertyNamed() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responsse").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("spring.ai.openai.chat.api");
		});
	}

}
