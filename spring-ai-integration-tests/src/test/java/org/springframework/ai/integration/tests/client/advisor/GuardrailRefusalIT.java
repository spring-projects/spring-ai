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

package org.springframework.ai.integration.tests.client.advisor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.GuardrailRefusalException;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.GuardrailRefusal;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for the refusal of a guardrail, as a {@link ChatClient} with a real
 * model returns it.
 *
 * @author Christian Tzolov
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class GuardrailRefusalIT {

	private static final String REFUSAL = "I can't talk about that.";

	private static final String REFUSED_QUESTION = "What is the capital of France? Keep it secret.";

	private static final String ALLOWED_QUESTION = "What is the capital of France?";

	@Autowired
	OpenAiChatModel openAiChatModel;

	private ModelCallCounter modelCalls;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		this.modelCalls = new ModelCallCounter();
		this.chatClient = ChatClient.builder(this.openAiChatModel)
			.defaultAdvisors(new SafeGuardAdvisor(List.of("secret"), REFUSAL, 0), this.modelCalls)
			.build();
	}

	@Test
	void refusedRequestDoesNotReachTheModel() {
		ChatResponse chatResponse = this.chatClient.prompt(REFUSED_QUESTION).call().chatResponse();

		assertThat(GuardrailRefusal.isRefusal(chatResponse)).isTrue();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo(REFUSAL);
		assertThat(this.modelCalls.count()).isZero();
	}

	@Test
	void entityThrowsTheRefusal() {
		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> this.chatClient.prompt(REFUSED_QUESTION).call().entity(Capital.class))
			.satisfies(ex -> {
				assertThat(ex.getRefusal()).isEqualTo(REFUSAL);
				assertThat(GuardrailRefusal.isRefusal(ex.getChatClientResponse().chatResponse())).isTrue();
			});
		assertThat(this.modelCalls.count()).isZero();
	}

	@Test
	void responseEntityReturnsTheRefusalWithoutAnEntity() {
		ResponseEntity<ChatResponse, Capital> response = this.chatClient.prompt(REFUSED_QUESTION)
			.call()
			.responseEntity(Capital.class);

		assertThat(response.getEntity()).isNull();
		assertThat(GuardrailRefusal.isRefusal(response.getResponse())).isTrue();
		assertThat(this.modelCalls.count()).isZero();
	}

	@Test
	void streamReturnsTheRefusal() {
		List<ChatResponse> chatResponses = this.chatClient.prompt(REFUSED_QUESTION)
			.stream()
			.chatResponse()
			.collectList()
			.block();

		assertThat(chatResponses).hasSize(1);
		assertThat(GuardrailRefusal.isRefusal(chatResponses.get(0))).isTrue();
		assertThat(chatResponses.get(0).getResult().getOutput().getText()).isEqualTo(REFUSAL);
		assertThat(this.modelCalls.count()).isZero();
	}

	@Test
	void answerOfTheModelIsNotARefusalAndIsConverted() {
		ResponseEntity<ChatResponse, Capital> response = this.chatClient.prompt(ALLOWED_QUESTION)
			.advisors(StructuredOutputValidationAdvisor.builder().outputType(Capital.class).build())
			.call()
			.responseEntity(Capital.class);

		assertThat(GuardrailRefusal.isRefusal(response.getResponse())).isFalse();
		assertThat(response.getEntity()).isNotNull();
		assertThat(response.getEntity().capital()).containsIgnoringCase("Paris");
		assertThat(this.modelCalls.count()).isEqualTo(1);
	}

	@Test
	void refusedAnswerOfTheModelIsNotValidatedOrAskedForAgain() {
		// The guardrail sits between the validation advisor and the model, and withholds
		// the answer. The refusal is not the JSON that was asked for: taken for an answer
		// of the model, it would fail the validation and the model would be asked again.
		ChatClient client = ChatClient.builder(this.openAiChatModel)
			.defaultAdvisors(StructuredOutputValidationAdvisor.builder().outputType(Capital.class).build(),
					new WithholdingGuardrail(), this.modelCalls)
			.build();

		assertThatExceptionOfType(GuardrailRefusalException.class)
			.isThrownBy(() -> client.prompt(ALLOWED_QUESTION).call().entity(Capital.class))
			.satisfies(ex -> assertThat(ex.getRefusal()).isEqualTo(REFUSAL));
		assertThat(this.modelCalls.count()).isEqualTo(1);
	}

	record Capital(String country, String capital) {
	}

	/**
	 * Counts the requests that reach the model. It is the last advisor before the model
	 * call.
	 */
	private static final class ModelCallCounter implements CallAdvisor, StreamAdvisor {

		private final AtomicInteger calls = new AtomicInteger();

		int count() {
			return this.calls.get();
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
			this.calls.incrementAndGet();
			return chain.nextCall(request);
		}

		@Override
		public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
			this.calls.incrementAndGet();
			return chain.nextStream(request);
		}

		@Override
		public String getName() {
			return "modelCallCounter";
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE - 1;
		}

	}

	/**
	 * An output guardrail that lets the model answer and then puts a refusal in place of
	 * the answer.
	 */
	private static final class WithholdingGuardrail implements CallAdvisor {

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
			ChatClientResponse response = chain.nextCall(request);
			return response.mutate()
				.chatResponse(ChatResponse.builder().generations(List.of(GuardrailRefusal.generation(REFUSAL))).build())
				.build();
		}

		@Override
		public String getName() {
			return "withholdingGuardrail";
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE - 1000;
		}

	}

}
