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

package org.springframework.ai.google.genai.tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Dan Dobrin
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
public class GoogleGenAiPaymentTransactionToolsIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiPaymentTransactionToolsIT.class);

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@Test
	public void paymentStatuses() {
		// @formatter:off
		String content = this.chatClient.prompt()
				.advisors(new SimpleLoggerAdvisor())
				.tools(new MyTools())
				.user("""
				What is the status of my payment transactions 001, 002 and 003?
				If required invoke the function per transaction.
				""").call().content();
		// @formatter:on
		logger.info("" + content);

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");
	}

	@RepeatedTest(5)
	public void streamingPaymentStatuses() {

		Flux<String> streamContent = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.tools(new MyTools())
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					If required invoke the function per transaction.
					""")
			.stream()
			.content();

		String content = streamContent.collectList().block().stream().collect(Collectors.joining());

		logger.info(content);

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");

		// Quota rate
		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
		}
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

	public static class MyTools {

		@Tool(description = "Get the list statuses of a list of payment transactions")
		public Statuses paymentStatuses(Transactions transactions) {
			logger.info("Transactions: " + transactions);
			return new Statuses(transactions.transactions().stream().map(t -> DATASET.get(t)).toList());
		}

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public ChatClient chatClient(GoogleGenAiChatModel chatModel) {
			return ChatClient.builder(chatModel).build();
		}

		@Bean
		public Client genAiClient() {

			String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
			String location = System.getenv("GOOGLE_CLOUD_LOCATION");

			// TODO: Update this to use the proper GenAI client initialization
			return Client.builder().project(projectId).location(location).vertexAI(true).build();
		}

		@Bean
		public GoogleGenAiChatModel vertexAiChatModel(Client genAiClient, ToolCallingManager toolCallingManager) {

			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.toolCallingManager(toolCallingManager)
				.defaultOptions(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH)
					.temperature(0.1)
					.build())
				.build();
		}

		@Bean
		ToolCallingManager toolCallingManager(GenericApplicationContext applicationContext,
				List<ToolCallback> toolCallbacks, ObjectProvider<ObservationRegistry> observationRegistry) {

			var staticToolCallbackResolver = new StaticToolCallbackResolver(toolCallbacks);
			var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
				.applicationContext(applicationContext)
				.build();

			ToolCallbackResolver toolCallbackResolver = new DelegatingToolCallbackResolver(
					List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));

			return ToolCallingManager.builder()
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.toolCallbackResolver(toolCallbackResolver)
				.toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(false))
				.build();
		}

	}

}
