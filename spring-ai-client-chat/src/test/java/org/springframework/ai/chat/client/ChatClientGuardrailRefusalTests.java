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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.GuardrailRefusal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * What a {@link ChatClient} returns when a guardrail refused the turn: the refusal as an
 * ordinary reply, and an exception where an entity was asked for.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ChatClientGuardrailRefusalTests {

	private static final String REFUSAL = "I can't talk about that.";

	@Mock
	ChatModel chatModel;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());
		this.chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(new SafeGuardAdvisor(List.of("dangerous"), REFUSAL, 0))
			.build();
	}

	@Test
	void theRefusalIsAnOrdinaryReplyForACallerThatReadsTheContent() {
		String content = this.chatClient.prompt("Tell me something dangerous").call().content();

		assertThat(content).isEqualTo(REFUSAL);
		verify(this.chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void entityThrowsTheRefusalInsteadOfFailingToConvertIt() {
		// The refusal is not JSON. Converted, it would surface as malformed output.
		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> this.chatClient.prompt("Tell me something dangerous").call().entity(Order.class))
			.satisfies(ex -> {
				assertThat(ex.getRefusal()).isEqualTo(REFUSAL);
				assertThat(ex).hasMessageContaining(REFUSAL);
				// One catch covers the refusal of a guardrail and the stop of a provider.
				assertThat(ex).isInstanceOf(ContentFilteredException.class);
				assertThat(ex.getText()).isEqualTo(REFUSAL);
				assertThat(ex.getFinishReason()).isEqualTo(GuardrailRefusal.FINISH_REASON);
				assertThat(GuardrailRefusal.isRefusal(ex.getChatClientResponse().chatResponse())).isTrue();
			});
		verify(this.chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void everyWayOfAskingForAnEntityThrowsTheRefusal() {
		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> refusedCall().entity(new ParameterizedTypeReference<List<Order>>() {
			}));
		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> refusedCall().entity(new BeanOutputConverter<>(Order.class)));
		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> refusedCall().entity(Order.class, spec -> spec.validateSchema()));
	}

	/** A new call each time: a response spec runs its advisor chain once. */
	private ChatClient.CallResponseSpec refusedCall() {
		return this.chatClient.prompt("Tell me something dangerous").call();
	}

	@Test
	void theExceptionCarriesTheContextTheGuardrailLeft() {
		ChatClient client = ChatClient.builder(this.chatModel)
			.defaultAdvisors(new FixedReplyAdvisor(
					new ChatResponse(List.of(GuardrailRefusal.generation("Please reach out for support.")))))
			.build();

		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> client.prompt("Anything").call().entity(Order.class))
			.satisfies(ex -> assertThat(ex.getChatClientResponse().context()).containsEntry("outcome", "SUPPORT"));
	}

	@Test
	void theExceptionKeepsTheRefusalWhenSerialized() throws Exception {
		GuardrailRefusalException thrown = catchThrowableOfType(GuardrailRefusalException.class,
				() -> this.chatClient.prompt("Tell me something dangerous").call().entity(Order.class));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(thrown);
		}
		GuardrailRefusalException copy;
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			copy = (GuardrailRefusalException) in.readObject();
		}

		// The response is not serializable, so it stays behind. The refusal travels.
		assertThat(copy.getRefusal()).isEqualTo(REFUSAL);
		assertThat(copy).hasMessage(thrown.getMessage());
		assertThatIllegalStateException().isThrownBy(copy::getChatClientResponse);
	}

	@Test
	void aRefusedChoiceMakesTheWholeTurnRefused() {
		ChatClient client = ChatClient.builder(this.chatModel)
			.defaultAdvisors(new FixedReplyAdvisor(new ChatResponse(
					List.of(new Generation(new AssistantMessage("{\"item\":\"headphones\",\"quantity\":2}")),
							GuardrailRefusal.generation("I can't talk about that.")))))
			.build();

		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> client.prompt("Two headphones, please").call().entity(Order.class))
			.satisfies(ex -> assertThat(ex.getRefusal()).isEqualTo("I can't talk about that."));
	}

	@Test
	void responseEntityReturnsTheRefusalWithoutAnEntity() {
		// The caller asked for the response too, and reads the refusal there.
		ResponseEntity<ChatResponse, Order> response = this.chatClient.prompt("Tell me something dangerous")
			.call()
			.responseEntity(Order.class);

		assertThat(response.getEntity()).isNull();
		assertThat(GuardrailRefusal.isRefusal(response.getResponse())).isTrue();
		assertThat(response.getResponse().getResult().getOutput().getText()).isEqualTo(REFUSAL);
	}

	@Test
	void anAnswerOfTheModelIsConvertedAsBefore() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(new ChatResponse(
				List.of(new Generation(new AssistantMessage("{\"item\":\"headphones\",\"quantity\":2}")))));

		Order order = this.chatClient.prompt("Two headphones, please").call().entity(Order.class);

		assertThat(order).isEqualTo(new Order("headphones", 2));
	}

	record Order(String item, int quantity) {
	}

	/**
	 * A guardrail that answers every turn itself and leaves its reason in the context.
	 */
	private static final class FixedReplyAdvisor implements CallAdvisor {

		private final ChatResponse reply;

		FixedReplyAdvisor(ChatResponse reply) {
			this.reply = reply;
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
			return ChatClientResponse.builder().chatResponse(this.reply).context(Map.of("outcome", "SUPPORT")).build();
		}

		@Override
		public String getName() {
			return "fixedReply";
		}

		@Override
		public int getOrder() {
			return 0;
		}

	}

}
