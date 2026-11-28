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

package org.springframework.ai.chat.client;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConversionException;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * @author Xiangnan Zhang
 */
@ExtendWith(MockitoExtension.class)
class ChatClientOutputConversionTests {

	private static final String RESPONSE = "I can only help with orders. " + "Please provide an order. ".repeat(100);

	@Mock
	ChatModel chatModel;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());
		given(this.chatModel.call(any(Prompt.class)))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(RESPONSE)))));
		this.chatClient = ChatClient.create(this.chatModel);
	}

	@Test
	void entityExposesOriginalResponse() {
		assertThatExceptionOfType(StructuredOutputConversionException.class)
			.isThrownBy(() -> this.chatClient.prompt("An order").call().entity(Order.class))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEqualTo(RESPONSE);
				assertThat(ex).hasCauseInstanceOf(JacksonException.class);
			});
	}

	@Test
	void parameterizedEntityExposesOriginalResponse() {
		assertThatExceptionOfType(StructuredOutputConversionException.class).isThrownBy(
				() -> this.chatClient.prompt("Orders").call().entity(new ParameterizedTypeReference<List<Order>>() {
				}))
			.satisfies(ex -> assertThat(ex.getText()).isEqualTo(RESPONSE));
	}

	@Test
	void explicitBeanOutputConverterExposesOriginalResponse() {
		assertThatExceptionOfType(StructuredOutputConversionException.class)
			.isThrownBy(() -> this.chatClient.prompt("An order").call().entity(new BeanOutputConverter<>(Order.class)))
			.satisfies(ex -> assertThat(ex.getText()).isEqualTo(RESPONSE));
	}

	@Test
	void responseEntityExposesOriginalResponseOnConversionFailure() {
		assertThatExceptionOfType(StructuredOutputConversionException.class)
			.isThrownBy(() -> this.chatClient.prompt("An order").call().responseEntity(Order.class))
			.satisfies(ex -> assertThat(ex.getText()).isEqualTo(RESPONSE));
	}

	record Order(int quantity) {
	}

}
