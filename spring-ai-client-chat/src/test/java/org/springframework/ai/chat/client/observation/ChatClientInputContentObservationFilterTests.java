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

import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatClientInputContentObservationFilter}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
class ChatClientInputContentObservationFilterTests {

	private final ChatClientInputContentObservationFilter observationFilter = new ChatClientInputContentObservationFilter();

	@Mock
	ChatModel chatModel;

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = this.observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenEmptyInputContentThenReturnOriginalContext() {
		var request = ChatClientRequest.builder().prompt(new Prompt()).build();

		var expectedContext = ChatClientObservationContext.builder().request(request).build();

		var actualContext = this.observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenWithTextThenAugmentContext() {
		var request = ChatClientRequest.builder()
			.prompt(new Prompt(new SystemMessage("sample system text"), new UserMessage("sample user text")))
			.context(ChatClientAttributes.USER_PARAMS.getKey(), Map.of("up1", "upv1"))
			.context(ChatClientAttributes.SYSTEM_PARAMS.getKey(), Map.of("sp1", "sp1v"))
			.build();

		var originalContext = ChatClientObservationContext.builder().request(request).build();

		var augmentedContext = this.observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT.asString(), "sample user text"));
		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAMS.asString(), "[\"up1\":\"upv1\"]"));
		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT.asString(), "sample system text"));
		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM.asString(), "[\"sp1\":\"sp1v\"]"));
	}

}
