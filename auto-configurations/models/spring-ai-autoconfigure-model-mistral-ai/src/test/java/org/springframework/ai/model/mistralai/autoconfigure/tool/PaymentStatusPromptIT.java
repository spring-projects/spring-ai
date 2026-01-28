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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
public class PaymentStatusPromptIT {

	// Assuming we have the following payment data.
	public static final Map<Transaction, StatusDate> DATA = Map.of(new Transaction("T1001"),
			new StatusDate("Paid", "2021-10-05"), new Transaction("T1002"), new StatusDate("Unpaid", "2021-10-06"),
			new Transaction("T1003"), new StatusDate("Paid", "2021-10-07"), new Transaction("T1004"),
			new StatusDate("Paid", "2021-10-05"), new Transaction("T1005"), new StatusDate("Pending", "2021-10-08"));

	private final Logger logger = LoggerFactory.getLogger(WeatherServicePromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mistralai.chat.options.model=" + MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.run(context -> {

				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);

				UserMessage userMessage = new UserMessage("What's the status of my transaction with id T1001?");

				var promptOptions = MistralAiChatOptions.builder()
					.toolCallbacks(List.of(FunctionToolCallback
						.builder("retrievePaymentStatus",
								(Transaction transaction) -> new Status(DATA.get(transaction).status()))
						.description("Get payment status of a transaction")
						.inputType(Transaction.class)
						.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("T1001");
				assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("paid");
			});
	}

	public record Transaction(@JsonProperty(required = true, value = "transaction_id") String id) {

	}

	public record Status(@JsonProperty(required = true, value = "status") String status) {

	}

	record StatusDate(String status, String date) {

	}

}
