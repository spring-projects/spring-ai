/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.client.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Unit tests for {@link DefaultChatClientObservationConvention}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class DefaultChatClientObservationConventionTests {

	@Mock
	ChatModel chatModel;

	private final DefaultChatClientObservationConvention observationConvention = new DefaultChatClientObservationConvention();

	DefaultChatClientRequestSpec request;

	@BeforeEach
	public void beforeEach() {
		request = new DefaultChatClientRequestSpec(chatModel, "", Map.of(), "", Map.of(), List.of(), List.of(),
				List.of(), List.of(), null, List.of(), Map.of(), ObservationRegistry.NOOP, null);
	}

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultChatClientObservationConvention.DEFAULT_NAME);
	}

	@Test
	void shouldHaveContextualName() {
		ChatClientObservationContext observationContext = new ChatClientObservationContext(request, "", true);

		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("spring_ai chat_client");
	}

	@Test
	void supportsOnlyChatClientObservationContext() {
		ChatClientObservationContext observationContext = new ChatClientObservationContext(request, "", true);

		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveRequiredKeyValues() {
		ChatClientObservationContext observationContext = new ChatClientObservationContext(request, "", true);

		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "chat_client"),
				KeyValue.of(LowCardinalityKeyNames.STREAM.asString(), "true"));
	}

	static RequestResponseAdvisor dummyAdvisor(String name) {
		return new RequestResponseAdvisor() {
			@Override
			public String getName() {
				return name;
			}
		};
	}

	static FunctionCallback dummyFunction(String name) {
		return new FunctionCallback() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public String getDescription() {
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("Unimplemented method 'getDescription'");
			}

			@Override
			public String getInputTypeSchema() {
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("Unimplemented method 'getInputTypeSchema'");
			}

			@Override
			public String call(String functionInput) {
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("Unimplemented method 'call'");
			}
		};
	}

	@Test
	void shouldHaveOptionalKeyValues() {

		var request = new DefaultChatClientRequestSpec(chatModel, "", Map.of(), "", Map.of(),
				List.of(dummyFunction("functionCallback1"), dummyFunction("functionCallback2")), List.of(),
				List.of("function1", "function2"), List.of(), null,
				List.of(dummyAdvisor("advisor1"), dummyAdvisor("advisor2")), Map.of("advParam1", "advisorParam1Value"),
				ObservationRegistry.NOOP, null);

		ChatClientObservationContext observationContext = new ChatClientObservationContext(request, "json", true);

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS.asString(), "[\"advisor1\",\"advisor2\"]"),
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAMS.asString(),
						"[\"advParam1\":\"advisorParam1Value\"]"),
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_NAMES.asString(),
						"[\"function1\",\"function2\"]"),
				KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS.asString(),
						"[\"functionCallback1\",\"functionCallback2\"]"));
	}

	static class TestUsage implements Usage {

		@Override
		public Long getPromptTokens() {
			return 1000L;
		}

		@Override
		public Long getGenerationTokens() {
			return 500L;
		}

	}

}
