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

package org.springframework.ai.session;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the typed {@link SessionEvent}: a message event versus every other kind.
 *
 * @author Christian Tzolov
 */
class SessionEventTests {

	@Test
	void messageEventIsDefaultType() {
		SessionEvent event = SessionEvent.builder().sessionId("s").message(new UserMessage("hi")).build();

		assertThat(event.getType()).isEqualTo(EventType.MESSAGE);
		assertThat(event.hasMessage()).isTrue();
		assertThat(event.getMessage().getText()).isEqualTo("hi");
		assertThat(event.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(event.isTurnStart()).isTrue();
		assertThat(event.getPayload()).isEmpty();
	}

	@Test
	void messageEventRequiresMessage() {
		assertThatThrownBy(() -> SessionEvent.builder().sessionId("s").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MESSAGE");
		assertThatThrownBy(() -> SessionEvent.builder().sessionId("s").type(EventType.MESSAGE).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonMessageEventCarriesPayloadAndNoMessage() {
		SessionEvent event = SessionEvent.builder()
			.sessionId("s")
			.type(EventType.of("STATUS"))
			.payload("status", "IDLE")
			.payload("stopReason", "END_TURN")
			.build();

		assertThat(event.getType()).isEqualTo(EventType.of("STATUS"));
		assertThat(event.hasMessage()).isFalse();
		assertThat(event.getPayload()).containsEntry("status", "IDLE").containsEntry("stopReason", "END_TURN");
		assertThat(event.isTurnStart()).isFalse();
		assertThat(event.hasToolCalls()).isFalse();
		assertThatThrownBy(event::getMessage).isInstanceOf(IllegalStateException.class).hasMessageContaining("STATUS");
		assertThatThrownBy(event::getMessageType).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void nonMessageEventRejectsMessage() {
		assertThatThrownBy(() -> SessionEvent.builder()
			.sessionId("s")
			.type(EventType.of("USAGE"))
			.message(new AssistantMessage("no"))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("USAGE");
	}

	@Test
	void payloadIsCopiedAndImmutable() {
		Map<String, Object> payload = new java.util.HashMap<>(Map.of("a", 1));
		SessionEvent event = SessionEvent.builder().sessionId("s").type(EventType.of("USAGE")).payload(payload).build();
		payload.put("b", 2);

		assertThat(event.getPayload()).containsOnlyKeys("a");
		assertThatThrownBy(() -> event.getPayload().put("c", 3)).isInstanceOf(UnsupportedOperationException.class);
	}

}
