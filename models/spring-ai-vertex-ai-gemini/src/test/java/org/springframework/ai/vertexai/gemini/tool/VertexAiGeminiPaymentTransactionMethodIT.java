/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
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
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiGeminiPaymentTransactionMethodIT {

	private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiPaymentTransactionMethodIT.class);

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@Test
	public void paymentStatuses() {

		String content = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.toolNames("paymentStatus")
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					If requred invoke the function per transaction.
					""")
			.call()
			.content();
		logger.info("" + content);

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");
	}

	@RepeatedTest(5)
	public void streamingPaymentStatuses() {

		Flux<String> streamContent = this.chatClient.prompt()
			.advisors(new SimpleLoggerAdvisor())
			.toolNames("paymentStatus")
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					If requred invoke the function per transaction.
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

	public static class PaymentService {

		@Tool(description = "Get the status of a single payment transaction")
		public Status paymentStatus(Transaction transaction) {
			logger.info("Single Transaction: " + transaction);
			return DATASET.get(transaction);
		}

		@Tool(description = "Get the list statuses of a list of payment transactions")
		public List<Status> statusespaymentStatuses(List<Transaction> transactions) {
			logger.info("Transactions: " + transactions);
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
		public ChatClient chatClient(VertexAiGeminiChatModel chatModel) {
			return ChatClient.builder(chatModel).build();
		}

		@Bean
		public VertexAI vertexAiApi() {

			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");

			return new VertexAI.Builder().setLocation(location)
				.setProjectId(projectId)
				.setTransport(Transport.REST)
				// .setTransport(Transport.GRPC)
				.build();
		}

		@Bean
		public VertexAiGeminiChatModel vertexAiChatModel(VertexAI vertexAi, ToolCallingManager toolCallingManager) {

			return VertexAiGeminiChatModel.builder()
				.vertexAI(vertexAi)
				.toolCallingManager(toolCallingManager)
				.defaultOptions(VertexAiGeminiChatOptions.builder()
					.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
					.temperature(0.1)
					.build())
				.build();
		}

		@Bean
		ToolCallingManager toolCallingManager(GenericApplicationContext applicationContext,
				List<ToolCallbackProvider> tcps, List<ToolCallback> toolCallbacks,
				ObjectProvider<ObservationRegistry> observationRegistry) {

			List<ToolCallback> allToolCallbacks = new ArrayList(toolCallbacks);
			tcps.stream().map(pr -> List.of(pr.getToolCallbacks())).forEach(allToolCallbacks::addAll);

			var staticToolCallbackResolver = new StaticToolCallbackResolver(allToolCallbacks);

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
