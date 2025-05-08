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

package org.springframework.ai.chat.client.observation;

import java.util.List;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultChatClientObservationConvention}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
class DefaultChatClientObservationConventionTests {

	private final DefaultChatClientObservationConvention observationConvention = new DefaultChatClientObservationConvention();

	@Mock
	ChatModel chatModel;

	ChatClientRequest request;

	static CallAdvisor dummyAdvisor(String name) {
		return new CallAdvisor() {

			@Override
			public String getName() {
				return name;
			}

			@Override
			public int getOrder() {
				return 0;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest,
					CallAdvisorChain callAdvisorChain) {
				return null;
			}

		};
	}

	static ToolCallback dummyFunction(String name) {
		return new ToolCallback() {

			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			}

			@Override
			public String call(String functionInput) {
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("Unimplemented method 'call'");
			}
		};
	}

	@BeforeEach
	public void beforeEach() {
		this.request = ChatClientRequest.builder().prompt(new Prompt()).build();
	}

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultChatClientObservationConvention.DEFAULT_NAME);
	}

	@Test
	void shouldHaveContextualName() {
		ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
			.request(this.request)
			.stream(true)
			.build();

		assertThat(this.observationConvention.getContextualName(observationContext))
			.isEqualTo("%s %s".formatted(AiProvider.SPRING_AI.value(), SpringAiKind.CHAT_CLIENT.value()));
	}

	@Test
	void supportsOnlyChatClientObservationContext() {
		ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
			.request(this.request)
			.stream(true)
			.build();

		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveRequiredKeyValues() {
		ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
			.request(this.request)
			.stream(true)
			.build();

		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "chat_client"),
				KeyValue.of(LowCardinalityKeyNames.STREAM.asString(), "true"));
	}

	@Test
	void shouldHaveOptionalKeyValues() {
		var request = ChatClientRequest.builder()
			.prompt(new Prompt("",
					ToolCallingChatOptions.builder()
						.toolNames("tool1", "tool2")
						.toolCallbacks(dummyFunction("toolCallback1"), dummyFunction("toolCallback2"))
						.build()))
			.context(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, "007")
			.build();

		ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
			.request(request)
			.format("json")
			.advisors(List.of(dummyAdvisor("advisor1"), dummyAdvisor("advisor2")))
			.stream(true)
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS.asString(), """
						["advisor1", "advisor2"]"""),
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_CONVERSATION_ID.asString(), "007"),
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_TOOL_NAMES.asString(), """
						["tool1", "tool2", "toolCallback1", "toolCallback2"]"""));
	}

}
