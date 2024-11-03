/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.observation;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.support.ChatModelObservationSupport;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit Tests for {@link ChatModelObservationSupport}.
 *
 * @author John Blum
 */
public class ChatModelObservationSupportTests {

	@Test
	void getObservationContextIsNullSafe() {
		assertThat(ChatModelObservationSupport.getObservationContext(null)).isEmpty();
	}

	@Test
	void getObservationContextForNonChat() {
		assertThat(ChatModelObservationSupport.getObservationContext(Observation.NOOP)).isEmpty();
	}

	@Test
	void getObservationContextForChat() {

		ChatModelObservation mockObservation = spy(ChatModelObservation.class);

		assertThat(ChatModelObservationSupport.getObservationContext(mockObservation).orElse(null))
			.isInstanceOf(ChatModelObservationContext.class);

		verify(mockObservation, times(1)).getContext();
		verifyNoMoreInteractions(mockObservation);
	}

	@Test
	void setsChatResponseInObservationContext() {

		ChatModelObservation mockObservation = spy(ChatModelObservation.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);

		Consumer<ChatResponse> chatResponseConsumer = ChatModelObservationSupport
			.setChatResponseInObservationContext(mockObservation);

		assertThat(chatResponseConsumer).isNotNull();

		chatResponseConsumer.accept(mockChatResponse);

		assertThat(mockObservation.getContext()).isNotNull()
			.asInstanceOf(InstanceOfAssertFactories.type(ChatModelObservationContext.class))
			.extracting(ChatModelObservationContext::getResponse)
			.isSameAs(mockChatResponse);

		verifyNoInteractions(mockChatResponse);
	}

	@Test
	void doesNotSetChatResponseInObservationContext() {

		ChatResponse mockChatResponse = mock(ChatResponse.class);
		Observation mockObservation = mock(Observation.class);
		Observation.Context mockContext = mock(Observation.Context.class);

		doReturn(mockContext).when(mockObservation).getContext();

		Consumer<ChatResponse> chatResponseConsumer = ChatModelObservationSupport
			.setChatResponseInObservationContext(mockObservation);

		assertThat(chatResponseConsumer).isNotNull();

		chatResponseConsumer.accept(mockChatResponse);

		verifyNoInteractions(mockChatResponse, mockContext);
	}

	@Test
	void setChatResponseInObservationContextIsNullSafe() {

		ChatResponse mockChatResponse = mock(ChatResponse.class);

		Consumer<ChatResponse> chatResponseConsumer = ChatModelObservationSupport
			.setChatResponseInObservationContext(null);

		assertThat(chatResponseConsumer).isNotNull();

		chatResponseConsumer.accept(mockChatResponse);

		verifyNoInteractions(mockChatResponse);
	}

	static abstract class ChatModelObservation implements Observation {

		private final AtomicReference<ChatModelObservationContext> contextRef = new AtomicReference<>(null);

		@Override
		public Context getContext() {
			return this.contextRef.updateAndGet(context -> context != null ? context : getContextSupplier().get());
		}

		static Supplier<ChatModelObservationContext> getContextSupplier() {
			return () -> {
				ChatOptions mockChatOptions = mock(ChatOptions.class);
				return new ChatModelObservationContext(new Prompt("This is a test"), "TestProvider", mockChatOptions);
			};
		}

	}

}
