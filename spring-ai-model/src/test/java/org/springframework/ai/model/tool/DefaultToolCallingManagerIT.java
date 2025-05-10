/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DefaultToolCallingManager}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = DefaultToolCallingManagerIT.Config.class)
class DefaultToolCallingManagerIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	ToolCallingManager toolCallingManager;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForToolCall() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		Prompt prompt = Prompt.builder()
			.content("Why does a raven look like a desk?")
			.chatOptions(ToolCallingChatOptions.builder().toolCallbacks(toolCallback).build())
			.build();

		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Answer", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult).isNotNull();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultToolCallingObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo(SpringAiKind.TOOL_CALL.value() + " " + toolCallback.getToolDefinition().name())
			.hasLowCardinalityKeyValue(
					ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.FRAMEWORK.value())
			.hasLowCardinalityKeyValue(
					ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.SPRING_AI.value())
			.hasLowCardinalityKeyValue(
					ToolCallingObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
					SpringAiKind.TOOL_CALL.value())
			.hasLowCardinalityKeyValue(
					ToolCallingObservationDocumentation.LowCardinalityKeyNames.TOOL_DEFINITION_NAME.asString(),
					toolCallback.getToolDefinition().name())
			.hasHighCardinalityKeyValue(
					ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_DESCRIPTION.asString(),
					toolCallback.getToolDefinition().description())
			.hasHighCardinalityKeyValue(
					ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_SCHEMA.asString(),
					toolCallback.getToolDefinition().inputSchema());
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public ToolCallingManager toolCallingManager(TestObservationRegistry observationRegistry) {
			return DefaultToolCallingManager.builder().observationRegistry(observationRegistry).build();
		}

	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
