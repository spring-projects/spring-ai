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

package org.springframework.ai.google.genai.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Dan Dobrin
 * @author Sebastien Deleuze
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".+")
public class GoogleGenAiPaymentTransactionMethodIT {

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@Autowired
	ToolCallbackProvider paymentServiceTools;

	@Test
	public void paymentStatuses() {
		ToolCallback getPaymentStatus = findToolCallback("getPaymentStatus");

		String content = this.chatClient.prompt().advisors(new SimpleLoggerAdvisor()).tools(getPaymentStatus).user("""
				What is the status of my payment transactions 001, 002 and 003?
				If required invoke the function per transaction.
				""").call().content();

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");
	}

	@RepeatedTest(5)
	public void streamingPaymentStatuses() {
		ToolCallback getPaymentStatuses = findToolCallback("getPaymentStatuses");

		Flux<String> streamContent = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.tools(getPaymentStatuses)
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					If required invoke the function per transaction.
					""")
			.stream()
			.content();

		String content = streamContent.collectList().block().stream().collect(Collectors.joining());

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");

		// Quota rate
		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
		}
	}

	private ToolCallback findToolCallback(String name) {
		return Arrays.stream(this.paymentServiceTools.getToolCallbacks())
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

	public static class PaymentService {

		@Tool(description = "Get the status of a single payment transaction")
		public Status getPaymentStatus(Transaction transaction) {
			return DATASET.get(transaction);
		}

		@Tool(description = "Get the list statuses of a list of payment transactions")
		public List<Status> getPaymentStatuses(List<Transaction> transactions) {
			return transactions.stream().map(t -> DATASET.get(t)).toList();
		}

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public ToolCallbackProvider paymentServiceTools() {
			return ToolCallbackProvider.from(List.of(ToolCallbacks.from(new PaymentService())));
		}

		@Bean
		public ChatClient chatClient(GoogleGenAiChatModel chatModel, ToolCallingManager toolCallingManager) {
			return ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();
		}

		@Bean
		public Client genAiClient() {
			String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
			String location = System.getenv("GOOGLE_CLOUD_LOCATION");
			return Client.builder().project(projectId).location(location).vertexAI(true).build();
		}

		@Bean
		public GoogleGenAiChatModel vertexAiChatModel(Client genAiClient) {
			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.options(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
					.temperature(0.1)
					.build())
				.build();
		}

		@Bean
		ToolCallingManager toolCallingManager(ObjectProvider<ObservationRegistry> observationRegistry) {
			return ToolCallingManager.builder()
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(false))
				.build();
		}

	}

}
