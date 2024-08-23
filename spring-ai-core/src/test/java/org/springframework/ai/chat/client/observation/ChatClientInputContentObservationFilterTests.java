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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.model.ChatModel;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Unit tests for {@link ChatClientImportContentObservationFilter}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ChatClientInputContentObservationFilterTests {

	private final ChatClientInputContentObservationFilter observationFilter = new ChatClientInputContentObservationFilter();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Mock
	ChatModel chatModel;

	@Test
	void whenEmptyInputContentThenReturnOriginalContext() {

		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		ChatClientObservationConvention customObservationConvention = null;

		var request = new DefaultChatClientRequestSpec(chatModel, "", Map.of(), "", Map.of(), List.of(), List.of(),
				List.of(), List.of(), null, List.of(), Map.of(), observationRegistry, customObservationConvention);

		var expectedContext = new ChatClientObservationContext(request, "", false);

		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenWithTextThenAugmentContext() {

		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		ChatClientObservationConvention customObservationConvention = null;

		var request = new DefaultChatClientRequestSpec(chatModel, "sample user text", Map.of("up1", "upv1"),
				"sample system text", Map.of("sp1", "sp1v"), List.of(), List.of(), List.of(), List.of(), null,
				List.of(), Map.of(), observationRegistry, customObservationConvention);

		var originalContext = new ChatClientObservationContext(request, "", false);

		var augmentedContext = observationFilter.map(originalContext);

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
