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

package org.springframework.ai.openai.chat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiPaymentTransactionIT {

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@Autowired
	List<ToolCallback> toolCallbacks;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "paymentStatus", "paymentStatuses" })
	public void transactionPaymentStatuses(String functionName) {
		ToolCallback toolCallback = findToolCallback(functionName);

		List<TransactionStatusResponse> content = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.tools(toolCallback)
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					""")
			.call()
			.entity(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {

			});

		assertThat(content.get(0).id()).isEqualTo("001");
		assertThat(content.get(0).status()).isEqualTo("pending");

		assertThat(content.get(1).id()).isEqualTo("002");
		assertThat(content.get(1).status()).isEqualTo("approved");

		assertThat(content.get(2).id()).isEqualTo("003");
		assertThat(content.get(2).status()).isEqualTo("rejected");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "paymentStatus", "paymentStatuses" })
	public void streamingPaymentStatuses(String functionName) {
		ToolCallback toolCallback = findToolCallback(functionName);

		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {

		});

		Flux<String> flux = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.tools(toolCallback)
			.user(u -> u.text("""
					What is the status of my payment transactions 001, 002 and 003?

					{format}
					""").param("format", converter.getFormat()))
			.stream()
			.content();

		String content = flux.collectList().block().stream().collect(Collectors.joining());

		List<TransactionStatusResponse> structure = converter.convert(content);

		assertThat(structure.get(0).id()).isEqualTo("001");
		assertThat(structure.get(0).status()).isEqualTo("pending");

		assertThat(structure.get(1).id()).isEqualTo("002");
		assertThat(structure.get(1).status()).isEqualTo("approved");

		assertThat(structure.get(2).id()).isEqualTo("003");
		assertThat(structure.get(2).status()).isEqualTo("rejected");
	}

	private ToolCallback findToolCallback(String name) {
		return this.toolCallbacks.stream()
			.filter(tc -> tc.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No ToolCallback found for name: " + name));
	}

	record TransactionStatusResponse(String id, String status) {

	}

	record Transaction(String id) {

	}

	record Status(String name) {

	}

	record Transactions(List<Transaction> transactions) {

	}

	record Statuses(List<Status> statuses) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		ToolCallback paymentStatus() {
			return FunctionToolCallback.builder("paymentStatus", (Transaction transaction) -> DATASET.get(transaction))
				.description("Get the status of a single payment transaction")
				.inputType(Transaction.class)
				.build();
		}

		@Bean
		ToolCallback paymentStatuses() {
			return FunctionToolCallback
				.builder("paymentStatuses",
						(Transactions transactions) -> new Statuses(
								transactions.transactions().stream().map(t -> DATASET.get(t)).toList()))
				.description("Get the list statuses of a list of payment transactions")
				.inputType(Transactions.class)
				.build();
		}

		@Bean
		public ChatClient chatClient(OpenAiChatModel chatModel, ToolCallingManager toolCallingManager) {
			return ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();
		}

		@Bean
		public OpenAiChatModel openAiClient() {
			return OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder()
					.apiKey(System.getenv("OPENAI_API_KEY"))
					.model("gpt-4o-mini")
					.temperature(0.1)
					.build())
				.build();
		}

		@Bean
		@ConditionalOnMissingBean
		ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
			return new DefaultToolExecutionExceptionProcessor(false);
		}

		@Bean
		@ConditionalOnMissingBean
		ToolCallingManager toolCallingManager(ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
				ObjectProvider<ObservationRegistry> observationRegistry) {
			return ToolCallingManager.builder()
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
				.build();
		}

	}

}
