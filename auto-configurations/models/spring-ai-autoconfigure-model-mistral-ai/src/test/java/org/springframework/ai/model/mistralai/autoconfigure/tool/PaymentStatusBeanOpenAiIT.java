/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same test as {@link PaymentStatusBeanIT} but using {@link OpenAiChatModel} for Mistral
 * AI Function Calling implementation.
 *
 * @author Christian Tzolov
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
class PaymentStatusBeanOpenAiIT {

	// Assuming we have the following data
	public static final Map<String, StatusDate> DATA = Map.of("T1001", new StatusDate("Paid", "2021-10-05"), "T1002",
			new StatusDate("Unpaid", "2021-10-06"), "T1003", new StatusDate("Paid", "2021-10-07"), "T1004",
			new StatusDate("Paid", "2021-10-05"), "T1005", new StatusDate("Pending", "2021-10-08"));

	private final Logger logger = LoggerFactory.getLogger(PaymentStatusBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"),
				"spring.ai.openai.chat.base-url=https://api.mistral.ai")
		.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {

		this.contextRunner
			.withPropertyValues(
					"spring.ai.openai.chat.options.model=" + MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

				ChatResponse response = chatModel
					.call(new Prompt(List.of(new UserMessage("What's the status of my transaction with id T1001?")),
							OpenAiChatOptions.builder()
								.toolNames("retrievePaymentStatus")
								.toolNames("retrievePaymentDate")
								.build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("T1001");
				assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("paid");
			});
	}

	record StatusDate(String status, String date) {

	}

	@Configuration
	static class Config {

		@Bean
		@Description("Get payment status of a transaction")
		public Function<Transaction, Status> retrievePaymentStatus() {
			return transaction -> new Status(DATA.get(transaction.transactionId).status());
		}

		@Bean
		@Description("Get payment date of a transaction")
		public Function<Transaction, Date> retrievePaymentDate() {
			return transaction -> new Date(DATA.get(transaction.transactionId).date());
		}

		public record Transaction(@JsonProperty(required = true, value = "transaction_id") String transactionId) {

		}

		public record Status(@JsonProperty(required = true, value = "status") String status) {

		}

		public record Date(@JsonProperty(required = true, value = "date") String date) {

		}

	}

}
