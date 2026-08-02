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

package org.springframework.ai.model.tool.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolCallingAutoConfiguration}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @author Yanming Zhou
 */
class ToolCallingAutoConfigurationTests {

	@Test
	void beansAreCreated() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				var toolExecutionExceptionProcessor = context.getBean(ToolExecutionExceptionProcessor.class);
				assertThat(toolExecutionExceptionProcessor).isInstanceOf(DefaultToolExecutionExceptionProcessor.class);

				var toolCallingManager = context.getBean(ToolCallingManager.class);
				assertThat(toolCallingManager).isInstanceOf(DefaultToolCallingManager.class);
			});
	}

	@Test
	void resolveMultipleFunctionAndToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				assertThat(toolCallbackResolver.resolve("getForecast")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getForecast").getToolDefinition().name())
					.isEqualTo("getForecast");

				assertThat(toolCallbackResolver.resolve("getAlert")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getAlert").getToolDefinition().name()).isEqualTo("getAlert");

				assertThat(toolCallbackResolver.resolve("weatherFunction1")).isNotNull();
				assertThat(toolCallbackResolver.resolve("weatherFunction1").getToolDefinition().name())
					.isEqualTo("weatherFunction1");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather3")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather3").getToolDefinition().name())
					.isEqualTo("getCurrentWeather3");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather4")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather4").getToolDefinition().name())
					.isEqualTo("getCurrentWeather4");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather5")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather5").getToolDefinition().name())
					.isEqualTo("getCurrentWeather5");
			});
	}

	@Test
	void resolveMissingToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				assertThat(toolCallbackResolver.resolve("NonExisting")).isNull();
			});
	}

	@Test
	void observationFilterDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> assertThat(context).doesNotHaveBean(ToolCallingContentObservationFilter.class));
	}

	@Test
	void observationFilterEnabled() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.observations.include-content=true")
			.withUserConfiguration(Config.class)
			.run(context -> assertThat(context).hasSingleBean(ToolCallingContentObservationFilter.class));
	}

	@Test
	void throwExceptionOnErrorDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolExecutionExceptionProcessor = context.getBean(ToolExecutionExceptionProcessor.class);
				assertThat(toolExecutionExceptionProcessor).isInstanceOf(DefaultToolExecutionExceptionProcessor.class);

				// Test behavior instead of accessing private field
				// Create a mock tool definition and exception
				var toolDefinition = ToolDefinition.builder()
					.name("testTool")
					.description("Test tool for exception handling")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}")
					.build();
				var cause = new RuntimeException("Test error");
				var exception = new ToolExecutionException(toolDefinition, cause);

				// Default behavior should not throw exception
				String result = toolExecutionExceptionProcessor.process(exception);
				assertThat(result).isEqualTo("Test error");
			});
	}

	@Test
	void throwExceptionOnErrorEnabled() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.throw-exception-on-error=true")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolExecutionExceptionProcessor = context.getBean(ToolExecutionExceptionProcessor.class);
				assertThat(toolExecutionExceptionProcessor).isInstanceOf(DefaultToolExecutionExceptionProcessor.class);

				// Test behavior instead of accessing private field
				// Create a mock tool definition and exception
				var toolDefinition = ToolDefinition.builder()
					.name("testTool")
					.description("Test tool for exception handling")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}")
					.build();
				var cause = new RuntimeException("Test error");
				var exception = new ToolExecutionException(toolDefinition, cause);

				// When property is set to true, it should throw the exception
				assertThat(toolExecutionExceptionProcessor).extracting(processor -> {
					try {
						processor.process(exception);
						return "No exception thrown";
					}
					catch (ToolExecutionException e) {
						return "Exception thrown";
					}
				}).isEqualTo("Exception thrown");
			});
	}

	@Test
	void toolCallbackResolverDoesNotUseMcpToolCallbackProviders() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var syncMcpToolCallbackProvider = context.getBean("syncMcpToolCallbackProvider",
						ToolCallbackProvider.class);
				var asyncMcpToolCallbackProvider = context.getBean("asyncMcpToolCallbackProvider",
						ToolCallbackProvider.class);

				verify(syncMcpToolCallbackProvider, never()).getToolCallbacks();
				verify(asyncMcpToolCallbackProvider, never()).getToolCallbacks();

				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver.resolve("getForecast")).isNotNull();

				verify(syncMcpToolCallbackProvider, never()).getToolCallbacks();
				verify(asyncMcpToolCallbackProvider, never()).getToolCallbacks();
			});
	}

	@Test
	void customToolCallbackResolverOverridesDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(CustomToolCallbackResolverConfig.class)
			.run(context -> {
				assertThat(context).hasBean("toolCallbackResolver");
				assertThat(context.getBean("toolCallbackResolver")).isInstanceOf(CustomToolCallbackResolver.class);
			});
	}

	@Test
	void customToolExecutionExceptionProcessorOverridesDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(CustomToolExecutionExceptionProcessorConfig.class)
			.run(context -> {
				assertThat(context).hasBean("toolExecutionExceptionProcessor");
				assertThat(context.getBean("toolExecutionExceptionProcessor"))
					.isInstanceOf(CustomToolExecutionExceptionProcessor.class);
			});
	}

	@Test
	void customToolCallingManagerOverridesDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(CustomToolCallingManagerConfig.class)
			.run(context -> {
				assertThat(context).hasBean("toolCallingManager");
				assertThat(context.getBean("toolCallingManager")).isInstanceOf(CustomToolCallingManager.class);
			});
	}

	@Test
	void observationContentFilterNotCreatedWhenPropertyDisabled() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.observations.include-content=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean("toolCallingContentObservationFilter");
				assertThat(context).doesNotHaveBean(ToolCallingContentObservationFilter.class);
			});
	}

	@Test
	void toolCallbackResolverResolvesToolCallbacksFromBeans() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(ToolCallbackBeansConfig.class)
			.run(context -> {
				var resolver = context.getBean(ToolCallbackResolver.class);

				assertThat(resolver.resolve("getWeather")).isNotNull();
				assertThat(resolver.resolve("getWeather").getToolDefinition().name()).isEqualTo("getWeather");

				assertThat(resolver.resolve("weatherFunction")).isNotNull();
				assertThat(resolver.resolve("weatherFunction").getToolDefinition().name()).isEqualTo("weatherFunction");

				assertThat(resolver.resolve("nonExistentTool")).isNull();
			});
	}

	@Test
	void toolCallbackResolverResolvesMethodToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(MethodToolCallbackConfig.class)
			.run(context -> {
				var resolver = context.getBean(ToolCallbackResolver.class);

				assertThat(resolver.resolve("getForecastMethod")).isNotNull();
				assertThat(resolver.resolve("getForecastMethod").getToolDefinition().name())
					.isEqualTo("getForecastMethod");
			});
	}

	@Test
	void toolCallingManagerIntegrationWithCustomComponents() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(CustomObservationConfig.class)
			.run(context -> {
				assertThat(context).hasBean("toolCallingManager");
				assertThat(context).hasBean("customObservationRegistry");
				assertThat(context).hasBean("customObservationConvention");

				var manager = context.getBean(ToolCallingManager.class);
				assertThat(manager).isNotNull();
			});
	}

	@Test
	void toolCallLimitsDefaultAppliesTwentyPerToolLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				// DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL prior calls
				// already recorded; the next one should breach the baked-in default,
				// which the auto-configuration applies with no properties set.
				Prompt prompt = promptWithPriorToolResponses("getForecast",
						DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL);
				ChatResponse chatResponse = chatResponseRequestingTool("getForecast");

				assertThatExceptionOfType(ToolCallLimitExceededException.class)
					.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
					.satisfies(ex -> {
						assertThat(ex.getToolName()).isEqualTo("getForecast");
						assertThat(ex.getLimit()).isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL);
					});
			});
	}

	@Test
	void toolCallLimitsDefaultAppliesFiftyTotalLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				// DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS prior calls
				// to a different tool already recorded; the next call to any tool
				// should breach the baked-in total default before even resolving it.
				Prompt prompt = promptWithPriorToolResponses("toolB",
						DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS);
				ChatResponse chatResponse = chatResponseRequestingTool("toolA");

				assertThatExceptionOfType(ToolCallLimitExceededException.class)
					.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
					.satisfies(ex -> {
						assertThat(ex.getToolName()).isNull();
						assertThat(ex.getLimit()).isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS);
					});
			});
	}

	@Test
	void maxCallsPerToolPropertySetToUnlimitedSentinelDisablesDefaultLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=-1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				// Well beyond the baked-in default; should still succeed since the
				// -1 sentinel disables the per-tool limit entirely.
				Prompt prompt = promptWithPriorToolResponses("getForecast",
						DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL + 5);
				ChatResponse chatResponse = chatResponseRequestingTool("getForecast");

				ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
				assertThat(result.conversationHistory()).isNotEmpty();
			});
	}

	@Test
	void maxTotalToolCallsPropertySetToUnlimitedSentinelDisablesDefaultLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=-1",
					"spring.ai.tools.limits.max-total-tool-calls=-1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				// Well beyond the baked-in total default; the per-tool limit is also
				// disabled here so it does not trip first on the same tool name.
				Prompt prompt = promptWithPriorToolResponses("getForecast",
						DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS + 5);
				ChatResponse chatResponse = chatResponseRequestingTool("getForecast");

				ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
				assertThat(result.conversationHistory()).isNotEmpty();
			});
	}

	@Test
	void maxCallsPerToolOverrideSetToUnlimitedSentinelExemptsToolFromDefaultLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool.getForecast=-1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				// Well beyond the baked-in default of 20; should still succeed since
				// the -1 override exempts this specific tool from the per-tool limit.
				Prompt prompt = promptWithPriorToolResponses("getForecast",
						DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL + 5);
				ChatResponse chatResponse = chatResponseRequestingTool("getForecast");

				ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
				assertThat(result.conversationHistory()).isNotEmpty();
			});
	}

	@Test
	void maxCallsPerToolPropertyThrowsWhenExceeded() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				Prompt prompt = promptWithPriorToolResponses("someTool", 1);
				ChatResponse chatResponse = chatResponseRequestingTool("someTool");

				assertThatExceptionOfType(ToolCallLimitExceededException.class)
					.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
					.satisfies(ex -> assertThat(ex.getToolName()).isEqualTo("someTool"));
			});
	}

	@Test
	void maxCallsPerToolOverridesPropertyAppliesPerTool() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool.someTool=1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				Prompt prompt = promptWithPriorToolResponses("someTool", 1);
				ChatResponse chatResponse = chatResponseRequestingTool("someTool");

				assertThatExceptionOfType(ToolCallLimitExceededException.class)
					.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse));
			});
	}

	@Test
	void excludedToolsPropertyBypassesPerToolLimit() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=1",
					"spring.ai.tools.limits.excluded-tools=getForecast")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				Prompt prompt = promptWithPriorToolResponses("getForecast", 5);
				ChatResponse chatResponse = chatResponseRequestingTool("getForecast");

				ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
				assertThat(result.conversationHistory()).isNotEmpty();
			});
	}

	@Test
	void maxTotalToolCallsPropertyThrowsRegardlessOfToolName() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-total-tool-calls=1")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				Prompt prompt = promptWithPriorToolResponses("toolB", 1);
				ChatResponse chatResponse = chatResponseRequestingTool("toolA");

				assertThatExceptionOfType(ToolCallLimitExceededException.class)
					.isThrownBy(() -> toolCallingManager.executeToolCalls(prompt, chatResponse))
					.satisfies(ex -> assertThat(ex.getToolName()).isNull());
			});
	}

	@Test
	void onLimitExceededReturnErrorResponsePropertySynthesizesErrorInsteadOfThrowing() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=1",
					"spring.ai.tools.limits.on-limit-exceeded=return_error_response")
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallingManager = context.getBean(ToolCallingManager.class);

				Prompt prompt = promptWithPriorToolResponses("someTool", 1);
				ChatResponse chatResponse = chatResponseRequestingTool("someTool");

				ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);

				var lastMessage = (ToolResponseMessage) result.conversationHistory()
					.get(result.conversationHistory().size() - 1);
				assertThat(lastMessage.getResponses()).singleElement()
					.satisfies(response -> assertThat(response.responseData()).contains("limit"));
			});
	}

	private static Prompt promptWithPriorToolResponses(String toolName, int priorCallCount) {
		List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
		for (int i = 0; i < priorCallCount; i++) {
			responses.add(new ToolResponseMessage.ToolResponse("priorId" + i, toolName, "result"));
		}
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder().responses(responses).build();
		return new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());
	}

	private static ChatResponse chatResponseRequestingTool(String toolName) {
		return ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall(toolName, "function", toolName, "{}")))
				.build())))
			.build();
	}

	@Test
	void toolCallbackProviderBeansAreResolved() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(ToolCallbackProviderConfig.class)
			.run(context -> {
				var resolver = context.getBean(ToolCallbackResolver.class);

				// Should resolve tools from the ToolCallbackProvider
				assertThat(resolver.resolve("providerTool")).isNotNull();
				assertThat(resolver.resolve("providerTool").getToolDefinition().name()).isEqualTo("providerTool");
			});
	}

	@Test
	void multipleToolCallbackProvidersAreResolved() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(MultipleToolCallbackProvidersConfig.class)
			.run(context -> {
				var resolver = context.getBean(ToolCallbackResolver.class);

				// Should resolve tools from both providers
				assertThat(resolver.resolve("tool1")).isNotNull();
				assertThat(resolver.resolve("tool2")).isNotNull();
				assertThat(resolver.resolve("tool3")).isNotNull();
			});
	}

	@Configuration
	static class CustomToolCallbackResolverConfig {

		@Bean
		public ToolCallbackResolver toolCallbackResolver() {
			return new CustomToolCallbackResolver();
		}

	}

	static class CustomToolCallbackResolver implements ToolCallbackResolver {

		@Override
		public ToolCallback resolve(String toolName) {
			return null;
		}

	}

	@Configuration
	static class CustomToolExecutionExceptionProcessorConfig {

		@Bean
		public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
			return new CustomToolExecutionExceptionProcessor();
		}

	}

	static class CustomToolExecutionExceptionProcessor implements ToolExecutionExceptionProcessor {

		@Override
		public String process(ToolExecutionException exception) {
			return "Custom error handling";
		}

	}

	@Configuration
	static class CustomToolCallingManagerConfig {

		@Bean
		public ToolCallingManager toolCallingManager(ToolCallbackResolver resolver,
				ToolExecutionExceptionProcessor processor) {
			return new CustomToolCallingManager();
		}

	}

	static class CustomToolCallingManager implements ToolCallingManager {

		@Override
		public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
			return List.of();
		}

		@Override
		public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
			return null;
		}

	}

	@Configuration
	static class ToolCallbackBeansConfig {

		@Bean
		public ToolCallback getWeather() {
			return FunctionToolCallback.builder("getWeather", (Request request) -> "Sunny, 25°C")
				.description("Gets the current weather")
				.inputType(Request.class)
				.build();
		}

		@Bean
		public ToolCallback weatherFunction() {
			return FunctionToolCallback.builder("weatherFunction", (Request request) -> new Response("Sunny"))
				.description("Get weather forecast")
				.inputType(Request.class)
				.build();
		}

	}

	@Configuration
	static class MethodToolCallbackConfig {

		@Bean
		public ToolCallbackProvider methodToolCallbacks() {
			return MethodToolCallbackProvider.builder().toolObjects(new WeatherServiceForMethod()).build();
		}

	}

	static class WeatherServiceForMethod {

		@Tool(description = "Get the weather forecast")
		public String getForecastMethod(String location) {
			return "Sunny, 25°C";
		}

	}

	@Configuration
	static class CustomObservationConfig {

		@Bean
		public ObservationRegistry customObservationRegistry() {
			return ObservationRegistry.create();
		}

		@Bean
		public ToolCallingObservationConvention customObservationConvention() {
			return new ToolCallingObservationConvention() {
			};
		}

	}

	@Configuration
	static class ToolCallbackProviderConfig {

		@Bean
		public ToolCallbackProvider toolCallbackProvider() {
			return () -> new ToolCallback[] {
					FunctionToolCallback.builder("providerTool", (Request request) -> "Result")
						.description("Tool from provider")
						.inputType(Request.class)
						.build() };
		}

	}

	@Configuration
	static class MultipleToolCallbackProvidersConfig {

		@Bean
		public ToolCallbackProvider toolCallbackProvider1() {
			return () -> new ToolCallback[] { FunctionToolCallback.builder("tool1", (Request request) -> "Result1")
				.description("Tool 1")
				.inputType(Request.class)
				.build() };
		}

		@Bean
		public ToolCallbackProvider toolCallbackProvider2() {
			return () -> new ToolCallback[] { FunctionToolCallback.builder("tool2", (Request request) -> "Result2")
				.description("Tool 2")
				.inputType(Request.class)
				.build() };
		}

		@Bean
		public List<ToolCallbackProvider> toolCallbackProviderList() {
			return List
				.of(() -> new ToolCallback[] { FunctionToolCallback.builder("tool3", (Request request) -> "Result3")
					.description("Tool 3")
					.inputType(Request.class)
					.build() });
		}

	}

	public record Request(String location) {
	}

	public record Response(String temperature) {
	}

	static class WeatherService {

		@Tool(description = "Get the weather in location. Return temperature in 36°F or 36°C format.")
		public String getForecast(String location) {
			return "30";
		}

		@Tool(description = "Get the weather in location. Return temperature in 36°F or 36°C format.")
		public String getForecast2(String location) {
			return "30";
		}

		public String getAlert(String usState) {
			return "Alert";
		}

	}

	@Configuration
	static class Config {

		// Note: Currently we do not have ToolCallbackResolver implementation that can
		// resolve the ToolCallback from the Tool annotation.
		// Therefore we need to provide the ToolCallback instances explicitly using the
		// ToolCallbacks.from(...) utility method.
		@Bean
		public ToolCallbackProvider toolCallbacks() {
			return MethodToolCallbackProvider.builder().toolObjects(new WeatherService()).build();
		}

		@Bean
		public ToolCallback weatherFunction1() {
			return FunctionToolCallback.builder("weatherFunction1", (Request request) -> new Response("30"))
				.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
				.inputType(Request.class)
				.build();
		}

		@Bean
		public ToolCallback functionCallbacks3() {
			return FunctionToolCallback.builder("getCurrentWeather3", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();
		}

		@Bean
		public ToolCallback functionCallbacks4() {
			return FunctionToolCallback.builder("getCurrentWeather4", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();

		}

		@Bean
		public ToolCallback toolCallbacks5() {
			return FunctionToolCallback.builder("getCurrentWeather5", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();

		}

		@Bean
		public ToolCallbackProvider blabla() {
			return new StaticToolCallbackProvider(
					FunctionToolCallback.builder("getCurrentWeather5", (Request request) -> "15.0°C")
						.description("Gets the weather in location")
						.inputType(Request.class)
						.build());

		}

		@Bean
		public ToolCallback toolCallbacks6() {
			var toolMethod = ReflectionUtils.findMethod(WeatherService.class, "getAlert", String.class);
			return MethodToolCallback.builder()
				.toolDefinition(ToolDefinitions.builder(toolMethod).build())
				.toolMethod(toolMethod)
				.toolObject(new WeatherService())
				.build();
		}

		@Bean
		public SyncMcpToolCallbackProvider syncMcpToolCallbackProvider() {
			SyncMcpToolCallbackProvider provider = mock(SyncMcpToolCallbackProvider.class);
			when(provider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
			return provider;
		}

		@Bean
		public AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider() {
			AsyncMcpToolCallbackProvider provider = mock(AsyncMcpToolCallbackProvider.class);
			when(provider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
			return provider;
		}

	}

}
