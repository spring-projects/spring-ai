/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.replicate;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicateChatModel}.
 *
 * @author Rene Maierhofer
 */
@SpringBootTest(classes = ReplicateTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "REPLICATE_API_TOKEN", matches = ".+")
class ReplicateChatModelIT {

	@Autowired
	private ReplicateChatModel chatModel;

	@Test
	void testSimpleCall() {
		String userMessage = "What is the capital of France? Answer in one word.";
		ChatResponse response = this.chatModel.call(new Prompt(userMessage));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		Generation generation = response.getResult();
		assertThat(generation).isNotNull();
		Assertions.assertNotNull(generation.getOutput().getText());
		Assertions.assertFalse(generation.getOutput().getText().isEmpty());
		assertThat(generation.getOutput().getText().toLowerCase()).contains("paris");

		ChatResponseMetadata metadata = response.getMetadata();
		assertThat(metadata).isNotNull();
		assertThat(metadata.getId()).isNotNull();
		assertThat(metadata.getModel()).isNotNull();
		assertThat(metadata.getUsage()).isNotNull();
		assertThat(metadata.getUsage().getPromptTokens()).isGreaterThanOrEqualTo(0);
		assertThat(metadata.getUsage().getCompletionTokens()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void testStreamingCall() {
		String userMessage = "Count from 1 to 500.";
		Flux<ChatResponse> responseFlux = this.chatModel.stream(new Prompt(userMessage));
		List<ChatResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull().isNotEmpty().hasSizeGreaterThan(1);

		responses.forEach(response -> {
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResult().getOutput().getText()).isNotNull();
		});

		List<String> chunks = responses.stream()
			.flatMap(chatResponse -> chatResponse.getResults().stream())
			.map(generation -> generation.getOutput().getText())
			.filter(Objects::nonNull)
			.filter(text -> !text.isEmpty())
			.toList();

		assertThat(chunks).hasSizeGreaterThan(1);

		String fullContent = String.join("", chunks);
		assertThat(fullContent).isNotEmpty();

		boolean hasMetadata = responses.stream().anyMatch(response -> response.getMetadata().getId() != null);
		assertThat(hasMetadata).isTrue();
	}

	@Test
	void testCallWithOptions() {
		int maxTokens = 10;
		ReplicateChatOptions options = ReplicateChatOptions.builder()
			.model("meta/meta-llama-3-8b-instruct")
			.withParameter("temperature", 0.8)
			.withParameter("max_tokens", maxTokens)
			.build();

		Prompt prompt = new Prompt("Write a very long poem.", options);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		ChatResponseMetadata metadata = response.getMetadata();
		Usage usage = metadata.getUsage();
		assertThat(usage.getCompletionTokens()).isLessThanOrEqualTo(maxTokens);
	}

	@Test
	void testMultiTurnConversation_shouldNotWork() {
		UserMessage userMessage1 = new UserMessage("My favorite color is blue.");
		AssistantMessage assistantMessage = new AssistantMessage("Noted!");
		UserMessage userMessage2 = new UserMessage("What is my favorite color?");
		Prompt prompt = new Prompt(List.of(userMessage1, assistantMessage, userMessage2));
		Assertions.assertThrows(AssertionError.class, () -> this.chatModel.call(prompt));
	}

}
