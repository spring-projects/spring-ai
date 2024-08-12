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
import org.springframework.ai.chat.model.ChatModel;

import io.micrometer.observation.ObservationRegistry;

/**
 * Unit tests for {@link ChatClientObservationContext}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ChatClientObservationContextTests {

	@Mock
	ChatModel chatModel;

	@Test
	void whenMandatoryRequestOptionsThenReturn() {

		var request = new DefaultChatClientRequestSpec(chatModel, "", Map.of(), "", Map.of(), List.of(), List.of(),
				List.of(), List.of(), null, List.of(), Map.of(), ObservationRegistry.NOOP, null);

		var observationContext = new ChatClientObservationContext(request, "", true);

		assertThat(observationContext).isNotNull();
	}

}
