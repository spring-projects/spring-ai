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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallConfirmationPolicy;
import org.springframework.ai.model.tool.ToolCallConfirmationRequiredException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tool call confirmation settings of {@link ToolCallingAutoConfiguration}: the
 * {@code spring.ai.tools.confirmation.*} properties and the
 * {@link ToolCallConfirmationPolicy} beans, seen through the auto-configured
 * {@link ToolCallingManager}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class ToolCallingAutoConfigurationConfirmationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class));

	private final List<String> ran = new ArrayList<>();

	@Test
	void toolsMarkedRequiresConfirmationPauseByDefault() {
		this.contextRunner.run(context -> {
			ToolCallingManager manager = context.getBean(ToolCallingManager.class);

			assertPauses(manager, "bookFlight");
			assertRuns(manager, "flightStatus");
		});
	}

	@Test
	void listedToolsPauseWhateverTheirMetadataSays() {
		this.contextRunner.withPropertyValues("spring.ai.tools.confirmation.tools=flightStatus").run(context -> {
			ToolCallingManager manager = context.getBean(ToolCallingManager.class);

			assertPauses(manager, "flightStatus");
			assertPauses(manager, "bookFlight");
		});
	}

	@Test
	void policyBeansAddToTheProperties() {
		this.contextRunner.withUserConfiguration(PolicyBeansConfig.class)
			.withPropertyValues("spring.ai.tools.confirmation.tools=cancelBooking")
			.run(context -> {
				ToolCallingManager manager = context.getBean(ToolCallingManager.class);

				assertPauses(manager, "bookFlight");
				assertPauses(manager, "cancelBooking");
				assertPauses(manager, "flightStatus");
				assertPauses(manager, "deleteFile");
				assertRuns(manager, "weather");
			});
	}

	@Test
	void aPolicyBeanCannotUngateAMarkedTool() {
		this.contextRunner.withUserConfiguration(NonePolicyConfig.class).run(context -> {
			ToolCallingManager manager = context.getBean(ToolCallingManager.class);

			assertPauses(manager, "bookFlight");
		});
	}

	@Test
	void disablingStopsHonoringTheMetadataWithAWarning(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(PolicyBeansConfig.class)
			.withPropertyValues("spring.ai.tools.confirmation.enabled=false",
					"spring.ai.tools.confirmation.tools=cancelBooking")
			.run(context -> {
				ToolCallingManager manager = context.getBean(ToolCallingManager.class);

				assertRuns(manager, "bookFlight");
				// The list and the beans still apply
				assertPauses(manager, "cancelBooking");
				assertPauses(manager, "flightStatus");
			});
		assertThat(output).contains("Tool call confirmation is disabled");
	}

	@Test
	void noWarningWhenEnabled(CapturedOutput output) {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(ToolCallingManager.class));
		assertThat(output).doesNotContain("Tool call confirmation is disabled");
	}

	@Test
	void propertiesBind() {
		this.contextRunner
			.withPropertyValues("spring.ai.tools.confirmation.enabled=false", "spring.ai.tools.confirmation.tools=a,b")
			.run(context -> {
				ToolCallingProperties.Confirmation confirmation = context.getBean(ToolCallingProperties.class)
					.getConfirmation();
				assertThat(confirmation.isEnabled()).isFalse();
				assertThat(confirmation.getTools()).containsExactly("a", "b");
			});
	}

	@Test
	void defaultProperties() {
		this.contextRunner.run(context -> {
			ToolCallingProperties.Confirmation confirmation = context.getBean(ToolCallingProperties.class)
				.getConfirmation();
			assertThat(confirmation.isEnabled()).isTrue();
			assertThat(confirmation.getTools()).isEmpty();
		});
	}

	private void assertPauses(ToolCallingManager manager, String toolName) {
		assertThatExceptionOfType(ToolCallConfirmationRequiredException.class)
			.isThrownBy(() -> manager.executeToolCalls(prompt(), reply(toolName)))
			.satisfies(ex -> assertThat(ex.getToolCallIds()).containsExactly("call_" + toolName));
		assertThat(this.ran).doesNotContain(toolName);
	}

	private void assertRuns(ToolCallingManager manager, String toolName) {
		manager.executeToolCalls(prompt(), reply(toolName));
		assertThat(this.ran).contains(toolName);
	}

	private Prompt prompt() {
		List<ToolCallback> tools = List.of(
				tool("bookFlight", ToolMetadata.builder().requiresConfirmation(true).build()),
				tool("flightStatus", ToolMetadata.builder().build()),
				tool("cancelBooking", ToolMetadata.builder().build()),
				tool("deleteFile", ToolMetadata.builder().build()), tool("weather", ToolMetadata.builder().build()));
		return new Prompt(List.of(new UserMessage("Help me travel")),
				ToolCallingChatOptions.builder().toolCallbacks(tools).build());
	}

	private ToolCallback tool(String name, ToolMetadata metadata) {
		return FunctionToolCallback.builder(name, (Input input) -> {
			this.ran.add(name);
			return "done";
		}).description(name).inputType(Input.class).toolMetadata(metadata).build();
	}

	private static ChatResponse reply(String toolName) {
		AssistantMessage message = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_" + toolName, "function", toolName, "{}")))
			.build();
		return new ChatResponse(List.of(new Generation(message)));
	}

	record Input() {
	}

	@Configuration(proxyBeanMethods = false)
	static class PolicyBeansConfig {

		@Bean
		ToolCallConfirmationPolicy flightStatusPolicy() {
			return ToolCallConfirmationPolicy.forTools("flightStatus");
		}

		@Bean
		ToolCallConfirmationPolicy deleteFilePolicy() {
			return (toolCall, toolMetadata) -> toolCall.name().startsWith("delete");
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class NonePolicyConfig {

		@Bean
		ToolCallConfirmationPolicy nonePolicy() {
			return ToolCallConfirmationPolicy.none();
		}

	}

}
