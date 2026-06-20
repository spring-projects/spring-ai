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

package org.springframework.ai.model.mistralai.autoconfigure.tool;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same test as {@link PaymentStatusBeanIT} but using {@link OpenAiChatModel} for Mistral
 * AI Function Calling implementation.
 *
 * @author Christian Tzolov
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class PaymentStatusBeanOpenAiIT {

	// Assuming we have the following data
	public static final Map<String, StatusDate> DATA = Map.of("T1001", new StatusDate("Paid", "2021-10-05"), "T1002",
			new StatusDate("Unpaid", "2021-10-06"), "T1003", new StatusDate("Paid", "2021-10-07"), "T1004",
			new StatusDate("Paid", "2021-10-05"), "T1005", new StatusDate("Pending", "2021-10-08"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.chat.api-key=" + System.getenv("MISTRAL_AI_API_KEY"),
				"spring.ai.openai.chat.base-url=https://api.mistral.ai/v1")
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, RestClientAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
				WebClientAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void helloWorld() {

		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			ChatResponse response = ChatClient.create(chatModel)
				.prompt("Tell me a joke?")
				.options(OpenAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue()))
				.call()
				.chatResponse();

			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		});
	}

	@Test
	void functionCallTest() {

		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			ToolCallback retrievePaymentStatus = context.getBean("retrievePaymentStatus", ToolCallback.class);
			ToolCallback retrievePaymentDate = context.getBean("retrievePaymentDate", ToolCallback.class);

			ChatResponse response = ChatClient.create(chatModel)
				.prompt("What's the status of my transaction with id T1001?")
				.options(OpenAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue()))
				.tools(retrievePaymentStatus, retrievePaymentDate)
				.call()
				.chatResponse();

			assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("T1001");
			assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("paid");
		});
	}

	record StatusDate(String status, String date) {

	}

	@Configuration
	static class Config {

		@Bean
		public ToolCallback retrievePaymentStatus() {
			return FunctionToolCallback
				.builder("retrievePaymentStatus",
						(Transaction transaction) -> new Status(DATA.get(transaction.transactionId).status()))
				.description("Get payment status of a transaction")
				.inputType(Transaction.class)
				.build();
		}

		@Bean
		public ToolCallback retrievePaymentDate() {
			return FunctionToolCallback
				.builder("retrievePaymentDate",
						(Transaction transaction) -> new Date(DATA.get(transaction.transactionId).date()))
				.description("Get payment date of a transaction")
				.inputType(Transaction.class)
				.build();
		}

		public record Transaction(@JsonProperty(required = true, value = "transaction_id") String transactionId) {

		}

		public record Status(@JsonProperty(required = true, value = "status") String status) {

		}

		public record Date(@JsonProperty(required = true, value = "date") String date) {

		}

	}

}
