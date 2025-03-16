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

package org.springframework.ai.openai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiPaymentTransactionIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiPaymentTransactionIT.class);

	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));

	@Autowired
	ChatClient chatClient;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "paymentStatus", "paymentStatuses" })
	public void transactionPaymentStatuses(String functionName) {
		List<TransactionStatusResponse> content = this.chatClient.prompt()
			.advisors(new LoggingAdvisor())
			.tools(functionName)
			.user("""
					What is the status of my payment transactions 001, 002 and 003?
					""")
			.call()
			.entity(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {

			});

		logger.info("" + content);

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

		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {

		});

		Flux<String> flux = this.chatClient.prompt()
			.advisors(new LoggingAdvisor())
			.tools(functionName)
			.user(u -> u.text("""
					What is the status of my payment transactions 001, 002 and 003?

					{format}
					""").param("format", converter.getFormat()))
			.stream()
			.content();

		String content = flux.collectList().block().stream().collect(Collectors.joining());

		List<TransactionStatusResponse> structure = converter.convert(content);
		logger.info("" + content);

		assertThat(structure.get(0).id()).isEqualTo("001");
		assertThat(structure.get(0).status()).isEqualTo("pending");

		assertThat(structure.get(1).id()).isEqualTo("002");
		assertThat(structure.get(1).status()).isEqualTo("approved");

		assertThat(structure.get(2).id()).isEqualTo("003");
		assertThat(structure.get(2).status()).isEqualTo("rejected");
	}

	record TransactionStatusResponse(String id, String status) {

	}

	private static class LoggingAdvisor implements CallAroundAdvisor {

		private final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);

		public String getName() {
			return this.getClass().getSimpleName();
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

			advisedRequest = this.before(advisedRequest);

			AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);

			this.observeAfter(advisedResponse);

			return advisedResponse;
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
				logger.info("Single transaction: " + transaction);
				return DATASET.get(transaction);
			};
		}

		@Bean
		@Description("Get the list statuses of a list of payment transactions")
		public Function<Transactions, Statuses> paymentStatuses() {
			return transactions -> {
				logger.info("List of transactions: " + transactions);
				return new Statuses(transactions.transactions().stream().map(t -> DATASET.get(t)).toList());
			};
		}

		@Bean
		public ChatClient chatClient(OpenAiChatModel chatModel) {
			return ChatClient.builder(chatModel).build();
		}

		@Bean
		public OpenAiApi chatCompletionApi() {
			return OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi, ToolCallingManager toolCallingManager) {
			return OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.toolCallingManager(toolCallingManager)
				.defaultOptions(
						OpenAiChatOptions.builder().model(ChatModel.GPT_4_O_MINI.getName()).temperature(0.1).build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.build();
		}

		@Bean
		@ConditionalOnMissingBean
		ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext,
				List<FunctionCallback> functionCallbacks, List<ToolCallbackProvider> tcbProviders) {

			List<FunctionCallback> allFunctionAndToolCallbacks = new ArrayList<>(functionCallbacks);
			tcbProviders.stream()
				.map(pr -> List.of(pr.getToolCallbacks()))
				.forEach(allFunctionAndToolCallbacks::addAll);

			var staticToolCallbackResolver = new StaticToolCallbackResolver(allFunctionAndToolCallbacks);

			var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
				.applicationContext(applicationContext)
				.build();

			return new DelegatingToolCallbackResolver(
					List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));
		}

		@Bean
		@ConditionalOnMissingBean
		ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
			return new DefaultToolExecutionExceptionProcessor(false);
		}

		@Bean
		@ConditionalOnMissingBean
		ToolCallingManager toolCallingManager(ToolCallbackResolver toolCallbackResolver,
				ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
				ObjectProvider<ObservationRegistry> observationRegistry) {
			return ToolCallingManager.builder()
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.toolCallbackResolver(toolCallbackResolver)
				.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
				.build();
		}

	}

}
