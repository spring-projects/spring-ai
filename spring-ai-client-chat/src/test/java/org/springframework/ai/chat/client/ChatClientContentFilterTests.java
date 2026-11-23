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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * What a {@link ChatClient} returns when a safety filter of the provider, or a refusal of
 * the model, stopped the response: the response as it is, and an exception where an
 * entity was asked for.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ChatClientContentFilterTests {

	private static final String ORDER_JSON = "{\"item\":\"headphones\",\"quantity\":2}";

	@Mock
	ChatModel chatModel;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());
		this.chatClient = ChatClient.builder(this.chatModel).build();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// OpenAI, DeepSeek
			"CONTENT_FILTER",
			// Anthropic
			"refusal",
			// Bedrock
			"guardrail_intervened", "content_filtered",
			// Gemini
			"SAFETY", "RECITATION", "PROHIBITED_CONTENT", "SPII", "BLOCKLIST", "IMAGE_SAFETY" })
	void entityThrowsWhateverNameTheProviderGivesTheStop(String finishReason) {
		modelReplies(filtered("", finishReason));

		assertThatExceptionOfType(ContentFilteredException.class).isThrownBy(() -> call().entity(Order.class))
			.satisfies(ex -> {
				assertThat(ex.getFinishReason()).isEqualTo(finishReason);
				assertThat(ex).hasMessageContaining(finishReason);
				assertThat(ex.getChatClientResponse()
					.chatResponse()
					.hasFinishReasonCategory(FinishReasonCategory.FILTERED)).isTrue();
			});
	}

	@Test
	void theExceptionCarriesWhatTheProviderReturned() {
		modelReplies(filtered("I can't help with that request.", "refusal"));

		assertThatExceptionOfType(ContentFilteredException.class).isThrownBy(() -> call().entity(Order.class))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEqualTo("I can't help with that request.");
				// The text may be what the filter withheld. It stays out of the message.
				assertThat(ex.getMessage()).doesNotContain("I can't help");
				// The parsing error that was thrown before is kept as the cause.
				assertThat(ex).hasCauseInstanceOf(RuntimeException.class);
			});
	}

	@Test
	void anEmptyFilteredResponseThrowsWithoutACause() {
		modelReplies(filtered("", "CONTENT_FILTER"));

		assertThatExceptionOfType(ContentFilteredException.class).isThrownBy(() -> call().entity(Order.class))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEmpty();
				assertThat(ex).hasNoCause();
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { "CONTENT_FILTER", "guardrail_intervened", "SAFETY" })
	void aFilteredResponseThatConvertsAllTheSameIsReturnedAsBefore(String finishReason) {
		// For example a guardrail that masked a value and left the output intact.
		modelReplies(filtered(ORDER_JSON, finishReason));

		assertThat(call().entity(Order.class)).isEqualTo(new Order("headphones", 2));
		assertThat(call().responseEntity(Order.class).getEntity()).isEqualTo(new Order("headphones", 2));
	}

	@Test
	void anAnswerThatNoFilterStoppedStillFailsWithTheParsingError() {
		modelReplies(generation("not json", "STOP"));

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> call().entity(Order.class))
			.isNotInstanceOf(ContentFilteredException.class);
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> call().responseEntity(Order.class))
			.isNotInstanceOf(ContentFilteredException.class);
	}

	@Test
	void everyWayOfAskingForAnEntityThrows() {
		modelReplies(filtered("", "CONTENT_FILTER"));

		assertThatExceptionOfType(ContentFilteredException.class)
			.isThrownBy(() -> call().entity(new ParameterizedTypeReference<List<Order>>() {
			}));
		assertThatExceptionOfType(ContentFilteredException.class)
			.isThrownBy(() -> call().entity(new BeanOutputConverter<>(Order.class)));
		assertThatExceptionOfType(ContentFilteredException.class)
			.isThrownBy(() -> call().entity(Order.class, spec -> spec.validateSchema()));
	}

	@Test
	void theExceptionKeepsTheFinishReasonAndTheTextWhenSerialized() throws Exception {
		modelReplies(filtered("I can't help with that request.", "refusal"));
		ContentFilteredException thrown = catchThrowableOfType(ContentFilteredException.class,
				() -> call().entity(Order.class));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(thrown);
		}
		ContentFilteredException copy;
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			copy = (ContentFilteredException) in.readObject();
		}

		// The response is not serializable, so it stays behind.
		assertThat(copy.getFinishReason()).isEqualTo("refusal");
		assertThat(copy.getText()).isEqualTo("I can't help with that request.");
		assertThat(copy).hasMessage(thrown.getMessage());
		assertThatIllegalStateException().isThrownBy(copy::getChatClientResponse);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "I can't help with that request." })
	void responseEntityReturnsTheResponseWithoutAnEntity(String text) {
		// Whether the filter left nothing or something that does not convert.
		modelReplies(filtered(text, "SAFETY"));

		ResponseEntity<ChatResponse, Order> response = call().responseEntity(Order.class);

		assertThat(response.getEntity()).isNull();
		assertThat(response.getResponse().getResult().getMetadata().getFinishReason()).isEqualTo("SAFETY");
	}

	@Test
	void aRefusalReportedWithAnOrdinaryStopThrowsToo() {
		// OpenAI reports a refusal in a field of its own. Only the category tells.
		modelReplies(filtered("", "STOP"));

		assertThatExceptionOfType(ContentFilteredException.class).isThrownBy(() -> call().entity(Order.class))
			.satisfies(ex -> assertThat(ex.getFinishReason()).isEqualTo("STOP"));
	}

	@Test
	void aGenerationWithoutACategoryIsClassifiedByItsFinishReason() {
		// As a response built by hand, or by a chat model that does not classify.
		modelReplies(generation("", "content_filter"));

		assertThatExceptionOfType(ContentFilteredException.class).isThrownBy(() -> call().entity(Order.class));
	}

	@Test
	void theContentIsReturnedAsTheProviderLeftIt() {
		modelReplies(filtered("I can't help with that request.", "refusal"));

		assertThat(call().content()).isEqualTo("I can't help with that request.");
	}

	@Test
	void theGenerationThatIsConvertedDecides() {
		// The entity is read from the first generation. A filter that stopped another
		// choice does not take the answer away.
		modelReplies(generation(ORDER_JSON, "STOP"), filtered("", "CONTENT_FILTER"));

		assertThat(call().entity(Order.class)).isEqualTo(new Order("headphones", 2));
	}

	@ParameterizedTest
	@ValueSource(strings = { "STOP", "end_turn", "LENGTH", "some_new_reason" })
	void anAnswerThatNoFilterStoppedIsConvertedAsBefore(String finishReason) {
		modelReplies(generation(ORDER_JSON, finishReason));

		assertThat(call().entity(Order.class)).isEqualTo(new Order("headphones", 2));
	}

	@Test
	void anAnswerWithoutAFinishReasonIsConvertedAsBefore() {
		modelReplies(new Generation(new AssistantMessage(ORDER_JSON)));

		assertThat(call().entity(Order.class)).isEqualTo(new Order("headphones", 2));
	}

	private void modelReplies(Generation... generations) {
		given(this.chatModel.call(any(Prompt.class))).willReturn(new ChatResponse(List.of(generations)));
	}

	/** A new call each time: a response spec runs its advisor chain once. */
	private ChatClient.CallResponseSpec call() {
		return this.chatClient.prompt("Two headphones, please").call();
	}

	/**
	 * A generation as a chat model returns it: the model classifies its finish reason.
	 */
	private static Generation filtered(String text, String finishReason) {
		return new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.builder()
					.finishReason(finishReason)
					.finishReasonCategory(FinishReasonCategory.FILTERED)
					.build());
	}

	private static Generation generation(String text, String finishReason) {
		return new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.builder().finishReason(finishReason).build());
	}

	record Order(String item, int quantity) {
	}

}
