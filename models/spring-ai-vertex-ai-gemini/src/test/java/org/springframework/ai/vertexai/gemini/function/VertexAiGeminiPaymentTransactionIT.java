/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.function;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiGeminiPaymentTransactionIT {

	private final static Logger logger = LoggerFactory.getLogger(VertexAiGeminiPaymentTransactionIT.class);

	@Autowired
	ChatClient chatClient;

	record TransactionStatusResponse(String id, String status) {
	}

	private static class LoggingAdvisor implements RequestAdvisor, ResponseAdvisor {

		private final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);

		@Override
		public String getName() {
			return this.getClass().getSimpleName();
		}

		@Override
		public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
			logger.info("System text: \n" + request.systemText());
			logger.info("System params: " + request.systemParams());
			logger.info("User text: \n" + request.userText());
			logger.info("User params:" + request.userParams());
			logger.info("Function names: " + request.functionNames());

			logger.info("Options: " + request.chatOptions().toString());

			return request;
		}

		@Override
		public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
			logger.info("Response: " + response);
			return response;
		}

	}

	@Test
	public void paymentStatuses() {
		// @formatter:off
		String content = this.chatClient.prompt()
				.advisors(new LoggingAdvisor())
				.functions("paymentStatus")
				.user("""
				What is the status of my payment transactions 001, 002 and 003?
				If requred invoke the function per transaction.
				""").call().content();

		logger.info("" + content);

		assertThat(content).contains("001", "002", "003");
		assertThat(content).contains("pending", "approved", "rejected");
	}

	@RepeatedTest(5)
	public void streamingPaymentStatuses() {

		Flux<String> streamContent = this.chatClient.prompt()
				.advisors(new LoggingAdvisor())
				.functions("paymentStatus")
				// .functions("paymentStatuses")
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
		} catch (InterruptedException e) {
		}
	}

	record Transaction(String id) {
	}

	record Status(String name) {
	}

	record Transactions(List<Transaction> transactions) {
	}

	record Statuses(List<Status> statuses) {
	}

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		@Description("Get the status of a single payment transaction")
		public Function<Transaction, Status> paymentStatus() {
			return transaction -> {
				logger.info("Single Transaction: " + transaction);
				return DATASET.get(transaction);
			};
		}

		@Bean
		@Description("Get the list statuses of a list of payment transactions")
		public Function<Transactions, Statuses> paymentStatuses() {
			return transactions -> {
				logger.info("Transactions: " + transactions);
				return new Statuses(transactions.transactions().stream().map(t -> DATASET.get(t)).toList());
			};
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
		public VertexAiGeminiChatModel vertexAiChatModel(VertexAI vertexAi, ApplicationContext context) {

			FunctionCallbackContext functionCallbackContext = springAiFunctionManager(context);

			return new VertexAiGeminiChatModel(vertexAi,
					VertexAiGeminiChatOptions.builder()
							.withModel(VertexAiGeminiChatModel.ChatModel.GEMINI_1_5_FLASH)
							.withTemperature(0.1f)
							.build(),
					functionCallbackContext);
		}

		/**
		 * Because of the OPEN_API_SCHEMA type, the FunctionCallbackContext instance
		 * must
		 * different from the other JSON schema types.
		 */
		private FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
			FunctionCallbackContext manager = new FunctionCallbackContext();
			manager.setSchemaType(SchemaType.OPEN_API_SCHEMA);
			manager.setApplicationContext(context);
			return manager;
		}

	}

}
