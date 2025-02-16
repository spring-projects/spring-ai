/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback.SchemaType;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
@Deprecated
public class VertexAiGeminiPaymentTransactionDeprecatedIT {

	private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiPaymentTransactionDeprecatedIT.class);

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@Test
	public void paymentStatuses() {
		// @formatter:off
		String content = this.chatClient.prompt()
				.advisors(new LoggingAdvisor())
				.tools("paymentStatus")
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
				.tools("paymentStatus")
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

	private static class LoggingAdvisor implements CallAroundAdvisor {

		private final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);

		@Override
		public String getName() {
			return this.getClass().getSimpleName();
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
			var response = chain.nextAroundCall(before(advisedRequest));
			observeAfter(response);
			return response;
		}

		private AdvisedRequest before(AdvisedRequest request) {
			logger.info("System text: \n" + request.systemText());
			logger.info("System params: " + request.systemParams());
			logger.info("User text: \n" + request.userText());
			logger.info("User params:" + request.userParams());
			logger.info("Function names: " + request.functionNames());

			logger.info("Options: " + request.chatOptions().toString());

			return request;
		}

		private void observeAfter(AdvisedResponse advisedResponse) {
			logger.info("Response: " + advisedResponse.response());
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

			FunctionCallbackResolver functionCallbackResolver = springAiFunctionManager(context);

			return new VertexAiGeminiChatModel(vertexAi,
					VertexAiGeminiChatOptions.builder()
							.model(VertexAiGeminiChatModel.ChatModel.GEMINI_1_5_FLASH)
							.temperature(0.1)
							.build(),
					functionCallbackResolver);
		}

		/**
		 * Because of the OPEN_API_SCHEMA type, the FunctionCallbackResolver instance
		 * must
		 * different from the other JSON schema types.
		 */
		private FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
			DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
			manager.setSchemaType(SchemaType.OPEN_API_SCHEMA);
			manager.setApplicationContext(context);
			return manager;
		}

	}

}
